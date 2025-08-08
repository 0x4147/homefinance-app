package ca.homefinance.dto;

import ca.homefinance.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Data
public class TransactionSummary {
    private Map<String, BigDecimal> totals;
    private Map<String, List<Transaction>> details;
}
