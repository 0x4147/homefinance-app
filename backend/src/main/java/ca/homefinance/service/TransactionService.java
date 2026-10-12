package ca.homefinance.service;

import ca.homefinance.dto.BalanceLineDto;
import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.AccountRepository;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private static final String ASANKA = "ASANKA";
    private static final String DIVYA = "DIVYA";

    private final TransactionRepository transactionRepository;
    private final PersonRepository personRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionCategorizationService transactionCategorizationService;

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction saveTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public Transaction createTransaction(TransactionDto transactionDto) {
        return createTransaction(transactionDto, false);
    }

    public Transaction createTransaction(TransactionDto transactionDto, boolean allowDuplicate) {
        if (transactionDto.getAmount() == null || transactionDto.getAmount().signum() == 0) {
            throw badRequest("Amount must be non-zero");
        }
        if (transactionDto.getDate() == null) {
            throw badRequest("Date is required");
        }
        if (transactionDto.getEntity() == null || transactionDto.getEntity().isBlank()) {
            throw badRequest("Merchant is required");
        }

        // The form sends a positive amount and says which way the money moved: only an expense is money out.
        Transaction.TransactionType type = parseEnum(Transaction.TransactionType.class,
                transactionDto.getTransactionType(), "transaction type");
        BigDecimal magnitude = transactionDto.getAmount().abs();

        Transaction transaction = new Transaction();
        transaction.setAmount(type == Transaction.TransactionType.EXPENSE ? magnitude.negate() : magnitude);
        transaction.setDate(transactionDto.getDate());
        transaction.setEntity(transactionDto.getEntity().trim());
        transaction.setDetails(transactionDto.getDetails());
        transaction.setAccount(accountRepository.findByCode(transactionDto.getAccount())
                .orElseThrow(() -> badRequest("Invalid account: " + transactionDto.getAccount())));
        transaction.setPerson(personRepository.findById(parseId(transactionDto.getPerson(), "person"))
                .orElseThrow(() -> badRequest("Unknown person")));

        if (!allowDuplicate && transactionRepository.countByAccount_CodeAndDateAndEntityAndAmount(
                transaction.getAccount().getCode(), transaction.getDate(), transaction.getEntity(), transaction.getAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A transaction with the same account, date, merchant and amount already exists");
        }

        Category category = resolveCategory(transactionDto.getCategory());
        if (category == null) {
            category = transactionCategorizationService.categorizeTransaction(
                    transaction.getEntity(), transaction.getDetails(), transaction.getAmount(), transaction.getDate());
        }
        if (category.getCategoryId() != null) {
            transaction.setCategory(category);
        } else {
            transaction.setUncategorizedTransaction(category.getUncategorizedTransaction());
        }
        return saveTransaction(transaction);
    }

    /** The category the user picked, or null to let the categorizer decide. */
    private Category resolveCategory(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Category category = categoryRepository.findByName(name.trim());
        if (category == null) {
            throw badRequest("Unknown category: " + name);
        }
        return category;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw badRequest("Invalid " + field + ": " + value);
        }
    }

    private static Integer parseId(String value, String field) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw badRequest("Invalid " + field + " id: " + value);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public List<Transaction> getTransactionsByDateRange(LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByDateBetween(startDate, endDate);
    }

    public List<TransactionDto> getTransactionDtosByDateRange(LocalDate startDate, LocalDate endDate) {
        return getTransactionsByDateRange(startDate, endDate).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * The transactions that make up a month's shared-expense balance: shared, and in a category that counts.
     * Each is paid by its account's payer (CIBC and AMEX are settled by one person whichever card was used).
     */
    private List<Transaction> loadBalanceTransactions(int month, int year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return transactionRepository.findBalanceTransactions(startDate, endDate);
    }

    private static String payerCode(Transaction tx) {
        return tx.getAccount().getPayer().getCode();
    }

    /**
     * The transactions behind one person's "paid" figure; the contributions sum to
     * {@code asankaPaid} / {@code divyaPaid} from {@link #getMonthlyBalance}.
     */
    public List<BalanceLineDto> getMonthlyBalanceTransactions(int month, int year, String person) {
        if (month < 1 || month > 12) {
            throw badRequest("Month must be between 1 and 12");
        }
        String payer = person == null ? "" : person.toUpperCase();
        if (!payer.equals(ASANKA) && !payer.equals(DIVYA)) {
            throw badRequest("Invalid person: " + person);
        }

        List<BalanceLineDto> lines = loadBalanceTransactions(month, year).stream()
                .filter(tx -> payerCode(tx).equals(payer))
                .map(tx -> new BalanceLineDto(toDto(tx), tx.balanceContribution(), tx.getType().name()))
                .sorted(Comparator.comparing((BalanceLineDto l) -> l.getTransaction().getDate()))
                .collect(Collectors.toList());
        return lines;
    }

    public MonthlyBalanceResponseDto getMonthlyBalance(int month, int year) {
        List<Transaction> transactions = loadBalanceTransactions(month, year);

        // Net contribution: spending adds, rental income and refunds subtract (negative when income exceeds spending).
        BigDecimal totalExpensesMinusIncomeAsanka = paidBy(transactions, ASANKA);
        BigDecimal totalExpensesMinusIncomeDivya = paidBy(transactions, DIVYA);

        // if minus value they owe the other person, if plus the other person owes.
        BigDecimal amountDividedByTwoAsanka = totalExpensesMinusIncomeAsanka.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal amountDividedByTwoDivya = totalExpensesMinusIncomeDivya.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal difference = amountDividedByTwoDivya.subtract(amountDividedByTwoAsanka);

        MonthlyBalanceResponseDto monthlyBalanceResponseDto = new MonthlyBalanceResponseDto();
        monthlyBalanceResponseDto.setBalanceAmount(difference.abs());
        monthlyBalanceResponseDto.setAsankaPaid(totalExpensesMinusIncomeAsanka);
        monthlyBalanceResponseDto.setDivyaPaid(totalExpensesMinusIncomeDivya);
        monthlyBalanceResponseDto.setMonthAndYear(month + ", " + year);

        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // Asanka owes Divya
            monthlyBalanceResponseDto.setWhoOwes("Asanka");
        } else if (difference.compareTo(BigDecimal.ZERO) < 0) {
            // Divya owes Asanka
            monthlyBalanceResponseDto.setWhoOwes("Divya");
        }

        return monthlyBalanceResponseDto;
    }

    private static BigDecimal paidBy(List<Transaction> transactions, String payer) {
        return transactions.stream()
                .filter(tx -> payerCode(tx).equals(payer))
                .map(Transaction::balanceContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public TransactionSummary getExpensesByCategory(LocalDate startDate, LocalDate endDate) {
        try {
            List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);
            return summarizeExpenses(transactions,
                    tx -> tx.getCategory() == null ? "Unknown" : tx.getCategory().getName(),
                    HashMap::new, HashMap::new);
        } catch (Exception e) {
            log.error("error preparing categories", e);
            throw new RuntimeException(e);
        }
    }

    public TransactionSummary getExpensesByCategoryTop10(LocalDate startDate, LocalDate endDate) {
        return top10(getExpensesByCategory(startDate, endDate));
    }

    public TransactionSummary getExpensesByEntity(LocalDate startDate, LocalDate endDate) {
        try {
            List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);
            return summarizeExpenses(transactions, Transaction::getEntity, HashMap::new, HashMap::new);
        } catch (Exception e) {
            log.error("error preparing entities", e);
            throw new RuntimeException(e);
        }
    }

    public TransactionSummary getExpensesByEntityTop10(LocalDate startDate, LocalDate endDate) {
        return top10(getExpensesByEntity(startDate, endDate));
    }

    public TransactionSummary getExpensesByMonth(YearMonth startMonth, YearMonth endMonth) {
        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = endMonth.atEndOfMonth();
        List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);
        return summarizeExpenses(transactions,
                tx -> YearMonth.from(tx.getDate()).toString(),
                () -> new TreeMap<>(Comparator.reverseOrder()),
                () -> new TreeMap<>(Comparator.reverseOrder()));
    }

    private TransactionDto toDto(Transaction tx) {
        return new TransactionDto(
                tx.getTransactionId(),
                tx.getAmount(),
                tx.getDate(),
                tx.getEntity(),
                tx.getDetails(),
                tx.getAccount().getCode(),
                tx.getType().name(),
                tx.getCategory() != null ? tx.getCategory().getName() : "Unknown",
                tx.getPerson() != null ? tx.getPerson().getName() : ""
        );
    }

    private TransactionSummary summarizeExpenses(List<Transaction> transactions,
                                                  Function<Transaction, String> keyExtractor,
                                                  Supplier<Map<String, BigDecimal>> totalsMapSupplier,
                                                  Supplier<Map<String, List<Transaction>>> detailsMapSupplier) {
        Map<String, BigDecimal> totals = totalsMapSupplier.get();
        Map<String, List<Transaction>> details = detailsMapSupplier.get();

        for (Transaction tx : transactions) {
            if (tx.getType() == Transaction.TransactionType.EXPENSE) {
                String key = keyExtractor.apply(tx);
                // Spending is stored as money out (negative); report it as a positive total.
                totals.merge(key, tx.getAmount().negate(), BigDecimal::add);
                details.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
            }
        }

        return new TransactionSummary(totals, details);
    }

    private TransactionSummary top10(TransactionSummary summary) {
        Map<String, BigDecimal> top10Totals = summary.getTotals().entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new // preserve order
                ));

        Map<String, List<Transaction>> top10Details = summary.getDetails().entrySet().stream()
                .filter(e -> top10Totals.containsKey(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new TransactionSummary(top10Totals, top10Details);
    }
}
