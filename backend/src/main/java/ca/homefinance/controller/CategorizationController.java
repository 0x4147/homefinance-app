package ca.homefinance.controller;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.repository.UncategorizedTransactionRepository;
import ca.homefinance.service.TransactionCategorizationService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/categorization")
public class CategorizationController {

    @Autowired
    private final TransactionCategorizationService categorizationService;
    
    @Autowired
    private final CategoryRepository categoryRepository;
    
    @Autowired
    private final TransactionRepository transactionRepository;
    
    @Autowired
    private final UncategorizedTransactionRepository uncategorizedTransactionRepository;

    /**
     * Get suggested categories for a merchant
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestedCategories(
            @RequestParam String merchant,
            @RequestParam(required = false) String details) {
        
        List<String> suggestions = categorizationService.getSuggestedCategories(merchant, details);
        return new ResponseEntity<>(suggestions, HttpStatus.OK);
    }

    /**
     * Learn from user correction
     */
    @PostMapping("/learn")
    public ResponseEntity<String> learnFromCorrection(
            @RequestParam String merchant,
            @RequestParam String correctCategory) {
        
        categorizationService.learnFromUserCorrection(merchant, correctCategory);
        return new ResponseEntity<>("Learning applied successfully", HttpStatus.OK);
    }

    /**
     * Auto-categorize a transaction
     */
    @PostMapping("/categorize")
    public ResponseEntity<Map<String, Object>> categorizeTransaction(
            @RequestParam String merchant,
            @RequestParam(required = false) String details,
            @RequestParam String amount,
            @RequestParam String date) {
        
        Category category = categorizationService.categorizeTransaction(
                merchant, details, new java.math.BigDecimal(amount), 
                java.time.LocalDate.parse(date));
        
        Map<String, Object> response = Map.of(
                "merchant", merchant,
                "category", category != null ? category.getName() : null,
                "confidence", categorizationService.getCategorizationConfidence(merchant),
                "autoCategorized", category != null
        );
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Update transaction category and learn from it
     */
    @PutMapping("/updateTransactionCategory")
    public ResponseEntity<String> updateTransactionCategory(
            @RequestParam Long transactionId,
            @RequestParam String newCategory) {
        
        Transaction transaction = transactionRepository.findById(transactionId.intValue())
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        
        Category category = categoryRepository.findByName(newCategory);
        if (category == null) {
            throw new RuntimeException("Category not found: " + newCategory);
        }
        
        // Update the transaction
        transaction.setCategory(category);
        transactionRepository.save(transaction);
        
        // Learn from this correction
        categorizationService.learnFromUserCorrection(transaction.getEntity(), newCategory);
        
        return new ResponseEntity<>("Transaction category updated and learning applied", HttpStatus.OK);
    }

    /**
     * Get all categories for dropdown selection
     */
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        return new ResponseEntity<>(categories, HttpStatus.OK);
    }

    /**
     * Get categorization statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCategorizationStats() {
        // This would return statistics about categorization accuracy
        // For now, return a simple response
        // Calculate stats manually since the repository methods don't exist
        long totalTransactions = transactionRepository.count();
        long categorizedTransactions = transactionRepository.findAll().stream()
                .filter(t -> t.getCategory() != null).count();
        long uncategorizedTransactions = totalTransactions - categorizedTransactions;
        
        Map<String, Object> stats = Map.of(
                "totalTransactions", totalTransactions,
                "categorizedTransactions", categorizedTransactions,
                "uncategorizedTransactions", uncategorizedTransactions
        );
        
        return new ResponseEntity<>(stats, HttpStatus.OK);
    }

    /**
     * Get all uncategorized transactions for review
     */
    @GetMapping("/uncategorized")
    public ResponseEntity<List<UncategorizedTransaction>> getUncategorizedTransactions() {
        List<UncategorizedTransaction> uncategorized = uncategorizedTransactionRepository
                .findByReviewedFalseOrderByCreatedAtDesc();
        return new ResponseEntity<>(uncategorized, HttpStatus.OK);
    }

    /**
     * Review and categorize an uncategorized transaction
     */
    @PostMapping("/review")
    public ResponseEntity<String> reviewUncategorizedTransaction(
            @RequestParam Integer uncategorizedId,
            @RequestParam String assignedCategory) {
        
        UncategorizedTransaction uncategorized = uncategorizedTransactionRepository
                .findById(uncategorizedId)
                .orElseThrow(() -> new RuntimeException("Uncategorized transaction not found"));
        
        // Mark as reviewed
        uncategorized.setReviewed(true);
        uncategorized.setAssignedCategory(assignedCategory);
        uncategorized.setReviewedAt(java.time.LocalDateTime.now());
        uncategorizedTransactionRepository.save(uncategorized);
        
        // Learn from this correction
        categorizationService.learnFromUserCorrection(uncategorized.getMerchant(), assignedCategory);
        
        return new ResponseEntity<>("Transaction reviewed and learning applied", HttpStatus.OK);
    }

    /**
     * Get count of uncategorized transactions
     */
    @GetMapping("/uncategorized/count")
    public ResponseEntity<Map<String, Object>> getUncategorizedCount() {
        long count = uncategorizedTransactionRepository.countByReviewedFalse();
        Map<String, Object> response = Map.of("uncategorizedCount", count);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
