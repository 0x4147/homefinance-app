package ca.homefinance.batch;

import ca.homefinance.entity.Transaction;
import ca.homefinance.mapper.AMEXTransactionFieldMapper;
import ca.homefinance.mapper.CIBCTransactionFieldMapper;
import ca.homefinance.mapper.SharedBillTransactionFieldMapper;
import ca.homefinance.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.core.configuration.annotation.StepScope;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);

    private final TransactionWriter writer;
    private final TransactionRepository transactionRepository;
    private final CIBCTransactionFieldMapper cibcTransactionFieldMapper;
    private final AMEXTransactionFieldMapper amexTransactionFieldMapper;
    private final SharedBillTransactionFieldMapper sharedBillTransactionFieldMapper;

    @Autowired
    public BatchConfig (TransactionWriter writer,
                        TransactionRepository transactionRepository,
                        CIBCTransactionFieldMapper cibcTransactionFieldMapper,
                        AMEXTransactionFieldMapper amexTransactionFieldMapper,
                        SharedBillTransactionFieldMapper sharedBillTransactionFieldMapper){
        this.writer = writer;
        this.transactionRepository = transactionRepository;
        this.cibcTransactionFieldMapper = cibcTransactionFieldMapper;
        this.amexTransactionFieldMapper = amexTransactionFieldMapper;
        this.sharedBillTransactionFieldMapper = sharedBillTransactionFieldMapper;
    }

    @Bean
    public Job transactionJob(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager) {
        logger.info("Creating transaction job");
        return new JobBuilder("transactionJob", jobRepository)
                .start(importTransactionsStep(jobRepository, transactionManager))
                .build();
    }

    @Bean
    public Step importTransactionsStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager) {
        logger.info("Creating import transactions step");
        return new StepBuilder("importTransactions", jobRepository)
                .<Transaction, Transaction>chunk(10, transactionManager)
                .reader(csvFileReader(null, null)) // Let Spring inject the value
                .processor(duplicateTransactionFilter())
                .writer(writer)
                .build();
    }

    @StepScope
    @Bean
    public DuplicateTransactionFilter duplicateTransactionFilter() {
        return new DuplicateTransactionFilter(transactionRepository);
    }

    @StepScope
    @Bean
    public FlatFileItemReader<Transaction> csvFileReader(@Value("#{jobParameters['filePath']}") String filePath,
                                                         @Value("#{jobParameters['sourceType']}") String sourceType) {
        logger.info("Creating CSV file reader - FilePath: {}, SourceType: {}", filePath, sourceType);
        
        FlatFileItemReader<Transaction> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(filePath));
        reader.setLinesToSkip(0);

        DefaultLineMapper<Transaction> lineMapper = new DefaultLineMapper<>();

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter(",");
        tokenizer.setQuoteCharacter('"');
//        tokenizer.setStrict(false);

        if ("amex".equalsIgnoreCase(sourceType)) {
            logger.info("Configuring reader for AMEX format");
            // First line is a header row
            reader.setLinesToSkip(1);
            // CSV columns: Date, Date Processed, Description, Card Member, Account #, Amount
            // Skip Date Processed (1) and Account # (4); only the remaining four are used
            tokenizer.setIncludedFields(0, 2, 3, 5);
            tokenizer.setNames("date", "entity", "person", "amount");
            lineMapper.setFieldSetMapper(amexTransactionFieldMapper);
        } else if ("cibc".equalsIgnoreCase(sourceType)) {
            logger.info("Configuring reader for CIBC format");
            tokenizer.setNames("date", "entity", "amount out", "amount in", "person");
            lineMapper.setFieldSetMapper(cibcTransactionFieldMapper);
        } else if ("asanka-shared".equalsIgnoreCase(sourceType) || "divya-shared".equalsIgnoreCase(sourceType)) {
            logger.info("Configuring reader for shared bills format ({})", sourceType);
            // No header row; details and category are optional trailing columns
            tokenizer.setStrict(false);
            tokenizer.setNames("date", "entity", "amount", "details", "category");
            Transaction.AccountType account = "asanka-shared".equalsIgnoreCase(sourceType)
                    ? Transaction.AccountType.ASANKA
                    : Transaction.AccountType.DIVYA;
            lineMapper.setFieldSetMapper(sharedBillTransactionFieldMapper.forAccount(account));
        } else {
            logger.error("Unknown source type: {}", sourceType);
            throw new IllegalArgumentException("Unknown source type: " + sourceType);
        }

        lineMapper.setLineTokenizer(tokenizer);
        reader.setLineMapper(lineMapper);

        logger.info("CSV file reader created successfully");
        return reader;
        }
}
