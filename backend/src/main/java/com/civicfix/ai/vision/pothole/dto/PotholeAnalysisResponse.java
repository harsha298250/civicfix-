package com.civicfix.ai.vision.pothole.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Structured API response for pothole computer vision analysis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PotholeAnalysisResponse {

    private UUID id;
    private UUID complaintId;
    private String photoUrl;
    private String status; // COMPLETED, NO_POTHOLE_DETECTED, INCONCLUSIVE, FAILED, PENDING
    private int potholeCount;
    private List<PotholeBox> boxes = new ArrayList<>();
    private PotholeMeasurement measurement;
    private PotholeSeverityResult severity;
    private String modelName;
    private boolean isAdvisory = true;
    private OffsetDateTime analyzedAt;
    private String errorMessage;

    public PotholeAnalysisResponse() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(UUID complaintId) {
        this.complaintId = complaintId;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPotholeCount() {
        return potholeCount;
    }

    public void setPotholeCount(int potholeCount) {
        this.potholeCount = potholeCount;
    }

    public List<PotholeBox> getBoxes() {
        return boxes;
    }

    public void setBoxes(List<PotholeBox> boxes) {
        this.boxes = boxes;
    }

    public PotholeMeasurement getMeasurement() {
        return measurement;
    }

    public void setMeasurement(PotholeMeasurement measurement) {
        this.measurement = measurement;
    }

    public PotholeSeverityResult getSeverity() {
        return severity;
    }

    public void setSeverity(PotholeSeverityResult severity) {
        this.severity = severity;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isAdvisory() {
        return isAdvisory;
    }

    public void setAdvisory(boolean advisory) {
        isAdvisory = advisory;
    }

    public OffsetDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(OffsetDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
