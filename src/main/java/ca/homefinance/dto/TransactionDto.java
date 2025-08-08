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

    private BigDecimal amount;
    private LocalDate date;
    private String entity;
    private String details;
    private String account;
    private String transactionType;
    private String category;
    private String person;

}
