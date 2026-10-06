package ca.homefinance.helper;

import org.junit.jupiter.api.Test;

import static ca.homefinance.helper.TransactionCategorizationHelper.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionCategorizationHelperTest {

    @Test
    void normalize_AmexFixedWidth_DropsCityAndStoreNumber() {
        assertEquals("marshalls", normalize("MARSHALLS 759           ANCASTER"));
        assertEquals("bell canada ob", normalize("BELL CANADA (OB)        MONTREAL"));
        assertEquals("oldnavy com", normalize("OLDNAVY.COM             800-427-7895"));
    }

    @Test
    void normalize_CibcProvinceSuffix_DropsCityWithoutEatingMerchantWords() {
        assertEquals("tim hortons", normalize("TIM HORTONS HAMILTON, ON"));
        assertEquals("uber trip", normalize("UBER TRIP TORONTO, ON"));
        assertEquals("nofrills tonys", normalize("NOFRILLS TONYS 723 HAMILTON, ON"));
    }

    @Test
    void normalize_ProcessorPrefixAndApostrophes() {
        assertEquals("clovermead", normalize("SP CLOVERMEAD AYLMER, ON"));
        // A full-width AMEX description has no padding, so the city stays; rules still match on the brand words
        assertEquals("carters hamilton", normalize("CARTER'S #3680 00000368 HAMILTON"));
    }

    @Test
    void normalize_NullAndBlank() {
        assertEquals("", normalize(null));
        assertEquals("", normalize("   "));
    }

    @Test
    void brandKey_TruncatedVariantsOfSameMerchant_MatchEachOther() {
        String a = brandKey("SECURITY NATIONAL INSUR MONTREAL");
        String b = brandKey("SECURITY NATIONAL INSU  MONTREAL");
        assertTrue(matchSpecificity(normalize("SECURITY NATIONAL INSUR MONTREAL"), b) > 0);
        assertTrue(matchSpecificity(normalize("SECURITY NATIONAL INSU  MONTREAL"), a) > 0);
    }

    @Test
    void matchSpecificity_SpacingDifferences_StillMatch() {
        assertTrue(matchSpecificity("nofrills tonys", "no frills") > 0);
        assertTrue(matchSpecificity("scotts no frills", "no frills") > 0);
        assertTrue(matchSpecificity("oldnavy com", "old navy") > 0);
    }

    @Test
    void matchSpecificity_TruncatedBankDescription_MatchesFullRuleWord() {
        assertTrue(matchSpecificity("fresh manna supermarke", "supermarket") > 0);
    }

    @Test
    void matchSpecificity_ShortRuleDoesNotMatchInsideOtherWords() {
        assertEquals(0, matchSpecificity("sububerb cafe", "uber"));
        assertEquals(0, matchSpecificity("cafeteria", "cafe"));
    }

    @Test
    void matchSpecificity_LongerRuleIsMoreSpecific() {
        assertTrue(matchSpecificity("uber eats", "uber eats") > matchSpecificity("uber eats", "uber"));
    }

    @Test
    void overlapScore_UnrelatedMerchant_IsZero() {
        assertEquals(0.0, overlapScore("chipotle", "old navy"));
    }

    @Test
    void overlapScore_SmallTypo_StillScores() {
        assertTrue(overlapScore("starbuks", "starbucks") >= 0.9);
    }
}
