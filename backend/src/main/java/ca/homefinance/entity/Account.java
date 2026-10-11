package ca.homefinance.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An account transactions are imported from; {@link #payer} is who ultimately funds it. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Integer accountId;

    /** Stable identifier used by uploads and the UI: CIBC, AMEX, ASANKA or DIVYA. */
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private AccountKind kind;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payer_person_id", referencedColumnName = "person_id")
    private Person payer;

    /** Serialized as just the code, so JSON shows "account": "CIBC" as it always has. */
    @JsonValue
    public String getCode() {
        return code;
    }

    public enum AccountKind {
        BANK, CREDIT_CARD
    }
}
