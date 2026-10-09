package ca.homefinance.mapper;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.CategoryRepository;
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

/**
 * Maps rows of a shared-bill CSV (date, entity, amount, details, category) for bills one person paid from
 * a personal account and that are split 50/50 in the monthly balance. The paying account (and so the person)
 * depends on the source, hence {@link #forAccount} rather than a single {@link FieldSetMapper}.
 * <p>
 * {@code details} and {@code category} are optional; a blank category falls back to the categorizer.
 */
@Component
public class SharedBillTransactionFieldMapper {

    private static final Logger logger = LoggerFactory.getLogger(SharedBillTransactionFieldMapper.class);

    /** MM-dd-yyyy, e.g. 04-12-2023 (single-digit month/day also accepted). */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M-d-yyyy");

    private final PersonRepository personRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionCategorizationService transactionCategorizationService;

    @Autowired
    public SharedBillTransactionFieldMapper(PersonRepository personRepository,
                                            CategoryRepository categoryRepository,
                                            TransactionCategorizationService transactionCategorizationService) {
        this.personRepository = personRepository;
        this.categoryRepository = categoryRepository;
        this.transactionCategorizationService = transactionCategorizationService;
    }

    public FieldSetMapper<Transaction> forAccount(Transaction.AccountType account) {
        return fieldSet -> mapFieldSet(fieldSet, account);
    }

    Transaction mapFieldSet(FieldSet fieldSet, Transaction.AccountType account) {
        try {
            Transaction transaction = new Transaction();
            transaction.setDate(LocalDate.parse(fieldSet.readString("date").trim(), DATE_FORMAT));
            transaction.setEntity(fieldSet.readString("entity").trim());
            transaction.setAmount(parseAmount(fieldSet.readString("amount")));
            transaction.setDetails(blankToNull(fieldSet.readString("details")));
            transaction.setTransactionType(Transaction.TransactionType.BILL);
            transaction.setAccount(account);
            transaction.setShared(true);
            transaction.setPerson(personRepository.findByCode(account.name()).orElseThrow());

            Category category = null;
            String categoryName = blankToNull(fieldSet.readString("category"));
            if (categoryName != null) {
                category = categoryRepository.findByName(categoryName);
                if (category == null) {
                    logger.warn("Unknown category '{}' for '{}'; falling back to the categorizer",
                            categoryName, transaction.getEntity());
                }
            }
            if (category == null) {
                category = transactionCategorizationService.categorizeTransaction(
                        transaction.getEntity(), transaction.getDetails(), transaction.getAmount(), transaction.getDate());
                if (category.getCategoryId() == null && category.getUncategorizedTransaction() != null) {
                    transaction.setUncategorizedTransaction(category.getUncategorizedTransaction());
                    category = null;
                }
            }
            transaction.setCategory(category);

            return transaction;
        } catch (Exception e) {
            logger.error("Error mapping shared bill transaction fields", e);
            throw new RuntimeException(e);
        }
    }

    private static BigDecimal parseAmount(String raw) {
        return new BigDecimal(raw.replace("$", "").replace(",", "").trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
