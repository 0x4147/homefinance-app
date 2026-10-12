package ca.homefinance.dto;

import java.util.List;

/**
 * Outcome of correcting a category for a merchant. Clients send it back unchanged to undo the correction.
 *
 * @param previousRuleCategoryId category of the user's rule before the correction, or null if it was a new rule
 * @param previousRuleSource     source of that rule before the correction, or null if it was a new rule
 */
public record CategoryCorrection(int updated, String merchant, String category, String ruleKey,
                                 Integer previousRuleCategoryId, String previousRuleSource,
                                 List<Change> changes) {

    /** A transaction's category before the correction; null means it was uncategorized. */
    public record Change(Integer transactionId, Integer previousCategoryId) {}
}
