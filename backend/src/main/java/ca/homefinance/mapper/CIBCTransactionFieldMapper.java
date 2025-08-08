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

@Component
public class CIBCTransactionFieldMapper implements FieldSetMapper<Transaction> {

    private final TransactionCategorizer categorizer;
    private final PersonRepository personRepository;

    @Autowired
    public CIBCTransactionFieldMapper(TransactionCategorizer categorizer, PersonRepository personRepository) {
        this.categorizer = categorizer;
        this.personRepository = personRepository;

    }

    @Override
    public Transaction mapFieldSet(FieldSet fieldSet) {
        try {
            Transaction transaction = new Transaction();

            transaction.setDate(LocalDate.parse(fieldSet.readString("date"), DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            transaction.setEntity(fieldSet.readString("entity"));

            String amountOutStr = fieldSet.readString("amount out");
            if(!amountOutStr.trim().isEmpty()){
                transaction.setAmount(new BigDecimal(amountOutStr));
                transaction.setTransactionType(GeneralHelper.determineTransactionType(transaction.getEntity(), transaction.getAmount()));
            }
            else {
                String amountInStr = fieldSet.readString("amount in");
                transaction.setAmount(new BigDecimal(amountInStr).negate());
                transaction.setTransactionType(GeneralHelper.determineTransactionType(transaction.getEntity(), transaction.getAmount()));
            }

            transaction.setAccount(Transaction.AccountType.CIBC);
            transaction.setCategory(categorizer.getCategory(transaction.getEntity()));

            String person = fieldSet.readString("person").trim();
            if(person.equals("5223********3406")) transaction.setPerson(personRepository.findById(2).get()); //Divya
            if(person.equals("5223********5844")) transaction.setPerson(personRepository.findById(1).get()); //Asanka

            return transaction;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
