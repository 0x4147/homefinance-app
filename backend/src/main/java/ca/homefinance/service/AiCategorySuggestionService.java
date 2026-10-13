package ca.homefinance.service;

import ca.homefinance.dto.ReviewGroup;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.UncategorizedTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proposes a category for each pending review group using AI. Only the merchant description and the
 * category list are sent; amounts, dates and details never leave the app. Results are stored as
 * suggestions and never reviewed or assigned automatically.
 */
@Service
public class AiCategorySuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiCategorySuggestionService.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*?}", Pattern.DOTALL);
    private static final String SYSTEM_PROMPT = """
            You categorize bank transaction merchants for a personal finance app.
            Pick exactly one category from the provided list that best fits the merchant.
            Reply with only a JSON object: {"category": "<exact name from the list, or null if unsure>", "confidence": <0.0-1.0>}.
            Never invent categories. Use null when the merchant is ambiguous.""";

    private final TransactionCategorizationService categorizationService;
    private final CategoryRepository categoryRepository;
    private final UncategorizedTransactionRepository uncategorizedRepository;
    private final AthenaService athenaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${athena.categorization.external.enabled:false}")
    private boolean externalAllowed;

    public AiCategorySuggestionService(TransactionCategorizationService categorizationService,
                                       CategoryRepository categoryRepository,
                                       UncategorizedTransactionRepository uncategorizedRepository,
                                       AthenaService athenaService) {
        this.categorizationService = categorizationService;
        this.categoryRepository = categoryRepository;
        this.uncategorizedRepository = uncategorizedRepository;
        this.athenaService = athenaService;
    }

    public record Result(int suggested, int skipped) {}

    /** Asks AI about every pending group that has no AI suggestion yet. */
    @Transactional
    public Result suggestForPendingGroups() {
        List<Category> categories = categoryRepository.findAll();
        int suggested = 0;
        int skipped = 0;
        for (ReviewGroup group : categorizationService.getReviewGroups()) {
            if (group.aiSuggestion() != null) {
                continue;
            }
            Optional<Suggestion> suggestion = suggest(group.merchant(), categories);
            if (suggestion.isEmpty()) {
                skipped++;
                continue;
            }
            for (Integer id : group.ids()) {
                uncategorizedRepository.findById(id).ifPresent(pending -> {
                    pending.setAiSuggestedCategory(suggestion.get().category());
                    pending.setAiConfidence(suggestion.get().confidence());
                    uncategorizedRepository.save(pending);
                });
            }
            suggested++;
        }
        log.info("AI category suggestions: {} suggested, {} skipped", suggested, skipped);
        return new Result(suggested, skipped);
    }

    Optional<Suggestion> suggest(String merchant, List<Category> categories) {
        String response = athenaService.ask(SYSTEM_PROMPT, buildUserPrompt(merchant, categories), externalAllowed);
        return parse(response, categories);
    }

    private static String buildUserPrompt(String merchant, List<Category> categories) {
        StringBuilder sb = new StringBuilder("Categories:\n");
        for (Category category : categories) {
            sb.append("- ").append(category.getName());
            if (category.getDescription() != null && !category.getDescription().isBlank()) {
                sb.append(": ").append(category.getDescription());
            }
            sb.append('\n');
        }
        return sb.append("\nMerchant: ").append(merchant).toString();
    }

    /** Accepts only an exact (case-insensitive) category name from the list; anything else is dropped. */
    Optional<Suggestion> parse(String response, List<Category> categories) {
        if (response == null) {
            return Optional.empty();
        }
        Matcher matcher = JSON_OBJECT.matcher(response);
        while (matcher.find()) {
            try {
                JsonNode node = objectMapper.readTree(matcher.group());
                JsonNode name = node.get("category");
                if (name == null || name.isNull()) {
                    return Optional.empty();
                }
                String wanted = name.asText().trim();
                for (Category category : categories) {
                    if (category.getName().equalsIgnoreCase(wanted)) {
                        double confidence = node.path("confidence").asDouble(0.0);
                        return Optional.of(new Suggestion(category.getName(), Math.max(0.0, Math.min(1.0, confidence))));
                    }
                }
                return Optional.empty();
            } catch (Exception ignored) {
                // not valid JSON; try the next candidate
            }
        }
        return Optional.empty();
    }

    record Suggestion(String category, double confidence) {}
}
