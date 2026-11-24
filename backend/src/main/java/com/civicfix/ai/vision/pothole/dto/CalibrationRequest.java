package com.civicfix.ai.vision.pothole.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request payload for calibrating real-world measurements from an in-frame reference target.
 */
public class CalibrationRequest {

    @NotBlank(message = "Reference type is required (e.g., A4_SHEET, STANDARD_CAN, KNOWN_MARKER, CUSTOM_LENGTH)")
    private String referenceType;

    @NotNull(message = "Known dimension in centimeters is required")
    @DecimalMin(value = "0.1", message = "Known dimension must be greater than zero")
    private BigDecimal knownDimensionCm;

    @NotNull(message = "Reference bounding box is required")
    private PotholeBox referenceBox;

    public CalibrationRequest() {}

    public CalibrationRequest(String referenceType, BigDecimal knownDimensionCm, PotholeBox referenceBox) {
        this.referenceType = referenceType;
        this.knownDimensionCm = knownDimensionCm;
        this.referenceBox = referenceBox;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public BigDecimal getKnownDimensionCm() {
        return knownDimensionCm;
    }

    public void setKnownDimensionCm(BigDecimal knownDimensionCm) {
        this.knownDimensionCm = knownDimensionCm;
    }

    public PotholeBox getReferenceBox() {
        return referenceBox;
    }

    public void setReferenceBox(PotholeBox referenceBox) {
        this.referenceBox = referenceBox;
    }
}
