package ca.homefinance.mapper;

import ca.homefinance.entity.Transaction;
import ca.homefinance.helper.GeneralHelper;
import ca.homefinance.helper.TransactionCategorizer;
import ca.homefinance.repository.PersonRepository;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class AMEXTransactionFieldMapper implements FieldSetMapper<Transaction> {
    private final TransactionCategorizer categorizer;
    private final PersonRepository personRepository;

    @Autowired
    public AMEXTransactionFieldMapper(TransactionCategorizer categorizer, PersonRepository personRepository) {
        this.categorizer = categorizer;
        this.personRepository = personRepository;
    }

    @Override
    public Transaction mapFieldSet(FieldSet fieldSet) {
        try {
            Transaction transaction = new Transaction();

            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("d MMM. yyyy", Locale.ENGLISH);
            DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

            String rawDate = fieldSet.readString("date");
            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(rawDate, formatter1);
            } catch (DateTimeParseException e1) {
                parsedDate = LocalDate.parse(rawDate, formatter2);
            }
            transaction.setDate(parsedDate);

            transaction.setEntity(fieldSet.readString("entity"));

            String rawAmount = fieldSet.readString("amount");
            String cleanedAmount = rawAmount.replace("$", "").trim();
            BigDecimal amount = new BigDecimal(cleanedAmount);
            transaction.setAmount(amount);

            transaction.setTransactionType(GeneralHelper.determineTransactionType(transaction.getEntity(), amount));

            transaction.setAccount(Transaction.AccountType.AMEX);
            transaction.setCategory(categorizer.getCategory(transaction.getEntity()));

            String person = fieldSet.readString("person").trim();
            if (person == null || person.isEmpty()){
                transaction.setPerson(personRepository.findById(2).get());
            }
            else {
                if(person.contains("DIVYA")) transaction.setPerson(personRepository.findById(2).get()); //Divya
                if(person.contains("ASANKA")) transaction.setPerson(personRepository.findById(1).get()); //Asanka
            }

            return transaction;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
