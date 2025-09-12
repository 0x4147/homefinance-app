package ca.homefinance.controller;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.*;
import ca.homefinance.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private CategoryRepository categoryRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private PersonRepository personRepository;

    @MockBean
    private ReceiptRepository receiptRepository;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private UncategorizedTransactionRepository uncategorizedTransactionRepository;

    private Person asanka;
    private Person divya;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        asanka = new Person();
        asanka.setPersonId(1);
        asanka.setName("Asanka");

        divya = new Person();
        divya.setPersonId(2);
        divya.setName("Divya");

        testCategory = new Category();
        testCategory.setCategoryId(1);
        testCategory.setName("Test Category");
    }

    @Test
    void getMonthlyBalance_EqualPayments_ShouldReturnZeroBalance() throws Exception {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Mock empty transaction lists - both people paid equally (nothing)
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), any(), any()))
                .thenReturn(Collections.emptyList());

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
    void getMonthlyBalance_AsankaOwesDivya_ShouldReturnCorrectBalance() throws Exception {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Asanka has $100 expense, Divya has $0
        Transaction asankaExpense = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Test Store", "Test expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        // Mock expenses from personal accounts
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense));

        // Mock other transaction types as empty
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.CARDPAYMENT))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.BILL))))
                .thenReturn(Collections.emptyList());

        // When & Then
        // Asanka paid $100, Divya paid $0
        // Asanka's share: $100/2 = $50, Divya's share: $0/2 = $0
        // Difference: $0 - $50 = -$50, so Divya owes Asanka $50
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("50.0"))
                .andExpect(jsonPath("$.asankaPaid").value("100.0"))
                .andExpect(jsonPath("$.divyaPaid").value("0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    @Test
    void getMonthlyBalance_DivyaOwesAsanka_ShouldReturnCorrectBalance() throws Exception {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Divya has $200 expense, Asanka has $0
        Transaction divyaExpense = createTransaction(2, new BigDecimal("200.00"), 
                LocalDate.of(2024, 1, 15), "Test Store", "Test expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        // Mock expenses from personal accounts
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(divyaExpense));

        // Mock other transaction types as empty
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.CARDPAYMENT))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.BILL))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME))))
                .thenReturn(Collections.emptyList());

        // When & Then
        // Asanka paid $0, Divya paid $200
        // Asanka's share: $0/2 = $0, Divya's share: $200/2 = $100
        // Difference: $100 - $0 = $100, so Asanka owes Divya $100
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("100.0"))
                .andExpect(jsonPath("$.asankaPaid").value("0"))
                .andExpect(jsonPath("$.divyaPaid").value("200.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Asanka"));
    }

    @Test
    void getMonthlyBalance_ComplexScenario_ShouldCalculateCorrectly() throws Exception {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Asanka: $100 expense + $50 card payment + $30 bill = $180 total
        Transaction asankaExpense = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 10), "Store A", "Expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);
        
        Transaction asankaCardPayment = createTransaction(1, new BigDecimal("-50.00"),
                LocalDate.of(2024, 1, 15), "Card Payment", "Card payment", 
                Transaction.AccountType.CIBC, Transaction.TransactionType.CARDPAYMENT, asanka);
        
        Transaction asankaBill = createTransaction(1, new BigDecimal("30.00"), 
                LocalDate.of(2024, 1, 20), "Utility Bill", "Bill payment", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.BILL, asanka);

        // Divya: $80 expense + $40 card payment + $20 bill = $140 total
        Transaction divyaExpense = createTransaction(2, new BigDecimal("80.00"), 
                LocalDate.of(2024, 1, 12), "Store B", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);
        
        Transaction divyaCardPayment = createTransaction(2, new BigDecimal("-40.00"),
                LocalDate.of(2024, 1, 18), "Card Payment", "Card payment", 
                Transaction.AccountType.AMEX, Transaction.TransactionType.CARDPAYMENT, divya);
        
        Transaction divyaBill = createTransaction(2, new BigDecimal("20.00"), 
                LocalDate.of(2024, 1, 25), "Utility Bill", "Bill payment", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.BILL, divya);

        // Mock different transaction types
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense, divyaExpense));

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.CARDPAYMENT))))
                .thenReturn(Arrays.asList(asankaCardPayment, divyaCardPayment));

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.BILL))))
                .thenReturn(Arrays.asList(asankaBill, divyaBill));

        // Mock other transaction types as empty
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME))))
                .thenReturn(Collections.emptyList());

        // When & Then
        // Asanka: $100 + $50 (negated) + $30 = $180
        // Divya: $80 + $40 (negated) + $20 = $140
        // Asanka's share: $180/2 = $90, Divya's share: $140/2 = $70
        // Difference: $70 - $90 = -$20, so Divya owes Asanka $20
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("20.0"))
                .andExpect(jsonPath("$.asankaPaid").value("180.0"))
                .andExpect(jsonPath("$.divyaPaid").value("140.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    @Test
    void getMonthlyBalance_WithRentalIncome_ShouldSubtractIncome() throws Exception {
        // Given

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // Asanka: $100 expense - $20 rental income = $80 net
        Transaction asankaExpense = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 10), "Store A", "Expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);
        
        Transaction asankaRentalIncome = createTransaction(1, new BigDecimal("20.00"), 
                LocalDate.of(2024, 1, 15), "Rental Income", "Rental bill income", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.RENTALBILLINCOME, asanka);

        // Divya: $60 expense
        Transaction divyaExpense = createTransaction(2, new BigDecimal("60.00"), 
                LocalDate.of(2024, 1, 12), "Store B", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        // Mock expenses
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense, divyaExpense));

        // Mock rental income
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME))))
                .thenReturn(Arrays.asList(asankaRentalIncome));

        // Mock other transaction types as empty
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.CARDPAYMENT))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.BILL))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME))))
                .thenReturn(Collections.emptyList());

        // When & Then
        // Asanka: $100 - $20 = $80 net
        // Divya: $60
        // Asanka's share: $80/2 = $40, Divya's share: $60/2 = $30
        // Difference: $30 - $40  = -$10, so Divya owes Asanka $10
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("10.0"))
                .andExpect(jsonPath("$.asankaPaid").value("80.0"))
                .andExpect(jsonPath("$.divyaPaid").value("60.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    @Test
    void getMonthlyBalance_WithCardPayments_ShouldHandleNegatedAmounts() throws Exception {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Asanka: $100 card payment (will be negated from -100 to +100)
        Transaction asankaCardPayment = createTransaction(1, new BigDecimal("-100.00"),
                LocalDate.of(2024, 1, 15), "Card Payment", "Card payment", 
                Transaction.AccountType.CIBC, Transaction.TransactionType.CARDPAYMENT, asanka);

        // Divya: $50 card payment (will be negated from -50 to +50)
        Transaction divyaCardPayment = createTransaction(2, new BigDecimal("-50.00"),
                LocalDate.of(2024, 1, 18), "Card Payment", "Card payment", 
                Transaction.AccountType.AMEX, Transaction.TransactionType.CARDPAYMENT, divya);

        // Mock card payments
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
                eq(Arrays.asList(Transaction.TransactionType.CARDPAYMENT))))
                .thenReturn(Arrays.asList(asankaCardPayment, divyaCardPayment));

        // Mock other transaction types as empty
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Collections.emptyList());

        // Mock rental income
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME))))
                .thenReturn(Collections.emptyList());

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
                eq(Arrays.asList(Transaction.TransactionType.BILL))))
                .thenReturn(Collections.emptyList());

        // When & Then
        // Asanka: -$100 (negated card payment)
        // Divya: -$50 (negated card payment)
        // Asanka's share: $100/2 = $50, Divya's share: $50/2 = $25
        // Difference: $25 - $50 = -$25, so Divya owes Asanka $25
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("25.0"))
                .andExpect(jsonPath("$.asankaPaid").value("100.0"))
                .andExpect(jsonPath("$.divyaPaid").value("50.0"))
                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
                .andExpect(jsonPath("$.whoOwes").value("Divya"));
    }

    @Test
    void getMonthlyBalance_InvalidMonth_ShouldThrowException() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "invalid")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMonthlyBalance_InvalidYear_ShouldThrowException() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "1")
                        .param("year", "invalid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMonthlyBalance_EdgeCaseFebruaryLeapYear_ShouldHandleCorrectly() throws Exception {
        // Given - February 2024 (leap year)
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 29);

        // Mock empty transaction lists
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "2")
                        .param("year", "2024")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("0"))
                .andExpect(jsonPath("$.monthAndYear").value("2, 2024"));
    }

    @Test
    void getMonthlyBalance_EdgeCaseFebruaryNonLeapYear_ShouldHandleCorrectly() throws Exception {
        // Given - February 2023 (non-leap year)
        LocalDate startDate = LocalDate.of(2023, 2, 1);
        LocalDate endDate = LocalDate.of(2023, 2, 28);

        // Mock empty transaction lists
        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
                        .param("month", "2")
                        .param("year", "2023")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAmount").value("0"))
                .andExpect(jsonPath("$.monthAndYear").value("2, 2023"));
    }

    /** MIGHT NOT REQUIRE THIS ROUDING CHECKL*/
