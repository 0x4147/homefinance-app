package ca.homefinance.mapper;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.PersonRepository;
import ca.homefinance.service.TransactionCategorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedBillTransactionFieldMapperTest {

    private static final String[] NAMES = {"date", "entity", "amount", "details", "category"};

    @Mock
    private PersonRepository personRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionCategorizationService categorizationService;

    private SharedBillTransactionFieldMapper mapper;
    private Person asanka;

    @BeforeEach
    void setUp() {
        mapper = new SharedBillTransactionFieldMapper(personRepository, categoryRepository, categorizationService);
        asanka = new Person();
        asanka.setPersonId(1);
        asanka.setCode("ASANKA");
    }

    @Test
    void mapFieldSet_AllColumns_ShouldBuildSharedBillOnPayingAccount() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        Category utilities = new Category();
        utilities.setCategoryId(4);
        utilities.setName("Utilities");
        when(categoryRepository.findByName("Utilities")).thenReturn(utilities);

        Transaction result = mapper.mapFieldSet(
                fieldSet("04-12-2023", "Enbridge Gas", "$1,234.50", "April bill", "Utilities"), Transaction.AccountType.ASANKA);

        assertEquals(LocalDate.of(2023, 4, 12), result.getDate());
        assertEquals("Enbridge Gas", result.getEntity());
        assertEquals(0, new BigDecimal("1234.50").compareTo(result.getAmount()));
        assertEquals("April bill", result.getDetails());
        assertEquals(Transaction.AccountType.ASANKA, result.getAccount());
        assertEquals(Transaction.TransactionType.BILL, result.getTransactionType());
        assertTrue(result.isShared());
        assertSame(asanka, result.getPerson());
        assertSame(utilities, result.getCategory());
        verify(categorizationService, never()).categorizeTransaction(any(), any(), any(), any());
    }

    @Test
    void mapFieldSet_OptionalColumnsMissing_ShouldFallBackToCategorizer() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        Category internet = new Category();
        internet.setCategoryId(9);
        internet.setName("Internet");
        when(categorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(internet);

        // A row with only date, entity and amount, tokenized the way BatchConfig does it
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setStrict(false);
        tokenizer.setNames(NAMES);
        FieldSet fieldSet = tokenizer.tokenize("04-12-2023,Rogers,89.99");

        Transaction result = mapper.mapFieldSet(fieldSet, Transaction.AccountType.ASANKA);

        assertNull(result.getDetails());
        assertSame(internet, result.getCategory());
        assertEquals(0, new BigDecimal("89.99").compareTo(result.getAmount()));
    }

    @Test
    void mapFieldSet_UnknownCategory_ShouldFallBackToCategorizer() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        when(categoryRepository.findByName("Nope")).thenReturn(null);
        Category fallback = new Category();
        fallback.setCategoryId(2);
        when(categorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(fallback);

        Transaction result = mapper.mapFieldSet(
                fieldSet("04-12-2023", "Rogers", "89.99", "", "Nope"), Transaction.AccountType.ASANKA);

        assertSame(fallback, result.getCategory());
    }

    @Test
    void forAccount_DivyaSource_ShouldUseDivyaAccountAndPerson() throws Exception {
        Person divya = new Person();
        divya.setPersonId(2);
        divya.setCode("DIVYA");
        when(personRepository.findByCode("DIVYA")).thenReturn(Optional.of(divya));
        Category any = new Category();
        any.setCategoryId(1);
        when(categorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(any);

        Transaction result = mapper.forAccount(Transaction.AccountType.DIVYA)
                .mapFieldSet(fieldSet("04-12-2023", "Rogers", "89.99", "", ""));

        assertEquals(Transaction.AccountType.DIVYA, result.getAccount());
        assertSame(divya, result.getPerson());
    }

    private FieldSet fieldSet(String... values) {
        return new DefaultFieldSet(values, NAMES);
    }
}
