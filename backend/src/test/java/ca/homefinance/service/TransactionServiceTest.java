package ca.homefinance.service;

import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Person asanka;
    private Person divya;

    @BeforeEach
    void setUp() {
        asanka = new Person();
        asanka.setPersonId(1);
        asanka.setName("Asanka");

        divya = new Person();
        divya.setPersonId(2);
        divya.setName("Divya");
    }

    @Test
    void searchTransactionByDateRangeAccountTypeTransactionType_ShouldReturnFilteredTransactions() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        List<Transaction.AccountType> accounts = Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA);
        List<Transaction.TransactionType> transactionTypes = Arrays.asList(Transaction.TransactionType.EXPENSE);

        Transaction asankaExpense = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Store A", "Expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction divyaExpense = createTransaction(2, new BigDecimal("50.00"), 
                LocalDate.of(2024, 1, 20), "Store B", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        List<Transaction> expectedTransactions = Arrays.asList(asankaExpense, divyaExpense);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes))
                .thenReturn(expectedTransactions);

        // When
        List<Transaction> result = transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedTransactions, result);
    }

    @Test
    void searchTransactionByDateRangeAccountTypeTransactionType_EmptyResult_ShouldReturnEmptyList() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        List<Transaction.AccountType> accounts = Arrays.asList(Transaction.AccountType.ASANKA);
        List<Transaction.TransactionType> transactionTypes = Arrays.asList(Transaction.TransactionType.EXPENSE);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes))
                .thenReturn(Collections.emptyList());

        // When
        List<Transaction> result = transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void searchTransactionByDateRangeAccountTypeTransactionType_NullParameters_ShouldHandleGracefully() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, null, null))
                .thenReturn(Collections.emptyList());

        // When
        List<Transaction> result = transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void searchTransactionByDateRangeAccountTypeTransactionType_CrossMonthBoundary_ShouldIncludeAllDates() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 15);
        LocalDate endDate = LocalDate.of(2024, 2, 15);
        List<Transaction.AccountType> accounts = Arrays.asList(Transaction.AccountType.ASANKA);
        List<Transaction.TransactionType> transactionTypes = Arrays.asList(Transaction.TransactionType.EXPENSE);

        Transaction januaryTransaction = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 20), "Store A", "January expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction februaryTransaction = createTransaction(2, new BigDecimal("50.00"), 
                LocalDate.of(2024, 2, 10), "Store B", "February expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        List<Transaction> expectedTransactions = Arrays.asList(januaryTransaction, februaryTransaction);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes))
                .thenReturn(expectedTransactions);

        // When
        List<Transaction> result = transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(t -> t.getDate().getMonthValue() == 1));
        assertTrue(result.stream().anyMatch(t -> t.getDate().getMonthValue() == 2));
    }

    @Test
    void searchTransactionByDateRangeAccountTypeTransactionType_MultipleAccountTypes_ShouldReturnAllMatching() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        List<Transaction.AccountType> accounts = Arrays.asList(
                Transaction.AccountType.ASANKA, 
                Transaction.AccountType.DIVYA,
                Transaction.AccountType.CIBC
        );
        List<Transaction.TransactionType> transactionTypes = Arrays.asList(Transaction.TransactionType.EXPENSE);

        Transaction asankaTransaction = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Store A", "Asanka expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction divyaTransaction = createTransaction(2, new BigDecimal("50.00"), 
                LocalDate.of(2024, 1, 20), "Store B", "Divya expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        Transaction cibcTransaction = createTransaction(3, new BigDecimal("75.00"), 
                LocalDate.of(2024, 1, 25), "Store C", "CIBC expense", 
                Transaction.AccountType.CIBC, Transaction.TransactionType.EXPENSE, asanka);

        List<Transaction> expectedTransactions = Arrays.asList(asankaTransaction, divyaTransaction, cibcTransaction);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes))
                .thenReturn(expectedTransactions);

        // When
        List<Transaction> result = transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(t -> t.getAccount() == Transaction.AccountType.ASANKA));
        assertTrue(result.stream().anyMatch(t -> t.getAccount() == Transaction.AccountType.DIVYA));
        assertTrue(result.stream().anyMatch(t -> t.getAccount() == Transaction.AccountType.CIBC));
    }

    @Test
    void searchTransactionByDateRangeAccountTypeTransactionType_MultipleTransactionTypes_ShouldReturnAllMatching() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        List<Transaction.AccountType> accounts = Arrays.asList(Transaction.AccountType.ASANKA);
        List<Transaction.TransactionType> transactionTypes = Arrays.asList(
                Transaction.TransactionType.EXPENSE,
                Transaction.TransactionType.BILL,
                Transaction.TransactionType.CARDPAYMENT
        );

        Transaction expenseTransaction = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Store A", "Expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction billTransaction = createTransaction(2, new BigDecimal("50.00"), 
                LocalDate.of(2024, 1, 20), "Utility", "Bill", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.BILL, asanka);

        Transaction cardPaymentTransaction = createTransaction(3, new BigDecimal("75.00"), 
                LocalDate.of(2024, 1, 25), "Card Payment", "Card payment", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.CARDPAYMENT, asanka);

        List<Transaction> expectedTransactions = Arrays.asList(expenseTransaction, billTransaction, cardPaymentTransaction);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes))
                .thenReturn(expectedTransactions);

        // When
        List<Transaction> result = transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(t -> t.getTransactionType() == Transaction.TransactionType.EXPENSE));
        assertTrue(result.stream().anyMatch(t -> t.getTransactionType() == Transaction.TransactionType.BILL));
        assertTrue(result.stream().anyMatch(t -> t.getTransactionType() == Transaction.TransactionType.CARDPAYMENT));
    }

    @Test
    void searchTransactionByDateRangeAccountTypeTransactionType_SameDayTransactions_ShouldIncludeAll() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 15);
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        List<Transaction.AccountType> accounts = Arrays.asList(Transaction.AccountType.ASANKA);
        List<Transaction.TransactionType> transactionTypes = Arrays.asList(Transaction.TransactionType.EXPENSE);

        Transaction morningTransaction = createTransaction(1, new BigDecimal("25.00"), 
                LocalDate.of(2024, 1, 15), "Morning Store", "Morning expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction afternoonTransaction = createTransaction(2, new BigDecimal("35.00"), 
                LocalDate.of(2024, 1, 15), "Afternoon Store", "Afternoon expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        List<Transaction> expectedTransactions = Arrays.asList(morningTransaction, afternoonTransaction);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes))
                .thenReturn(expectedTransactions);

        // When
        List<Transaction> result = transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                startDate, endDate, accounts, transactionTypes);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.getDate().equals(LocalDate.of(2024, 1, 15))));
    }

    private Transaction createTransaction(Integer transactionId, BigDecimal amount, LocalDate date, 
                                        String entity, String details, Transaction.AccountType account, 
                                        Transaction.TransactionType transactionType, Person person) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setAmount(amount);
        transaction.setDate(date);
        transaction.setEntity(entity);
        transaction.setDetails(details);
        transaction.setAccount(account);
        transaction.setTransactionType(transactionType);
        transaction.setPerson(person);
        return transaction;
    }
}
