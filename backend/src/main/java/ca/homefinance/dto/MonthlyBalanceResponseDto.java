package ca.homefinance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlyBalanceResponseDto {
    private BigDecimal asankaPaid;
    private BigDecimal divyaPaid;
    private BigDecimal balanceAmount;
    private String whoOwes;
    private String monthAndYear;
}
