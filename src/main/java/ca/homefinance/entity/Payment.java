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
@Table(name = "Payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_date_range")
    private LocalDate startDateRange;

    @Column(name = "end_date_range")
    private LocalDate endDateRange;

    @ManyToOne
    @JoinColumn(name = "from_person_id", referencedColumnName = "person_id")
    private Person fromPerson;

    @ManyToOne
    @JoinColumn(name = "to_person_id", referencedColumnName = "person_id")
    private Person toPerson;

    @ManyToOne
    @JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")
    private Transaction transaction;

}
