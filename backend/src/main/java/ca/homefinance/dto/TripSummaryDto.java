package ca.homefinance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** What a trip cost: spending is positive, refunds reduce it. Only transactions that count in the balance are included. */
@Data
@AllArgsConstructor
public class TripSummaryDto {

    private TripDto trip;
    private BigDecimal total;
    private Map<String, BigDecimal> byCategory;
    private Map<LocalDate, BigDecimal> byDay;
    /** Keyed by the code of the person who settles the account (ASANKA / DIVYA). */
    private Map<String, BigDecimal> byPayer;
    /** Budget minus total; null when the trip has no budget. */
    private BigDecimal budgetRemaining;
    /** Tagged transactions left out of the total because they are unshared or uncategorized. */
    private int uncountedTransactions;
}
