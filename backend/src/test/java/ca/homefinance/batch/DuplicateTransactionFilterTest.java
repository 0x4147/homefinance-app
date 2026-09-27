package ca.homefinance.batch;

import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateTransactionFilterTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

    @Mock
    private TransactionRepository repository;

    private DuplicateTransactionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DuplicateTransactionFilter(repository);
    }

    @Test
    void process_NothingExistingInDatabase_ShouldKeepTransaction() {
        when(repository.countByAccountAndDateAndEntityAndAmount(any(), any(), any(), any())).thenReturn(0L);

        Transaction tx = transaction("Store", "25.00");

        assertSame(tx, filter.process(tx));
    }

    @Test
    void process_AlreadyImported_ShouldSkipTransaction() {
        when(repository.countByAccountAndDateAndEntityAndAmount(
                eq(Transaction.AccountType.CIBC), eq(DATE), eq("Store"), eq(new BigDecimal("25.00"))))
                .thenReturn(1L);

        assertNull(filter.process(transaction("Store", "25.00")));
    }

    @Test
    void process_TwoIdenticalRowsInFileAndNoneExisting_ShouldKeepBoth() {
        when(repository.countByAccountAndDateAndEntityAndAmount(any(), any(), any(), any())).thenReturn(0L);

        assertNotNull(filter.process(transaction("Coffee Shop", "4.50")));
        assertNotNull(filter.process(transaction("Coffee Shop", "4.50")));
    }

    @Test
    void process_TwoIdenticalRowsInFileAndOneExisting_ShouldSkipOnlyFirst() {
        when(repository.countByAccountAndDateAndEntityAndAmount(any(), any(), any(), any())).thenReturn(1L);

        assertNull(filter.process(transaction("Coffee Shop", "4.50")));
        assertNotNull(filter.process(transaction("Coffee Shop", "4.50")));
    }

    @Test
    void process_TwoIdenticalRowsInFileAndTwoExisting_ShouldSkipBoth() {
        when(repository.countByAccountAndDateAndEntityAndAmount(any(), any(), any(), any())).thenReturn(2L);

        assertNull(filter.process(transaction("Coffee Shop", "4.50")));
        assertNull(filter.process(transaction("Coffee Shop", "4.50")));
    }

    @Test
    void process_AmountScaleAndEntityWhitespaceDiffer_ShouldStillMatch() {
        when(repository.countByAccountAndDateAndEntityAndAmount(
                eq(Transaction.AccountType.CIBC), eq(DATE), eq("Store"), eq(new BigDecimal("25.00"))))
                .thenReturn(1L);

        assertNull(filter.process(transaction("  Store ", "25")));
    }

    @Test
    void process_DatabaseCountIsLookedUpOncePerKey() {
        when(repository.countByAccountAndDateAndEntityAndAmount(any(), any(), any(), any())).thenReturn(0L);

        filter.process(transaction("Store", "25.00"));
        filter.process(transaction("Store", "25.00"));
        filter.process(transaction("Store", "25.00"));

        verify(repository, times(1)).countByAccountAndDateAndEntityAndAmount(any(), any(), any(), any());
    }

    @Test
    void process_DifferentAmountsOrDates_ShouldBeTreatedAsDistinct() {
        when(repository.countByAccountAndDateAndEntityAndAmount(
                any(), eq(DATE), eq("Store"), eq(new BigDecimal("25.00")))).thenReturn(1L);
        when(repository.countByAccountAndDateAndEntityAndAmount(
                any(), eq(DATE), eq("Store"), eq(new BigDecimal("26.00")))).thenReturn(0L);

        assertNull(filter.process(transaction("Store", "25.00")));
        assertNotNull(filter.process(transaction("Store", "26.00")));
    }

    private Transaction transaction(String entity, String amount) {
        Transaction tx = new Transaction();
        tx.setAccount(Transaction.AccountType.CIBC);
        tx.setDate(DATE);
        tx.setEntity(entity);
        tx.setAmount(new BigDecimal(amount));
        return tx;
    }
}
