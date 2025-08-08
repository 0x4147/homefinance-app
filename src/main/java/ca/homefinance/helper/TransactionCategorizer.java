package ca.homefinance.helper;

import ca.homefinance.entity.Category;
import ca.homefinance.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TransactionCategorizer {

    private final CategoryRepository categoryRepository;
    private static final String MAPPINGS_FILE = "category_mappings.json";
    private Map<String, String> entityToCategory = new HashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public TransactionCategorizer(CategoryRepository categoryRepository){
        this.categoryRepository = categoryRepository;
    }

    @PostConstruct
    public void init() {
        loadMappings();
    }

    public Category getCategory(String entityName) {
        String lowerEntity = entityName.toLowerCase();
        for (Map.Entry<String, String> entry : entityToCategory.entrySet()) {
            if (lowerEntity.contains(entry.getKey())) {
                return categoryRepository.findByName(entry.getValue());
            }
        }
        return null; // Return null if no match is found
    }

    private void loadMappings() {
        // Loading from resources as a stream
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(MAPPINGS_FILE)) {
            if (inputStream != null) {
                // Read the JSON file into a map
                Map<String, List<String>> categories = objectMapper.readValue(inputStream, new TypeReference<Map<String, List<String>>>() {});

                for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
                    String category = entry.getKey();
                    for (String merchant : entry.getValue()) {
                        // For each merchant, map the name to the category
                        entityToCategory.put(merchant.toLowerCase(), category);
                    }
                }
            } else {
                System.out.println("No mappings file found, starting fresh.");
            }
        } catch (IOException e) {
            System.err.println("Failed to load mappings: " + e.getMessage());
        }
    }

    private void saveNewMapping(String entityKey, String category) {
        entityToCategory.put(entityKey, category);
        try {
            // Writing back the mappings, but consider writing to a different location if using the JAR
            Path path = Paths.get("target/classes/category_mappings.json"); // You can choose a writable location
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), entityToCategory);
            System.out.println("Saved new mapping: " + entityKey + " -> " + category);
        } catch (IOException e) {
            System.err.println("Failed to save new mapping: " + e.getMessage());
        }
    }
}
