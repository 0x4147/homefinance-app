package ca.homefinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pending review items that belong to the same merchant, so one decision resolves all of them.
 *
 * @param key          stable merchant key; pass it back to review the whole group
 * @param merchant     a representative raw merchant description
 * @param count        number of pending transactions
 * @param total        sum of their amounts
 * @param suggestions  best-guess categories, most likely first (an AI suggestion, when present, leads)
 * @param ids          the pending {@code UncategorizedTransaction} ids in the group
 * @param aiSuggestion category proposed by AI, or null; never applied without a user decision
 * @param aiConfidence the model's self-reported confidence for {@code aiSuggestion}, or null
 * @param firstDate    date of the earliest transaction in the group, or null if none is dated
 * @param lastDate     date of the latest transaction in the group, or null if none is dated
 * @param transactions the individual pending transactions, newest first
 * @param direction    DEBIT or CREDIT when every transaction agrees, MIXED otherwise
 * @param accounts     codes of the accounts (CIBC, AMEX, ASANKA, DIVYA) the transactions came from, sorted
 */
public record ReviewGroup(String key, String merchant, int count, BigDecimal total,
                          List<String> suggestions, List<Integer> ids,
                          String aiSuggestion, Double aiConfidence,
                          LocalDate firstDate, LocalDate lastDate,
                          List<Line> transactions, Direction direction,
                          List<String> accounts) {

    /** Which way the money moved: a debit is money out, a credit is money in (e.g. a refund). */
    public enum Direction {
        DEBIT, CREDIT, MIXED;

        public static Direction of(BigDecimal amount) {
            return amount != null && amount.signum() > 0 ? CREDIT : DEBIT;
        }
    }

    /** One pending transaction within a group. */
    public record Line(Integer id, LocalDate date, String merchant, BigDecimal amount, Direction direction,
                       String account) {}
}
