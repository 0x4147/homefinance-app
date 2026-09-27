package ca.homefinance.batch;

import ca.homefinance.entity.Category;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.service.TransactionCategorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Runs the real transaction import job to verify re-uploading a file (or an overlapping one) doesn't
 * create duplicate transactions, while genuinely repeated rows within a file are kept.
 */
@SpringBootTest
@ActiveProfiles("test")
// Flyway is disabled in tests, so create the Spring Batch metadata tables (Flyway V3 does this in production).
@Sql(scripts = "classpath:org/springframework/batch/core/schema-h2.sql",
        config = @SqlConfig(errorMode = SqlConfig.ErrorMode.CONTINUE_ON_ERROR))
class TransactionImportDeduplicationIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job transactionJob;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private TransactionCategorizationService categorizationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("Test Category");
        category.setType(Category.CategoryType.EXPENSE);
        category = categoryRepository.save(category);
        when(categorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(category);
    }

    @Test
    void reuploadingSameFile_ShouldNotCreateDuplicates() throws Exception {
        Path file = cibcFile("first.csv",
                "2024-01-05,Grocery Store,45.10,,5223********5844",
                "2024-01-06,Gas Station,60.00,,5223********5844");

        JobExecution first = runJob(file);
        JobExecution second = runJob(file);

        assertEquals(BatchStatus.COMPLETED, first.getStatus());
        assertEquals(2, written(first));
        assertEquals(0, filtered(first));

        assertEquals(BatchStatus.COMPLETED, second.getStatus());
        assertEquals(0, written(second));
        assertEquals(2, filtered(second));
        assertEquals(2, transactionRepository.count());
    }

    @Test
    void overlappingFile_ShouldOnlyImportNewRows() throws Exception {
        runJob(cibcFile("january.csv",
                "2024-01-30,Store A,10.00,,5223********5844",
                "2024-01-31,Store B,20.00,,5223********5844"));

        JobExecution overlapping = runJob(cibcFile("jan-feb.csv",
                "2024-01-31,Store B,20.00,,5223********5844",
                "2024-02-01,Store C,30.00,,5223********5844"));

        assertEquals(1, written(overlapping));
        assertEquals(1, filtered(overlapping));
        assertEquals(3, transactionRepository.count());
    }

    @Test
    void identicalRowsWithinOneFile_ShouldAllBeImportedOnce() throws Exception {
        Path file = cibcFile("coffee.csv",
                "2024-01-10,Coffee Shop,4.50,,5223********5844",
                "2024-01-10,Coffee Shop,4.50,,5223********5844");

        JobExecution first = runJob(file);
        JobExecution second = runJob(file);

        assertEquals(2, written(first));
        assertEquals(0, written(second));
        assertEquals(2, filtered(second));
        assertEquals(2, transactionRepository.count());
    }

    @Test
    void extraIdenticalRowInLaterFile_ShouldImportOnlyTheExtraOne() throws Exception {
        runJob(cibcFile("one.csv", "2024-01-10,Coffee Shop,4.50,,5223********5844"));

        JobExecution later = runJob(cibcFile("two.csv",
                "2024-01-10,Coffee Shop,4.50,,5223********5844",
                "2024-01-10,Coffee Shop,4.50,,5223********5844"));

        assertEquals(1, written(later));
        assertEquals(1, filtered(later));
        assertEquals(2, transactionRepository.count());
    }

    private Path cibcFile(String name, String... rows) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, String.join("\n", rows) + "\n");
        return file;
    }

    private JobExecution runJob(Path file) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("filePath", file.toAbsolutePath().toString())
                .addString("sourceType", "cibc")
                .addLong("timestamp", System.nanoTime())
                .toJobParameters();
        return jobLauncher.run(transactionJob, params);
    }

    private long written(JobExecution execution) {
        return execution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
    }

    private long filtered(JobExecution execution) {
        return execution.getStepExecutions().stream().mapToLong(StepExecution::getFilterCount).sum();
    }
}
