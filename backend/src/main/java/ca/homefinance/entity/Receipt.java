package ca.homefinance.entity;

import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "receipt")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer receiptId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 255)
    private String filePath;

    @Column(nullable = false)
    private LocalDate receiptDate;

    @ManyToOne
    @JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id", nullable = false)
    private Transaction transaction;
}
