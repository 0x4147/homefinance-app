package ca.homefinance.integration;

import ca.homefinance.entity.Account;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.AccountRepository;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class MonthlyBalanceIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;


    private MockMvc mockMvc;

    private Person asanka;
    private Person divya;
    private Category testCategory;
    private Category rentalBillIncome;
    private Category rentalRentIncome;
    private Category cardPayment;
    private final java.util.Map<String, Account> accounts = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Clean up existing data
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        personRepository.deleteAll();
        categoryRepository.deleteAll();

        // Create test persons
        asanka = new Person();
        asanka.setCode("ASANKA");
        asanka.setName("Asanka");
        asanka.setEmail("asanka@test.com");
        asanka = personRepository.save(asanka);

        divya = new Person();
        divya.setCode("DIVYA");
        divya.setName("Divya");
        divya.setEmail("divya@test.com");
        divya = personRepository.save(divya);

        // Create test categories: what a category says decides how its transactions count
        testCategory = category("Test Category", Category.CategoryKind.EXPENSE, true);
        rentalBillIncome = category("Rental Bill Income", Category.CategoryKind.INCOME, true);
        rentalRentIncome = category("Rental Rent Income", Category.CategoryKind.INCOME, true);
        cardPayment = category("Card payment", Category.CategoryKind.TRANSFER, false);

        // CIBC and AMEX are settled by Divya, whichever card was used
        account("ASANKA", Account.AccountKind.BANK, asanka);
        account("DIVYA", Account.AccountKind.BANK, divya);
        account("CIBC", Account.AccountKind.BANK, divya);
        account("AMEX", Account.AccountKind.CREDIT_CARD, divya);
    }

    @Test
    void getMonthlyBalance_RealDatabaseScenario_ShouldCalculateCorrectly() throws Exception {
        // Given - Create realistic transaction data for January 2024
        LocalDate january2024 = LocalDate.of(2024, 1, 2);

        // Asanka's transactions
        Transaction asankaExpense1 = createTransaction(new BigDecimal("120.50"), 
                january2024, "Grocery Store", "Weekly groceries", 
                "ASANKA", Kind.EXPENSE, asanka);
        
        Transaction asankaExpense2 = createTransaction(new BigDecimal("45.00"), 
                january2024.plusDays(5), "Gas Station", "Fuel", 
                "ASANKA", Kind.EXPENSE, asanka);
        
        // CIBC/AMEX hold shared purchases only (both cards); Divya settles both in full each month,
        // so every card purchase counts toward her contribution regardless of whose card made it.
        Transaction asankaCardPurchase = createTransaction(new BigDecimal("200.00"),
                january2024.plusDays(10), "Costco", "Shared purchase on Asanka's CIBC card",
                "CIBC", Kind.EXPENSE, asanka);

        Transaction asankaBill = createTransaction(new BigDecimal("85.30"), 
                january2024.plusDays(15), "Electric Company", "Electric bill", 
                "ASANKA", Kind.BILL, asanka);
        
        Transaction asankaRentalIncome = createTransaction(new BigDecimal("50.00"), 
                january2024.plusDays(11), "Tenant", "Rental bill income",
                "ASANKA", Kind.RENTALBILLINCOME, asanka);

        // Divya's transactions
        Transaction divyaExpense1 = createTransaction(new BigDecimal("95.75"), 
                january2024.plusDays(2), "Restaurant", "Dinner out", 
                "DIVYA", Kind.EXPENSE, divya);
        
        Transaction divyaExpense2 = createTransaction(new BigDecimal("30.00"), 
                january2024.plusDays(8), "Coffee Shop", "Coffee", 
                "DIVYA", Kind.EXPENSE, divya);
        
        Transaction divyaCardPurchase = createTransaction(new BigDecimal("150.00"),
                january2024.plusDays(12), "Amazon.ca", "Shared purchase on Divya's AMEX card",
                "AMEX", Kind.EXPENSE, divya);

        Transaction divyaBill = createTransaction(new BigDecimal("60.25"), 
                january2024.plusDays(18), "Internet Provider", "Internet bill", 
                "DIVYA", Kind.BILL, divya);
        
        Transaction divyaRentalIncome = createTransaction(new BigDecimal("75.00"), 
                january2024.plusDays(11), "Tenant", "Rental rent income",
                "DIVYA", Kind.RENTALRENTINCOME, divya);

        // Save all transactions
        transactionRepository.saveAll(java.util.Arrays.asList(
                asankaExpense1, asankaExpense2, asankaCardPurchase, asankaBill, asankaRentalIncome,
                divyaExpense1, divyaExpense2, divyaCardPurchase, divyaBill, divyaRentalIncome
        ));

        // When & Then
        // Expected calculations:
        // Asanka: $120.50 + $45.00 + $85.30 - $50.00 = $200.80 (no card component; card purchases go to Divya)
        // Shared card spend (both cards, either person's card): $200.00 + $150.00 = $350.00
        // Divya: $95.75 + $30.00 + $60.25 - $75.00 + $350.00 = $461.00
        // Asanka's share: $200.80/2 = $100.40
        // Divya's share: $461.00/2 = $230.50
        // Difference: $230.50 - $100.40 = $130.10, so Asanka owes Divya $130.10

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("130.1"))
                .andExpect(jsonPath("$.asankaPaid").value("200.8"))
                .andExpect(jsonPath("$.divyaPaid").value("461.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Asanka"));
    }

    @Test
    void getMonthlyBalance_NoTransactions_ShouldReturnZeroBalance() throws Exception {
        // Given - No transactions in database

        // When & Then
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("0"))
                .andExpect(jsonPath("$.asankaPaid").value("0"))
                .andExpect(jsonPath("$.divyaPaid").value("0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").doesNotExist());
    }

    @Test
    void getMonthlyBalance_OnlyAsankaTransactions_ShouldShowDivyaOwes() throws Exception {
        // Given - Only Asanka has transactions
        LocalDate january2024 = LocalDate.of(2024, 1, 15);

        Transaction asankaExpense = createTransaction(new BigDecimal("200.00"), 
                january2024, "Store", "Expense", 
                "ASANKA", Kind.EXPENSE, asanka);

        transactionRepository.save(asankaExpense);

        // When & Then
        // Asanka: $200, Divya: $0
        // Asanka's share: $200/2 = $100, Divya's share: $0/2 = $0
        // Difference: $0 - $100 = -$100, so Divya owes Asanka $100

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("100.0"))
                .andExpect(jsonPath("$.asankaPaid").value("200.0"))
                .andExpect(jsonPath("$.divyaPaid").value("0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    @Test
    void getMonthlyBalance_OnlyDivyaTransactions_ShouldShowAsankaOwes() throws Exception {
        // Given - Only Divya has transactions
        LocalDate january2024 = LocalDate.of(2024, 1, 15);

        Transaction divyaExpense = createTransaction(new BigDecimal("300.00"), 
                january2024, "Store", "Expense", 
                "DIVYA", Kind.EXPENSE, divya);

        transactionRepository.save(divyaExpense);

        // When & Then
        // Asanka: $0, Divya: $300
        // Asanka's share: $0/2 = $0, Divya's share: $300/2 = $150
        // Difference: $150 - $0 = $150, so Asanka owes Divya $150

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("150.0"))
                .andExpect(jsonPath("$.asankaPaid").value("0"))
                .andExpect(jsonPath("$.divyaPaid").value("300.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Asanka"));
    }

    @Test
    void getMonthlyBalance_EqualTransactions_ShouldShowNoBalance() throws Exception {
        // Given - Both have equal transactions
        LocalDate january2024 = LocalDate.of(2024, 1, 15);

        Transaction asankaExpense = createTransaction(new BigDecimal("100.00"), 
                january2024, "Store A", "Expense", 
                "ASANKA", Kind.EXPENSE, asanka);

        Transaction divyaExpense = createTransaction(new BigDecimal("100.00"), 
                january2024, "Store B", "Expense", 
                "DIVYA", Kind.EXPENSE, divya);

        transactionRepository.saveAll(java.util.Arrays.asList(asankaExpense, divyaExpense));

        // When & Then
        // Asanka: $100, Divya: $100
        // Asanka's share: $100/2 = $50, Divya's share: $100/2 = $50
        // Difference: $50 - $50 = $0, so no one owes anything

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("0.0"))
                .andExpect(jsonPath("$.asankaPaid").value("100.0"))
                .andExpect(jsonPath("$.divyaPaid").value("100.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").doesNotExist());
    }

    @Test
    void getMonthlyBalance_TransactionsOutsideDateRange_ShouldNotInclude() throws Exception {
        // Given - Transactions in different months
        LocalDate december2023 = LocalDate.of(2023, 12, 15);
        LocalDate january2024 = LocalDate.of(2024, 1, 15);
        LocalDate february2024 = LocalDate.of(2024, 2, 15);

        Transaction decemberTransaction = createTransaction(new BigDecimal("100.00"), 
                december2023, "Store", "December expense", 
                "ASANKA", Kind.EXPENSE, asanka);

        Transaction januaryTransaction = createTransaction(new BigDecimal("50.00"), 
                january2024, "Store", "January expense", 
                "ASANKA", Kind.EXPENSE, asanka);

        Transaction februaryTransaction = createTransaction(new BigDecimal("75.00"), 
                february2024, "Store", "February expense", 
                "ASANKA", Kind.EXPENSE, asanka);

        transactionRepository.saveAll(java.util.Arrays.asList(decemberTransaction, januaryTransaction, februaryTransaction));

        // When & Then - Only January transaction should be included
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("25.0"))
                .andExpect(jsonPath("$.asankaPaid").value("50.0"))
                .andExpect(jsonPath("$.divyaPaid").value("0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    @Test
    void getMonthlyBalance_ComplexRentalIncomeScenario_ShouldCalculateCorrectly() throws Exception {
        // Given - Complex scenario with rental income
        LocalDate january2024 = LocalDate.of(2024, 1, 15);

        // Asanka: $200 expenses, $100 rental income = $100 net
        Transaction asankaExpense = createTransaction(new BigDecimal("200.00"), 
                january2024, "Store", "Expense", 
                "ASANKA", Kind.EXPENSE, asanka);
        
        Transaction asankaRentalBillIncome = createTransaction(new BigDecimal("50.00"), 
                january2024.plusDays(5), "Tenant", "Rental bill income", 
                "ASANKA", Kind.RENTALBILLINCOME, asanka);
        
        Transaction asankaRentalRentIncome = createTransaction(new BigDecimal("50.00"), 
                january2024.plusDays(10), "Tenant", "Rental rent income", 
                "ASANKA", Kind.RENTALRENTINCOME, asanka);

        // Divya: $150 expenses, $25 rental income = $125 net
        Transaction divyaExpense = createTransaction(new BigDecimal("150.00"), 
                january2024, "Store", "Expense", 
                "DIVYA", Kind.EXPENSE, divya);
        
        Transaction divyaRentalIncome = createTransaction(new BigDecimal("25.00"), 
                january2024.plusDays(8), "Tenant", "Rental bill income", 
                "DIVYA", Kind.RENTALBILLINCOME, divya);

        transactionRepository.saveAll(java.util.Arrays.asList(
                asankaExpense, asankaRentalBillIncome, asankaRentalRentIncome,
                divyaExpense, divyaRentalIncome
        ));

        // When & Then
        // Asanka: $200 - $50 - $50 = $100 net
        // Divya: $150 - $25 = $125 net
        // Asanka's share: $100/2 = $50, Divya's share: $125/2 = $62.50
        // Difference: $62.50 - $50 = $12.50, so Asanka owes Divya $12.50

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("12.5"))
                .andExpect(jsonPath("$.asankaPaid").value("100.0"))
                .andExpect(jsonPath("$.divyaPaid").value("125.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Asanka"));
    }

    @Test
    void getMonthlyBalance_MonthOutOfRange_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "13")
                        .param("year", "2024"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMonthlyBalance_CardPaymentWithoutPerson_ShouldNotFail() throws Exception {
        Transaction asankaExpense = createTransaction(new BigDecimal("100.00"),
                LocalDate.of(2024, 1, 15), "Store", "Expense",
                "ASANKA", Kind.EXPENSE, asanka);

        Transaction orphanCardPayment = createTransaction(new BigDecimal("-250.00"),
                LocalDate.of(2024, 1, 20), "Card Payment", "Unrecognised card number",
                "CIBC", Kind.CARDPAYMENT, null);

        transactionRepository.saveAll(Arrays.asList(asankaExpense, orphanCardPayment));

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("50.0"))
                .andExpect(jsonPath("$.asankaPaid").value("100.0"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    @Test
    void getMonthlyBalance_PersonalUncategorizedAndTransfers_AreNotCounted() throws Exception {
        LocalDate day = LocalDate.of(2024, 1, 15);

        Transaction shared = createTransaction(new BigDecimal("100.00"), day, "Store", "Shared",
                "ASANKA", Kind.EXPENSE, asanka);
        Transaction personal = createTransaction(new BigDecimal("500.00"), day, "Gym gear", "Personal",
                "ASANKA", Kind.EXPENSE, asanka);
        personal.setShared(false);
        Transaction uncategorized = createTransaction(new BigDecimal("70.00"), day, "Mystery", "Awaiting review",
                "ASANKA", Kind.EXPENSE, asanka);
        uncategorized.setCategory(null);
        Transaction payment = createTransaction(new BigDecimal("5861.69"), day, "PAYMENT RECEIVED - THANK YOU", "Bill payment",
                "AMEX", Kind.CARDPAYMENT, divya);

        transactionRepository.saveAll(Arrays.asList(shared, personal, uncategorized, payment));

        // Only the $100 shared, categorized purchase counts.
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asankaPaid").value("100.0"))
                .andExpect(jsonPath("$.divyaPaid").value("0"))
                .andExpect(jsonPath("$.balanceAmount").value("50.0"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    /** How a scenario row behaves; each maps to a seeded-style category (the category decides, not a stored type). */
    private enum Kind { EXPENSE, BILL, RENTALBILLINCOME, RENTALRENTINCOME, CARDPAYMENT }

    /** @param amount the amount as the scenario describes it (a magnitude); the sign is derived from the kind */
    private Transaction createTransaction(BigDecimal amount, LocalDate date, String entity,
                                        String details, String accountCode,
                                        Kind kind, Person person) {
        Transaction transaction = new Transaction();
        transaction.setDate(date);
        transaction.setEntity(entity);
        transaction.setDetails(details);
        transaction.setAccount(accounts.get(accountCode));
        transaction.setPerson(person);
        switch (kind) {
            case EXPENSE, BILL -> {
                transaction.setAmount(amount.abs().negate());
                transaction.setCategory(testCategory);
            }
            case RENTALBILLINCOME -> {
                transaction.setAmount(amount.abs());
                transaction.setCategory(rentalBillIncome);
            }
            case RENTALRENTINCOME -> {
                transaction.setAmount(amount.abs());
                transaction.setCategory(rentalRentIncome);
            }
            case CARDPAYMENT -> {
                transaction.setAmount(amount.abs());
                transaction.setCategory(cardPayment);
            }
        }
        return transaction;
    }

    private Category category(String name, Category.CategoryKind kind, boolean inBalance) {
        Category category = new Category();
        category.setName(name);
        category.setKind(kind);
        category.setInBalance(inBalance);
        return categoryRepository.save(category);
    }

    private void account(String code, Account.AccountKind kind, Person payer) {
        Account account = new Account();
        account.setCode(code);
        account.setName(code);
        account.setKind(kind);
        account.setPayer(payer);
        accounts.put(code, accountRepository.save(account));
    }
}
