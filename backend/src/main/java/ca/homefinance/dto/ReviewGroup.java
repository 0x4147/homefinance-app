package ca.homefinance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pending review items that belong to the same merchant, so one decision resolves all of them.
 *
 * @param key         stable merchant key; pass it back to review the whole group
 * @param merchant    a representative raw merchant description
 * @param count       number of pending transactions
 * @param total       sum of their amounts
 * @param suggestions best-guess categories, most likely first
 * @param ids         the pending {@code UncategorizedTransaction} ids in the group
 */
public record ReviewGroup(String key, String merchant, int count, BigDecimal total,
                          List<String> suggestions, List<Integer> ids) {}
