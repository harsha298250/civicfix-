package com.civicfix.complaint.dto;

import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class NearbyComplaintResponse {

    private UUID id;
    private String referenceId;
    private String title;
    private String category;
    private ComplaintStatus status;
    private String priority;
    private String address;
    private String ward;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double distanceMeters;
    private OffsetDateTime createdAt;
    private Boolean locationSuppressed;
    private Boolean privacySanitized;

    public NearbyComplaintResponse() {}

    public static NearbyComplaintResponse fromEntity(Complaint c, Double distanceMeters) {
        NearbyComplaintResponse r = new NearbyComplaintResponse();
        r.setId(c.getId());
        r.setReferenceId(c.getReferenceId());
        r.setTitle(c.getTitle());
        r.setCategory(c.getCategory());
        r.setStatus(c.getStatus());
        r.setPriority(c.getPriority() != null ? c.getPriority().name() : "MEDIUM");
        r.setAddress(c.getAddress());
        r.setWard(c.getWard());
        r.setLatitude(c.getLatitude());
        r.setLongitude(c.getLongitude());
        r.setDistanceMeters(distanceMeters);
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void sanitizeForPublic(boolean suppressCoordinates) {
        this.privacySanitized = true;
        this.locationSuppressed = suppressCoordinates;
        this.address = null;
        if (suppressCoordinates) {
            this.latitude = null;
            this.longitude = null;
        } else {
            if (this.latitude != null) {
                this.latitude = this.latitude.setScale(3, java.math.RoundingMode.HALF_UP);
            }
            if (this.longitude != null) {
                this.longitude = this.longitude.setScale(3, java.math.RoundingMode.HALF_UP);
            }
        }
    }

    public Boolean getLocationSuppressed() {
        return locationSuppressed;
    }

    public void setLocationSuppressed(Boolean locationSuppressed) {
        this.locationSuppressed = locationSuppressed;
    }

    public Boolean getPrivacySanitized() {
        return privacySanitized;
    }

    public void setPrivacySanitized(Boolean privacySanitized) {
        this.privacySanitized = privacySanitized;
    }
}
