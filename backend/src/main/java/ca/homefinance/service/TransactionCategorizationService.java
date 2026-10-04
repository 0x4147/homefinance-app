package ca.homefinance.service;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.MerchantRule;
import ca.homefinance.entity.MerchantRule.Source;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.MerchantRuleRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.repository.UncategorizedTransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ca.homefinance.helper.TransactionCategorizationHelper.*;

@Service
public class TransactionCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategorizationService.class);
    private static final String MAPPINGS_FILE = "category_mappings.json";
    private static final double FUZZY_THRESHOLD = 0.80;
    private static final double SUGGESTION_MIN_SCORE = 0.50;
    private static final int SUGGESTION_LIMIT = 3;

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UncategorizedTransactionRepository uncategorizedTransactionRepository;
    private final MerchantRuleRepository merchantRuleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        seedFromStaticMappings();
        seedFromHistory();
    }

    /**
     * Rule phrase match first, then fuzzy match; otherwise flags the transaction for review.
     */
    public Category categorizeTransaction(String merchant, String details, BigDecimal amount, LocalDate date) {
        String key = searchKey(merchant, details);
        List<MerchantRule> rules = merchantRuleRepository.findAll();

        MerchantRule containedRule = findContainedRule(key, rules);
        if (containedRule != null) {
            log.debug("Rule match for '{}' -> {}", key, containedRule.getNormalizedMerchant());
            return containedRule.getCategory();
        }

        List<ScoredRule> ranked = rankRules(key, rules);
        double topScore = ranked.isEmpty() ? 0.0 : ranked.get(0).score();
        if (topScore >= FUZZY_THRESHOLD) {
            log.debug("Fuzzy match for '{}' -> {} ({})", key, ranked.get(0).rule().getNormalizedMerchant(), topScore);
            return ranked.get(0).rule().getCategory();
        }

        UncategorizedTransaction uncategorized = flagForUserReview(merchant, details, amount, date, ranked, topScore);
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
     * Stores a user decision as a rule and resolves any pending reviews for the same merchant.
     */
    @Transactional
    public void learnFromUserCorrection(String merchant, String correctCategory) {
        String key = normalize(merchant);
        if (key.isBlank()) {
            throw new IllegalArgumentException("Cannot learn from blank merchant: " + merchant);
        }
        Category category = requireCategory(correctCategory);

        MerchantRule rule = merchantRuleRepository.findByNormalizedMerchant(key).orElseGet(MerchantRule::new);
        rule.setNormalizedMerchant(key);
        rule.setCategory(category);
        rule.setSource(Source.USER);
        merchantRuleRepository.save(rule);

        applyToPendingReviews(key, category);
        log.info("Learned rule: {} -> {}", key, category.getName());
    }

    public List<String> getSuggestedCategories(String merchant, String details) {
        List<ScoredRule> ranked = rankRules(searchKey(merchant, details), merchantRuleRepository.findAll());
        return suggestionsFrom(ranked);
    }

    private MerchantRule findContainedRule(String key, List<MerchantRule> rules) {
        String padded = " " + key + " ";
        return rules.stream()
                .filter(rule -> padded.contains(" " + rule.getNormalizedMerchant() + " "))
                .max(Comparator.comparingInt(rule -> rule.getNormalizedMerchant().length()))
                .orElse(null);
    }

    private List<ScoredRule> rankRules(String key, List<MerchantRule> rules) {
        return rules.stream()
                .map(rule -> new ScoredRule(rule, similarityScore(key, rule.getNormalizedMerchant())))
                .sorted(Comparator.comparingDouble(ScoredRule::score).reversed())
                .toList();
    }

    private List<String> suggestionsFrom(List<ScoredRule> ranked) {
        return ranked.stream()
                .filter(scored -> scored.score() >= SUGGESTION_MIN_SCORE)
                .limit(SUGGESTION_LIMIT)
                .map(scored -> scored.rule().getCategory().getName())
                .distinct()
                .toList();
    }

    private void applyToPendingReviews(String key, Category category) {
        LocalDateTime now = LocalDateTime.now();
        for (UncategorizedTransaction pending : uncategorizedTransactionRepository.findByReviewedFalseOrderByCreatedAtDesc()) {
            if (!key.equals(normalize(pending.getMerchant()))) {
                continue;
            }
            pending.setReviewed(true);
            pending.setAssignedCategory(category.getName());
            pending.setReviewedAt(now);
            uncategorizedTransactionRepository.save(pending);

            for (Transaction linked : transactionRepository.findByUncategorizedTransaction_Id(pending.getId())) {
                linked.setCategory(category);
                transactionRepository.save(linked);
            }
        }
    }

    private void seedFromStaticMappings() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(MAPPINGS_FILE)) {
            if (inputStream == null) {
                log.warn("{} not found; skipping static rule seed", MAPPINGS_FILE);
                return;
            }
            Map<String, List<String>> mappings = objectMapper.readValue(inputStream,
                    new TypeReference<Map<String, List<String>>>() {});
            for (Map.Entry<String, List<String>> entry : mappings.entrySet()) {
                Category category = categoryRepository.findByName(entry.getKey());
                if (category == null) {
                    log.warn("Category '{}' from {} does not exist", entry.getKey(), MAPPINGS_FILE);
                    continue;
                }
                for (String merchant : entry.getValue()) {
                    addRuleIfAbsent(normalize(merchant), category, Source.SEED);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load {}", MAPPINGS_FILE, e);
        }
    }

    private void seedFromHistory() {
        Map<String, Map<String, Integer>> countsByMerchant = new HashMap<>();
        for (Transaction transaction : transactionRepository.findAll()) {
            if (transaction.getCategory() == null || transaction.getEntity() == null) {
                continue;
            }
            countsByMerchant.computeIfAbsent(normalize(transaction.getEntity()), k -> new HashMap<>())
                    .merge(transaction.getCategory().getName(), 1, Integer::sum);
        }
        for (Map.Entry<String, Map<String, Integer>> entry : countsByMerchant.entrySet()) {
            String mostFrequent = entry.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            Category category = mostFrequent == null ? null : categoryRepository.findByName(mostFrequent);
            if (category != null) {
                addRuleIfAbsent(entry.getKey(), category, Source.HISTORY);
            }
        }
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
                                                       LocalDate date, List<ScoredRule> ranked, double confidence) {
        try {
            String suggestedCategoriesJson = objectMapper.writeValueAsString(suggestionsFrom(ranked));

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

    private Category requireCategory(String name) {
        Category category = categoryRepository.findByName(name);
        if (category == null) {
            throw new RuntimeException("Category not found: " + name);
        }
        return category;
    }

    private static String searchKey(String merchant, String details) {
        return normalize((merchant == null ? "" : merchant) + " " + (details == null ? "" : details));
    }

    private record ScoredRule(MerchantRule rule, double score) {}
}
