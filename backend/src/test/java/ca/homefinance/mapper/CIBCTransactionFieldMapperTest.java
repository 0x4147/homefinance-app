package ca.homefinance.mapper;

import ca.homefinance.entity.Account;
import ca.homefinance.entity.Category;
import ca.homefinance.entity.Person;
import ca.homefinance.entity.PersonCard;
import ca.homefinance.entity.Transaction;
import ca.homefinance.repository.AccountRepository;
import ca.homefinance.repository.PersonCardRepository;
import ca.homefinance.service.TransactionCategorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.FieldSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CIBCTransactionFieldMapperTest {

    private static final String ASANKA_CARD = "5223********5844";

    @Mock
    private PersonCardRepository personCardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionCategorizationService categorizationService;

    private CIBCTransactionFieldMapper mapper;
    private final Account cibc = new Account();

    @BeforeEach
    void setUp() {
        cibc.setCode("CIBC");
        lenient().when(accountRepository.findByCode("CIBC")).thenReturn(Optional.of(cibc));
        mapper = new CIBCTransactionFieldMapper(personCardRepository, accountRepository, categorizationService);

        Category category = new Category();
        category.setCategoryId(1);
        category.setName("Groceries");
        when(categorizationService.categorizeTransaction(any(), any(), any(), any())).thenReturn(category);
    }

    @Test
    void mapFieldSet_RegisteredCard_ShouldAssignCardHolder() {
        Person asanka = new Person();
        asanka.setPersonId(7);
        asanka.setCode("ASANKA");
        PersonCard card = new PersonCard();
        card.setPerson(asanka);
        card.setCardIdentifier(ASANKA_CARD);
        when(personCardRepository.findByCardIdentifier(eq(ASANKA_CARD))).thenReturn(Optional.of(card));

        Transaction result = mapper.mapFieldSet(fieldSet(ASANKA_CARD));

        assertSame(asanka, result.getPerson());
        assertSame(cibc, result.getAccount());
        assertEquals(0, new BigDecimal("-25.00").compareTo(result.getAmount()));
        assertEquals(LocalDate.of(2024, 1, 15), result.getDate());
    }

    @Test
    void mapFieldSet_UnknownCard_ShouldImportWithoutPerson() {
        when(personCardRepository.findByCardIdentifier(eq("9999********0000"))).thenReturn(Optional.empty());

        Transaction result = mapper.mapFieldSet(fieldSet("9999********0000"));

        assertNull(result.getPerson());
        assertEquals("Some Store", result.getEntity());
    }

    @Test
    void mapFieldSet_AmountIn_ShouldBeStoredAsMoneyIn() {
        when(personCardRepository.findByCardIdentifier(eq(ASANKA_CARD))).thenReturn(Optional.empty());

        Transaction result = mapper.mapFieldSet(new DefaultFieldSet(
                new String[]{"2024-01-15", "PAYMENT THANK YOU", "", "250.00", ASANKA_CARD},
                new String[]{"date", "entity", "amount out", "amount in", "person"}));

        assertEquals(0, new BigDecimal("250.00").compareTo(result.getAmount()));
    }

    private FieldSet fieldSet(String cardNumber) {
        return new DefaultFieldSet(
                new String[]{"2024-01-15", "Some Store", "25.00", "", cardNumber},
                new String[]{"date", "entity", "amount out", "amount in", "person"});
    }
}
