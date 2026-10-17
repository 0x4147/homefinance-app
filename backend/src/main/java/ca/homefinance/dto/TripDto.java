package ca.homefinance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TripDto {

    private Integer tripId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    /** Optional spending target; null when the trip has none. */
    private BigDecimal budget;
    /**
     * Read-only: what the trip has cost (spending positive, refunds negative; only transactions that count in the
     * balance). Filled in by the trip list and summary; null elsewhere and ignored on input.
     */
    private BigDecimal total;
}
