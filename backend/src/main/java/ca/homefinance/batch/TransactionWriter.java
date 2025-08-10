package ca.homefinance.batch;

import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TransactionWriter implements ItemWriter<Transaction> {

    private static final Logger logger = LoggerFactory.getLogger(TransactionWriter.class);

    @Autowired
    private TransactionRepository repository;

    @Override
    public void write(Chunk<? extends Transaction> chunk) throws Exception {
        logger.info("Writing {} transactions to database", chunk.size());
        repository.saveAll(chunk);
        logger.info("Successfully wrote {} transactions to database", chunk.size());
    }
}