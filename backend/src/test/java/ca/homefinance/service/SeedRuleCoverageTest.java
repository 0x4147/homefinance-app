package ca.homefinance.service;

import ca.homefinance.entity.Category;
import ca.homefinance.entity.MerchantRule;
import ca.homefinance.entity.UncategorizedTransaction;
import ca.homefinance.repository.CategoryRepository;
import ca.homefinance.repository.MerchantRuleRepository;
import ca.homefinance.repository.TransactionRepository;
import ca.homefinance.repository.UncategorizedTransactionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Runs real bank descriptions (taken from an actual import) through the rules seeded by the Flyway
 * migration V2, so a regression in normalization or in the seed data shows up as a failed assertion.
 */
class SeedRuleCoverageTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern RULE_ROW = Pattern.compile(
            "\\('([^']+)',\\s*\\(SELECT category_id FROM category WHERE name = '([^']+)'\\),\\s*'SEED'\\)");

    private static TransactionCategorizationService service;

    @BeforeAll
    static void loadRulesFromMigrations() throws Exception {
        Map<String, MerchantRule> rules = new LinkedHashMap<>();
        addRules(rules, Files.readString(MIGRATIONS.resolve("V2__initial_data.sql")));

        MerchantRuleRepository ruleRepository = Mockito.mock(MerchantRuleRepository.class);
        when(ruleRepository.findAll()).thenReturn(new ArrayList<>(rules.values()));
        UncategorizedTransactionRepository uncategorized = Mockito.mock(UncategorizedTransactionRepository.class);
        when(uncategorized.save(any(UncategorizedTransaction.class))).thenAnswer(i -> i.getArgument(0));
        service = new TransactionCategorizationService(Mockito.mock(CategoryRepository.class),
                Mockito.mock(TransactionRepository.class), uncategorized, ruleRepository);
    }

    private static void addRules(Map<String, MerchantRule> rules, String sql) {
        Matcher row = RULE_ROW.matcher(sql);
        while (row.find()) {
            Category category = new Category();
            category.setName(row.group(2));
            MerchantRule rule = new MerchantRule();
            rule.setNormalizedMerchant(row.group(1));
            rule.setCategory(category);
            rule.setSource(MerchantRule.Source.SEED);
            rules.putIfAbsent(row.group(1), rule);
        }
    }

    private static String categorize(String merchant) {
        Category category = service.categorizeTransaction(merchant, null, BigDecimal.TEN, LocalDate.of(2024, 1, 1));
        return category.getName() == null ? "UNCATEGORIZED" : category.getName();
    }

    @Test
    void realMerchants_AreCategorizedByShippedRules() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("SECURITY NATIONAL INSUR MONTREAL", "Insurance");
        expected.put("SECURITY NATIONAL INSU  MONTREAL", "Insurance");
        expected.put("MEMBERSHIP FEE INSTALLMENT", "Bank Fees");
        expected.put("BELL CANADA (OB)        MONTREAL", "Utility Bill");
        expected.put("KOODO MOBILE PAC KOODO  EDMONTON", "Utility Bill");
        expected.put("MARSHALLS 759           ANCASTER", "Clothing");
        expected.put("CHIPOTLE 4449           ANCASTER", "Take Out");
        expected.put("PARIKAR INDIAN NEPALI C HAMILTON", "Take Out");
        expected.put("NOFRILLS TONYS 723 HAMILTON, ON", "Groceries");
        expected.put("SCOTT'S NO FRILLS 9796 HAMILTON, ON", "Groceries");
        expected.put("MOMO CHOW MEIN - THE H HAMILTON, ON", "Take Out");
        expected.put("CARTER'S #3680 00000368 HAMILTON", "Clothing");
        expected.put("OLDNAVY.COM             800-427-7895", "Clothing");
        expected.put("#389 SPORT CHEK         ANCASTER", "Clothing");
        expected.put("AIRBNB * HMREK2BJHM     LONDON", "Travel");
        expected.put("AMEX TRAVEL CAD", "Travel");
        expected.put("SHERWIN WILLIAMS CANADA OAKVILLE", "Home maintenance");
        expected.put("FARM BOY #16 HAMILTON   HAMILTON", "Groceries");
        expected.put("RELIANCEHOMECOMFORT TORONTO, ON", "Home maintenance");
        expected.put("INDIGO 774              ANCASTER", "Shopping");
        expected.put("# 818 PARTY CITY        ANCASTER", "Shopping");
        expected.put("SPOTHERO 844-356-8054   CHICAGO", "Transportation");
        expected.put("PARKEDIN PRECISE PARKLI NORTH YORK", "Transportation");
        expected.put("DESI MART INC. DESI MAR Hamilton", "Groceries");
        expected.put("ONROUTE                 DUTTON", "Take Out");
        expected.put("APNA PIND MISSISSAUGA, ON", "Take Out");
        expected.put("SHEIN DISTRIBUTION CANA TORONTO", "Clothing");
        expected.put("CHRIS & JENN'S NF HAMI HAMILTON, ON", "Groceries");
        expected.put("FOOT LOCKER CANADA      TORONTO", "Clothing");
        expected.put("SWEET PARADISE BAKERY 2 HAMILTON", "Take Out");
        expected.put("ROOTS  LIMERIDGE        HAMILTON", "Clothing");
        expected.put("HAMILTON UTILITY BILLI HAMILTON, ON", "Utility Bill");
        expected.put("ROYAL PAAN HAMILTON, ON", "Take Out");
        expected.put("CHAT HUT MISSISSAUGA, ON", "Take Out");
        expected.put("STAPLES STORE #59       ANCASTER", "Shopping");
        expected.put("DRSHAMBLY DENT PROF CO ANCASTER, ON", "Healthcare");
        expected.put("PETSMART INC. 0919      ANCASTER", "Shopping");
        expected.put("PELLER EST WINERY REST NIAGARA ON-LK, ON", "Take Out");
        expected.put("NETFLIX.COM", "Entertainment");
        expected.put("FRESH MANNA SUPERMARKE Hamilton, ON", "Groceries");
        expected.put("TIM HORTONS #1234 HAMILTON, ON", "Take Out");

        Map<String, String> actual = new LinkedHashMap<>();
        expected.keySet().forEach(merchant -> actual.put(merchant, categorize(merchant)));

        assertEquals(expected, actual);
    }
}
