package com.civicfix.ai.vision.pothole.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Normalized 2D bounding box representing a localized pothole region.
 * Coordinates are normalized in [0.0, 1.0] relative to image dimensions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PotholeBox {

    private String id;
    private double ymin;
    private double xmin;
    private double ymax;
    private double xmax;
    private BigDecimal confidence;
    private String label = "pothole";
    private Integer pixelWidth;
    private Integer pixelHeight;
    private Integer pixelArea;
    private Double relativeAreaPercent;

    public PotholeBox() {}

    public PotholeBox(String id, double ymin, double xmin, double ymax, double xmax, BigDecimal confidence) {
        this.id = id;
        this.ymin = ymin;
        this.xmin = xmin;
        this.ymax = ymax;
        this.xmax = xmax;
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getYmin() {
        return ymin;
    }

    public void setYmin(double ymin) {
        this.ymin = ymin;
    }

    public double getXmin() {
        return xmin;
    }

    public void setXmin(double xmin) {
        this.xmin = xmin;
    }

    public double getYmax() {
        return ymax;
    }

    public void setYmax(double ymax) {
        this.ymax = ymax;
    }

    public double getXmax() {
        return xmax;
    }

    public void setXmax(double xmax) {
        this.xmax = xmax;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getPixelWidth() {
        return pixelWidth;
    }

    public void setPixelWidth(Integer pixelWidth) {
        this.pixelWidth = pixelWidth;
    }

    public Integer getPixelHeight() {
        return pixelHeight;
    }

    public void setPixelHeight(Integer pixelHeight) {
        this.pixelHeight = pixelHeight;
    }

    public Integer getPixelArea() {
        return pixelArea;
    }

    public void setPixelArea(Integer pixelArea) {
        this.pixelArea = pixelArea;
    }

    public Double getRelativeAreaPercent() {
        return relativeAreaPercent;
    }

    public void setRelativeAreaPercent(Double relativeAreaPercent) {
        this.relativeAreaPercent = relativeAreaPercent;
    }
}
