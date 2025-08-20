package ca.homefinance.mapper;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.helper.GeneralHelper;
import ca.homefinance.helper.TransactionCategorizer;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.service.TransactionCategorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(AMEXTransactionFieldMapper.class);
    
    private final TransactionCategorizer categorizer;
    private final PersonRepository personRepository;
    private final TransactionCategorizationService transactionCategorizationService;

    @Autowired
    public AMEXTransactionFieldMapper(TransactionCategorizer categorizer, PersonRepository personRepository, TransactionCategorizationService transactionCategorizationService) {
        this.categorizer = categorizer;
        this.personRepository = personRepository;
        this.transactionCategorizationService = transactionCategorizationService;
    }

    @Override
    public Transaction mapFieldSet(FieldSet fieldSet) {
        try {
            logger.debug("Mapping AMEX transaction fields");
            Transaction transaction = new Transaction();

            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("d MMM. yyyy", Locale.ENGLISH);
            DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

            String rawDate = fieldSet.readString("date");
            logger.debug("Raw date from field set: {}", rawDate);
            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(rawDate, formatter1);
                logger.debug("Parsed date with formatter1: {}", parsedDate);
            } catch (DateTimeParseException e1) {
                logger.debug("Failed to parse with formatter1, trying formatter2");
                parsedDate = LocalDate.parse(rawDate, formatter2);
                logger.debug("Parsed date with formatter2: {}", parsedDate);
            }
            transaction.setDate(parsedDate);

            String entity = fieldSet.readString("entity");
            logger.debug("Entity from field set: {}", entity);
            transaction.setEntity(entity);

            String rawAmount = fieldSet.readString("amount");
            logger.debug("Raw amount from field set: {}", rawAmount);
            String cleanedAmount = rawAmount.replace("$", "").trim();
            BigDecimal amount = new BigDecimal(cleanedAmount);
            logger.debug("Cleaned and parsed amount: {}", amount);
            transaction.setAmount(amount);

            Transaction.TransactionType transactionType = GeneralHelper.determineTransactionType(transaction.getEntity(), amount);
            logger.debug("Determined transaction type: {}", transactionType);
            transaction.setTransactionType(transactionType);

            transaction.setAccount(Transaction.AccountType.AMEX);
            
//            Category category = categorizer.getCategory(transaction.getEntity()); //PREVIOUS CATEGORIZER - CAN DELETE LATER
            Category category = transactionCategorizationService.categorizeTransaction(transaction.getEntity(), null, transaction.getAmount(), transaction.getDate());

            logger.debug("Categorized entity '{}' as: {}", transaction.getEntity(), category != null ? category.getName() : "null");
            transaction.setCategory(category);

            String person = fieldSet.readString("person").trim();
            logger.debug("Person from field set: {}", person);
            if (person == null || person.isEmpty()){
                logger.debug("Person is null or empty, setting to default (ID: 2)");
                transaction.setPerson(personRepository.findById(2).get());
            }
            else {
                if(person.contains("DIVYA")) {
                    logger.debug("Person contains 'DIVYA', setting to Divya (ID: 2)");
                    transaction.setPerson(personRepository.findById(2).get()); //Divya
                }
                if(person.contains("ASANKA")) {
                    logger.debug("Person contains 'ASANKA', setting to Asanka (ID: 1)");
                    transaction.setPerson(personRepository.findById(1).get()); //Asanka
                }
            }

            logger.debug("Successfully mapped AMEX transaction: {}", transaction);
            return transaction;
        } catch (Exception e) {
            logger.error("Error mapping AMEX transaction fields", e);
            throw new RuntimeException(e);
        }
    }
}
