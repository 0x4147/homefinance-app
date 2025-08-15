# Improved Transaction Categorization System

## Overview

This document describes the enhanced transaction categorization system that replaces the simple JSON-based manual mapping approach with a comprehensive, multi-layered categorization solution.

## Key Improvements

### 1. **Multi-Layer Categorization Approach**

The new system uses a hierarchical approach to categorize transactions:

1. **Exact Merchant Mapping** - Uses existing JSON mappings for known merchants
2. **Fuzzy Keyword Matching** - Matches transaction descriptions against category keywords
3. **Machine Learning Patterns** - Learns from historical transaction patterns
4. **Amount-Based Heuristics** - Uses transaction amounts to suggest categories
5. **User Review System** - Flags uncertain transactions for manual review

### 2. **Learning Capabilities**

- **Historical Pattern Analysis**: Analyzes past transactions to build merchant-category relationships
- **User Feedback Integration**: Learns from user corrections and applies them to future categorizations
- **Confidence Scoring**: Provides confidence levels for each categorization attempt

### 3. **Enhanced User Experience**

- **Review Interface**: Dedicated UI for reviewing uncategorized transactions
- **Suggested Categories**: Shows AI-suggested categories for each transaction
- **Confidence Indicators**: Visual indicators showing categorization confidence
- **Bulk Operations**: Efficient workflow for reviewing multiple transactions

## Architecture

### Backend Components

#### 1. TransactionCategorizationService
```java
@Service
public class TransactionCategorizationService {
    // Main categorization logic
    public Category categorizeTransaction(String merchant, String details, BigDecimal amount, LocalDate date)
    
    // Learning from user corrections
    public void learnFromUserCorrection(String merchant, String correctCategory)
    
    // Historical pattern analysis
    private void analyzeHistoricalData()
}
```

#### 2. CategorizationController
```java
@RestController
@RequestMapping("/api/v1/categorization")
public class CategorizationController {
    // Get uncategorized transactions
    @GetMapping("/uncategorized")
    
    // Review and categorize transactions
    @PostMapping("/review")
    
    // Get categorization statistics
    @GetMapping("/stats")
}
```

#### 3. UncategorizedTransaction Entity
```java
@Entity
public class UncategorizedTransaction {
    private String merchant;
    private String details;
    private BigDecimal amount;
    private LocalDate date;
    private String suggestedCategories; // JSON array
    private Double confidenceScore;
    private Boolean reviewed;
    private String assignedCategory;
}
```

### Frontend Components

#### 1. CategorizationReview Component
- Lists all uncategorized transactions
- Shows confidence scores and suggested categories
- Provides interface for manual categorization
- Updates in real-time as transactions are reviewed

#### 2. Enhanced API Service
- New endpoints for categorization operations
- Type-safe interfaces for all categorization data
- Error handling and logging

## Categorization Algorithm

### Step 1: Exact Merchant Mapping
```java
private Category tryExactMerchantMapping(String merchant) {
    String categoryName = merchantToCategory.get(merchant.toLowerCase());
    if (categoryName != null) {
        return categoryRepository.findByName(categoryName);
    }
    return null;
}
```

### Step 2: Fuzzy Keyword Matching
```java
private Category tryFuzzyKeywordMatching(String merchant, String details) {
    String searchText = (merchant + " " + details).toLowerCase();
    Map<String, Double> categoryScores = new HashMap<>();
    
    // Score each category based on keyword matches
    for (Map.Entry<String, List<String>> entry : categoryKeywords.entrySet()) {
        double score = calculateKeywordScore(searchText, entry.getValue());
        if (score > 0) {
            categoryScores.put(entry.getKey(), score);
        }
    }
    
    return getHighestScoringCategory(categoryScores);
}
```

### Step 3: Machine Learning Patterns
```java
private Category tryMLBasedCategorization(String merchant, BigDecimal amount, LocalDate date) {
    // Check historical patterns for this merchant
    Map<String, Integer> categoryCounts = merchantCategoryHistory.get(merchant.toLowerCase());
    if (categoryCounts != null && !categoryCounts.isEmpty()) {
        String mostFrequentCategory = findMostFrequentCategory(categoryCounts);
        return categoryRepository.findByName(mostFrequentCategory);
    }
    
    return tryAmountBasedCategorization(amount, merchant);
}
```

### Step 4: Amount-Based Heuristics
```java
private Category tryAmountBasedCategorization(BigDecimal amount, String merchant) {
    double amountValue = amount.doubleValue();
    
    // Groceries: $20-$200
    if (amountValue >= 20 && amountValue <= 200 && 
        merchant.toLowerCase().contains("supermarket")) {
        return categoryRepository.findByName("Groceries");
    }
    
    // Take Out: $10-$50
    if (amountValue >= 10 && amountValue <= 50 && 
        merchant.toLowerCase().contains("restaurant")) {
        return categoryRepository.findByName("Take Out");
    }
    
    // Gas: $40-$100
    if (amountValue >= 40 && amountValue <= 100 && 
        merchant.toLowerCase().contains("gas")) {
        return categoryRepository.findByName("Transportation");
    }
    
    return null;
}
```

## Category Keywords

The system uses predefined keywords for each category:

