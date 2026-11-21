package com.civicfix.citizen.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class CitizenDataExportResponse {

    private UUID residentId;
    private String fullName;
    private String email;
    private String phone;
    private String ward;
    private Integer trustScore;
    private OffsetDateTime memberSince;
    private OffsetDateTime exportedAt;
    private int totalComplaintsFiled;
    private List<ExportedComplaint> complaints;

    public CitizenDataExportResponse() {}

    public CitizenDataExportResponse(UUID residentId, String fullName, String email, String phone,
                                     String ward, Integer trustScore, OffsetDateTime memberSince,
                                     OffsetDateTime exportedAt, int totalComplaintsFiled,
                                     List<ExportedComplaint> complaints) {
        this.residentId = residentId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.ward = ward;
        this.trustScore = trustScore;
        this.memberSince = memberSince;
        this.exportedAt = exportedAt;
        this.totalComplaintsFiled = totalComplaintsFiled;
        this.complaints = complaints;
    }

    public UUID getResidentId() {
        return residentId;
    }

    public void setResidentId(UUID residentId) {
        this.residentId = residentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public Integer getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(Integer trustScore) {
        this.trustScore = trustScore;
    }

    public OffsetDateTime getMemberSince() {
        return memberSince;
    }

    public void setMemberSince(OffsetDateTime memberSince) {
        this.memberSince = memberSince;
    }

    public OffsetDateTime getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(OffsetDateTime exportedAt) {
        this.exportedAt = exportedAt;
    }

    public int getTotalComplaintsFiled() {
        return totalComplaintsFiled;
    }

    public void setTotalComplaintsFiled(int totalComplaintsFiled) {
        this.totalComplaintsFiled = totalComplaintsFiled;
    }

    public List<ExportedComplaint> getComplaints() {
        return complaints;
    }

    public void setComplaints(List<ExportedComplaint> complaints) {
        this.complaints = complaints;
    }

    public static class ExportedComplaint {
        private String id;
        private String referenceId;
        private String title;
        private String description;
        private String category;
        private String priority;
        private String status;
        private String address;
        private String ward;
        private OffsetDateTime createdAt;
        private OffsetDateTime confirmedAt;
        private List<ExportedHistory> timeline;

        public ExportedComplaint() {}

        public ExportedComplaint(String id, String referenceId, String title, String description,
                                 String category, String priority, String status, String address,
                                 String ward, OffsetDateTime createdAt, OffsetDateTime confirmedAt,
                                 List<ExportedHistory> timeline) {
            this.id = id;
            this.referenceId = referenceId;
            this.title = title;
            this.description = description;
            this.category = category;
            this.priority = priority;
            this.status = status;
            this.address = address;
            this.ward = ward;
            this.createdAt = createdAt;
            this.confirmedAt = confirmedAt;
            this.timeline = timeline;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReferenceId() { return referenceId; }
        public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getWard() { return ward; }
        public void setWard(String ward) { this.ward = ward; }
        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
        public OffsetDateTime getConfirmedAt() { return confirmedAt; }
        public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
        public List<ExportedHistory> getTimeline() { return timeline; }
        public void setTimeline(List<ExportedHistory> timeline) { this.timeline = timeline; }
    }

    public static class ExportedHistory {
        private String previousStatus;
        private String newStatus;
        private String changedByName;
        private String notes;
        private OffsetDateTime timestamp;

        public ExportedHistory() {}

        public ExportedHistory(String previousStatus, String newStatus, String changedByName,
                               String notes, OffsetDateTime timestamp) {
            this.previousStatus = previousStatus;
            this.newStatus = newStatus;
            this.changedByName = changedByName;
            this.notes = notes;
            this.timestamp = timestamp;
        }

        public String getPreviousStatus() { return previousStatus; }
        public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
        public String getNewStatus() { return newStatus; }
        public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
        public String getChangedByName() { return changedByName; }
        public void setChangedByName(String changedByName) { this.changedByName = changedByName; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public OffsetDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
    }
}
