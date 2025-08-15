package ca.homefinance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "UncategorizedTransaction")
public class UncategorizedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "merchant", nullable = false)
    private String merchant;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "date", nullable = false)
    private java.time.LocalDate date;

    @Column(name = "suggested_categories", columnDefinition = "TEXT")
    private String suggestedCategories; // JSON array of suggested categories

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "reviewed")
    private Boolean reviewed = false;

    @Column(name = "assigned_category")
    private String assignedCategory;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}


