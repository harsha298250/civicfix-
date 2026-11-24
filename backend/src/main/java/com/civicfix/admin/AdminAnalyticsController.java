package com.civicfix.admin;

import com.civicfix.admin.dto.analytics.*;
import com.civicfix.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;

    public AdminAnalyticsController(AdminAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/staff-performance")
    public ResponseEntity<ApiResponse<List<StaffPerformanceItem>>> getStaffPerformance(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate
    ) {
        List<StaffPerformanceItem> items = analyticsService.getStaffPerformance(department, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/area-performance")
    public ResponseEntity<ApiResponse<List<AreaPerformanceItem>>> getAreaPerformance(
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate
    ) {
        List<AreaPerformanceItem> items = analyticsService.getAreaPerformance(ward, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/category-performance")
    public ResponseEntity<ApiResponse<List<CategoryPerformanceItem>>> getCategoryPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate
    ) {
        List<CategoryPerformanceItem> items = analyticsService.getCategoryPerformance(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<List<MonthlyAnalyticsItem>>> getMonthlyAnalytics(
            @RequestParam(required = false) Integer year
    ) {
        List<MonthlyAnalyticsItem> items = analyticsService.getMonthlyAnalytics(year);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/yearly")
    public ResponseEntity<ApiResponse<List<YearlyAnalyticsItem>>> getYearlyAnalytics() {
        List<YearlyAnalyticsItem> items = analyticsService.getYearlyAnalytics();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(defaultValue = "staff") String reportType,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate
    ) {
        String csv = analyticsService.generateCsv(reportType, department, ward, year, startDate, endDate);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String filename = "civicfix_analytics_" + reportType.toLowerCase() + "_" + System.currentTimeMillis() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(bytes);
    }
}
