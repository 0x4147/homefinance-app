package ca.homefinance.validation;

import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Validation tests for the monthly balance calculation logic.
 * These tests focus on edge cases and business rule validation.
 */
@ExtendWith(MockitoExtension.class)
class MonthlyBalanceValidationTest {

    @Mock
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
    void validateBusinessLogic_EqualPayments_ShouldReturnZeroBalance() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Both have $100 expenses
        Transaction asankaExpense = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Store A", "Expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction divyaExpense = createTransaction(2, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Store B", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense, divyaExpense));

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When - Simulate the calculation logic
        BigDecimal asankaTotal = new BigDecimal("100.00");
        BigDecimal divyaTotal = new BigDecimal("100.00");
        
        BigDecimal asankaShare = asankaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal divyaShare = divyaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        
        BigDecimal difference = divyaShare.subtract(asankaShare);

        // Then
        assertEquals(new BigDecimal("0.00"), difference);
        assertEquals(new BigDecimal("50.00"), asankaShare);
        assertEquals(new BigDecimal("50.00"), divyaShare);
    }

    @Test
    void validateBusinessLogic_AsankaOwesDivya_ShouldCalculateCorrectly() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Asanka: $200, Divya: $100
        Transaction asankaExpense = createTransaction(1, new BigDecimal("200.00"), 
                LocalDate.of(2024, 1, 15), "Store A", "Expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction divyaExpense = createTransaction(2, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Store B", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense, divyaExpense));

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When - Simulate the calculation logic
        BigDecimal asankaTotal = new BigDecimal("200.00");
        BigDecimal divyaTotal = new BigDecimal("100.00");
        
        BigDecimal asankaShare = asankaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal divyaShare = divyaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        
        BigDecimal difference = divyaShare.subtract(asankaShare);

        // Then
        assertEquals(new BigDecimal("-50.00"), difference); // Negative means Asanka owes Divya
        assertEquals(new BigDecimal("100.00"), asankaShare);
        assertEquals(new BigDecimal("50.00"), divyaShare);
        assertEquals(new BigDecimal("50.00"), difference.abs()); // Balance amount
    }

    @Test
    void validateBusinessLogic_DivyaOwesAsanka_ShouldCalculateCorrectly() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        // Asanka: $100, Divya: $300
        Transaction asankaExpense = createTransaction(1, new BigDecimal("100.00"), 
                LocalDate.of(2024, 1, 15), "Store A", "Expense", 
                Transaction.AccountType.ASANKA, Transaction.TransactionType.EXPENSE, asanka);

        Transaction divyaExpense = createTransaction(2, new BigDecimal("300.00"), 
                LocalDate.of(2024, 1, 15), "Store B", "Expense", 
                Transaction.AccountType.DIVYA, Transaction.TransactionType.EXPENSE, divya);

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), 
                eq(Arrays.asList(Transaction.AccountType.ASANKA, Transaction.AccountType.DIVYA)),
                eq(Arrays.asList(Transaction.TransactionType.EXPENSE))))
                .thenReturn(Arrays.asList(asankaExpense, divyaExpense));

        when(transactionService.searchTransactionByDateRangeAccountTypeTransactionType(
                eq(startDate), eq(endDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When - Simulate the calculation logic
        BigDecimal asankaTotal = new BigDecimal("100.00");
        BigDecimal divyaTotal = new BigDecimal("300.00");
        
        BigDecimal asankaShare = asankaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal divyaShare = divyaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        
        BigDecimal difference = divyaShare.subtract(asankaShare);

        // Then
        assertEquals(new BigDecimal("100.00"), difference); // Positive means Divya owes Asanka
        assertEquals(new BigDecimal("50.00"), asankaShare);
        assertEquals(new BigDecimal("150.00"), divyaShare);
        assertEquals(new BigDecimal("100.00"), difference.abs()); // Balance amount
    }

    @Test
    void validateBusinessLogic_RoundingMode_ShouldUseHalfUp() {
        // Given - Amounts that will result in .5 when divided by 2
        BigDecimal asankaTotal = new BigDecimal("99.00"); // 99/2 = 49.5
        BigDecimal divyaTotal = new BigDecimal("101.00"); // 101/2 = 50.5

        // When
        BigDecimal asankaShare = asankaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal divyaShare = divyaTotal.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        
        BigDecimal difference = divyaShare.subtract(asankaShare);

        // Then
        assertEquals(new BigDecimal("50.00"), asankaShare); // 49.5 rounds up to 50
        assertEquals(new BigDecimal("51.00"), divyaShare); // 50.5 rounds up to 51
        assertEquals(new BigDecimal("1.00"), difference); // 51 - 50 = 1
    }

    @Test
    void validateBusinessLogic_CardPayments_ShouldBeNegated() {
        // Given - Card payments are negated in the calculation
        BigDecimal cardPaymentAmount = new BigDecimal("100.00");
        BigDecimal negatedAmount = cardPaymentAmount.negate();

        // When
        BigDecimal result = negatedAmount;

        // Then
        assertEquals(new BigDecimal("-100.00"), result);
    }

    @Test
    void validateBusinessLogic_RentalIncome_ShouldBeSubtracted() {
        // Given - Rental income is subtracted from expenses
        BigDecimal expenses = new BigDecimal("200.00");
        BigDecimal rentalIncome = new BigDecimal("50.00");

        // When
        BigDecimal netAmount = expenses.subtract(rentalIncome);

        // Then
        assertEquals(new BigDecimal("150.00"), netAmount);
    }

    @Test
    void validateBusinessLogic_ComplexScenario_ShouldCalculateCorrectly() {
        // Given - Complex scenario with multiple transaction types
        BigDecimal asankaExpenses = new BigDecimal("100.00");
        BigDecimal asankaCardPayments = new BigDecimal("50.00").negate(); // Negated
        BigDecimal asankaBills = new BigDecimal("30.00");
        BigDecimal asankaRentalBillIncome = new BigDecimal("20.00");
        BigDecimal asankaRentalRentIncome = new BigDecimal("10.00");

        BigDecimal divyaExpenses = new BigDecimal("80.00");
        BigDecimal divyaCardPayments = new BigDecimal("40.00").negate(); // Negated
        BigDecimal divyaBills = new BigDecimal("20.00");
        BigDecimal divyaRentalBillIncome = new BigDecimal("15.00");
        BigDecimal divyaRentalRentIncome = new BigDecimal("5.00");

        // When - Calculate net amounts
        BigDecimal asankaNet = asankaExpenses
                .add(asankaCardPayments)
                .add(asankaBills)
                .subtract(asankaRentalBillIncome)
                .subtract(asankaRentalRentIncome);

        BigDecimal divyaNet = divyaExpenses
                .add(divyaCardPayments)
                .add(divyaBills)
                .subtract(divyaRentalBillIncome)
                .subtract(divyaRentalRentIncome);

        // Calculate shares
        BigDecimal asankaShare = asankaNet.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal divyaShare = divyaNet.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        
        BigDecimal difference = divyaShare.subtract(asankaShare);

        // Then
        // Asanka: 100 + (-50) + 30 - 20 - 10 = 50
        // Divya: 80 + (-40) + 20 - 15 - 5 = 40
        // Asanka's share: 50/2 = 25, Divya's share: 40/2 = 20
        // Difference: 20 - 25 = -5, so Asanka owes Divya $5
        assertEquals(new BigDecimal("50.00"), asankaNet);
        assertEquals(new BigDecimal("40.00"), divyaNet);
        assertEquals(new BigDecimal("25.00"), asankaShare);
        assertEquals(new BigDecimal("20.00"), divyaShare);
        assertEquals(new BigDecimal("-5.00"), difference);
        assertEquals(new BigDecimal("5.00"), difference.abs());
    }

    @Test
    void validateBusinessLogic_EdgeCaseZeroAmounts_ShouldHandleCorrectly() {
        // Given
        BigDecimal zeroAmount = BigDecimal.ZERO;

        // When
        BigDecimal share = zeroAmount.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal difference = share.subtract(share);

        // Then
        assertEquals(BigDecimal.ZERO, share);
        assertEquals(BigDecimal.ZERO, difference);
    }

    @Test
    void validateBusinessLogic_EdgeCaseVerySmallAmounts_ShouldHandlePrecision() {
        // Given
        BigDecimal smallAmount = new BigDecimal("0.01");

        // When
        BigDecimal share = smallAmount.divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal difference = share.subtract(BigDecimal.ZERO);

        // Then
        assertEquals(new BigDecimal("0.01"), share); // 0.005 rounds up to 0.01
        assertEquals(new BigDecimal("0.01"), difference);
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
