package ca.homefinance.dto;

import lombok.Data;

@Data
public class MonthlyBalanceResponseDto {
    private String asankaPaid;
    private String divyaPaid;
    private String balanceAmount;
    private String whoOwes;
    private String monthAndYear;
}
