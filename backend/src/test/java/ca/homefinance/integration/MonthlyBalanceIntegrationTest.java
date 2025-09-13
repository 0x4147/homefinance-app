package ca.homefinance.integration;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
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


    private MockMvc mockMvc;

    private Person asanka;
    private Person divya;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Clean up existing data
        transactionRepository.deleteAll();
        personRepository.deleteAll();
        categoryRepository.deleteAll();

        // Create test persons
        asanka = new Person();
        asanka.setName("Asanka");
        asanka.setEmail("asanka@test.com");
        asanka = personRepository.save(asanka);

        divya = new Person();
        divya.setName("Divya");
        divya.setEmail("divya@test.com");
        divya = personRepository.save(divya);

        // Create test category
        testCategory = new Category();
        testCategory.setName("Test Category");
        testCategory.setType(Category.CategoryType.EXPENSE);
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    void getMonthlyBalance_RealDatabaseScenario_ShouldCalculateCorrectly() throws Exception {
        // Given - Create realistic transaction data for January 2024
        LocalDate january2024 = LocalDate.of(2024, 1, 2);

        // Asanka's transactions
        Transaction asankaExpense1 = createTransaction(new BigDecimal("120.50"), 
                january2024, "Grocery Store", "Weekly groceries", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);
        
        Transaction asankaExpense2 = createTransaction(new BigDecimal("45.00"), 
                january2024.plusDays(5), "Gas Station", "Fuel", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);
        
        Transaction asankaCardPayment = createTransaction(new BigDecimal("-200.00"),
                january2024.plusDays(10), "Card Payment", "Credit card payment", 
                Transaction.AccountType.CIBC, Transaction.TransactionType.CARDPAYMENT, asanka);
        
        Transaction asankaBill = createTransaction(new BigDecimal("85.30"), 
                january2024.plusDays(15), "Electric Company", "Electric bill", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.BILL, asanka);
        
        Transaction asankaRentalIncome = createTransaction(new BigDecimal("50.00"), 
                january2024.plusDays(11), "Tenant", "Rental bill income",
                Transaction.AccountType.ASANKA, Transaction.TransactionType.RENTALBILLINCOME, asanka);

        // Divya's transactions
        Transaction divyaExpense1 = createTransaction(new BigDecimal("95.75"), 
                january2024.plusDays(2), "Restaurant", "Dinner out", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);
        
        Transaction divyaExpense2 = createTransaction(new BigDecimal("30.00"), 
                january2024.plusDays(8), "Coffee Shop", "Coffee", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);
        
        Transaction divyaCardPayment = createTransaction(new BigDecimal("-150.00"),
                january2024.plusDays(12), "Card Payment", "Credit card payment", 
                Transaction.AccountType.AMEX, Transaction.TransactionType.CARDPAYMENT, divya);
        
        Transaction divyaBill = createTransaction(new BigDecimal("60.25"), 
                january2024.plusDays(18), "Internet Provider", "Internet bill", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.BILL, divya);
        
        Transaction divyaRentalIncome = createTransaction(new BigDecimal("75.00"), 
                january2024.plusDays(11), "Tenant", "Rental rent income",
                Transaction.AccountType.DIVYA, Transaction.TransactionType.RENTALRENTINCOME, divya);

        // Save all transactions
        transactionRepository.saveAll(java.util.Arrays.asList(
                asankaExpense1, asankaExpense2, asankaCardPayment, asankaBill, asankaRentalIncome,
                divyaExpense1, divyaExpense2, divyaCardPayment, divyaBill, divyaRentalIncome
        ));

        // When & Then
        // Expected calculations:
        // Asanka: $120.50 + $45.00 + $200.00 + $85.30 - $50.00 = $400.80
        // Divya: $95.75 + $30.00 + $150.00 + $60.25 - $75.00 = $261.00
        // Asanka's share: $400.80/2 = $200.40
        // Divya's share: $261.00/2 = $130.50
        // Difference: $130.50 - $200.40 = -$69.90, so Divya owes Asanka $69.90

        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("69.9"))
                .andExpect(jsonPath("$.asankaPaid").value("400.8"))
                .andExpect(jsonPath("$.divyaPaid").value("261.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
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
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

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
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

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
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction divyaExpense = createTransaction(new BigDecimal("100.00"), 
                january2024, "Store B", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

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
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction januaryTransaction = createTransaction(new BigDecimal("50.00"), 
                january2024, "Store", "January expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction februaryTransaction = createTransaction(new BigDecimal("75.00"), 
                february2024, "Store", "February expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

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
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);
        
        Transaction asankaRentalBillIncome = createTransaction(new BigDecimal("50.00"), 
                january2024.plusDays(5), "Tenant", "Rental bill income", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.RENTALBILLINCOME, asanka);
        
        Transaction asankaRentalRentIncome = createTransaction(new BigDecimal("50.00"), 
                january2024.plusDays(10), "Tenant", "Rental rent income", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.RENTALRENTINCOME, asanka);

        // Divya: $150 expenses, $25 rental income = $125 net
        Transaction divyaExpense = createTransaction(new BigDecimal("150.00"), 
                january2024, "Store", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);
        
        Transaction divyaRentalIncome = createTransaction(new BigDecimal("25.00"), 
                january2024.plusDays(8), "Tenant", "Rental bill income", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.RENTALBILLINCOME, divya);

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

    private Transaction createTransaction(BigDecimal amount, LocalDate date, String entity, 
                                        String details, Transaction.AccountType account, 
                                        Transaction.TransactionType transactionType, Person person) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setDate(date);
        transaction.setEntity(entity);
        transaction.setDetails(details);
        transaction.setAccount(account);
        transaction.setTransactionType(transactionType);
        transaction.setPerson(person);
        transaction.setCategory(testCategory);
        return transaction;
    }
}
