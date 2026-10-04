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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionCategorizationServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UncategorizedTransactionRepository uncategorizedTransactionRepository;
    @Mock
    private MerchantRuleRepository merchantRuleRepository;

    private TransactionCategorizationService service;
    private Category takeOut;

    @BeforeEach
    void setUp() {
        service = new TransactionCategorizationService(categoryRepository, transactionRepository,
                uncategorizedTransactionRepository, merchantRuleRepository);
        takeOut = category(1, "Take Out");
    }

    @Test
    void categorize_StoreNumberAndLocationDescriptor_MatchesRuleAsPhrase() {
        when(merchantRuleRepository.findAll()).thenReturn(List.of(rule("tim hortons", takeOut)));

        Category result = service.categorizeTransaction("TIM HORTONS #1234 TORONTO, ON", null,
                new BigDecimal("4.50"), LocalDate.of(2024, 1, 3));

        assertEquals("Take Out", result.getName());
        assertNull(result.getUncategorizedTransaction());
    }

    @Test
    void categorize_LongerRuleWinsOverShorterContainedRule() {
        Category amazon = category(2, "Amazon");
        Category clothing = category(3, "Clothing");
        when(merchantRuleRepository.findAll()).thenReturn(List.of(
                rule("amazon", amazon),
                rule("amazon fashion", clothing)));

        Category result = service.categorizeTransaction("AMAZON FASHION CA", null,
                BigDecimal.TEN, LocalDate.of(2024, 1, 3));

        assertEquals("Clothing", result.getName());
    }

    @Test
    void categorize_NoRuleMatch_FlagsForReview() {
        when(merchantRuleRepository.findAll()).thenReturn(List.of(rule("tim hortons", takeOut)));
        when(uncategorizedTransactionRepository.save(any(UncategorizedTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category result = service.categorizeTransaction("ZZZ UNKNOWN SHOP", null,
                BigDecimal.TEN, LocalDate.of(2024, 1, 3));

        assertNull(result.getCategoryId());
        assertNotNull(result.getUncategorizedTransaction());
        assertEquals("ZZZ UNKNOWN SHOP", result.getUncategorizedTransaction().getMerchant());
        verify(uncategorizedTransactionRepository).save(any(UncategorizedTransaction.class));
    }

    @Test
    void learnFromUserCorrection_NewMerchant_CreatesUserRule() {
        when(categoryRepository.findByName("Take Out")).thenReturn(takeOut);
        when(merchantRuleRepository.findByNormalizedMerchant("new cafe")).thenReturn(java.util.Optional.empty());
        when(uncategorizedTransactionRepository.findByReviewedFalseOrderByCreatedAtDesc()).thenReturn(List.of());

        service.learnFromUserCorrection("NEW CAFE #77 OTTAWA, ON", "Take Out");

        verify(merchantRuleRepository).save(argThat(rule ->
                "new cafe".equals(rule.getNormalizedMerchant())
                        && rule.getSource() == Source.USER
                        && rule.getCategory() == takeOut));
    }

    @Test
    void learnFromUserCorrection_PendingReviewWithSameMerchant_ResolvesItAndLinkedTransaction() {
        when(categoryRepository.findByName("Take Out")).thenReturn(takeOut);
        when(merchantRuleRepository.findByNormalizedMerchant("new cafe")).thenReturn(java.util.Optional.empty());

        UncategorizedTransaction pending = new UncategorizedTransaction();
        pending.setId(9);
        pending.setMerchant("NEW CAFE #77 OTTAWA, ON");
        when(uncategorizedTransactionRepository.findByReviewedFalseOrderByCreatedAtDesc()).thenReturn(List.of(pending));

        Transaction linked = new Transaction();
        when(transactionRepository.findByUncategorizedTransaction_Id(9)).thenReturn(List.of(linked));

        service.learnFromUserCorrection("NEW CAFE #77 OTTAWA, ON", "Take Out");

        assertTrue(pending.getReviewed());
        assertEquals("Take Out", pending.getAssignedCategory());
        assertSame(takeOut, linked.getCategory());
        verify(transactionRepository).save(linked);
    }

    private static Category category(int id, String name) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        return category;
    }

    private static MerchantRule rule(String normalizedMerchant, Category category) {
        MerchantRule rule = new MerchantRule();
        rule.setNormalizedMerchant(normalizedMerchant);
        rule.setCategory(category);
        rule.setSource(Source.SEED);
        return rule;
    }
}
