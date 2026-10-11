package ca.homefinance.mapper;

import ca.homefinance.entity.Account;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.Transaction;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.AccountRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedBillTransactionFieldMapperTest {

    private static final String[] NAMES = {"date", "entity", "amount", "details", "category"};

    @Mock
    private PersonRepository personRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionCategorizationService categorizationService;

    private SharedBillTransactionFieldMapper mapper;
    private Person asanka;
    private Account asankaAccount;
    private Account divyaAccount;

    @BeforeEach
    void setUp() {
        mapper = new SharedBillTransactionFieldMapper(personRepository, accountRepository, categoryRepository, categorizationService);
        asanka = new Person();
        asanka.setPersonId(1);
        asanka.setCode("ASANKA");
        asankaAccount = account("ASANKA");
        divyaAccount = account("DIVYA");
        lenient().when(accountRepository.findByCode("ASANKA")).thenReturn(Optional.of(asankaAccount));
        lenient().when(accountRepository.findByCode("DIVYA")).thenReturn(Optional.of(divyaAccount));
    }

    @Test
    void mapFieldSet_AllColumns_ShouldBuildSharedBillOnPayingAccount() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        Category utilities = new Category();
        utilities.setCategoryId(4);
        utilities.setName("Utilities");
        utilities.setKind(Category.CategoryKind.EXPENSE);
        when(categoryRepository.findByName("Utilities")).thenReturn(utilities);

        Transaction result = mapper.mapFieldSet(
                fieldSet("04/12/2023", "Enbridge Gas", "$1,234.50", "April bill", "Utilities"), "ASANKA");

        assertEquals(LocalDate.of(2023, 4, 12), result.getDate());
        assertEquals("Enbridge Gas", result.getEntity());
        assertEquals(0, new BigDecimal("-1234.50").compareTo(result.getAmount()));
        assertEquals("April bill", result.getDetails());
        assertSame(asankaAccount, result.getAccount());
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
        FieldSet fieldSet = tokenizer.tokenize("04/12/2023,Rogers,89.99");

        Transaction result = mapper.mapFieldSet(fieldSet, "ASANKA");

        assertNull(result.getDetails());
        assertSame(internet, result.getCategory());
        assertEquals(0, new BigDecimal("-89.99").compareTo(result.getAmount()));
    }

    @Test
    void mapFieldSet_UnknownCategory_ShouldFailInsteadOfGuessing() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        when(categoryRepository.findByName("Nope")).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class, () -> mapper.mapFieldSet(
                fieldSet("04/12/2023", "Rogers", "89.99", "", "Nope"), "ASANKA"));

        assertTrue(e.getCause().getMessage().contains("Unknown category 'Nope'"));
        verifyNoInteractions(categorizationService);
    }

    @Test
    void mapFieldSet_MonthFirstDate_ShouldParseAsMonthDayYear() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        Category utilities = new Category();
        utilities.setCategoryId(4);
        when(categoryRepository.findByName("Utilities")).thenReturn(utilities);

        Transaction result = mapper.mapFieldSet(
                fieldSet("10/08/2026", "Enbridge Gas", "50.00", "", "Utilities"), "ASANKA");

        assertEquals(LocalDate.of(2026, 10, 8), result.getDate());
    }

    @Test
    void mapFieldSet_SampleCsvRows_ShouldParse() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        Category utilityBill = new Category();
        utilityBill.setCategoryId(5);
        utilityBill.setName("Utility Bill");
        when(categoryRepository.findByName("Utility Bill")).thenReturn(utilityBill);

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setStrict(false);
        tokenizer.setNames(NAMES);

        Transaction first = mapper.mapFieldSet(
                tokenizer.tokenize("10/08/2026,Alectra,106.49,Electricity bill,Utility Bill"), "ASANKA");
        Transaction second = mapper.mapFieldSet(
                tokenizer.tokenize("09/08/2026,Alectra,141.54,Electricity bill,Utility Bill"), "ASANKA");

        assertEquals(LocalDate.of(2026, 10, 8), first.getDate());
        assertEquals(0, new BigDecimal("-106.49").compareTo(first.getAmount()));
        assertEquals("Electricity bill", first.getDetails());
        assertSame(utilityBill, first.getCategory());
        assertEquals(LocalDate.of(2026, 9, 8), second.getDate());
        assertEquals(0, new BigDecimal("-141.54").compareTo(second.getAmount()));
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

        Transaction result = mapper.forAccount("DIVYA")
                .mapFieldSet(fieldSet("04/12/2023", "Rogers", "89.99", "", ""));

        assertSame(divyaAccount, result.getAccount());
        assertSame(divya, result.getPerson());
    }

    @Test
    void mapFieldSet_IncomeCategory_ShouldBeMoneyIn() {
        when(personRepository.findByCode("DIVYA")).thenReturn(Optional.of(new Person()));
        Category rentalBillIncome = new Category();
        rentalBillIncome.setCategoryId(30);
        rentalBillIncome.setKind(Category.CategoryKind.INCOME);
        rentalBillIncome.setInBalance(true);
        when(categoryRepository.findByName("Rental Bill Income")).thenReturn(rentalBillIncome);

        Transaction result = mapper.mapFieldSet(
                fieldSet("09/29/2024", "Rental Utility payment", "46.5", "Ekta Utility payment", "Rental Bill Income"), "DIVYA");

        assertEquals(0, new BigDecimal("46.50").compareTo(result.getAmount()));
        assertSame(rentalBillIncome, result.getCategory());
    }

    @Test
    void mapFieldSet_NegativeAmount_ShouldReverseTheCategoryDirection() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        Category utilities = new Category();
        utilities.setCategoryId(4);
        utilities.setKind(Category.CategoryKind.EXPENSE);
        when(categoryRepository.findByName("Utilities")).thenReturn(utilities);

        Transaction refund = mapper.mapFieldSet(
                fieldSet("04/12/2023", "Enbridge Gas", "-20.00", "", "Utilities"), "ASANKA");

        assertEquals(0, new BigDecimal("20.00").compareTo(refund.getAmount()));
    }

    @Test
    void mapFieldSet_Uncategorized_ShouldBeMoneyOut() {
        when(personRepository.findByCode("ASANKA")).thenReturn(Optional.of(asanka));
        Category placeholder = new Category(); // no id: the categorizer queued it for review
        placeholder.setUncategorizedTransaction(new UncategorizedTransaction());
        when(categorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(placeholder);

        Transaction result = mapper.mapFieldSet(fieldSet("04/12/2023", "Mystery", "15.00", "", ""), "ASANKA");

        assertNull(result.getCategory());
        assertEquals(0, new BigDecimal("-15.00").compareTo(result.getAmount()));
    }

    private static Account account(String code) {
        Account account = new Account();
        account.setCode(code);
        return account;
    }

    private FieldSet fieldSet(String... values) {
        return new DefaultFieldSet(values, NAMES);
    }
}
