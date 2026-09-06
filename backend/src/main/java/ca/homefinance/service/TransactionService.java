package ca.homefinance.service;

import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final CategoryRepository categoryRepository;
    private final PersonRepository personRepository;

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction saveTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public Transaction createTransaction(TransactionDto transactionDto) {
        Transaction transaction = new Transaction();
        transaction.setAmount(transactionDto.getAmount());
        transaction.setDate(transactionDto.getDate());
        transaction.setEntity(transactionDto.getEntity());
        transaction.setDetails(transactionDto.getDetails());
        transaction.setAccount(Transaction.AccountType.valueOf(transactionDto.getAccount()));
        transaction.setTransactionType(Transaction.TransactionType.valueOf(transactionDto.getTransactionType()));
        transaction.setCategory(categoryRepository.findById(Integer.parseInt(transactionDto.getCategory())).orElseThrow());
        transaction.setPerson(personRepository.findById(Integer.parseInt(transactionDto.getPerson())).orElseThrow());
        return saveTransaction(transaction);
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

    public MonthlyBalanceResponseDto getMonthlyBalance(int month, int year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Transaction> expenses = searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate,
                Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA),
                Arrays.asList(Transaction.TransactionType.EXPENSE));

        List<Transaction> cardPayments = searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate,
                Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX),
                Arrays.asList(Transaction.TransactionType.CARDPAYMENT));

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

        BigDecimal[] expenseTotals = splitByAccount(expenses);
        BigDecimal[] cardPaymentTotals = splitCardPaymentsByPerson(cardPayments);
        BigDecimal[] rentalBillTotals = splitByAccount(rentalBillIncome);
        BigDecimal[] rentalRentTotals = splitByAccount(rentalRentIncome);
        BigDecimal[] billTotals = splitByAccount(billsPaid);

        BigDecimal totalExpensesMinusIncomeAsanka = expenseTotals[0]
                .add(cardPaymentTotals[0])
                .add(billTotals[0])
                .subtract(rentalBillTotals[0])
                .subtract(rentalRentTotals[0]);

        BigDecimal totalExpensesMinusIncomeDivya = expenseTotals[1]
                .add(cardPaymentTotals[1])
                .add(billTotals[1])
                .subtract(rentalBillTotals[1])
                .subtract(rentalRentTotals[1]);

        // if minus value they owe the other person, if plus the other person owes.
        BigDecimal amountDividedByTwoAsanka = totalExpensesMinusIncomeAsanka.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal amountDividedByTwoDivya = totalExpensesMinusIncomeDivya.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        BigDecimal difference = amountDividedByTwoDivya.subtract(amountDividedByTwoAsanka);

        MonthlyBalanceResponseDto monthlyBalanceResponseDto = new MonthlyBalanceResponseDto();
        monthlyBalanceResponseDto.setBalanceAmount(difference.abs());
        monthlyBalanceResponseDto.setAsankaPaid(totalExpensesMinusIncomeAsanka.abs());
        monthlyBalanceResponseDto.setDivyaPaid(totalExpensesMinusIncomeDivya.abs());
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
                tx.getPerson().getName()
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

    private static BigDecimal[] splitCardPaymentsByPerson(List<Transaction> transactions) {
        BigDecimal asankaTotal = BigDecimal.ZERO;
        BigDecimal divyaTotal = BigDecimal.ZERO;

        for (Transaction txn : transactions) {
            if (txn.getPerson().getPersonId() == 1) {
                asankaTotal = asankaTotal.add(txn.getAmount().negate());
            } else if (txn.getPerson().getPersonId() == 2) {
                divyaTotal = divyaTotal.add(txn.getAmount().negate());
            }
        }

        return new BigDecimal[]{asankaTotal, divyaTotal};
    }
}