//    @Test
//    void getMonthlyBalance_DecimalPrecision_ShouldRoundCorrectly() throws Exception {
//        // Given
//        LocalDate startDate = LocalDate.of(2024, 1, 1);
//        LocalDate endDate = LocalDate.of(2024, 1, 31);
//
//        // Asanka: $99 expense (will result in $49.5 when divided by 2)
//        Transaction asankaExpense = createTransaction(1, new BigDecimal("99.00"),
//                LocalDate.of(2024, 1, 15), "Test Store", "Test expense",
//                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);
//
//        // Divya: $100 expense (will result in $50 when divided by 2)
//        Transaction divyaExpense = createTransaction(2, new BigDecimal("100.00"),
//                LocalDate.of(2024, 1, 15), "Test Store", "Test expense",
//                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);
//
//        // Mock expenses
//        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
//                eq(startDate), eq(endDate),
//                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
//                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
//                .thenReturn(Arrays.asList(asankaExpense, divyaExpense));
//
//        // Mock other transaction types as empty
//        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
//                any(LocalDate.class),
//                any(LocalDate.class),
//                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
//                eq(Arrays.asList(Transaction.TransactionType.RENTALBILLINCOME))))
//                .thenReturn(Collections.emptyList());
//
//        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
//                any(LocalDate.class),
//                any(LocalDate.class),
//                eq(Arrays.asList(Transaction.AccountType.CIBC, Transaction.AccountType.AMEX)),
//                eq(Arrays.asList(Transaction.TransactionType.CARDPAYMENT))))
//                .thenReturn(Collections.emptyList());
//
//        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
//                any(LocalDate.class),
//                any(LocalDate.class),
//                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
//                eq(Arrays.asList(Transaction.TransactionType.RENTALRENTINCOME))))
//                .thenReturn(Collections.emptyList());
//
//        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
//                any(LocalDate.class),
//                any(LocalDate.class),
//                eq(Arrays.asList(Transaction.AccountType.DIVYA, Transaction.AccountType.ASANKA)),
//                eq(Arrays.asList(Transaction.TransactionType.BILL))))
//                .thenReturn(Collections.emptyList());
//
//        // When & Then
//        // Asanka: $99/2 = $49.5 (rounded to $50 with HALF_UP)
//        // Divya: $100/2 = $50
//        // Difference: $50 - $50 = $0, so no one owes anything
//        mockMvc.perform(get("/api/v1/transaction/getMonthlyBalance")
//                        .param("month", "1")
//                        .param("year", "2024")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.balanceAmount").value("0"))
//                .andExpect(jsonPath("$.asankaPaid").value("99.0"))
//                .andExpect(jsonPath("$.divyaPaid").value("100.0"))
//                .andExpect(jsonPath("$.monthAndYear").value("1, 2024"))
//                .andExpect(jsonPath("$.whoOwes").doesNotExist());
//    }

    private Transaction createTransaction(Integer personId, BigDecimal amount, LocalDate date, 
                                        String entity, String details, Transaction.AccountType account, 
                                        Transaction.TransactionType transactionType, Person person) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(personId);
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
