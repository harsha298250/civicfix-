package com.civicfix.ai.vision.pothole;

import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPhoto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity representing persistent computer vision pothole detection and measurement records.
 */
@Entity
@Table(name = "pothole_detections")
public class PotholeDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id")
    private ComplaintPhoto photo;

    @Column(name = "photo_url", nullable = false, length = 500)
    private String photoUrl;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "pothole_count", nullable = false)
    private Integer potholeCount = 0;

    @Column(name = "detections_json", columnDefinition = "TEXT")
    private String detectionsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private PotholeSeverity severity = PotholeSeverity.INCONCLUSIVE;

    @Column(name = "severity_explanation", columnDefinition = "TEXT")
    private String severityExplanation;

    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "is_calibrated", nullable = false)
    private Boolean isCalibrated = false;

    @Column(name = "calibration_method", length = 100)
    private String calibrationMethod;

    @Column(name = "pixel_width")
    private Integer pixelWidth;

    @Column(name = "pixel_height")
    private Integer pixelHeight;

    @Column(name = "estimated_metric_width_cm", precision = 8, scale = 2)
    private BigDecimal estimatedMetricWidthCm;

    @Column(name = "estimated_metric_length_cm", precision = 8, scale = 2)
    private BigDecimal estimatedMetricLengthCm;

    @Column(name = "estimated_metric_area_sqm", precision = 8, scale = 4)
    private BigDecimal estimatedMetricAreaSqm;

    @Column(name = "measurement_notes", columnDefinition = "TEXT")
    private String measurementNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public PotholeDetection() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public void setComplaint(Complaint complaint) {
        this.complaint = complaint;
    }

    public ComplaintPhoto getPhoto() {
        return photo;
    }

    public void setPhoto(ComplaintPhoto photo) {
        this.photo = photo;
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

    public Integer getPotholeCount() {
        return potholeCount;
    }

    public void setPotholeCount(Integer potholeCount) {
        this.potholeCount = potholeCount;
    }

    public String getDetectionsJson() {
        return detectionsJson;
    }

    public void setDetectionsJson(String detectionsJson) {
        this.detectionsJson = detectionsJson;
    }

    public PotholeSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(PotholeSeverity severity) {
        this.severity = severity;
    }

    public String getSeverityExplanation() {
        return severityExplanation;
    }

    public void setSeverityExplanation(String severityExplanation) {
        this.severityExplanation = severityExplanation;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Boolean getIsCalibrated() {
        return isCalibrated;
    }

    public void setIsCalibrated(Boolean calibrated) {
        isCalibrated = calibrated;
    }

    public String getCalibrationMethod() {
        return calibrationMethod;
    }

    public void setCalibrationMethod(String calibrationMethod) {
        this.calibrationMethod = calibrationMethod;
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

    public BigDecimal getEstimatedMetricWidthCm() {
        return estimatedMetricWidthCm;
    }

    public void setEstimatedMetricWidthCm(BigDecimal estimatedMetricWidthCm) {
        this.estimatedMetricWidthCm = estimatedMetricWidthCm;
    }

    public BigDecimal getEstimatedMetricLengthCm() {
        return estimatedMetricLengthCm;
    }

    public void setEstimatedMetricLengthCm(BigDecimal estimatedMetricLengthCm) {
        this.estimatedMetricLengthCm = estimatedMetricLengthCm;
    }

    public BigDecimal getEstimatedMetricAreaSqm() {
        return estimatedMetricAreaSqm;
    }

    public void setEstimatedMetricAreaSqm(BigDecimal estimatedMetricAreaSqm) {
        this.estimatedMetricAreaSqm = estimatedMetricAreaSqm;
    }

    public String getMeasurementNotes() {
        return measurementNotes;
    }

    public void setMeasurementNotes(String measurementNotes) {
        this.measurementNotes = measurementNotes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
