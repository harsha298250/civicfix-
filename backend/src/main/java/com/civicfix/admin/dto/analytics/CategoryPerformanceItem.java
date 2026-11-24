package com.civicfix.admin.dto.analytics;

public class CategoryPerformanceItem {
    private String category;
    private long totalReceived;
    private long resolvedCount;
    private long pendingCount;
    private long overdueCount;
    private double avgResolutionHours;
    private double slaBreachRatePercent;

    public CategoryPerformanceItem() {}

    public CategoryPerformanceItem(String category, long totalReceived, long resolvedCount,
                                   long pendingCount, long overdueCount, double avgResolutionHours,
                                   double slaBreachRatePercent) {
        this.category = category;
        this.totalReceived = totalReceived;
        this.resolvedCount = resolvedCount;
        this.pendingCount = pendingCount;
        this.overdueCount = overdueCount;
        this.avgResolutionHours = avgResolutionHours;
        this.slaBreachRatePercent = slaBreachRatePercent;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getTotalReceived() { return totalReceived; }
    public void setTotalReceived(long totalReceived) { this.totalReceived = totalReceived; }

    public long getResolvedCount() { return resolvedCount; }
    public void setResolvedCount(long resolvedCount) { this.resolvedCount = resolvedCount; }

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }

    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }

    public double getAvgResolutionHours() { return avgResolutionHours; }
    public void setAvgResolutionHours(double avgResolutionHours) { this.avgResolutionHours = avgResolutionHours; }

    public double getSlaBreachRatePercent() { return slaBreachRatePercent; }
    public void setSlaBreachRatePercent(double slaBreachRatePercent) { this.slaBreachRatePercent = slaBreachRatePercent; }
}
