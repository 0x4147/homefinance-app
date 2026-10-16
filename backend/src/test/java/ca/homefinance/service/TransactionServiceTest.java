package ca.homefinance.service;

import ca.homefinance.dto.BalanceLineDto;
import ca.homefinance.dto.MonthlyBalanceResponseDto;
import ca.homefinance.dto.TransactionDto;
import ca.homefinance.dto.TransactionScope;
import ca.homefinance.dto.TransactionSummary;
import ca.homefinance.entity.Account;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.AccountRepository;
import ca.homefinance.repository.CategoryRepository;
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
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionCategorizationService transactionCategorizationService;

    @InjectMocks
    private TransactionService transactionService;

    private Person asanka;
    private Person divya;
    private Account asankaAccount;
    private Account divyaAccount;
    private Account cibc;
    private Account amex;

    private final Category spending = category(1, "Test Category", Category.CategoryKind.EXPENSE, true);

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

        // CIBC and AMEX are settled by Divya, whichever card was used
        asankaAccount = account("ASANKA", asanka);
        divyaAccount = account("DIVYA", divya);
        cibc = account("CIBC", divya);
        amex = account("AMEX", divya);
    }

    @Test
    void createTransaction_ValidExpense_SavesMoneyOut() {
        Category groceries = category(3, "Groceries", Category.CategoryKind.EXPENSE, true);
        stubCategorizer(groceries);
        stubAccountAndPerson();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("25.00", "  Costco ", "CIBC", "EXPENSE", null, "1"));

        assertEquals(0, new BigDecimal("-25.00").compareTo(saved.getAmount()));
        assertEquals("Costco", saved.getEntity());
        assertEquals(Transaction.TransactionType.EXPENSE, saved.getType());
        assertSame(cibc, saved.getAccount());
        assertSame(groceries, saved.getCategory());
        assertSame(asanka, saved.getPerson());
        verify(transactionCategorizationService).categorizeTransaction(eq("Costco"), isNull(), any(), any());
    }

    @Test
    void createTransaction_ExplicitCategory_IsUsedInsteadOfTheCategorizer() {
        Category rentalBillIncome = category(30, "Rental Bill Income", Category.CategoryKind.INCOME, true);
        when(categoryRepository.findByName("Rental Bill Income")).thenReturn(rentalBillIncome);
        stubAccountAndPerson();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(
                dto("46.50", "Tenant", "CIBC", "INCOME", "Rental Bill Income", "1"));

        assertEquals(0, new BigDecimal("46.50").compareTo(saved.getAmount()));
        assertSame(rentalBillIncome, saved.getCategory());
        assertEquals(Transaction.TransactionType.INCOME, saved.getType());
        verify(transactionCategorizationService, never()).categorizeTransaction(any(), any(), any(), any());
    }

    @Test
    void createTransaction_UnknownCategory_ThrowsBadRequest() {
        stubAccountAndPerson();
        when(categoryRepository.findByName("Nope")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "EXPENSE", "Nope", "1")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_UnmatchedMerchant_LinksToReviewInsteadOfCategory() {
        UncategorizedTransaction review = new UncategorizedTransaction();
        review.setId(7);
        Category placeholder = new Category();
        placeholder.setUncategorizedTransaction(review);
        stubCategorizer(placeholder);
        stubAccountAndPerson();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("25.00", "Mystery Shop", "CIBC", "EXPENSE", null, "1"));

        assertNull(saved.getCategory());
        assertSame(review, saved.getUncategorizedTransaction());
    }

    @Test
    void createTransaction_Refund_StoresMoneyIn() {
        stubCategorizer(category(3, "Groceries", Category.CategoryKind.EXPENSE, true));
        stubAccountAndPerson();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "REFUND", null, "1"));

        assertEquals(0, new BigDecimal("25.00").compareTo(saved.getAmount()));
        assertEquals(Transaction.TransactionType.REFUND, saved.getType());
    }

    @Test
    void createTransaction_ZeroAmount_ThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(dto("0", "Costco", "CIBC", "EXPENSE", null, "1")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createTransaction_InvalidAccount_ThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(dto("25.00", "Costco", "BANK", "EXPENSE", null, "1")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createTransaction_ExistingMatch_ThrowsConflictWithoutSaving() {
        stubAccountAndPerson();
        when(transactionRepository.countByAccount_CodeAndDateAndEntityAndAmount(
                eq("CIBC"), any(), eq("Costco"), any())).thenReturn(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "EXPENSE", null, "1")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(transactionRepository, never()).save(any());
        verify(transactionCategorizationService, never()).categorizeTransaction(any(), any(), any(), any());
    }

    @Test
    void createTransaction_ExistingMatchAndAllowDuplicate_Saves() {
        stubCategorizer(category(3, "Groceries", Category.CategoryKind.EXPENSE, true));
        stubAccountAndPerson();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "EXPENSE", null, "1"), true);

        assertEquals(0, new BigDecimal("-25.00").compareTo(saved.getAmount()));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_NoExistingMatch_SavesWithoutOverride() {
        stubCategorizer(category(3, "Groceries", Category.CategoryKind.EXPENSE, true));
        stubAccountAndPerson();
        when(transactionRepository.countByAccount_CodeAndDateAndEntityAndAmount(
                any(), any(), any(), any())).thenReturn(0L);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.createTransaction(dto("25.00", "Costco", "CIBC", "EXPENSE", null, "1"));

        verify(transactionRepository).save(any(Transaction.class));
    }

    // The balance query returns only shared transactions in counting categories (the filtering itself is
    // covered by MonthlyBalanceIntegrationTest); the service splits them by who funds the account.

    @Test
    void getMonthlyBalance_NothingToCount_ShouldReturnZeroBalance() {
        when(transactionRepository.findBalanceTransactions(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(Collections.emptyList());

        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getBalanceAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getDivyaPaid()));
        assertEquals("1, 2024", result.getMonthAndYear());
        assertNull(result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_AsankaPaidMore_DivyaOwes() {
        stubBalance(spend(1, "100.00", asankaAccount, asanka));

        // Asanka paid $100, Divya $0: shares $50 vs $0, so Divya owes Asanka $50
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("50.0").compareTo(result.getBalanceAmount()));
        assertEquals(0, new BigDecimal("100.0").compareTo(result.getAsankaPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getDivyaPaid()));
        assertEquals("Divya", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_DivyaPaidMore_AsankaOwes() {
        stubBalance(spend(2, "200.00", divyaAccount, divya));

        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("100.0").compareTo(result.getBalanceAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertEquals(0, new BigDecimal("200.0").compareTo(result.getDivyaPaid()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_EqualPayments_NobodyOwes() {
        stubBalance(spend(1, "100.00", asankaAccount, asanka), spend(2, "100.00", divyaAccount, divya));

        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getBalanceAmount()));
        assertNull(result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_CardPurchases_CountFullyTowardDivyaWhoseCardWasUsed() {
        // CIBC/AMEX hold shared purchases only; Divya settles both cards in full each month,
        // so every purchase is her contribution no matter whose physical card made it.
        stubBalance(spend(1, "100.00", cibc, asanka), spend(2, "50.00", amex, divya));

        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("75.0").compareTo(result.getBalanceAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertEquals(0, new BigDecimal("150.0").compareTo(result.getDivyaPaid()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_PersonalPurchaseOnSharedCard_OwnerOwesAllOfIt() {
        // Divya settles the card: $100 shared + $60 that is entirely Asanka's.
        // Asanka owes half the shared $100 plus all of his $60.
        Transaction personal = spend(2, "60.00", cibc, asanka);
        personal.setOwner(asanka);
        stubBalance(spend(1, "100.00", cibc, asanka), personal);

        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("110.0").compareTo(result.getBalanceAmount()));
        assertEquals(0, new BigDecimal("160.00").compareTo(result.getDivyaPaid()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_PersonalPurchaseOwnedByTheSettler_ChangesNothing() {
        Transaction personal = spend(2, "60.00", cibc, asanka);
        personal.setOwner(divya);
        stubBalance(spend(1, "100.00", cibc, asanka), personal);

        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("50.0").compareTo(result.getBalanceAmount()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    /** One of each: shared (counted, split), excluded, owned by one person, and in a category that doesn't count. */
    private void stubMixedTransactions() {
        Transaction shared = spend(1, "10.00", cibc, asanka);
        Transaction excluded = spend(2, "20.00", cibc, asanka);
        excluded.setShared(false);
        Transaction owned = spend(3, "30.00", cibc, asanka);
        owned.setOwner(asanka);
        Transaction notCounted = transaction(4, "-40.00", cibc, asanka,
                category(2, "Card Payment", Category.CategoryKind.TRANSFER, false));
        when(transactionRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Arrays.asList(shared, excluded, owned, notCounted));
    }

    @Test
    void getExpensesByCategory_OnlyIncludesTheSharedFinances() {
        stubMixedTransactions();

        TransactionSummary result = transactionService.getExpensesByCategory(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertEquals(1, result.getTotals().size());
        assertEquals(0, new BigDecimal("10.00").compareTo(result.getTotals().get("Test Category")));
    }

    @Test
    void getExpensesByMonth_OnlyIncludesTheSharedFinances() {
        stubMixedTransactions();

        TransactionSummary result = transactionService.getExpensesByMonth(
                java.time.YearMonth.of(2024, 1), java.time.YearMonth.of(2024, 1));

        assertEquals(0, new BigDecimal("10.00").compareTo(result.getTotals().get("2024-01")));
    }

    @Test
    void getTransactionDtosByDateRange_SharedScopeHidesEverythingButSharedFinances() {
        stubMixedTransactions();

        List<TransactionDto> shared = transactionService.getTransactionDtosByDateRange(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), TransactionScope.SHARED);
        List<TransactionDto> all = transactionService.getTransactionDtosByDateRange(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertEquals(1, shared.size());
        assertEquals(1, shared.get(0).getTransactionId());
        assertEquals(4, all.size());
    }

    @Test
    void setOwner_AssignsAndClearsTheOwner() {
        Transaction tx = spend(7, "10.00", cibc, asanka);
        when(transactionRepository.findById(7)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));

        assertEquals("ASANKA", transactionService.setOwner(7, "asanka").getOwner());
        assertSame(asanka, tx.getOwner());

        assertNull(transactionService.setOwner(7, null).getOwner());
        assertNull(tx.getOwner());
    }

    @Test
    void setOwner_UnknownPerson_ReturnsBadRequest() {
        Transaction tx = spend(7, "10.00", cibc, asanka);
        when(transactionRepository.findById(7)).thenReturn(Optional.of(tx));
        when(personRepository.findByCode("NOBODY")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> transactionService.setOwner(7, "nobody"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getMonthlyBalance_CardRefund_ReducesSharedCardSpend() {
        Transaction purchase = spend(1, "200.00", cibc, asanka);
        Transaction refund = transaction(2, "49.46", cibc, null, spending);
        stubBalance(purchase, refund);

        // Net shared card spend: $200 - $49.46 = $150.54, all Divya's contribution
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("150.54").compareTo(result.getDivyaPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAsankaPaid()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalance_RentalIncomeExceedsExpenses_ShouldReturnNegativeNetContribution() {
        Category rent = category(31, "Rental Rent Income", Category.CategoryKind.INCOME, true);
        stubBalance(
                spend(1, "500.00", asankaAccount, asanka),
                transaction(2, "2000.00", asankaAccount, asanka, rent),
                spend(3, "300.00", divyaAccount, divya));

        // Asanka: $500 - $2000 = -$1500 net, Divya: $300 net
        // Difference: $150 - (-$750) = $900, so Asanka owes Divya $900
        MonthlyBalanceResponseDto result = transactionService.getMonthlyBalance(1, 2024);

        assertEquals(0, new BigDecimal("-1500.00").compareTo(result.getAsankaPaid()));
        assertEquals(0, new BigDecimal("300.00").compareTo(result.getDivyaPaid()));
        assertEquals(0, new BigDecimal("900.00").compareTo(result.getBalanceAmount()));
        assertEquals("Asanka", result.getWhoOwes());
    }

    @Test
    void getMonthlyBalanceTransactions_ListsOnlyThePayersLines_WithTheirContribution() {
        Category bill = category(32, "Rental Bill Income", Category.CategoryKind.INCOME, true);
        when(transactionRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(Arrays.asList(
                spend(1, "100.00", asankaAccount, asanka),
                transaction(2, "40.00", asankaAccount, asanka, bill),
                spend(3, "60.00", cibc, asanka)));

        List<BalanceLineDto> lines = transactionService.getMonthlyBalanceTransactions(1, 2024, "asanka");

        assertEquals(2, lines.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(lines.get(0).getContribution()));
        assertEquals("EXPENSE", lines.get(0).getKind());
        assertEquals(0, new BigDecimal("-40.00").compareTo(lines.get(1).getContribution()));
        assertEquals("INCOME", lines.get(1).getKind());
    }

    @Test
    void getMonthlyBalanceTransactions_AlsoListsUncountedLines_WithZeroContribution() {
        Category personal = category(2, "Personal", Category.CategoryKind.EXPENSE, false);
        Transaction counted = spend(1, "100.00", asankaAccount, asanka);
        Transaction leftOut = spend(2, "25.00", asankaAccount, asanka);
        leftOut.setShared(false);
        Transaction otherCategory = transaction(3, "-15.00", asankaAccount, asanka, personal);
        Transaction uncategorized = transaction(4, "-5.00", asankaAccount, asanka, null);
        Transaction divyas = spend(5, "60.00", divyaAccount, divya);
        when(transactionRepository.findByDateBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(Arrays.asList(counted, leftOut, otherCategory, uncategorized, divyas));

        List<BalanceLineDto> lines = transactionService.getMonthlyBalanceTransactions(1, 2024, "ASANKA");

        assertEquals(4, lines.size());
        assertTrue(lines.get(0).getTransaction().isCountsInBalance());
        assertEquals(0, new BigDecimal("100.00").compareTo(lines.get(0).getContribution()));
        for (BalanceLineDto uncounted : lines.subList(1, 4)) {
            assertFalse(uncounted.getTransaction().isCountsInBalance());
            assertEquals(0, BigDecimal.ZERO.compareTo(uncounted.getContribution()));
        }
        assertEquals(0, new BigDecimal("100.00").compareTo(
                lines.stream().map(BalanceLineDto::getContribution).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Test
    void getMonthlyBalanceTransactions_InvalidPerson_ThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.getMonthlyBalanceTransactions(1, 2024, "CIBC"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getExpensesByCategory_ReportsSpendingAsPositiveTotals() {
        Category utilities = category(4, "Utilities", Category.CategoryKind.EXPENSE, true);
        Category rentalBill = category(5, "Rental Bill Income", Category.CategoryKind.INCOME, true);
        Transaction bill = transaction(1, "-80.00", asankaAccount, asanka, utilities);
        Transaction income = transaction(2, "50.00", asankaAccount, asanka, rentalBill);
        when(transactionRepository.findByDateBetween(any(), any())).thenReturn(Arrays.asList(bill, income));

        var summary = transactionService.getExpensesByCategory(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertEquals(0, new BigDecimal("80.00").compareTo(summary.getTotals().get("Utilities")));
        assertFalse(summary.getTotals().containsKey("Rental Bill Income"));
    }

    @Test
    void getExpensesByCategory_RefundNetsAgainstSpendingInItsCategory() {
        Category shopping = category(6, "Shopping", Category.CategoryKind.EXPENSE, true);
        Transaction purchase = transaction(1, "-100.00", asankaAccount, asanka, shopping);
        Transaction refund = transaction(2, "30.00", asankaAccount, asanka, shopping);
        when(transactionRepository.findByDateBetween(any(), any())).thenReturn(Arrays.asList(purchase, refund));

        var summary = transactionService.getExpensesByCategory(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertEquals(0, new BigDecimal("70.00").compareTo(summary.getTotals().get("Shopping")));
    }

    @Test
    void getMonthlyBalance_InvalidMonth_ShouldThrowException() {
        assertThrows(java.time.DateTimeException.class, () -> transactionService.getMonthlyBalance(13, 2024));
    }

    private void stubCategorizer(Category result) {
        when(transactionCategorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(result);
    }

    private void stubAccountAndPerson() {
        when(accountRepository.findByCode("CIBC")).thenReturn(Optional.of(cibc));
        when(personRepository.findById(1)).thenReturn(Optional.of(asanka));
    }

    private void stubBalance(Transaction... transactions) {
        when(transactionRepository.findBalanceTransactions(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Arrays.asList(transactions));
    }

    @Test
    void listedTransactions_ShowWhetherTheyCountTowardTheBalance() {
        Category personal = category(2, "Personal", Category.CategoryKind.EXPENSE, false);
        Transaction counted = spend(1, "10.00", cibc, asanka);
        Transaction excluded = spend(2, "20.00", cibc, asanka);
        excluded.setShared(false);
        Transaction wrongCategory = transaction(3, "-30.00", cibc, asanka, personal);
        when(transactionRepository.findByDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Arrays.asList(counted, excluded, wrongCategory));

        List<TransactionDto> result = transactionService.getTransactionDtosByDateRange(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertTrue(result.get(0).isShared());
        assertTrue(result.get(0).isCountsInBalance());
        assertFalse(result.get(1).isShared());
        assertFalse(result.get(1).isCountsInBalance());
        assertTrue(result.get(2).isShared());
        assertFalse(result.get(2).isCountsInBalance());
    }

    @Test
    void setShared_ExcludesTransactionFromBalance() {
        Transaction tx = spend(7, "10.00", cibc, asanka);
        when(transactionRepository.findById(7)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionDto result = transactionService.setShared(7, false);

        assertFalse(tx.isShared());
        assertFalse(result.isShared());
        assertFalse(result.isCountsInBalance());
        verify(transactionRepository).save(tx);
    }

    @Test
    void setShared_IncludesTransactionInBalance() {
        Transaction tx = spend(7, "10.00", cibc, asanka);
        tx.setShared(false);
        when(transactionRepository.findById(7)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionDto result = transactionService.setShared(7, true);

        assertTrue(result.isShared());
        assertTrue(result.isCountsInBalance());
    }

    @Test
    void setShared_UnknownTransaction_ReturnsNotFound() {
        when(transactionRepository.findById(99)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> transactionService.setShared(99, false));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(transactionRepository, never()).save(any());
    }

    private static TransactionDto dto(String amount, String entity, String account, String type, String category, String person) {
        return new TransactionDto(null, new BigDecimal(amount), LocalDate.of(2024, 2, 1), entity, null,
                account, type, category, person);
    }

    private static Category category(int id, String name, Category.CategoryKind kind, boolean inBalance) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        category.setKind(kind);
        category.setInBalance(inBalance);
        return category;
    }

    private static Account account(String code, Person payer) {
        Account account = new Account();
        account.setCode(code);
        account.setPayer(payer);
        return account;
    }

    /** A shared purchase: money out in a category that counts. */
    private Transaction spend(Integer id, String amount, Account account, Person person) {
        return transaction(id, "-" + amount, account, person, spending);
    }

    private Transaction transaction(Integer id, String amount, Account account, Person person, Category category) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(id);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setDate(LocalDate.of(2024, 1, 15));
        transaction.setEntity("Test Store");
        transaction.setAccount(account);
        transaction.setPerson(person);
        transaction.setCategory(category);
        return transaction;
    }
}
