package com.civicfix.admin.dto.analytics;

import java.util.UUID;

public class StaffPerformanceItem {
    private UUID staffId;
    private String fullName;
    private String email;
    private String role;
    private String department;
    private long assignedCount;
    private long completedCount;
    private long pendingCount;
    private long overdueCount;
    private long reworkCount;
    private double avgResolutionHours;

    public StaffPerformanceItem() {}

    public StaffPerformanceItem(UUID staffId, String fullName, String email, String role, String department,
                                long assignedCount, long completedCount, long pendingCount,
                                long overdueCount, long reworkCount, double avgResolutionHours) {
        this.staffId = staffId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.department = department;
        this.assignedCount = assignedCount;
        this.completedCount = completedCount;
        this.pendingCount = pendingCount;
        this.overdueCount = overdueCount;
        this.reworkCount = reworkCount;
        this.avgResolutionHours = avgResolutionHours;
    }

    public UUID getStaffId() { return staffId; }
    public void setStaffId(UUID staffId) { this.staffId = staffId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public long getAssignedCount() { return assignedCount; }
    public void setAssignedCount(long assignedCount) { this.assignedCount = assignedCount; }

    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }

    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }

    public long getReworkCount() { return reworkCount; }
    public void setReworkCount(long reworkCount) { this.reworkCount = reworkCount; }

    public double getAvgResolutionHours() { return avgResolutionHours; }
    public void setAvgResolutionHours(double avgResolutionHours) { this.avgResolutionHours = avgResolutionHours; }
}
