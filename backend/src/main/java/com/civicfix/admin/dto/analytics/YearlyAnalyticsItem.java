package com.civicfix.admin.dto.analytics;

public class YearlyAnalyticsItem {
    private int year;
    private long receivedCount;
    private long resolvedCount;
    private long pendingCount;
    private long overdueCount;
    private double resolutionRatePercent;
    private double slaCompliancePercent;

    public YearlyAnalyticsItem() {}

    public YearlyAnalyticsItem(int year, long receivedCount, long resolvedCount,
                               long pendingCount, long overdueCount, double resolutionRatePercent,
                               double slaCompliancePercent) {
        this.year = year;
        this.receivedCount = receivedCount;
        this.resolvedCount = resolvedCount;
        this.pendingCount = pendingCount;
        this.overdueCount = overdueCount;
        this.resolutionRatePercent = resolutionRatePercent;
        this.slaCompliancePercent = slaCompliancePercent;
    }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public long getReceivedCount() { return receivedCount; }
    public void setReceivedCount(long receivedCount) { this.receivedCount = receivedCount; }

    public long getResolvedCount() { return resolvedCount; }
    public void setResolvedCount(long resolvedCount) { this.resolvedCount = resolvedCount; }

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }

    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }

    public double getResolutionRatePercent() { return resolutionRatePercent; }
    public void setResolutionRatePercent(double resolutionRatePercent) { this.resolutionRatePercent = resolutionRatePercent; }

    public double getSlaCompliancePercent() { return slaCompliancePercent; }
    public void setSlaCompliancePercent(double slaCompliancePercent) { this.slaCompliancePercent = slaCompliancePercent; }
}