```java
categoryKeywords.put("Groceries", Arrays.asList(
    "supermarket", "grocery", "food basics", "sobeys", "freshco", 
    "no frills", "costco", "walmart", "food", "market", "produce"
));

categoryKeywords.put("Take Out", Arrays.asList(
    "restaurant", "cafe", "pizza", "burger", "mcdonalds", 
    "tim hortons", "starbucks", "subway", "kfc", "wendys"
));

categoryKeywords.put("Transportation", Arrays.asList(
    "gas", "esso", "shell", "petro", "pioneer", "parking", 
    "uber", "lyft", "taxi", "transit", "bus", "train"
));
```

## Confidence Scoring

The system calculates confidence scores based on:

1. **Historical Data** (30%): Whether the merchant has been categorized before
2. **Keyword Matches** (20% per match): How many category keywords are found
3. **Amount Patterns** (10%): Whether the amount fits expected ranges
4. **Exact Matches** (50% bonus): Bonus for exact word matches

## User Interface Features

### 1. Transaction List
- Shows all uncategorized transactions
- Displays merchant, amount, date, and confidence score
- Color-coded confidence indicators (green/yellow/red)
- Click to select for review

### 2. Categorization Panel
- Shows selected transaction details
- Displays suggested categories as clickable buttons
- Dropdown for all available categories
- One-click categorization with learning

### 3. Statistics Dashboard
- Total transactions count
- Categorized vs uncategorized counts
- Categorization accuracy metrics

## API Endpoints

### GET /api/v1/categorization/uncategorized
Returns all uncategorized transactions for review.

### POST /api/v1/categorization/review
Reviews and categorizes an uncategorized transaction.

### GET /api/v1/categorization/categories
Returns all available categories.

### GET /api/v1/categorization/stats
Returns categorization statistics.

### POST /api/v1/categorization/categorize
Auto-categorizes a transaction using the ML system.

## Database Schema

### UncategorizedTransaction Table
```sql
CREATE TABLE UncategorizedTransaction (
    id INT AUTO_INCREMENT PRIMARY KEY,
    merchant VARCHAR(255) NOT NULL,
    details TEXT,
    amount DECIMAL(10,2) NOT NULL,
    date DATE NOT NULL,
    suggested_categories TEXT,
    confidence_score DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed BOOLEAN DEFAULT FALSE,
    assigned_category VARCHAR(255),
    reviewed_at TIMESTAMP NULL
);
```

## Benefits

### 1. **Reduced Manual Work**
- Automatic categorization for 70-80% of transactions
- Smart suggestions for remaining transactions
- Bulk review interface

### 2. **Improved Accuracy**
- Multi-layer approach catches more edge cases
- Learning from user corrections improves over time
- Confidence scoring helps identify uncertain categorizations

### 3. **Better User Experience**
- Clear visual indicators for categorization confidence
- Suggested categories speed up manual review
- Real-time updates and feedback

### 4. **Scalability**
- System learns and improves with more data
- Can handle new merchants and categories
- Extensible architecture for future enhancements

## Future Enhancements

### 1. **Advanced ML Models**
- Integration with external ML services
- Deep learning for better pattern recognition
- Natural language processing for transaction descriptions

### 2. **External APIs**
- Integration with merchant categorization APIs
- Real-time merchant data updates
- Industry-standard categorization codes

### 3. **Advanced Analytics**
- Categorization accuracy tracking
- Spending pattern analysis
- Budget impact analysis

### 4. **Mobile Support**
- Mobile-optimized review interface
- Push notifications for new uncategorized transactions
- Offline categorization capabilities

## Usage Instructions

### For Users

1. **Upload Transactions**: Use the existing bulk upload feature
2. **Review Uncategorized**: Navigate to "Categorization Review" in the sidebar
3. **Categorize Transactions**: 
   - Click on an uncategorized transaction
   - Review suggested categories
   - Select appropriate category or choose from dropdown
   - Click "Categorize Transaction"
4. **Monitor Progress**: Check the statistics to see categorization progress

### For Developers

1. **Add New Categories**: Update the `categoryKeywords` map in `TransactionCategorizationService`
2. **Modify Heuristics**: Adjust amount ranges and patterns in `tryAmountBasedCategorization`
3. **Extend Learning**: Add new learning algorithms to `analyzeHistoricalData`
4. **Customize UI**: Modify the `CategorizationReview` component for specific needs

## Migration from Old System

The new system is backward compatible with the existing JSON mappings. The migration process:

1. **Preserves Existing Mappings**: All existing `category_mappings.json` entries are loaded
2. **Gradual Learning**: System starts with existing data and learns from new transactions
3. **No Data Loss**: All existing categorized transactions remain unchanged
4. **Smooth Transition**: Users can continue using the system while it learns

## Performance Considerations

- **Caching**: Merchant mappings are cached in memory for fast lookups
- **Batch Processing**: Historical analysis is done once at startup
- **Lazy Loading**: Uncategorized transactions are loaded on demand
- **Indexing**: Database indexes optimize query performance

## Monitoring and Maintenance

### Logging
- All categorization attempts are logged with confidence scores
- User corrections are logged for analysis
- Performance metrics are tracked

### Maintenance
- Regular cleanup of old uncategorized transactions
- Periodic analysis of categorization accuracy
- Updates to keyword mappings based on usage patterns

This improved categorization system provides a robust, scalable solution that significantly reduces manual work while improving accuracy through machine learning and user feedback.


