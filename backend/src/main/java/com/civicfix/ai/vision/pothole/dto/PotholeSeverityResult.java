package com.civicfix.ai.vision.pothole.dto;

import com.civicfix.ai.vision.pothole.PotholeSeverity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Explainable visual severity assessment with contributing factors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PotholeSeverityResult {

    private PotholeSeverity severity;
    private BigDecimal confidence;
    private List<String> factors;
    private String explanation;
    private boolean isAdvisory = true;

    public PotholeSeverityResult() {}

    public PotholeSeverityResult(PotholeSeverity severity, BigDecimal confidence, List<String> factors, String explanation) {
        this.severity = severity;
        this.confidence = confidence;
        this.factors = factors;
        this.explanation = explanation;
        this.isAdvisory = true;
    }

    public PotholeSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(PotholeSeverity severity) {
        this.severity = severity;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public List<String> getFactors() {
        return factors;
    }

    public void setFactors(List<String> factors) {
        this.factors = factors;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isAdvisory() {
        return isAdvisory;
    }

    public void setAdvisory(boolean advisory) {
        isAdvisory = advisory;
    }
}
