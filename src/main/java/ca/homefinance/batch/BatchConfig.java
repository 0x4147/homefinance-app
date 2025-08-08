package ca.homefinance.batch;

import ca.homefinance.entity.Transaction;
import ca.homefinance.mapper.AMEXTransactionFieldMapper;
import ca.homefinance.mapper.CIBCTransactionFieldMapper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
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

    private final TransactionWriter writer;
    private final CIBCTransactionFieldMapper cibcTransactionFieldMapper;
    private final AMEXTransactionFieldMapper amexTransactionFieldMapper;

    @Autowired
    public BatchConfig (TransactionWriter writer,
                        CIBCTransactionFieldMapper cibcTransactionFieldMapper,
                        AMEXTransactionFieldMapper amexTransactionFieldMapper){
        this.writer = writer;
        this.cibcTransactionFieldMapper = cibcTransactionFieldMapper;
        this.amexTransactionFieldMapper = amexTransactionFieldMapper;
    }

    @Bean
    public Job transactionJob(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager) {
        return new JobBuilder("transactionJob", jobRepository)
                .start(importTransactionsStep(jobRepository, transactionManager))
                .build();
    }

    @Bean
    public Step importTransactionsStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager) {
        return new StepBuilder("importTransactions", jobRepository)
                .<Transaction, Transaction>chunk(10, transactionManager)
                .reader(csvFileReader(null, null)) // Let Spring inject the value
                .writer(writer)
                .build();
    }

    @StepScope
    @Bean
    public FlatFileItemReader<Transaction> csvFileReader(@Value("#{jobParameters['filePath']}") String filePath,
                                                         @Value("#{jobParameters['sourceType']}") String sourceType) {
        FlatFileItemReader<Transaction> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(filePath));
        reader.setLinesToSkip(0);

        DefaultLineMapper<Transaction> lineMapper = new DefaultLineMapper<>();

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter(",");
        tokenizer.setQuoteCharacter('"');
//        tokenizer.setStrict(false);

        if ("amex".equalsIgnoreCase(sourceType)) {
            tokenizer.setNames("date", "entity", "person", "amount");
            lineMapper.setFieldSetMapper(amexTransactionFieldMapper);
        } else if ("cibc".equalsIgnoreCase(sourceType)) {
            tokenizer.setNames("date", "entity", "amount out", "amount in", "person");
            lineMapper.setFieldSetMapper(cibcTransactionFieldMapper);
        }

        lineMapper.setLineTokenizer(tokenizer);
        reader.setLineMapper(lineMapper);

        return reader;
        }
}
