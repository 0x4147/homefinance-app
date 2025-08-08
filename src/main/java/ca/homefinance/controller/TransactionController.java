package ca.homefinance.controller;

import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.service.TransactionService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/transaction")
public class TransactionController {

    @Autowired
    private final TransactionService transactionService;

    @Autowired
    private final CategoryRepository categoryRepository;

    @Autowired
    private final PersonRepository personRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    @GetMapping("/getAllTransactions")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return new ResponseEntity<>(transactionService.getAllTransactions(), HttpStatus.OK);
    }

    @GetMapping("/getTransactionsByDateRange")
    public ResponseEntity<List<TransactionDto>> getTransactionsByDateRange(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        List<TransactionDto> toRet = transactionService.getTransactionsByDateRange(start, end)
                .stream()
                .map(tx -> new TransactionDto(
                        tx.getAmount(),
                        tx.getDate(),
                        tx.getEntity(),
                        tx.getDetails(),
                        tx.getAccount().name(),
                        tx.getTransactionType().name(),
                        tx.getCategory().getName(),
                        tx.getPerson().getName()
                )).collect(Collectors.toList());
        return new ResponseEntity<>(toRet, HttpStatus.OK);
    }

    @PostMapping("/saveTransaction")
    public ResponseEntity<Transaction> saveTransaction(@RequestBody TransactionDto transaction) {
        Transaction transactionEntity = new Transaction();
        transactionEntity.setAmount(transaction.getAmount());
        transactionEntity.setDate(transaction.getDate());
        transactionEntity.setEntity(transaction.getEntity());
        transactionEntity.setDetails(transaction.getDetails());
        transactionEntity.setAccount(Transaction.AccountType.valueOf(transaction.getAccount()));
        transactionEntity.setTransactionType(Transaction.TransactionType.valueOf(transaction.getTransactionType()));
        transactionEntity.setCategory(categoryRepository.findById(Integer.parseInt(transaction.getCategory())).orElseThrow());
        transactionEntity.setPerson(personRepository.findById(Integer.parseInt(transaction.getPerson())).orElseThrow());
        return new ResponseEntity<>(transactionService.saveTransaction(transactionEntity), HttpStatus.OK);
    }

    @GetMapping("/getMonthlyBalance")
    public ResponseEntity<MonthlyBalanceResponseDto> getMonthlyBalance(@RequestParam String month, @RequestParam String year) {
        try {
            LocalDate startLocalDate = LocalDate.of(Integer.valueOf(year), Integer.valueOf(month), 1);
            LocalDate endLocalDate = startLocalDate.withDayOfMonth(startLocalDate.lengthOfMonth());

            BigDecimal totalExpensePaidFromPersonalAccountsAsanka = BigDecimal.ZERO;
            BigDecimal totalExpensePaidFromPersonalAccountsDivya = BigDecimal.ZERO;
            BigDecimal totalCardPaymentsAsanka = BigDecimal.ZERO;
            BigDecimal totalCardPaymentsDivya = BigDecimal.ZERO;
            BigDecimal totalRentalBillIncomeAsanka = BigDecimal.ZERO;
            BigDecimal totalRentalBillIncomeDivya = BigDecimal.ZERO;
            BigDecimal totalRentalRentIncomeAsanka = BigDecimal.ZERO;
            BigDecimal totalRentalRentIncomeDivya = BigDecimal.ZERO;
            BigDecimal totalBillsPaidFromPersonalAccountsAsanka = BigDecimal.ZERO;
            BigDecimal totalBillsPaidFromPersonalAccountsDivya = BigDecimal.ZERO;
            MonthlyBalanceResponseDto monthlyBalanceResponseDto = new MonthlyBalanceResponseDto();

            List<Transaction> expensesPaidFromPersonalAccounts =
                    transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                            startLocalDate,
                            endLocalDate,
                            Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA),
                            Arrays.asList(Transaction.TransactionType.EXPENSE));

            List<Transaction> cardPayments =
                    transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                            startLocalDate,
                            endLocalDate,
                            Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX),
                            Arrays.asList(Transaction.TransactionType.CARDPAYMENT));

            List<Transaction> rentalBillIncome =
                    transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                            startLocalDate,
                            endLocalDate,
                            Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA),
                            Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME));

            List<Transaction> rentalRentIncome =
                    transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                            startLocalDate,
                            endLocalDate,
                            Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA),
                            Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME));

            List<Transaction> billsPaidByFromPersonalAccounts =
                    transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                            startLocalDate,
                            endLocalDate,
                            Arrays.asList(Transaction.AccountType.DIVYA,Transaction.AccountType.ASANKA),
                            Arrays.asList(Transaction.TransactionType.BILL));

            for (Transaction txn : expensesPaidFromPersonalAccounts) {
                if (txn.getAccount() == Transaction.AccountType.ASANKA) {
                    totalExpensePaidFromPersonalAccountsAsanka = totalExpensePaidFromPersonalAccountsAsanka.add(txn.getAmount());
                } else if (txn.getAccount() == Transaction.AccountType.DIVYA) {
                    totalExpensePaidFromPersonalAccountsDivya = totalExpensePaidFromPersonalAccountsDivya.add(txn.getAmount());
                }
            }

            for (Transaction txn : cardPayments) {
                if (txn.getPerson().getPersonId() == 1) {
                    totalCardPaymentsAsanka = totalCardPaymentsAsanka.add(txn.getAmount().negate());
                } else if (txn.getPerson().getPersonId() == 2) {
                    totalCardPaymentsDivya = totalCardPaymentsDivya.add(txn.getAmount().negate());
                }
            }

            for (Transaction txn : rentalBillIncome) {
                if (txn.getAccount().equals(Transaction.AccountType.ASANKA)) {
                    totalRentalBillIncomeAsanka = totalRentalBillIncomeAsanka.add(txn.getAmount());
                } else if (txn.getAccount().equals(Transaction.AccountType.DIVYA)) {
                    totalRentalBillIncomeDivya = totalRentalBillIncomeDivya.add(txn.getAmount());
                }
            }

            for (Transaction txn : rentalRentIncome) {
                if (txn.getAccount().equals(Transaction.AccountType.ASANKA)) {
                    totalRentalRentIncomeAsanka = totalRentalRentIncomeAsanka.add(txn.getAmount());
                } else if (txn.getAccount().equals(Transaction.AccountType.DIVYA)) {
                    totalRentalRentIncomeDivya = totalRentalRentIncomeDivya.add(txn.getAmount());
                }
            }

            for (Transaction txn : billsPaidByFromPersonalAccounts) {
                if (txn.getAccount().equals(Transaction.AccountType.ASANKA)) {
                    totalBillsPaidFromPersonalAccountsAsanka = totalBillsPaidFromPersonalAccountsAsanka.add(txn.getAmount());
                } else if (txn.getAccount().equals(Transaction.AccountType.DIVYA)) {
                    totalBillsPaidFromPersonalAccountsDivya = totalBillsPaidFromPersonalAccountsDivya.add(txn.getAmount());
                }
            }


            BigDecimal totalExpensesMinusIncomeAsanka = totalExpensePaidFromPersonalAccountsAsanka
                    .add(totalCardPaymentsAsanka)
                    .add(totalBillsPaidFromPersonalAccountsAsanka)
                    .subtract(totalRentalBillIncomeAsanka)
                    .subtract(totalRentalRentIncomeAsanka);

            BigDecimal totalExpensesMinusIncomeDivya = totalExpensePaidFromPersonalAccountsDivya
                    .add(totalCardPaymentsDivya)
                    .add(totalBillsPaidFromPersonalAccountsDivya)
                    .subtract(totalRentalBillIncomeDivya)
                    .subtract(totalRentalRentIncomeDivya);

            //if minus value they owe the other person, if plus the other person owes.
            BigDecimal amountDividedByTwoAsanka = totalExpensesMinusIncomeAsanka.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
            BigDecimal amountDividedByTwoDivya = totalExpensesMinusIncomeDivya.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

            BigDecimal difference = amountDividedByTwoDivya.subtract(amountDividedByTwoAsanka);

            monthlyBalanceResponseDto.setBalanceAmount(difference.abs().toString());
            monthlyBalanceResponseDto.setAsankaPaid(totalExpensesMinusIncomeAsanka.abs().toString());
            monthlyBalanceResponseDto.setDivyaPaid(totalExpensesMinusIncomeDivya.abs().toString());
            monthlyBalanceResponseDto.setMonthAndYear(month + ", " + year);

            if (difference.compareTo(BigDecimal.ZERO) > 0) {
                // Asanka owe Divya
                monthlyBalanceResponseDto.setWhoOwes("Asanka");
            } else if (difference.compareTo(BigDecimal.ZERO) < 0) {
                // Divya owe Asanka
                monthlyBalanceResponseDto.setWhoOwes("Divya");
            } else {
                // Both paid equally
            }

            return new ResponseEntity<>(monthlyBalanceResponseDto, HttpStatus.OK);
        } catch (NumberFormatException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/getExpensesByCategory")
    public ResponseEntity<TransactionSummary> getExpensesByCategory(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, List<Transaction>> details = new HashMap<>();

        try {
            List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);

            for (Transaction tx : transactions) {
                if (tx.getTransactionType().equals(Transaction.TransactionType.EXPENSE)) {
                    String category = tx.getCategory() == null ? "Unknown" : tx.getCategory().getName();
                    BigDecimal amount = tx.getAmount();
                    totals.put(category, totals.getOrDefault(category, BigDecimal.ZERO).add(amount));
                    details.computeIfAbsent(category, k -> new ArrayList<>()).add(tx);
                }
            }
        } catch (Exception e){
            log.error("error preparing categories", e);
            throw new RuntimeException(e);
        }


        return new ResponseEntity<>(new TransactionSummary(totals, details), HttpStatus.OK);
    }

    @GetMapping("/getExpensesByEntity")
    public ResponseEntity<TransactionSummary> getExpensesByEntity(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, List<Transaction>> details = new HashMap<>();

        List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);

        for (Transaction tx : transactions) {
            if (tx.getTransactionType().equals(Transaction.TransactionType.EXPENSE)) {
                String entity = tx.getEntity();
                BigDecimal amount = tx.getAmount();
                totals.put(entity, totals.getOrDefault(entity, BigDecimal.ZERO).add(amount));
                details.computeIfAbsent(entity, k -> new ArrayList<>()).add(tx);
            }
        }

        return new ResponseEntity<>(new TransactionSummary(totals, details), HttpStatus.OK);
    }

    @GetMapping("/getExpensesByMonth")
    public ResponseEntity<TransactionSummary> getExpensesByMonth(@RequestParam YearMonth startMonth, @RequestParam YearMonth endMonth) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, List<Transaction>> details = new HashMap<>();
        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = endMonth.atEndOfMonth();

        List<Transaction> transactions = transactionRepository.findByDateBetween(startDate, endDate);

        for (Transaction tx : transactions) {
            if (tx.getTransactionType().equals(Transaction.TransactionType.EXPENSE)) {
                YearMonth ym = YearMonth.from(tx.getDate());
                String monthKey = ym.toString(); // e.g. "2025-04"

                BigDecimal amount = tx.getAmount();
                totals.put(monthKey, totals.getOrDefault(monthKey, BigDecimal.ZERO).add(amount));
                details.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(tx);
            }
        }

        return new ResponseEntity<>(new TransactionSummary(totals, details), HttpStatus.OK);
    }
}