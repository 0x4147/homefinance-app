package ca.homefinance.service;

import ca.homefinance.entity.Category;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCategorySuggestionServiceTest {

    private final AiCategorySuggestionService service =
            new AiCategorySuggestionService(null, null, null, null);

    private static Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private final List<Category> categories = List.of(category("Groceries"), category("Gas"));

    @Test
    void acceptsExactCategoryAndClampsConfidence() {
        Optional<AiCategorySuggestionService.Suggestion> result =
                service.parse("{\"category\": \"groceries\", \"confidence\": 1.7}", categories);

        assertEquals("Groceries", result.orElseThrow().category());
        assertEquals(1.0, result.get().confidence());
    }

    @Test
    void extractsJsonFromChattyOutput() {
        Optional<AiCategorySuggestionService.Suggestion> result =
                service.parse("Sure! Here you go:\n{\"category\":\"Gas\",\"confidence\":0.8}\nHope that helps", categories);

        assertEquals("Gas", result.orElseThrow().category());
    }

    @Test
    void rejectsCategoryNotInList() {
        assertTrue(service.parse("{\"category\": \"Crypto\", \"confidence\": 0.9}", categories).isEmpty());
    }

    @Test
    void rejectsNullGarbageAndMissingResponse() {
        assertTrue(service.parse("{\"category\": null, \"confidence\": 0.2}", categories).isEmpty());
        assertTrue(service.parse("no idea", categories).isEmpty());
        assertTrue(service.parse(null, categories).isEmpty());
    }
}
