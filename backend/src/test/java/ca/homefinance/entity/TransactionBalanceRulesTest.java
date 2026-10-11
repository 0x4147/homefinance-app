package ca.homefinance.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules that decide how a transaction behaves: its category's kind and in_balance flag, and the sign of the amount. */
class TransactionBalanceRulesTest {

    private static final Category GROCERIES = category(Category.CategoryKind.EXPENSE, true);
    private static final Category RENTAL_BILL_INCOME = category(Category.CategoryKind.INCOME, true);
    private static final Category SALARY = category(Category.CategoryKind.INCOME, false);
    private static final Category CARD_PAYMENT = category(Category.CategoryKind.TRANSFER, false);

    @Test
    void expense_IsMoneyOut_AddsToThePool() {
        Transaction tx = transaction("-40.00", GROCERIES, true);

        assertEquals(Transaction.TransactionType.EXPENSE, tx.getType());
        assertTrue(tx.countsInBalance());
        assertEquals(0, new BigDecimal("40.00").compareTo(tx.balanceContribution()));
    }

    @Test
    void refundInExpenseCategory_ReducesThePool() {
        Transaction tx = transaction("15.00", GROCERIES, true);

        assertEquals(Transaction.TransactionType.REFUND, tx.getType());
        assertEquals(0, new BigDecimal("-15.00").compareTo(tx.balanceContribution()));
    }

    @Test
    void rentalIncome_IsIncome_ReducesThePool() {
        Transaction tx = transaction("46.50", RENTAL_BILL_INCOME, true);

        assertEquals(Transaction.TransactionType.INCOME, tx.getType());
        assertEquals(0, new BigDecimal("-46.50").compareTo(tx.balanceContribution()));
    }

    @Test
    void incomeOutsideTheBalance_IsIgnored() {
        Transaction tx = transaction("3000.00", SALARY, true);

        assertEquals(Transaction.TransactionType.INCOME, tx.getType());
        assertFalse(tx.countsInBalance());
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.balanceContribution()));
    }

    @Test
    void cardPayment_IsATransfer_NeverCounts() {
        Transaction tx = transaction("250.00", CARD_PAYMENT, true);

        assertEquals(Transaction.TransactionType.TRANSFER, tx.getType());
        assertFalse(tx.countsInBalance());
    }

    @Test
    void personalSpending_NeverCounts_EvenInACountingCategory() {
        Transaction tx = transaction("-40.00", GROCERIES, false);

        assertEquals(Transaction.TransactionType.EXPENSE, tx.getType());
        assertFalse(tx.countsInBalance());
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.balanceContribution()));
    }

    @Test
    void uncategorized_IsShownAsSpendingButDoesNotCountUntilReviewed() {
        Transaction tx = transaction("-40.00", null, true);

        assertEquals(Transaction.TransactionType.EXPENSE, tx.getType());
        assertFalse(tx.countsInBalance());
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.balanceContribution()));
    }

    private static Category category(Category.CategoryKind kind, boolean inBalance) {
        Category category = new Category();
        category.setKind(kind);
        category.setInBalance(inBalance);
        return category;
    }

    private static Transaction transaction(String amount, Category category, boolean shared) {
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal(amount));
        tx.setCategory(category);
        tx.setShared(shared);
        return tx;
    }
}
