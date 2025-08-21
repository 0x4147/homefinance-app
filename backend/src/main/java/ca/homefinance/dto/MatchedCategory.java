package ca.homefinance.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchedCategory {

    public final String matchedMerchantKey;
    public final String categoryName;
    public final double confidence;

    public MatchedCategory(String matchedMerchantKey, String categoryName, double confidence) {
        this.matchedMerchantKey = matchedMerchantKey;
        this.categoryName = categoryName;
        this.confidence = confidence;
    }

}
