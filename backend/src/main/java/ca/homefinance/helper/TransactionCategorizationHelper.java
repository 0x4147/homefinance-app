package ca.homefinance.helper;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.util.*;
import java.util.stream.Collectors;

public final class TransactionCategorizationHelper {

    private static final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();

    /** Payment-processor prefixes that say nothing about the merchant. */
    private static final Set<String> PROCESSOR_PREFIXES = Set.of("sp", "sq", "tst", "paypal", "pp", "pos");
    /** Tokens too generic to justify a suggestion on their own. */
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "the", "and", "inc", "ltd", "corp", "canada", "store", "stores", "com", "www", "ca", "co");

    private static final int MIN_SQUASHED_RULE_LENGTH = 5;
    private static final int MIN_PREFIX_LENGTH = 4;
    private static final int BRAND_KEY_MAX_TOKENS = 3;
    private static final double TOKEN_TYPO_THRESHOLD = 0.93;

    private TransactionCategorizationHelper() {}

    /**
     * Reduces a raw bank description to its merchant words: drops the city/province, store and phone
     * numbers, punctuation and processor prefixes.
     * <ul>
     *   <li>AMEX pads the merchant to a fixed width and appends the city after 2+ spaces.</li>
     *   <li>CIBC appends "CITY, PROVINCE".</li>
     * </ul>
     */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).replace(' ', ' ').trim();

        // AMEX: everything after a run of 2+ spaces is the city / phone number
        String[] columns = t.split("\\s{2,}", 2);
        t = columns[0];

        // CIBC: trailing ", ON" marks a province, so the token before it is the city
        boolean hasProvince = t.matches(".*,\\s*[a-z]{2}$");
        if (hasProvince) {
            t = t.replaceAll(",\\s*[a-z]{2}$", "");
        }

        t = t.replace("'", "").replace("’", "");
        t = t.replaceAll("[^a-z0-9]", " ");

        List<String> words = new ArrayList<>();
        for (String word : t.trim().split("\\s+")) {
            if (word.isEmpty() || word.chars().anyMatch(Character::isDigit)) continue;
            words.add(word);
        }
        if (hasProvince && words.size() > 1) {
            words.remove(words.size() - 1);
        }
        while (words.size() > 1 && PROCESSOR_PREFIXES.contains(words.get(0))) {
            words.remove(0);
        }
        return String.join(" ", words);
    }

    /** Short, stable key for a merchant, used for learned rules and for grouping the review queue. */
    public static String brandKey(String s) {
        List<String> words = tokenList(normalize(s));
        return String.join(" ", words.subList(0, Math.min(BRAND_KEY_MAX_TOKENS, words.size())));
    }

    /**
     * How specifically a rule phrase matches the search key; 0 when it does not match.
     * A match is the rule's words appearing in order (the last one may be a truncated bank
     * abbreviation) or, for longer rules, the rule appearing inside the key once spaces are ignored
     * ("no frills" matches "nofrills tonys").
     */
    public static int matchSpecificity(String key, String rulePhrase) {
        List<String> keyTokens = tokenList(key);
        List<String> ruleTokens = tokenList(rulePhrase);
        if (keyTokens.isEmpty() || ruleTokens.isEmpty()) return 0;

        String squashedRule = String.join("", ruleTokens);
        if (containsPhrase(keyTokens, ruleTokens)) {
            return squashedRule.length();
        }
        if (squashedRule.length() >= MIN_SQUASHED_RULE_LENGTH && String.join("", keyTokens).contains(squashedRule)) {
            return squashedRule.length();
        }
        return 0;
    }

    private static boolean containsPhrase(List<String> keyTokens, List<String> ruleTokens) {
        for (int start = 0; start + ruleTokens.size() <= keyTokens.size(); start++) {
            boolean matches = true;
            for (int i = 0; i < ruleTokens.size() && matches; i++) {
                boolean last = i == ruleTokens.size() - 1;
                matches = tokenMatches(keyTokens.get(start + i), ruleTokens.get(i),
                        last, start + i == keyTokens.size() - 1, ruleTokens.size() > 1);
            }
            if (matches) return true;
        }
        return false;
    }

    private static boolean tokenMatches(String keyToken, String ruleToken, boolean lastRuleToken,
                                        boolean lastKeyToken, boolean multiWordRule) {
        if (keyToken.equals(ruleToken)) return true;
        if (!lastRuleToken) return false;
        // The bank truncated the description: "insu" for "insurance"
        if (lastKeyToken && keyToken.length() >= MIN_PREFIX_LENGTH && ruleToken.startsWith(keyToken)) return true;
        // A rule learned from a truncated description: "insu" must still match "insurance"
        return multiWordRule && ruleToken.length() >= MIN_PREFIX_LENGTH && keyToken.startsWith(ruleToken);
    }

    /**
     * Evidence that a key belongs to a rule without containing it: the share of the rule's
     * meaningful words found in the key (allowing small typos). 0..1.
     */
    public static double overlapScore(String key, String rulePhrase) {
        Set<String> keyTokens = new LinkedHashSet<>(tokenList(key));
        List<String> ruleTokens = tokenList(rulePhrase).stream()
                .filter(token -> token.length() >= MIN_PREFIX_LENGTH && !GENERIC_TOKENS.contains(token))
                .toList();
        if (keyTokens.isEmpty() || ruleTokens.isEmpty()) return 0.0;

        long hits = ruleTokens.stream()
                .filter(ruleToken -> keyTokens.stream().anyMatch(keyToken -> similarToken(keyToken, ruleToken)))
                .count();
        return (double) hits / ruleTokens.size();
    }

    private static boolean similarToken(String keyToken, String ruleToken) {
        if (keyToken.equals(ruleToken)) return true;
        if (keyToken.length() < 5 || ruleToken.length() < 5) return false;
        return safeJaro(keyToken, ruleToken) >= TOKEN_TYPO_THRESHOLD;
    }

    public static double safeJaro(String a, String b) {
        Double v = jw.apply(a, b);
        return v == null ? 0.0 : v;
    }

    public static List<String> tokenList(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.trim().split("\\s+"))
                .filter(tok -> !tok.isEmpty())
                .collect(Collectors.toList());
    }

    public static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

}
