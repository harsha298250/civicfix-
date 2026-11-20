package com.civicfix.admin.dto;

import com.civicfix.complaint.Complaint;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Administrative projection of a soft-deleted municipal complaint record.
 * Accessible strictly by administrators to inspect archived tickets, audit deletion reasons,
 * and review audit metadata before performing controlled restoration.
 */
public class AdminDeletedComplaintResponse {

    private UUID id;
    private String referenceId;
    private String title;
    private String category;
    private String status;
    private String ward;
    private OffsetDateTime deletedAt;
    private UUID deletedById;
    private String deletedByName;
    private String deletionReason;
    private OffsetDateTime createdAt;

    public AdminDeletedComplaintResponse() {}

    public static AdminDeletedComplaintResponse fromEntity(Complaint c) {
        if (c == null) return null;
        AdminDeletedComplaintResponse resp = new AdminDeletedComplaintResponse();
        resp.setId(c.getId());
        resp.setReferenceId(c.getReferenceId());
        resp.setTitle(c.getTitle());
        resp.setCategory(c.getCategory());
        resp.setStatus(c.getStatus() != null ? c.getStatus().name() : null);
        resp.setWard(c.getWard());
        resp.setDeletedAt(c.getDeletedAt());
        if (c.getDeletedBy() != null) {
            resp.setDeletedById(c.getDeletedBy().getId());
            resp.setDeletedByName(c.getDeletedBy().getFullName());
        }
        resp.setDeletionReason(c.getDeletionReason());
        resp.setCreatedAt(c.getCreatedAt());
        return resp;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public UUID getDeletedById() {
        return deletedById;
    }

    public void setDeletedById(UUID deletedById) {
        this.deletedById = deletedById;
    }

    public String getDeletedByName() {
        return deletedByName;
    }

    public void setDeletedByName(String deletedByName) {
        this.deletedByName = deletedByName;
    }

    public String getDeletionReason() {
        return deletionReason;
    }

    public void setDeletionReason(String deletionReason) {
        this.deletionReason = deletionReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
