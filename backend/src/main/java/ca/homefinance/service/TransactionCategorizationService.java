package ca.homefinance.service;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.repository.UncategorizedTransactionRepository;
import ca.homefinance.entity.UncategorizedTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
        loadCategoryKeywords();
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
        category = tryFuzzyKeywordMatching(merchant, details);
        if (category != null) {
            log.debug("Fuzzy match found for merchant: {}", merchant);
            return category;
        }
        
        // 3. Try machine learning based on historical patterns
        category = tryMLBasedCategorization(merchant, amount, date);
        if (category != null) {
            log.debug("ML-based categorization for merchant: {}", merchant);
            return category;
        }
        
        // 4. Try amount-based heuristics
        category = tryAmountBasedCategorization(amount, merchant);
        if (category != null) {
            log.debug("Amount-based categorization for merchant: {}", merchant);
            return category;
        }
        
        // 5. Flag for user review if no match found
        flagForUserReview(merchant, details, amount, date);
        
        return null; // Return null to indicate no automatic categorization
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
    private Category tryFuzzyKeywordMatching(String merchant, String details) {
        String searchText = (merchant + " " + (details != null ? details : "")).toLowerCase();
        Map<String, Double> categoryScores = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : categoryKeywords.entrySet()) {
            String categoryName = entry.getKey();
            List<String> keywords = entry.getValue();
            
            double score = 0.0;
            for (String keyword : keywords) {
                if (searchText.contains(keyword.toLowerCase())) {
                    score += 1.0;
                    // Bonus for exact word matches
                    if (searchText.matches(".*\\b" + keyword.toLowerCase() + "\\b.*")) {
                        score += 0.5;
                    }
                }
            }
            
            if (score > 0) {
                categoryScores.put(categoryName, score);
            }
        }
        
        // Return category with highest score if above threshold
        return categoryScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() >= 1.0) // Minimum threshold
                .map(entry -> categoryRepository.findByName(entry.getKey()))
                .orElse(null);
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
        
        // Try amount-based pattern matching
        return tryAmountBasedCategorization(amount, merchant);
    }
    
    /**
     * Amount-based categorization using heuristics
     */
    private Category tryAmountBasedCategorization(BigDecimal amount, String merchant) {
        double amountValue = amount.doubleValue();
        
        // Groceries: typically $20-$200
        if (amountValue >= 20 && amountValue <= 200 && 
            (merchant.toLowerCase().contains("supermarket") || 
             merchant.toLowerCase().contains("grocery") ||
             merchant.toLowerCase().contains("food"))) {
            return categoryRepository.findByName("Groceries");
        }
        
        // Take Out: typically $10-$50
        if (amountValue >= 10 && amountValue <= 50 && 
            (merchant.toLowerCase().contains("restaurant") || 
             merchant.toLowerCase().contains("cafe") ||
             merchant.toLowerCase().contains("pizza") ||
             merchant.toLowerCase().contains("burger"))) {
            return categoryRepository.findByName("Take Out");
        }
        
        // Gas/Transportation: typically $40-$100
        if (amountValue >= 40 && amountValue <= 100 && 
            (merchant.toLowerCase().contains("gas") || 
             merchant.toLowerCase().contains("esso") ||
             merchant.toLowerCase().contains("shell") ||
             merchant.toLowerCase().contains("petro"))) {
            return categoryRepository.findByName("Transportation");
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
                
                // Build amount patterns
                categoryAmountPatterns.merge(category, transaction.getAmount(), BigDecimal::add);
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
     * Load category keywords for fuzzy matching
     */
    private void loadCategoryKeywords() {
        categoryKeywords.put("Groceries", Arrays.asList(
            "supermarket", "grocery", "food basics", "sobeys", "freshco", "no frills", 
            "costco", "walmart", "food", "market", "produce"
        ));
        
        categoryKeywords.put("Take Out", Arrays.asList(
            "restaurant", "cafe", "pizza", "burger", "mcdonalds", "tim hortons", 
            "starbucks", "subway", "kfc", "wendys", "dairy queen", "shawarma"
        ));
        
        categoryKeywords.put("Transportation", Arrays.asList(
            "gas", "esso", "shell", "petro", "pioneer", "parking", "uber", "lyft", 
            "taxi", "transit", "bus", "train", "subway"
        ));
        
        categoryKeywords.put("Healthcare", Arrays.asList(
            "pharmacy", "drug", "medical", "doctor", "hospital", "clinic", 
            "dental", "optical", "prescription", "health"
        ));
        
        categoryKeywords.put("Home maintenance", Arrays.asList(
            "home depot", "canadian tire", "ikea", "hardware", "tools", 
            "furniture", "appliance", "repair", "maintenance"
        ));
        
        categoryKeywords.put("Entertainment", Arrays.asList(
            "movie", "theatre", "cinema", "concert", "show", "game", "sport", 
            "lcbo", "beer", "wine", "liquor", "bar", "pub"
        ));
        
        categoryKeywords.put("Amazon", Arrays.asList(
            "amazon", "amzn"
        ));
        
        categoryKeywords.put("Temu", Arrays.asList(
            "temu"
        ));
    }
    
    /**
     * Flag transaction for user review
     */
    private void flagForUserReview(String merchant, String details, BigDecimal amount, LocalDate date) {
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
            
            uncategorizedTransactionRepository.save(uncategorized);
            
            log.info("Transaction flagged for review: {} - {} - ${}", merchant, details, amount);
        } catch (Exception e) {
            log.error("Failed to save uncategorized transaction for review", e);
        }
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
        
        // Add categories from fuzzy matching
        String searchText = (merchant + " " + (details != null ? details : "")).toLowerCase();
        for (Map.Entry<String, List<String>> entry : categoryKeywords.entrySet()) {
            String categoryName = entry.getKey();
            List<String> keywords = entry.getValue();
            
            for (String keyword : keywords) {
                if (searchText.contains(keyword.toLowerCase())) {
                    suggestions.add(categoryName);
                    break;
                }
            }
        }
        
        // Add categories from historical data
        Map<String, Integer> categoryCounts = merchantCategoryHistory.get(merchant.toLowerCase());
        if (categoryCounts != null) {
            suggestions.addAll(categoryCounts.keySet());
        }
        
        return new ArrayList<>(suggestions);
    }
}
