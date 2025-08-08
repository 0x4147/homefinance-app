package ca.homefinance.batch;

import ca.homefinance.entity.Transaction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.JsonItemReader;
import org.springframework.batch.item.json.builder.JsonItemReaderBuilder;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class JsonTransactionReader {

    public JsonItemReader<Transaction> reader(String filePath) {
        return new JsonItemReaderBuilder<Transaction>()
                .jsonObjectReader(new JacksonJsonObjectReader<>(Transaction.class))
                .resource(new FileSystemResource(filePath))
                .name("jsonTransactionReader")
                .build();
    }
}