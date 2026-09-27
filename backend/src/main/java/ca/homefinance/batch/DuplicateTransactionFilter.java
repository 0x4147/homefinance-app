package ca.homefinance.batch;

import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Skips transactions that were already imported, so re-uploading a file (or one that overlaps a
 * previous upload) doesn't create duplicates.
 * <p>
 * Identical rows can be genuine (two coffees at the same shop on the same day), so this compares
 * <em>counts</em> rather than existence: the Nth occurrence of a row in the file is a duplicate only if
 * the database already held at least N matching rows before this import started. Rows are matched on
 * account, date, entity and amount; the person is deliberately excluded so a row imported earlier
 * without a person (unregistered card) still counts as already imported.
 * <p>
 * Holds per-import state, so it must be step-scoped: a fresh instance for every job run.
 */
public class DuplicateTransactionFilter implements ItemProcessor<Transaction, Transaction> {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateTransactionFilter.class);

    private final TransactionRepository repository;
    private final Map<Key, Integer> seenInFile = new HashMap<>();
    private final Map<Key, Long> existingBeforeImport = new HashMap<>();

    public DuplicateTransactionFilter(TransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Transaction process(Transaction transaction) {
        Key key = Key.of(transaction);
        int occurrence = seenInFile.merge(key, 1, Integer::sum);
        // Looked up on first sight of the key, i.e. before any row with this key is written by this import.
        long existing = existingBeforeImport.computeIfAbsent(key, k -> repository.countByAccountAndDateAndEntityAndAmount(
                k.account(), k.date(), k.entity(), k.amount()));

        if (occurrence <= existing) {
            logger.info("Skipping already imported transaction: {} {} {} {} (occurrence {} of {} existing)",
                    key.account(), key.date(), key.entity(), key.amount(), occurrence, existing);
            return null;
        }
        return transaction;
    }

    private record Key(Transaction.AccountType account, LocalDate date, String entity, BigDecimal amount) {
        static Key of(Transaction t) {
            return new Key(t.getAccount(), t.getDate(),
                    t.getEntity() == null ? "" : t.getEntity().trim(),
                    t.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
    }
}
