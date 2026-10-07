package ca.homefinance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SettlementDto {
    private int year;
    private int month;
    private BigDecimal amount;
    private LocalDate paidDate;
    private String notes;
    /** Name of the person who paid. */
    private String from;
    /** Name of the person who received the payment. */
    private String to;
}
