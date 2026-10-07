package ca.homefinance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SettlementRequestDto {
    private BigDecimal amount;
    private LocalDate paidDate;
    private String notes;
}
