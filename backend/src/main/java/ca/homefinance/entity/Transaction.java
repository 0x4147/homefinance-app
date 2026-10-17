package ca.homefinance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Integer transactionId;

    /** Signed cash flow: negative = money out, positive = money in (a refund is money in). */
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "entity", nullable = false)
    private String entity; // Payer or Payee

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id")
    private Account account;

    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "category_id")
    private Category category;

    @OneToOne
    @JoinColumn(name = "uncategorized_transaction_id", referencedColumnName = "id")
    private UncategorizedTransaction uncategorizedTransaction;

    /** Cardholder / who made the purchase; who actually pays is {@link Account#getPayer()}. */
    @ManyToOne
    @JoinColumn(name = "person_id", referencedColumnName = "person_id")
    private Person person;

    /** Counts toward the monthly shared-expense balance; false for one person's own spending. */
    @Column(name = "shared", nullable = false)
    private boolean shared = true;

    /**
     * Who this transaction is really for. Null means it is split between both people; otherwise that person
     * owes all of it, even though {@link Account#getPayer()} settled it (a personal purchase on a shared card).
     */
    @ManyToOne
    @JoinColumn(name = "owner_person_id", referencedColumnName = "person_id")
    private Person owner;

    /** The trip this transaction is tagged with, if any; a reporting label only, it never affects the balance. */
    @ManyToOne
    @JoinColumn(name = "trip_id", referencedColumnName = "trip_id")
    private Trip trip;

    /** Display label derived from the category's kind and the sign of the amount; never stored. */
    public enum TransactionType {
        EXPENSE, REFUND, INCOME, TRANSFER
    }

    public TransactionType getType() {
        Category.CategoryKind kind = category == null ? null : category.getKind();
        if (kind == Category.CategoryKind.TRANSFER) {
            return TransactionType.TRANSFER;
        }
        boolean moneyIn = amount != null && amount.signum() > 0;
        if (kind == Category.CategoryKind.INCOME) {
            return moneyIn ? TransactionType.INCOME : TransactionType.EXPENSE;
        }
        // EXPENSE category, or not categorized yet (treated as spending, money in as a refund)
        return moneyIn ? TransactionType.REFUND : TransactionType.EXPENSE;
    }

    /** Takes part in the shared-expense balance: shared, and categorized in a category that counts. */
    public boolean countsInBalance() {
        return shared && category != null && category.isInBalance();
    }

    /**
     * Part of the shared finances: it counts in the balance and is split between both people, not owed
     * entirely by one. This is what the shared dashboard and insights are built from.
     */
    public boolean isSharedExpense() {
        return countsInBalance() && owner == null;
    }

    /** What this transaction adds to its payer's "paid" figure: spending is positive, income/refunds negative. */
    public BigDecimal balanceContribution() {
        return countsInBalance() ? amount.negate() : BigDecimal.ZERO;
    }
}
