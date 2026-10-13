package ca.homefinance.dto;

import java.math.BigDecimal;
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
 */
public record ReviewGroup(String key, String merchant, int count, BigDecimal total,
                          List<String> suggestions, List<Integer> ids,
                          String aiSuggestion, Double aiConfidence) {}
