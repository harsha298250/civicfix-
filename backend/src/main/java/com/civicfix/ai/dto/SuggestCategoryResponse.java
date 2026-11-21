package com.civicfix.ai.dto;

import java.util.List;

public class SuggestCategoryResponse {

    private String category;
    private Double confidence;
    private String explanation;
    private List<String> matchedTokens;

    public SuggestCategoryResponse() {}

    public SuggestCategoryResponse(String category, Double confidence, String explanation, List<String> matchedTokens) {
        this.category = category;
        this.confidence = confidence;
        this.explanation = explanation;
        this.matchedTokens = matchedTokens;
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
}
