package com.civicfix.ai.vision.pothole.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Image-space and calibrated real-world measurements for pothole detections.
 * Strictly enforces that real-world dimensions are null unless calibrated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PotholeMeasurement {

    private Integer imageWidth;
    private Integer imageHeight;
    private Long totalPotholeAreaPixels;
    private Double imageCoveragePercent;
    private boolean calibrated;
    private boolean provisional;
    private String calibrationMethod;
    private BigDecimal metricWidthCm;
    private BigDecimal metricLengthCm;
    private BigDecimal metricAreaSqm;
    private BigDecimal estimatedDepthCm; // Stays null for monocular RGB
    private String statusMessage;

    public PotholeMeasurement() {}

    public Integer getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Integer imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Integer getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Integer imageHeight) {
        this.imageHeight = imageHeight;
    }

    public Long getTotalPotholeAreaPixels() {
        return totalPotholeAreaPixels;
    }

    public void setTotalPotholeAreaPixels(Long totalPotholeAreaPixels) {
        this.totalPotholeAreaPixels = totalPotholeAreaPixels;
    }

    public Double getImageCoveragePercent() {
        return imageCoveragePercent;
    }

    public void setImageCoveragePercent(Double imageCoveragePercent) {
        this.imageCoveragePercent = imageCoveragePercent;
    }

    public boolean isCalibrated() {
        return calibrated;
    }

    public void setCalibrated(boolean calibrated) {
        this.calibrated = calibrated;
    }

    public boolean isProvisional() {
        return provisional;
    }

    public void setProvisional(boolean provisional) {
        this.provisional = provisional;
    }

    public String getCalibrationMethod() {
        return calibrationMethod;
    }

    public void setCalibrationMethod(String calibrationMethod) {
        this.calibrationMethod = calibrationMethod;
    }

    public BigDecimal getMetricWidthCm() {
        return metricWidthCm;
    }

    public void setMetricWidthCm(BigDecimal metricWidthCm) {
        this.metricWidthCm = metricWidthCm;
    }

    public BigDecimal getMetricLengthCm() {
        return metricLengthCm;
    }

    public void setMetricLengthCm(BigDecimal metricLengthCm) {
        this.metricLengthCm = metricLengthCm;
    }

    public BigDecimal getMetricAreaSqm() {
        return metricAreaSqm;
    }

    public void setMetricAreaSqm(BigDecimal metricAreaSqm) {
        this.metricAreaSqm = metricAreaSqm;
    }

    public BigDecimal getEstimatedDepthCm() {
        return estimatedDepthCm;
    }

    public void setEstimatedDepthCm(BigDecimal estimatedDepthCm) {
        this.estimatedDepthCm = estimatedDepthCm;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
}
