package ca.homefinance.service;

import ca.homefinance.dto.ReviewGroup;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.MerchantRule;
import ca.homefinance.entity.MerchantRule.Source;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.MerchantRuleRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.repository.UncategorizedTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static ca.homefinance.helper.TransactionCategorizationHelper.*;

@Service
public class TransactionCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategorizationService.class);
    private static final double SUGGESTION_MIN_SCORE = 0.50;
    /** Suggestions are guesses; never let them look as certain as a rule match. */
    private static final double SUGGESTION_MAX_CONFIDENCE = 0.79;
    private static final int SUGGESTION_LIMIT = 3;
    /** Share of a merchant's history that must agree before it becomes a rule. */
    private static final double HISTORY_AGREEMENT = 0.80;
    private static final int MIN_HISTORY_KEY_LENGTH = 4;

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UncategorizedTransactionRepository uncategorizedTransactionRepository;
    private final MerchantRuleRepository merchantRuleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile List<MerchantRule> cachedRules;

    public TransactionCategorizationService(CategoryRepository categoryRepository,
                                            TransactionRepository transactionRepository,
                                            UncategorizedTransactionRepository uncategorizedTransactionRepository,
                                            MerchantRuleRepository merchantRuleRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.uncategorizedTransactionRepository = uncategorizedTransactionRepository;
        this.merchantRuleRepository = merchantRuleRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedRules() {
        seedFromHistory();
        int resolved = recategorizePending();
        log.info("Startup categorization pass resolved {} pending review items", resolved);
    }

    /**
     * Most specific matching rule wins; otherwise the transaction is flagged for review with suggestions.
     */
    public Category categorizeTransaction(String merchant, String details, BigDecimal amount, LocalDate date) {
        String key = searchKey(merchant, details);

        MerchantRule rule = findBestRule(key, rules());
        if (rule != null) {
            log.debug("Rule match for '{}' -> {}", key, rule.getNormalizedMerchant());
            return rule.getCategory();
        }

        List<SuggestedCategory> suggestions = suggest(key, rules());
        double confidence = suggestions.isEmpty() ? 0.0 : suggestions.get(0).score();

        UncategorizedTransaction uncategorized = flagForUserReview(merchant, details, amount, date, suggestions, confidence);
        Category placeholder = new Category();
        placeholder.setUncategorizedTransaction(uncategorized);
        return placeholder;
    }

    @Transactional
    public void updateTransactionCategory(Integer transactionId, String newCategory) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        Category category = requireCategory(newCategory);
        transaction.setCategory(category);
        transactionRepository.save(transaction);

        learnFromUserCorrection(transaction.getEntity(), newCategory);
    }

    /**
     * Stores a user decision as a rule and resolves every pending review the new rule covers.
     *
     * @return number of pending reviews resolved
     */
    @Transactional
    public int learnFromUserCorrection(String merchant, String correctCategory) {
        String key = brandKey(merchant);
        if (key.isBlank()) {
            throw new IllegalArgumentException("Cannot learn from blank merchant: " + merchant);
        }
        Category category = requireCategory(correctCategory);

        MerchantRule rule = merchantRuleRepository.findByNormalizedMerchant(key).orElseGet(MerchantRule::new);
        rule.setNormalizedMerchant(key);
        rule.setCategory(category);
        rule.setSource(Source.USER);
        merchantRuleRepository.save(rule);
        cachedRules = null;

        int resolved = applyToPendingReviews(key, category);
        log.info("Learned rule: {} -> {} ({} pending resolved)", key, category.getName(), resolved);
        return resolved;
    }

    public List<String> getSuggestedCategories(String merchant, String details) {
        return suggest(searchKey(merchant, details), rules()).stream()
                .map(SuggestedCategory::name)
                .toList();
    }

    /**
     * Re-runs the current rules over every pending review. Use after rules change.
     *
     * @return number of pending reviews that could now be categorized
     */
    @Transactional
    public int recategorizePending() {
        List<MerchantRule> rules = rules();
        int resolved = 0;
        for (UncategorizedTransaction pending : uncategorizedTransactionRepository.findByReviewedFalseOrderByCreatedAtDesc()) {
            MerchantRule rule = findBestRule(searchKey(pending.getMerchant(), pending.getDetails()), rules);
            if (rule != null) {
                resolve(pending, rule.getCategory(), LocalDateTime.now());
                resolved++;
            }
        }
        return resolved;
    }

    /**
     * Pending reviews grouped by merchant, largest spend first, so each decision clears as many as possible.
     */
    public List<ReviewGroup> getReviewGroups() {
        Map<String, List<UncategorizedTransaction>> byKey = new LinkedHashMap<>();
        for (UncategorizedTransaction pending : uncategorizedTransactionRepository.findByReviewedFalseOrderByCreatedAtDesc()) {
            String key = brandKey(pending.getMerchant());
            byKey.computeIfAbsent(key.isBlank() ? normalize(pending.getMerchant()) : key, k -> new ArrayList<>()).add(pending);
        }
        List<MerchantRule> rules = rules();
        return byKey.entrySet().stream()
                .map(entry -> {
                    List<UncategorizedTransaction> items = entry.getValue();
                    UncategorizedTransaction first = items.get(0);
                    BigDecimal total = items.stream().map(UncategorizedTransaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<String> suggestions = suggest(searchKey(first.getMerchant(), first.getDetails()), rules).stream()
                            .map(SuggestedCategory::name).toList();
                    return new ReviewGroup(entry.getKey(), first.getMerchant(), items.size(), total, suggestions,
                            items.stream().map(UncategorizedTransaction::getId).toList());
                })
                .sorted(Comparator.comparing((ReviewGroup group) -> group.total().abs()).reversed())
                .toList();
    }

    private MerchantRule findBestRule(String key, List<MerchantRule> rules) {
        MerchantRule best = null;
        int bestSpecificity = 0;
        for (MerchantRule rule : rules) {
            int specificity = matchSpecificity(key, rule.getNormalizedMerchant());
            if (specificity == 0) continue;
            if (best == null || specificity > bestSpecificity
                    || (specificity == bestSpecificity && priority(rule) > priority(best))) {
                best = rule;
                bestSpecificity = specificity;
            }
        }
        return best;
    }

    private static int priority(MerchantRule rule) {
        return switch (rule.getSource()) {
            case USER -> 2;
            case SEED -> 1;
            case HISTORY -> 0;
        };
    }

    private List<SuggestedCategory> suggest(String key, List<MerchantRule> rules) {
        Map<String, Double> scoreByCategory = new HashMap<>();
        for (MerchantRule rule : rules) {
            double score = overlapScore(key, rule.getNormalizedMerchant());
            if (score >= SUGGESTION_MIN_SCORE) {
                scoreByCategory.merge(rule.getCategory().getName(), Math.min(score, SUGGESTION_MAX_CONFIDENCE), Math::max);
            }
        }
        return scoreByCategory.entrySet().stream()
                .map(entry -> new SuggestedCategory(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(SuggestedCategory::score).reversed()
                        .thenComparing(SuggestedCategory::name))
                .limit(SUGGESTION_LIMIT)
                .toList();
    }

    private int applyToPendingReviews(String key, Category category) {
        LocalDateTime now = LocalDateTime.now();
        int resolved = 0;
        for (UncategorizedTransaction pending : uncategorizedTransactionRepository.findByReviewedFalseOrderByCreatedAtDesc()) {
            if (matchSpecificity(searchKey(pending.getMerchant(), pending.getDetails()), key) == 0) {
                continue;
            }
            resolve(pending, category, now);
            resolved++;
        }
        return resolved;
    }

    private void resolve(UncategorizedTransaction pending, Category category, LocalDateTime when) {
        pending.setReviewed(true);
        pending.setAssignedCategory(category.getName());
        pending.setReviewedAt(when);
        uncategorizedTransactionRepository.save(pending);

        for (Transaction linked : transactionRepository.findByUncategorizedTransaction_Id(pending.getId())) {
            linked.setCategory(category);
            transactionRepository.save(linked);
        }
    }

    /**
     * Turns merchants whose history is consistently one category into rules, unless a rule already
     * covers them. Rules are keyed by brand, not by the full bank string, so they apply to future imports.
     */
    private void seedFromHistory() {
        List<MerchantRule> rules = rules();
        Map<String, Map<String, Integer>> countsByBrand = new HashMap<>();
        for (Transaction transaction : transactionRepository.findAll()) {
            if (transaction.getCategory() == null || transaction.getEntity() == null) {
                continue;
            }
            if (findBestRule(searchKey(transaction.getEntity(), null), rules) != null) {
                continue;
            }
            String brand = brandKey(transaction.getEntity());
            if (brand.replace(" ", "").length() < MIN_HISTORY_KEY_LENGTH) {
                continue;
            }
            countsByBrand.computeIfAbsent(brand, k -> new HashMap<>())
                    .merge(transaction.getCategory().getName(), 1, Integer::sum);
        }
        for (Map.Entry<String, Map<String, Integer>> entry : countsByBrand.entrySet()) {
            int total = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            entry.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .filter(top -> (double) top.getValue() / total >= HISTORY_AGREEMENT)
                    .map(top -> categoryRepository.findByName(top.getKey()))
                    .ifPresent(category -> addRuleIfAbsent(entry.getKey(), category, Source.HISTORY));
        }
        cachedRules = null;
    }

    private void addRuleIfAbsent(String key, Category category, Source source) {
        if (key.isBlank() || merchantRuleRepository.findByNormalizedMerchant(key).isPresent()) {
            return;
        }
        MerchantRule rule = new MerchantRule();
        rule.setNormalizedMerchant(key);
        rule.setCategory(category);
        rule.setSource(source);
        merchantRuleRepository.save(rule);
    }

    private UncategorizedTransaction flagForUserReview(String merchant, String details, BigDecimal amount,
                                                       LocalDate date, List<SuggestedCategory> suggestions,
                                                       double confidence) {
        try {
            String suggestedCategoriesJson = objectMapper.writeValueAsString(
                    suggestions.stream().map(SuggestedCategory::name).toList());

            UncategorizedTransaction uncategorized = new UncategorizedTransaction();
            uncategorized.setMerchant(merchant);
            uncategorized.setDetails(details);
            uncategorized.setAmount(amount);
            uncategorized.setDate(date);
            uncategorized.setSuggestedCategories(suggestedCategoriesJson);
            uncategorized.setConfidenceScore(round2(confidence));
            uncategorized.setReviewed(false);

            log.info("Transaction flagged for review: {} - {} - ${}", merchant, details, amount);
            return uncategorizedTransactionRepository.save(uncategorized);
        } catch (Exception e) {
            log.error("Failed to save uncategorized transaction for review", e);
        }
        return null;
    }

    private List<MerchantRule> rules() {
        List<MerchantRule> rules = cachedRules;
        if (rules == null) {
            rules = merchantRuleRepository.findAll();
            cachedRules = rules;
        }
        return rules;
    }

    private Category requireCategory(String name) {
        Category category = categoryRepository.findByName(name);
        if (category == null) {
            throw new RuntimeException("Category not found: " + name);
        }
        return category;
    }

    private static String searchKey(String merchant, String details) {
        String text = merchant == null ? "" : merchant;
        String normalized = normalize(text);
        if (details == null || details.isBlank()) {
            return normalized;
        }
        return (normalized + " " + normalize(details)).trim();
    }

    private record SuggestedCategory(String name, double score) {}
}
