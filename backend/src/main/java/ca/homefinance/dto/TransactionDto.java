package ca.homefinance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDto {

    private Integer transactionId;
    private BigDecimal amount;
    private LocalDate date;
    private String entity;
    private String details;
    private String account;
    private String transactionType;
    private String category;
    private String person;
    /** The editable flag: false means this one transaction is kept out of the shared balance. */
    private boolean shared;
    /** Read-only result of the balance rule: shared, and in a category that counts. */
    private boolean countsInBalance;

    public TransactionDto(Integer transactionId, BigDecimal amount, LocalDate date, String entity, String details,
                          String account, String transactionType, String category, String person) {
        this(transactionId, amount, date, entity, details, account, transactionType, category, person, false, false);
    }

}
