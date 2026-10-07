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
    private Integer month;
    private Integer year;
    /** SETTLED, UNSETTLED or EVEN. Set by SettlementService. */
    private String status;
    private SettlementDto settlement;
    /** True when a settlement exists but its amount no longer matches the computed balance. */
    private boolean settlementMismatch;
}
