package ca.homefinance.helper;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.util.*;
import java.util.stream.Collectors;

public final class TransactionCategorizationHelper {

    private static final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();

    private TransactionCategorizationHelper() {}

    public static String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);

        // FIRST remove location while comma is still intact
        t = t.replaceAll("\\s+[a-z]+(?:\\s+[a-z]+)*,\\s*[a-z]{2}$", "");

        // drop store numbers like W1105 or #1234
        t = t.replaceAll("\\bw\\d{3,}\\b", " ");
        t = t.replaceAll("#\\d{2,}", " ");

        // keep only letters/numbers/spaces (this removes the comma)
        t = t.replaceAll("[^a-z0-9 ]", " ");

        // squeeze spaces
        t = t.replaceAll("\\s+", " ").trim();

        return t;
    }

    // Combine Jaro–Winkler with a light token-overlap boost
    public static double similarityScore(String a, String b) {
        double jwScore = safeJaro(a, b);

        // Token overlap (recall of candidate tokens inside input)
        Set<String> aTokens = tokens(a);
        Set<String> bTokens = tokens(b);
        if (bTokens.isEmpty()) return jwScore;

        long overlap = bTokens.stream().filter(aTokens::contains).count();
        double tokenRecall = (double) overlap / (double) bTokens.size();

        // Weighted sum
        return 0.8 * jwScore + 0.2 * tokenRecall;
    }

    public static double safeJaro(String a, String b) {
        Double v = jw.apply(a, b);
        return v == null ? 0.0 : v;
    }

    public static Set<String> tokens(String s) {
        if (s.isBlank()) return Collections.emptySet();
        return Arrays.stream(s.split(" "))
                .filter(tok -> tok.length() > 1) // ignore single letters
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

}
