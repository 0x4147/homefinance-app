package ca.homefinance.mapper;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.helper.GeneralHelper;
import ca.homefinance.helper.TransactionCategorizer;
import ca.homefinance.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class CIBCTransactionFieldMapper implements FieldSetMapper<Transaction> {

    private static final Logger logger = LoggerFactory.getLogger(CIBCTransactionFieldMapper.class);

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
            logger.debug("Mapping CIBC transaction fields");
            Transaction transaction = new Transaction();

            String rawDate = fieldSet.readString("date");
            logger.debug("Raw date from field set: {}", rawDate);
            LocalDate parsedDate = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            logger.debug("Parsed date: {}", parsedDate);
            transaction.setDate(parsedDate);

            String entity = fieldSet.readString("entity");
            logger.debug("Entity from field set: {}", entity);
            transaction.setEntity(entity);

            String amountOutStr = fieldSet.readString("amount out");
            logger.debug("Amount out from field set: '{}'", amountOutStr);
            if(!amountOutStr.trim().isEmpty()){
                BigDecimal amount = new BigDecimal(amountOutStr);
                logger.debug("Using amount out: {}", amount);
                transaction.setAmount(amount);
                Transaction.TransactionType transactionType = GeneralHelper.determineTransactionType(transaction.getEntity(), transaction.getAmount());
                logger.debug("Determined transaction type: {}", transactionType);
                transaction.setTransactionType(transactionType);
            }
            else {
                String amountInStr = fieldSet.readString("amount in");
                logger.debug("Amount in from field set: '{}'", amountInStr);
                BigDecimal amount = new BigDecimal(amountInStr).negate();
                logger.debug("Using negated amount in: {}", amount);
                transaction.setAmount(amount);
                Transaction.TransactionType transactionType = GeneralHelper.determineTransactionType(transaction.getEntity(), transaction.getAmount());
                logger.debug("Determined transaction type: {}", transactionType);
                transaction.setTransactionType(transactionType);
            }

            transaction.setAccount(Transaction.AccountType.CIBC);
            
            Category category = categorizer.getCategory(transaction.getEntity());
            logger.debug("Categorized entity '{}' as: {}", transaction.getEntity(), category != null ? category.getName() : "null");
            transaction.setCategory(category);

            String person = fieldSet.readString("person").trim();
            logger.debug("Person from field set: {}", person);
            if(person.equals("5223********3406")) {
                logger.debug("Person matches Divya's card, setting to Divya (ID: 2)");
                transaction.setPerson(personRepository.findById(2).get()); //Divya
            }
            if(person.equals("5223********5844")) {
                logger.debug("Person matches Asanka's card, setting to Asanka (ID: 1)");
                transaction.setPerson(personRepository.findById(1).get()); //Asanka
            }

            logger.debug("Successfully mapped CIBC transaction: {}", transaction);
            return transaction;
        } catch (Exception e) {
            logger.error("Error mapping CIBC transaction fields", e);
            throw new RuntimeException(e);
        }
    }

}
