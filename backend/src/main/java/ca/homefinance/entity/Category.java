package ca.homefinance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "name", nullable = false)
    private String name;

    /** What the money is: spending, income, or a transfer between the household's own accounts. */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private CategoryKind kind;

    /** Transactions in this category count toward the shared-expense balance (if they are also shared). */
    @Column(name = "in_balance", nullable = false)
    private boolean inBalance;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private UncategorizedTransaction uncategorizedTransaction;

    public enum CategoryKind {
        EXPENSE, INCOME, TRANSFER
    }
}
