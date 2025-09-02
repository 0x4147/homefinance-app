package ca.homefinance.service;

import ca.homefinance.dto.MatchedCategory;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.repository.UncategorizedTransactionRepository;
import ca.homefinance.entity.UncategorizedTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static ca.homefinance.helper.TransactionCategorizationHelper.*;

@Service
public class TransactionCategorizationService {
    
    private static final Logger log = LoggerFactory.getLogger(TransactionCategorizationService.class);
    
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UncategorizedTransactionRepository uncategorizedTransactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Enhanced mapping storage
    private Map<String, String> merchantToCategory = new HashMap<>();
    private Map<String, Double> merchantConfidence = new HashMap<>();
    private Map<String, List<String>> categoryKeywords = new HashMap<>();
    
    // Machine learning features
    private Map<String, Map<String, Integer>> merchantCategoryHistory = new HashMap<>();
    private Map<String, BigDecimal> categoryAmountPatterns = new HashMap<>();
    
    @Autowired
    public TransactionCategorizationService(CategoryRepository categoryRepository, 
                                          TransactionRepository transactionRepository,
                                          UncategorizedTransactionRepository uncategorizedTransactionRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.uncategorizedTransactionRepository = uncategorizedTransactionRepository;
        initializeCategorizationSystem();
    }
    
    private void initializeCategorizationSystem() {
        loadStaticMappings();
        analyzeHistoricalData();
    }
    
    /**
     * Main categorization method that combines multiple approaches
     */
    public Category categorizeTransaction(String merchant, String details, BigDecimal amount, LocalDate date) {

        // 1. Try exact merchant mapping first
        Category category = tryExactMerchantMapping(merchant);
        if (category != null) {
            log.debug("Exact match found for merchant: {}", merchant);
            return category;
        }

        // 2. Try fuzzy matching with keywords
        MatchedCategory matchedCategory = tryFuzzyKeywordMatching(merchant, details, true);
        if (matchedCategory != null && matchedCategory.getCategoryName() != null) {
            log.debug("Fuzzy match found for merchant: {}", merchant);
            return categoryRepository.findByName(matchedCategory.getCategoryName());
        }

        // 3. Try machine learning based on historical patterns
        category = tryMLBasedCategorization(merchant, amount, date);
        if (category != null) {
            log.debug("ML-based categorization for merchant: {}", merchant);
            return category;
        }

        // 4. Flag for user review if no match found
        UncategorizedTransaction uncategorizedTransaction = flagForUserReview(merchant, details, amount, date);
        Category Category = new Category();
        Category.setUncategorizedTransaction(uncategorizedTransaction);

        return Category;
    }

    public void updateTransactionCategory (Integer transactionId, String newCategory){
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        Category category = categoryRepository.findByName(newCategory);
        if (category == null) {
            throw new RuntimeException("Category not found: " + newCategory);
        }

        // Update the transaction
        transaction.setCategory(category);
        transactionRepository.save(transaction);

        // Learn from this correction
        learnFromUserCorrection(transaction.getEntity(), newCategory);
    }
    /**
     * Exact merchant mapping from static JSON
     */
    private Category tryExactMerchantMapping(String merchant) {
        String categoryName = merchantToCategory.get(merchant.toLowerCase());
        if (categoryName != null) {
            return categoryRepository.findByName(categoryName);
        }
        return null;
    }
    
    /**
     * Fuzzy keyword matching with confidence scoring
     */
    private MatchedCategory tryFuzzyKeywordMatching(String merchant, String details, boolean returnOnlyIfConfident) {
        double weakThreshold = 0.80;

        String merchantNormalized = normalize((merchant == null ? "" : merchant) + " " + (details == null ? "" : details));

        // Evaluate best candidate
        double bestScore = 0.0;
        String bestKey = null;
        String bestCategory = null;

        for (Map.Entry<String, String> entry : merchantToCategory.entrySet()) {
            String candidate = entry.getKey();
            double score = similarityScore(merchantNormalized, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestKey = candidate;
                bestCategory = entry.getValue();
            }
        }

        if (returnOnlyIfConfident){
            if(bestScore >= weakThreshold){
                return new MatchedCategory(bestKey, bestCategory, round2(bestScore));
            }
            else return null;
        }
        return new MatchedCategory(bestKey, bestCategory, round2(bestScore));
    }
    
    /**
     * Machine learning based on historical transaction patterns
     */
    private Category tryMLBasedCategorization(String merchant, BigDecimal amount, LocalDate date) {
        // Check if we have historical data for this merchant
        Map<String, Integer> categoryCounts = merchantCategoryHistory.get(merchant.toLowerCase());
        if (categoryCounts != null && !categoryCounts.isEmpty()) {
            // Find most frequent category for this merchant
            String mostFrequentCategory = categoryCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            
            if (mostFrequentCategory != null) {
                return categoryRepository.findByName(mostFrequentCategory);
            }
        }
        return null;
    }
    
