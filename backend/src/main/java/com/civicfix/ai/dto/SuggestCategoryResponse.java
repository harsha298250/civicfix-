package com.civicfix.ai.dto;

import java.util.Collections;
import java.util.List;

public class SuggestCategoryResponse {

    private String category;
    private Double confidence;
    private String explanation;
    private List<String> matchedTokens;
    private List<String> alternativeCategories = Collections.emptyList();
    private Boolean isConfident = true;

    public SuggestCategoryResponse() {}

    public SuggestCategoryResponse(String category, Double confidence, String explanation, List<String> matchedTokens) {
        this.category = category;
        this.confidence = confidence;
        this.explanation = explanation;
        this.matchedTokens = matchedTokens;
        this.alternativeCategories = Collections.emptyList();
        this.isConfident = confidence != null && confidence >= 60.0;
    }

    public SuggestCategoryResponse(
            String category,
            Double confidence,
            String explanation,
            List<String> matchedTokens,
            List<String> alternativeCategories,
            Boolean isConfident) {
        this.category = category;
        this.confidence = confidence;
        this.explanation = explanation;
        this.matchedTokens = matchedTokens;
        this.alternativeCategories = alternativeCategories != null ? alternativeCategories : Collections.emptyList();
        this.isConfident = isConfident != null ? isConfident : (confidence != null && confidence >= 60.0);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getMatchedTokens() {
        return matchedTokens;
    }

    public void setMatchedTokens(List<String> matchedTokens) {
        this.matchedTokens = matchedTokens;
    }

    public List<String> getAlternativeCategories() {
        return alternativeCategories;
    }

    public void setAlternativeCategories(List<String> alternativeCategories) {
        this.alternativeCategories = alternativeCategories;
    }

    public Boolean getIsConfident() {
        return isConfident;
    }

    public void setIsConfident(Boolean isConfident) {
        this.isConfident = isConfident;
    }
}
