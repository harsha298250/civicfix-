package com.civicfix.complaint.dto;

import com.civicfix.complaint.Complaint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * PublicMapComplaintResponse — Sanitized Public GIS DTO
 * Exposes strictly non-sensitive metadata needed for rendering public map markers.
 * Strictly excludes citizen names, phone numbers, email addresses, personal descriptions,
 * internal officer rework notes, worker comments, titles, reference IDs, and real database IDs.
 * Coordinates are rounded to 3 decimal places (~110m resolution) for resident privacy protection.
 * Low-density areas (< k complaints, default k=5) have coordinates suppressed to prevent individual resident identification.
 */
public class PublicMapComplaintResponse {

    private Integer id;
    private String category;
    private String status;
    private String priority;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String ward;
    private OffsetDateTime createdAt;
    private Boolean locationSuppressed = false;
    private Boolean aggregated = false;
    private Integer count = 1;
    private String suppressionReason;

    public PublicMapComplaintResponse() {}

    public static PublicMapComplaintResponse fromEntity(Complaint c) {
        return fromEntity(c, 1);
    }

    public static PublicMapComplaintResponse fromEntity(Complaint c, int index) {
        PublicMapComplaintResponse resp = new PublicMapComplaintResponse();
        resp.setId(index);
        resp.setCategory(c.getCategory());
        resp.setStatus(c.getStatus() != null ? c.getStatus().name() : null);
        resp.setPriority(c.getPriority() != null ? c.getPriority().name() : null);
        resp.setWard(c.getWard());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setLocationSuppressed(false);
        resp.setAggregated(false);
        resp.setCount(1);
        if (c.getLatitude() != null) {
            resp.setLatitude(c.getLatitude().setScale(3, RoundingMode.HALF_UP));
        }
        if (c.getLongitude() != null) {
            resp.setLongitude(c.getLongitude().setScale(3, RoundingMode.HALF_UP));
        }
        return resp;
    }

    public static PublicMapComplaintResponse suppressed(Complaint c, int index, String reason) {
        PublicMapComplaintResponse resp = new PublicMapComplaintResponse();
        resp.setId(index);
        resp.setCategory(c.getCategory());
        resp.setStatus(c.getStatus() != null ? c.getStatus().name() : null);
        resp.setPriority(c.getPriority() != null ? c.getPriority().name() : null);
        resp.setWard(c.getWard());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setLatitude(null);
        resp.setLongitude(null);
        resp.setLocationSuppressed(true);
        resp.setAggregated(false);
        resp.setCount(1);
        resp.setSuppressionReason(reason != null ? reason : "Low complaint density. Location suppressed to protect resident privacy.");
        return resp;
    }

    public static PublicMapComplaintResponse aggregatedCluster(int index,
                                                               String ward,
                                                               String category,
                                                               String status,
                                                               String priority,
                                                               BigDecimal centroidLat,
                                                               BigDecimal centroidLng,
                                                               int count,
                                                               OffsetDateTime createdAt) {
        PublicMapComplaintResponse resp = new PublicMapComplaintResponse();
        resp.setId(index);
        resp.setWard(ward);
        resp.setCategory(category);
        resp.setStatus(status);
        resp.setPriority(priority);
        resp.setCount(count);
        resp.setAggregated(true);
        resp.setLocationSuppressed(false);
        resp.setCreatedAt(createdAt);
        if (centroidLat != null) {
            resp.setLatitude(centroidLat.setScale(3, RoundingMode.HALF_UP));
        }
        if (centroidLng != null) {
            resp.setLongitude(centroidLng.setScale(3, RoundingMode.HALF_UP));
        }
        return resp;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
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

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getLocationSuppressed() {
        return locationSuppressed;
    }

    public void setLocationSuppressed(Boolean locationSuppressed) {
        this.locationSuppressed = locationSuppressed;
    }

    public Boolean getAggregated() {
        return aggregated;
    }

    public void setAggregated(Boolean aggregated) {
        this.aggregated = aggregated;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getSuppressionReason() {
        return suppressionReason;
    }

    public void setSuppressionReason(String suppressionReason) {
        this.suppressionReason = suppressionReason;
    }
}