    /**
     * Learn from user corrections and historical data
     */
    public void learnFromUserCorrection(String merchant, String correctCategory) {
        // Update static mappings
        merchantToCategory.put(merchant.toLowerCase(), correctCategory);
        
        // Update historical patterns
        merchantCategoryHistory.computeIfAbsent(merchant.toLowerCase(), k -> new HashMap<>())
                .merge(correctCategory, 1, Integer::sum);
        
        // Save updated mappings
        saveMappings();
        
        log.info("Learned new mapping: {} -> {}", merchant, correctCategory);
    }
    
    /**
     * Analyze historical transaction data to build patterns
     */
    private void analyzeHistoricalData() {
        List<Transaction> historicalTransactions = transactionRepository.findAll();
        
        for (Transaction transaction : historicalTransactions) {
            if (transaction.getCategory() != null && transaction.getEntity() != null) {
                String merchant = transaction.getEntity().toLowerCase();
                String category = transaction.getCategory().getName();
                
                // Build merchant-category history
                merchantCategoryHistory.computeIfAbsent(merchant, k -> new HashMap<>())
                        .merge(category, 1, Integer::sum);

            }
        }
        
        log.info("Analyzed {} historical transactions for patterns", historicalTransactions.size());
    }
    
    /**
     * Load static mappings from JSON file
     */
    private void loadStaticMappings() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("category_mappings.json")) {
            if (inputStream != null) {
                Map<String, List<String>> categories = objectMapper.readValue(inputStream, 
                        new TypeReference<Map<String, List<String>>>() {});
                
                for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
                    String category = entry.getKey();
                    for (String merchant : entry.getValue()) {
                        merchantToCategory.put(merchant.toLowerCase(), category);
                    }
                }
                
                log.info("Loaded {} static merchant mappings", merchantToCategory.size());
            }
        } catch (IOException e) {
            log.error("Failed to load static mappings", e);
        }
    }

    /**
     * Flag transaction for user review
     */
    private UncategorizedTransaction flagForUserReview(String merchant, String details, BigDecimal amount, LocalDate date) {
        try {
            // Get suggested categories
            List<String> suggestions = getSuggestedCategories(merchant, details);
            String suggestedCategoriesJson = objectMapper.writeValueAsString(suggestions);
            
            // Calculate confidence score
            double confidence = calculateConfidenceScore(merchant, details, amount);
            
            // Save to database for user review
            UncategorizedTransaction uncategorized = new UncategorizedTransaction();
            uncategorized.setMerchant(merchant);
            uncategorized.setDetails(details);
            uncategorized.setAmount(amount);
            uncategorized.setDate(date);
            uncategorized.setSuggestedCategories(suggestedCategoriesJson);
            uncategorized.setConfidenceScore(confidence);
            uncategorized.setReviewed(false);

            log.info("Transaction flagged for review: {} - {} - ${}", merchant, details, amount);
            return uncategorizedTransactionRepository.save(uncategorized);

        } catch (Exception e) {
            log.error("Failed to save uncategorized transaction for review", e);
        }
        return null;
    }
    
    /**
     * Calculate confidence score for categorization
     */
    private double calculateConfidenceScore(String merchant, String details, BigDecimal amount) {
        double score = 0.0;
        
        // Check if merchant exists in historical data
        if (merchantCategoryHistory.containsKey(merchant.toLowerCase())) {
            score += 0.3;
        }
        
        // Check if keywords match
        String searchText = (merchant + " " + (details != null ? details : "")).toLowerCase();
        for (List<String> keywords : categoryKeywords.values()) {
            for (String keyword : keywords) {
                if (searchText.contains(keyword.toLowerCase())) {
                    score += 0.2;
                    break;
                }
            }
        }
        
        // Check amount patterns
        if (amount.doubleValue() > 0 && amount.doubleValue() < 1000) {
            score += 0.1;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Save updated mappings to persistent storage
     */
    private void saveMappings() {
        // In a production system, you'd save to a database or file
        // For now, we'll just keep it in memory
        log.info("Mappings updated, {} merchants mapped", merchantToCategory.size());
    }
    
    /**
     * Get categorization confidence for a merchant
     */
    public double getCategorizationConfidence(String merchant) {
        return merchantConfidence.getOrDefault(merchant.toLowerCase(), 0.0);
    }
    
    /**
     * Get suggested categories for manual review
     */
    public List<String> getSuggestedCategories(String merchant, String details) {
        Set<String> suggestions = new HashSet<>();

        MatchedCategory matchedCategory = tryFuzzyKeywordMatching(merchant, details, false);

        suggestions.add(matchedCategory.getCategoryName());

        // Add categories from historical data
        Map<String, Integer> categoryCounts = merchantCategoryHistory.get(merchant.toLowerCase());
        if (categoryCounts != null) {
            suggestions.addAll(categoryCounts.keySet());
        }
        
        return new ArrayList<>(suggestions);
    }
}
