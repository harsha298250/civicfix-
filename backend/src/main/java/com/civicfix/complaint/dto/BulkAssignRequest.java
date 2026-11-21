package com.civicfix.complaint.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public class BulkAssignRequest {

    @NotEmpty(message = "At least one complaint ID is required for bulk assignment.")
    private List<String> complaintIds;

    @NotNull(message = "Target worker ID is required.")
    private UUID workerId;

    private String notes;

    public BulkAssignRequest() {}

    public BulkAssignRequest(List<String> complaintIds, UUID workerId, String notes) {
        this.complaintIds = complaintIds;
        this.workerId = workerId;
        this.notes = notes;
    }

    public List<String> getComplaintIds() {
        return complaintIds;
    }

    public void setComplaintIds(List<String> complaintIds) {
        this.complaintIds = complaintIds;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public void setWorkerId(UUID workerId) {
        this.workerId = workerId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
