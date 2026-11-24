package com.civicfix.ai.summary;

import java.time.OffsetDateTime;

/**
 * Structured advisory complaint summary for municipal staff.
 * Synthesizes citizen narrative, spatial context, evidence, and recommended dispatch actions
 * while strictly preserving the original complaint description.
 */
public class ComplaintSummaryResponse {

    private String complaintId;
    private String referenceId;
    private String summary;
    private String defectCore;
    private String locationContext;
    private String impactAssessment;
    private String evidenceStatus;
    private String recommendedAction;
    private String language;
    private OffsetDateTime generatedAt;

    public ComplaintSummaryResponse() {}

    public ComplaintSummaryResponse(
            String complaintId,
            String referenceId,
            String summary,
            String defectCore,
            String locationContext,
            String impactAssessment,
            String evidenceStatus,
            String recommendedAction,
            String language,
            OffsetDateTime generatedAt) {
        this.complaintId = complaintId;
        this.referenceId = referenceId;
        this.summary = summary;
        this.defectCore = defectCore;
        this.locationContext = locationContext;
        this.impactAssessment = impactAssessment;
        this.evidenceStatus = evidenceStatus;
        this.recommendedAction = recommendedAction;
        this.language = language;
        this.generatedAt = generatedAt;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDefectCore() {
        return defectCore;
    }

    public void setDefectCore(String defectCore) {
        this.defectCore = defectCore;
    }

    public String getLocationContext() {
        return locationContext;
    }

    public void setLocationContext(String locationContext) {
        this.locationContext = locationContext;
    }

    public String getImpactAssessment() {
        return impactAssessment;
    }

    public void setImpactAssessment(String impactAssessment) {
        this.impactAssessment = impactAssessment;
    }

    public String getEvidenceStatus() {
        return evidenceStatus;
    }

    public void setEvidenceStatus(String evidenceStatus) {
        this.evidenceStatus = evidenceStatus;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
