package ca.homefinance.service;

import ca.homefinance.dto.BalanceLineDto;
import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
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

    private final TransactionRepository transactionRepository;
    private final PersonRepository personRepository;
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

        Transaction.TransactionType transactionType = parseEnum(Transaction.TransactionType.class,
                transactionDto.getTransactionType(), "transaction type");
        BigDecimal magnitude = transactionDto.getAmount().abs();
        boolean outflowSign = transactionType == Transaction.TransactionType.REFUND
                || transactionType == Transaction.TransactionType.CARDPAYMENT;

        Transaction transaction = new Transaction();
        transaction.setAmount(outflowSign ? magnitude.negate() : magnitude);
        transaction.setDate(transactionDto.getDate());
        transaction.setEntity(transactionDto.getEntity().trim());
        transaction.setDetails(transactionDto.getDetails());
        transaction.setAccount(parseEnum(Transaction.AccountType.class, transactionDto.getAccount(), "account"));
        transaction.setTransactionType(transactionType);
        transaction.setPerson(personRepository.findById(parseId(transactionDto.getPerson(), "person"))
                .orElseThrow(() -> badRequest("Unknown person")));

        if (!allowDuplicate && transactionRepository.countByAccountAndDateAndEntityAndAmount(
                transaction.getAccount(), transaction.getDate(), transaction.getEntity(), transaction.getAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A transaction with the same account, date, merchant and amount already exists");
        }

        Category category = transactionCategorizationService.categorizeTransaction(
                transaction.getEntity(), transaction.getDetails(), transaction.getAmount(), transaction.getDate());
        if (category.getCategoryId() != null) {
            transaction.setCategory(category);
        } else {
            transaction.setUncategorizedTransaction(category.getUncategorizedTransaction());
        }
        return saveTransaction(transaction);
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

    public List<Transaction> searchTransactionByDateRangeAccountTypeTransactionType(LocalDate startDate, LocalDate endDate, List<Transaction.AccountType> accounts, List<Transaction.TransactionType> transactionTypes) {
        return transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(startDate, endDate, accounts, transactionTypes);
    }

    /** The transactions that make up a month's balance, grouped by how each group contributes. */
    private record BalanceInputs(List<Transaction> expenses, List<Transaction> cardSpending,
                                 List<Transaction> rentalBillIncome, List<Transaction> rentalRentIncome,
                                 List<Transaction> billsPaid) {
    }

    private BalanceInputs loadBalanceInputs(int month, int year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Transaction> expenses = searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate,
                Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA),
                Arrays.asList(Transaction.TransactionType.EXPENSE));

        // CIBC and AMEX hold shared/common purchases only, regardless of whose physical card was used,
        // so every purchase (and its refunds) counts toward the shared pool - not just the CARDPAYMENT
        // rows, which merely record when the bill happens to get paid and lag behind the spending by a
        // billing cycle. Divya settles both cards in full every month, so the net amount is her
        // contribution to the pool.
        List<Transaction> cardSpending = searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate,
                Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX),
                Arrays.asList(Transaction.TransactionType.EXPENSE, Transaction.TransactionType.REFUND));

        List<Transaction> rentalBillIncome = searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate,
                Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA),
                Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME));

        List<Transaction> rentalRentIncome = searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate,
                Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA),
                Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME));

        List<Transaction> billsPaid = searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate,
                Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA),
                Arrays.asList(Transaction.TransactionType.BILL));

        return new BalanceInputs(expenses, cardSpending, rentalBillIncome, rentalRentIncome, billsPaid);
    }

    /**
     * The transactions behind one person's "paid" figure; the contributions sum to
     * {@code asankaPaid} / {@code divyaPaid} from {@link #getMonthlyBalance}.
     */
    public List<BalanceLineDto> getMonthlyBalanceTransactions(int month, int year, String person) {
        if (month < 1 || month > 12) {
            throw badRequest("Month must be between 1 and 12");
        }
        Transaction.AccountType account;
        try {
            account = Transaction.AccountType.valueOf(person == null ? "" : person.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw badRequest("Invalid person: " + person);
        }
        if (account != Transaction.AccountType.ASANKA && account != Transaction.AccountType.DIVYA) {
            throw badRequest("Invalid person: " + person);
        }

        BalanceInputs inputs = loadBalanceInputs(month, year);
        List<BalanceLineDto> lines = new ArrayList<>();
        addBalanceLines(lines, inputs.expenses(), account, "EXPENSE", false);
        addBalanceLines(lines, inputs.billsPaid(), account, "BILL", false);
        addBalanceLines(lines, inputs.rentalBillIncome(), account, "RENTAL_BILL_INCOME", true);
        addBalanceLines(lines, inputs.rentalRentIncome(), account, "RENTAL_RENT_INCOME", true);
        if (account == Transaction.AccountType.DIVYA) {
            // Shared CIBC/AMEX spending is attributed to Divya, whichever card was used.
            addBalanceLines(lines, inputs.cardSpending(), null, "CARD_SPENDING", false);
        }
        lines.sort(Comparator.comparing((BalanceLineDto l) -> l.getTransaction().getDate()));
        return lines;
    }

    private void addBalanceLines(List<BalanceLineDto> lines, List<Transaction> source,
                                 Transaction.AccountType account, String kind, boolean negate) {
        for (Transaction tx : source) {
            if (account != null && tx.getAccount() != account) {
                continue;
            }
            lines.add(new BalanceLineDto(toDto(tx), negate ? tx.getAmount().negate() : tx.getAmount(), kind));
        }
    }

    public MonthlyBalanceResponseDto getMonthlyBalance(int month, int year) {
        BalanceInputs inputs = loadBalanceInputs(month, year);

        BigDecimal[] expenseTotals = splitByAccount(inputs.expenses());
        BigDecimal cardSpendingNet = inputs.cardSpending().stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal[] rentalBillTotals = splitByAccount(inputs.rentalBillIncome());
        BigDecimal[] rentalRentTotals = splitByAccount(inputs.rentalRentIncome());
        BigDecimal[] billTotals = splitByAccount(inputs.billsPaid());

        BigDecimal totalExpensesMinusIncomeAsanka = expenseTotals[0]
                .add(billTotals[0])
                .subtract(rentalBillTotals[0])
                .subtract(rentalRentTotals[0]);

        BigDecimal totalExpensesMinusIncomeDivya = expenseTotals[1]
                .add(billTotals[1])
                .add(cardSpendingNet)
                .subtract(rentalBillTotals[1])
                .subtract(rentalRentTotals[1]);

        // if minus value they owe the other person, if plus the other person owes.
        BigDecimal amountDividedByTwoAsanka = totalExpensesMinusIncomeAsanka.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal amountDividedByTwoDivya = totalExpensesMinusIncomeDivya.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal difference = amountDividedByTwoDivya.subtract(amountDividedByTwoAsanka);

        MonthlyBalanceResponseDto monthlyBalanceResponseDto = new MonthlyBalanceResponseDto();
        monthlyBalanceResponseDto.setBalanceAmount(difference.abs());
        // Net contribution: negative when rental income received exceeds what the person paid out.
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
                tx.getAmount(),
                tx.getDate(),
                tx.getEntity(),
                tx.getDetails(),
                tx.getAccount().name(),
                tx.getTransactionType().name(),
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
            if (tx.getTransactionType() == Transaction.TransactionType.EXPENSE) {
                String key = keyExtractor.apply(tx);
                totals.merge(key, tx.getAmount(), BigDecimal::add);
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

    private static BigDecimal[] splitByAccount(List<Transaction> transactions) {
        BigDecimal asankaTotal = BigDecimal.ZERO;
        BigDecimal divyaTotal = BigDecimal.ZERO;

        for (Transaction txn : transactions) {
            if (txn.getAccount() == Transaction.AccountType.ASANKA) {
                asankaTotal = asankaTotal.add(txn.getAmount());
            } else if (txn.getAccount() == Transaction.AccountType.DIVYA) {
                divyaTotal = divyaTotal.add(txn.getAmount());
            }
        }

        return new BigDecimal[]{asankaTotal, divyaTotal};
    }
}
