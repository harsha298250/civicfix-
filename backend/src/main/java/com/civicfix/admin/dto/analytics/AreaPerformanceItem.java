package com.civicfix.admin.dto.analytics;

public class AreaPerformanceItem {
    private String district;
    private String ward;
    private long totalReceived;
    private long resolvedCount;
    private long pendingCount;
    private long overdueCount;
    private double resolutionRatePercent;

    public AreaPerformanceItem() {}

    public AreaPerformanceItem(String district, String ward, long totalReceived,
                               long resolvedCount, long pendingCount, long overdueCount,
                               double resolutionRatePercent) {
        this.district = district;
        this.ward = ward;
        this.totalReceived = totalReceived;
        this.resolvedCount = resolvedCount;
        this.pendingCount = pendingCount;
        this.overdueCount = overdueCount;
        this.resolutionRatePercent = resolutionRatePercent;
    }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }

    public long getTotalReceived() { return totalReceived; }
    public void setTotalReceived(long totalReceived) { this.totalReceived = totalReceived; }

    public long getResolvedCount() { return resolvedCount; }
    public void setResolvedCount(long resolvedCount) { this.resolvedCount = resolvedCount; }

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }

    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }

    public double getResolutionRatePercent() { return resolutionRatePercent; }
    public void setResolutionRatePercent(double resolutionRatePercent) { this.resolutionRatePercent = resolutionRatePercent; }
}
