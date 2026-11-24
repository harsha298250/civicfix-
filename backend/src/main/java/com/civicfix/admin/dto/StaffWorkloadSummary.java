package com.civicfix.admin.dto;

public class StaffWorkloadSummary {

    private long activeCount;
    private long pendingCount;
    private long inProgressCount;
    private long completedCount;
    private long overdueCount;

    public StaffWorkloadSummary() {}

    public StaffWorkloadSummary(long activeCount, long pendingCount, long inProgressCount, long completedCount, long overdueCount) {
        this.activeCount = activeCount;
        this.pendingCount = pendingCount;
        this.inProgressCount = inProgressCount;
        this.completedCount = completedCount;
        this.overdueCount = overdueCount;
    }

    public long getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(long activeCount) {
        this.activeCount = activeCount;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(long pendingCount) {
        this.pendingCount = pendingCount;
    }

    public long getInProgressCount() {
        return inProgressCount;
    }

    public void setInProgressCount(long inProgressCount) {
        this.inProgressCount = inProgressCount;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }

    public long getOverdueCount() {
        return overdueCount;
    }

    public void setOverdueCount(long overdueCount) {
        this.overdueCount = overdueCount;
    }
}
