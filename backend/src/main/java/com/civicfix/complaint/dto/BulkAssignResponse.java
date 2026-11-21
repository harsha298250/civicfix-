package com.civicfix.complaint.dto;

import java.util.ArrayList;
import java.util.List;

public class BulkAssignResponse {

    private int totalRequested;
    private int successCount;
    private int failureCount;
    private List<BulkItemResult> results = new ArrayList<>();

    public BulkAssignResponse() {}

    public BulkAssignResponse(int totalRequested, int successCount, int failureCount, List<BulkItemResult> results) {
        this.totalRequested = totalRequested;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.results = results != null ? results : new ArrayList<>();
    }

    public int getTotalRequested() {
        return totalRequested;
    }

    public void setTotalRequested(int totalRequested) {
        this.totalRequested = totalRequested;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<BulkItemResult> getResults() {
        return results;
    }

    public void setResults(List<BulkItemResult> results) {
        this.results = results;
    }

    public static class BulkItemResult {
        private String complaintId;
        private String referenceId;
        private boolean success;
        private String message;

        public BulkItemResult() {}

        public BulkItemResult(String complaintId, String referenceId, boolean success, String message) {
            this.complaintId = complaintId;
            this.referenceId = referenceId;
            this.success = success;
            this.message = message;
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

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
