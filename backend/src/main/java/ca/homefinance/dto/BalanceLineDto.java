package ca.homefinance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One transaction behind a person's "paid" figure in the monthly balance. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceLineDto {
    private TransactionDto transaction;
    /** Signed amount this transaction adds to the person's paid figure (rental income is negative). */
    private BigDecimal contribution;
    /** EXPENSE, BILL, RENTAL_BILL_INCOME, RENTAL_RENT_INCOME or CARD_SPENDING. */
    private String kind;
}
