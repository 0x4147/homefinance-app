package ca.homefinance.batch;

import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TransactionWriter implements ItemWriter<Transaction> {

    @Autowired
    private TransactionRepository repository;

    @Override
    public void write(Chunk<? extends Transaction> chunk) throws Exception {
        repository.saveAll(chunk);
    }
}