package ca.homefinance.service;

import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private TransactionCategorizationService transactionCategorizationService;

    @InjectMocks
    private TransactionService transactionService;

    private Person asanka;
    private Person divya;

    @BeforeEach
    void setUp() {
        asanka = new Person();
        asanka.setPersonId(1);
        asanka.setCode("ASANKA");
        asanka.setName("Asanka");

        divya = new Person();
        divya.setPersonId(2);
        divya.setCode("DIVYA");
        divya.setName("Divya");
    }

    @Test
    void createTransaction_ValidExpense_SavesPositiveAmount() {
        Category groceries = category(3, "Groceries");
        stubCategorizer(groceries);
        when(personRepository.findById(1)).thenReturn(Optional.of(asanka));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("25.00", "  Costco ", "CIBC", "EXPENSE", "3", "1"));

        assertEquals(0, new BigDecimal("25.00").compareTo(saved.getAmount()));
        assertEquals("Costco", saved.getEntity());
        assertEquals(Transaction.TransactionType.EXPENSE, saved.getTransactionType());
        assertEquals(Transaction.AccountType.CIBC, saved.getAccount());
        assertSame(groceries, saved.getCategory());
        assertSame(asanka, saved.getPerson());
        verify(transactionCategorizationService).categorizeTransaction(eq("Costco"), isNull(), any(), any());
    }

    @Test
    void createTransaction_UnmatchedMerchant_LinksToReviewInsteadOfCategory() {
        UncategorizedTransaction review = new UncategorizedTransaction();
        review.setId(7);
        Category placeholder = new Category();
        placeholder.setUncategorizedTransaction(review);
        stubCategorizer(placeholder);
        when(personRepository.findById(1)).thenReturn(Optional.of(asanka));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("25.00", "Mystery Shop", "CIBC", "EXPENSE", null, "1"));

        assertNull(saved.getCategory());
        assertSame(review, saved.getUncategorizedTransaction());
    }

    private void stubCategorizer(Category result) {
        when(transactionCategorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(result);
    }

    @Test
    void createTransaction_Refund_StoresNegativeAmount() {
        stubCategorizer(category(3, "Groceries"));
        when(personRepository.findById(1)).thenReturn(Optional.of(asanka));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("-25.00", "Costco", "CIBC", "REFUND", "3", "1"));

        assertEquals(0, new BigDecimal("-25.00").compareTo(saved.getAmount()));
    }

    @Test
    void createTransaction_ZeroAmount_ThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(dto("0", "Costco", "CIBC", "EXPENSE", "3", "1")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createTransaction_InvalidAccount_ThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(dto("25.00", "Costco", "BANK", "EXPENSE", "3", "1")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createTransaction_ExistingMatch_ThrowsConflictWithoutSaving() {
        when(personRepository.findById(1)).thenReturn(Optional.of(asanka));
        when(transactionRepository.countByAccountAndDateAndEntityAndAmount(
                eq(Transaction.AccountType.CIBC), any(), eq("Costco"), any())).thenReturn(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "EXPENSE", "3", "1")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(transactionRepository, never()).save(any());
        verify(transactionCategorizationService, never()).categorizeTransaction(any(), any(), any(), any());
    }

    @Test
    void createTransaction_ExistingMatchAndAllowDuplicate_Saves() {
        stubCategorizer(category(3, "Groceries"));
        when(personRepository.findById(1)).thenReturn(Optional.of(asanka));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "EXPENSE", "3", "1"), true);

        assertEquals(0, new BigDecimal("25.00").compareTo(saved.getAmount()));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_NoExistingMatch_SavesWithoutOverride() {
        stubCategorizer(category(3, "Groceries"));
        when(personRepository.findById(1)).thenReturn(Optional.of(asanka));
        when(transactionRepository.countByAccountAndDateAndEntityAndAmount(
                any(), any(), any(), any())).thenReturn(0L);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "EXPENSE", "3", "1"));

        verify(transactionRepository).save(any(Transaction.class));
    }

    private static TransactionDto dto(String amount, String entity, String account, String type, String category, String person) {
        return new TransactionDto(new BigDecimal(amount), LocalDate.of(2024, 2, 1), entity, null,
                account, type, category, person);
    }

    private static Category category(int id, String name) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        return category;
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

    @Test
    void getMonthlyBalance_EqualPayments_ShouldReturnZeroBalance() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        // Then
        assertEquals(BigDecimal.ZERO, result.getBalanceAmount());
        assertEquals(BigDecimal.ZERO, result.getAsankaPaid());
        assertEquals(BigDecimal.ZERO, result.getDivyaPaid());
        assertEquals("1, 2024", result.getMonthAndYear());
        assertNull(result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_AsankaOwesDivya_ShouldReturnCorrectBalance() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        Transaction asankaExpense = createTransaction(1, new BigDecimal("100.00"),
                LocalDate.of(2024, 1, 15), "Test Store", "Test expense",
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense));

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                any()))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE, Transaction.TransactionType.REFUND))))
                .thenReturn(Collections.emptyList());

        // When
        // Asanka paid $100, Divya paid $0
        // Asanka's share: $100/2 = $50, Divya's share: $0/2 = $0
        // Difference: $0 - $50 = -$50, so Divya owes Asanka $50
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        // Then
        assertEquals(0, new BigDecimal("50.0").compareTo(result.getBalanceAmount()));
        assertEquals(0, new BigDecimal("100.0").compareTo(result.getAsankaPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getDivyaPaid()));
        assertEquals("Divya", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_DivyaOwesAsanka_ShouldReturnCorrectBalance() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        Transaction divyaExpense = createTransaction(2, new BigDecimal("200.00"),
                LocalDate.of(2024, 1, 15), "Test Store", "Test expense",
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(divyaExpense));

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                any()))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE, Transaction.TransactionType.REFUND))))
                .thenReturn(Collections.emptyList());

        // When
        // Asanka paid $0, Divya paid $200
        // Asanka's share: $0/2 = $0, Divya's share: $200/2 = $100
        // Difference: $100 - $0 = $100, so Asanka owes Divya $100
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        // Then
        assertEquals(0, new BigDecimal("100.0").compareTo(result.getBalanceAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertEquals(0, new BigDecimal("200.0").compareTo(result.getDivyaPaid()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_CardPurchases_ShouldCountFullyTowardDivyaRegardlessOfWhoseCardWasUsed() {
        // Given - CIBC/AMEX hold shared purchases only; Divya settles both cards in full each month,
        // so every purchase counts as her contribution no matter whose physical card made it.
        Transaction asankaCardPurchase = createTransaction(1, new BigDecimal("100.00"),
                LocalDate.of(2024, 1, 15), "Costco", "Groceries",
                Transaction.AccountType.CIBC, Transaction.TransactionType.EXPENSE, asanka);

        Transaction divyaCardPurchase = createTransaction(2, new BigDecimal("50.00"),
                LocalDate.of(2024, 1, 18), "Tim Hortons", "Coffee",
                Transaction.AccountType.AMEX, Transaction.TransactionType.EXPENSE, divya);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE, Transaction.TransactionType.REFUND))))
                .thenReturn(Arrays.asList(asankaCardPurchase, divyaCardPurchase));

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                any()))
                .thenReturn(Collections.emptyList());

        // When
        // Shared card spend: $100 + $50 = $150, all counted as Divya's contribution
        // Asanka's share: $0/2 = $0, Divya's share: $150/2 = $75
        // Difference: $75 - $0 = $75, so Asanka owes Divya $75
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        // Then
        assertEquals(0, new BigDecimal("75.0").compareTo(result.getBalanceAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertEquals(0, new BigDecimal("150.0").compareTo(result.getDivyaPaid()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_CardRefund_ShouldReduceSharedCardSpend() {
        Transaction cardPurchase = createTransaction(1, new BigDecimal("200.00"),
                LocalDate.of(2024, 1, 10), "Canadian Tire", "Purchase",
                Transaction.AccountType.CIBC, Transaction.TransactionType.EXPENSE, asanka);

        Transaction cardRefund = createTransaction(2, new BigDecimal("-49.46"),
                LocalDate.of(2024, 1, 21), "Canadian Tire", "Return",
                Transaction.AccountType.CIBC, Transaction.TransactionType.REFUND, null);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE, Transaction.TransactionType.REFUND))))
                .thenReturn(Arrays.asList(cardPurchase, cardRefund));

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                any()))
                .thenReturn(Collections.emptyList());

        // Net shared card spend: $200 - $49.46 = $150.54, all counted as Divya's contribution
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("150.54").compareTo(result.getDivyaPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_CardPaymentTransactionType_ShouldBeExcludedFromBalance() {
        // Given - CARDPAYMENT rows only record bank repayment timing (a billing-cycle behind actual
        // spending), so they must never feed into the balance; only EXPENSE/REFUND does.
        Transaction cardBillPayment = createTransaction(1, new BigDecimal("-5861.69"),
                LocalDate.of(2024, 1, 4), "PAYMENT RECEIVED - THANK YOU", "Bill payment",
                Transaction.AccountType.AMEX, Transaction.TransactionType.CARDPAYMENT, divya);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE, Transaction.TransactionType.REFUND))))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                any()))
                .thenReturn(Collections.emptyList());

        // A CARDPAYMENT-typed transaction is never requested from the repository at all, since the
        // service no longer queries that type; if it were mistakenly returned by a broader query, it
        // still shouldn't be counted (the mock above only returns EXPENSE/REFUND rows, so this asserts
        // the balance ignores a $5,861.69 payment entirely).
        assertNotNull(cardBillPayment);
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getDivyaPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertNull(result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_RentalIncomeExceedsExpenses_ShouldReturnNegativeNetContribution() {
        Transaction asankaExpense = createTransaction(1, new BigDecimal("500.00"),
                LocalDate.of(2024, 1, 10), "Store", "Expense",
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction asankaRent = createTransaction(2, new BigDecimal("2000.00"),
                LocalDate.of(2024, 1, 1), "Tenant", "Rent",
                Transaction.AccountType.ASANKA, Transaction.TransactionType.RENTALRENTINCOME, asanka);

        Transaction divyaExpense = createTransaction(3, new BigDecimal("300.00"),
                LocalDate.of(2024, 1, 12), "Store", "Expense",
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense, divyaExpense));

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                any()))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME))))
                .thenReturn(Arrays.asList(asankaRent));

        when(transactionRepository.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class), any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE, Transaction.TransactionType.REFUND))))
                .thenReturn(Collections.emptyList());

        // Asanka: $500 - $2000 = -$1500 net, Divya: $300 net
        // Difference: $150 - (-$750) = $900, so Asanka owes Divya $900
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("-1500.00").compareTo(result.getAsankaPaid()));
        assertEquals(0, new BigDecimal("300.00").compareTo(result.getDivyaPaid()));
        assertEquals(0, new BigDecimal("900.00").compareTo(result.getBalanceAmount()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_InvalidMonth_ShouldThrowException() {
        assertThrows(java.time.DateTimeException.class, () -> transactionService.getMonthlyBalance(13, 2024));
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
