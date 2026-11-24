package com.civicfix.admin;

import com.civicfix.admin.dto.analytics.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    public AdminAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static final List<String> TN_DISTRICTS = List.of(
            "Ariyalur", "Chengalpattu", "Chennai", "Coimbatore", "Cuddalore",
            "Dharmapuri", "Dindigul", "Erode", "Kallakurichi", "Kancheepuram",
            "Kanyakumari", "Karur", "Krishnagiri", "Madurai", "Mayiladuthurai",
            "Nagapattinam", "Namakkal", "Nilgiris", "Perambalur", "Pudukkottai",
            "Ramanathapuram", "Ranipet", "Salem", "Sivaganga", "Tenkasi",
            "Thanjavur", "Theni", "Thoothukudi", "Tiruchirappalli", "Tirunelveli",
            "Tirupathur", "Tiruppur", "Tiruvallur", "Tiruvannamalai", "Tiruvarur",
            "Vellore", "Viluppuram", "Virudhunagar"
    );

    @Transactional(readOnly = true)
    public List<StaffPerformanceItem> getStaffPerformance(String department, OffsetDateTime startDate, OffsetDateTime endDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                u.id AS staff_id,
                u.full_name,
                u.email,
                (SELECT r2.name FROM roles r2 JOIN user_roles ur2 ON r2.id = ur2.role_id 
                 WHERE ur2.user_id = u.id 
                 ORDER BY CASE WHEN r2.name = 'OFFICER' THEN 1 WHEN r2.name = 'SUPERVISOR' THEN 2 WHEN r2.name = 'WORKER' THEN 3 ELSE 4 END LIMIT 1) AS role_name,
                COALESCE(u.department, 'Unassigned') AS department,
                COUNT(DISTINCT c.id) AS assigned_count,
                COUNT(DISTINCT CASE WHEN c.status IN ('RESOLVED', 'RESOLUTION_SUBMITTED', 'PENDING_CITIZEN_CONFIRMATION') THEN c.id END) AS completed_count,
                COUNT(DISTINCT CASE WHEN c.status IN ('ASSIGNED', 'IN_PROGRESS', 'REWORK_REQUESTED') THEN c.id END) AS pending_count,
                COUNT(DISTINCT CASE WHEN c.sla_due_at < NOW() AND c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN c.id END) AS overdue_count,
                COUNT(DISTINCT CASE WHEN rh.complaint_id IS NOT NULL THEN c.id END) AS rework_count,
                ROUND(COALESCE(AVG(CASE WHEN c.status = 'RESOLVED' 
                    THEN EXTRACT(EPOCH FROM (COALESCE(c.confirmed_at, c.updated_at) - c.created_at)) / 3600.0 END), 0)::numeric, 1) AS avg_resolution_hours
            FROM users u
            LEFT JOIN complaints c ON (c.assigned_worker_id = u.id OR c.assigned_officer_id = u.id) AND c.deleted_at IS NULL
            LEFT JOIN (
                SELECT complaint_id 
                FROM complaint_status_history 
                WHERE new_status = 'REWORK_REQUESTED' 
                GROUP BY complaint_id
            ) rh ON rh.complaint_id = c.id
            WHERE EXISTS (
                SELECT 1 FROM user_roles ur JOIN roles r ON ur.role_id = r.id 
                WHERE ur.user_id = u.id AND r.name IN ('WORKER', 'OFFICER', 'SUPERVISOR')
            )
        """);

        List<Object> params = new ArrayList<>();
        if (department != null && !department.isBlank() && !department.equalsIgnoreCase("ALL")) {
            sql.append(" AND u.department = ? ");
            params.add(department.trim());
        }
        if (startDate != null) {
            sql.append(" AND (c.created_at IS NULL OR c.created_at >= ?) ");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND (c.created_at IS NULL OR c.created_at <= ?) ");
            params.add(endDate);
        }

        sql.append("""
            GROUP BY u.id, u.full_name, u.email, u.department
            ORDER BY completed_count DESC, assigned_count DESC, u.full_name ASC
        """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            UUID staffId = rs.getObject("staff_id", UUID.class);
            String fullName = rs.getString("full_name");
            String email = rs.getString("email");
            String role = rs.getString("role_name");
            String dept = rs.getString("department");
            long assigned = rs.getLong("assigned_count");
            long completed = rs.getLong("completed_count");
            long pending = rs.getLong("pending_count");
            long overdue = rs.getLong("overdue_count");
            long rework = rs.getLong("rework_count");
            double avgHours = rs.getDouble("avg_resolution_hours");
            return new StaffPerformanceItem(staffId, fullName, email, role, dept, assigned, completed, pending, overdue, rework, avgHours);
        }, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<AreaPerformanceItem> getAreaPerformance(String ward, OffsetDateTime startDate, OffsetDateTime endDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                COALESCE(NULLIF(TRIM(c.ward), ''), 'Unassigned Ward') AS ward_name,
                MAX(c.address) AS sample_address,
                COUNT(c.id) AS total_received,
                COUNT(CASE WHEN c.status = 'RESOLVED' THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REWORK_REQUESTED', 'RESOLUTION_SUBMITTED', 'PENDING_CITIZEN_CONFIRMATION') THEN 1 END) AS pending_count,
                COUNT(CASE WHEN c.sla_due_at < NOW() AND c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN 1 END) AS overdue_count,
                ROUND((COUNT(CASE WHEN c.status = 'RESOLVED' THEN 1 END) * 100.0 / NULLIF(COUNT(c.id), 0))::numeric, 1) AS resolution_rate_pct
            FROM complaints c
            WHERE c.deleted_at IS NULL
        """);

        List<Object> params = new ArrayList<>();
        if (ward != null && !ward.isBlank() && !ward.equalsIgnoreCase("ALL")) {
            sql.append(" AND c.ward ILIKE ? ");
            params.add("%" + ward.trim() + "%");
        }
        if (startDate != null) {
            sql.append(" AND c.created_at >= ? ");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND c.created_at <= ? ");
            params.add(endDate);
        }

        sql.append("""
            GROUP BY ward_name
            ORDER BY total_received DESC, ward_name ASC
        """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String wardName = rs.getString("ward_name");
            String sampleAddress = rs.getString("sample_address");
            String district = resolveDistrict(wardName, sampleAddress);
            long total = rs.getLong("total_received");
            long resolved = rs.getLong("resolved_count");
            long pending = rs.getLong("pending_count");
            long overdue = rs.getLong("overdue_count");
            double resRate = rs.getDouble("resolution_rate_pct");
            return new AreaPerformanceItem(district, wardName, total, resolved, pending, overdue, resRate);
        }, params.toArray());
    }

    public String resolveDistrict(String ward, String address) {
        if (ward == null || ward.isBlank() || ward.equalsIgnoreCase("Unassigned Ward")) {
            return "Unassigned District";
        }
        String combined = (ward + " " + (address != null ? address : "")).toLowerCase();
        for (String d : TN_DISTRICTS) {
            String dl = d.toLowerCase();
            if (combined.matches(".*\\b" + java.util.regex.Pattern.quote(dl) + "\\b.*")) {
                return d;
            }
        }
        if (ward.toLowerCase().contains("ward") || ward.toLowerCase().contains("zone")) {
            return "Chennai";
        }
        return "Tamil Nadu (General)";
    }

    @Transactional(readOnly = true)
    public List<CategoryPerformanceItem> getCategoryPerformance(OffsetDateTime startDate, OffsetDateTime endDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                COALESCE(c.category, 'OTHER') AS category_name,
                COUNT(c.id) AS total_received,
                COUNT(CASE WHEN c.status = 'RESOLVED' THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN 1 END) AS pending_count,
                COUNT(CASE WHEN c.sla_due_at < NOW() AND c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN 1 END) AS overdue_count,
                ROUND(COALESCE(AVG(CASE WHEN c.status = 'RESOLVED' 
                    THEN EXTRACT(EPOCH FROM (c.updated_at - c.created_at)) / 3600.0 END), 0)::numeric, 1) AS avg_resolution_hours,
                ROUND((COUNT(CASE 
                    WHEN c.status = 'RESOLVED' AND c.updated_at > c.sla_due_at THEN 1 
                    WHEN c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') AND c.sla_due_at < NOW() THEN 1 
                    END) * 100.0 / NULLIF(COUNT(c.id), 0))::numeric, 1) AS sla_breach_rate_pct
            FROM complaints c
            WHERE c.deleted_at IS NULL
        """);

        List<Object> params = new ArrayList<>();
        if (startDate != null) {
            sql.append(" AND c.created_at >= ? ");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND c.created_at <= ? ");
            params.add(endDate);
        }

        sql.append("""
            GROUP BY category_name
            ORDER BY total_received DESC, category_name ASC
        """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String cat = rs.getString("category_name");
            long total = rs.getLong("total_received");
            long resolved = rs.getLong("resolved_count");
            long pending = rs.getLong("pending_count");
            long overdue = rs.getLong("overdue_count");
            double avgHours = rs.getDouble("avg_resolution_hours");
            double breachRate = rs.getDouble("sla_breach_rate_pct");
            return new CategoryPerformanceItem(cat, total, resolved, pending, overdue, avgHours, breachRate);
        }, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<MonthlyAnalyticsItem> getMonthlyAnalytics(Integer year) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                TO_CHAR(c.created_at, 'YYYY-MM') AS month_label,
                COUNT(c.id) AS received_count,
                COUNT(CASE WHEN c.status = 'RESOLVED' THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN 1 END) AS pending_count,
                COUNT(CASE WHEN c.sla_due_at < NOW() AND c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN 1 END) AS overdue_count,
                ROUND((COUNT(CASE WHEN c.status = 'RESOLVED' THEN 1 END) * 100.0 / NULLIF(COUNT(c.id), 0))::numeric, 1) AS resolution_rate_pct,
                ROUND((COUNT(CASE 
                    WHEN c.status = 'RESOLVED' AND c.updated_at <= c.sla_due_at THEN 1 
                    WHEN c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') AND c.sla_due_at >= NOW() THEN 1 
                    END) * 100.0 / NULLIF(COUNT(c.id), 0))::numeric, 1) AS sla_compliance_pct
            FROM complaints c
            WHERE c.deleted_at IS NULL
        """);

        List<Object> params = new ArrayList<>();
        if (year != null && year > 2000) {
            sql.append(" AND EXTRACT(YEAR FROM c.created_at) = ? ");
            params.add(year);
        }

        sql.append("""
            GROUP BY month_label
            ORDER BY month_label ASC
        """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String month = rs.getString("month_label");
            long received = rs.getLong("received_count");
            long resolved = rs.getLong("resolved_count");
            long pending = rs.getLong("pending_count");
            long overdue = rs.getLong("overdue_count");
            double resRate = rs.getDouble("resolution_rate_pct");
            double compRate = rs.getDouble("sla_compliance_pct");
            return new MonthlyAnalyticsItem(month, received, resolved, pending, overdue, resRate, compRate);
        }, params.toArray());
    }

    @Transactional(readOnly = true)
    public List<YearlyAnalyticsItem> getYearlyAnalytics() {
        String sql = """
            SELECT 
                EXTRACT(YEAR FROM c.created_at)::int AS year_label,
                COUNT(c.id) AS received_count,
                COUNT(CASE WHEN c.status = 'RESOLVED' THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN 1 END) AS pending_count,
                COUNT(CASE WHEN c.sla_due_at < NOW() AND c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') THEN 1 END) AS overdue_count,
                ROUND((COUNT(CASE WHEN c.status = 'RESOLVED' THEN 1 END) * 100.0 / NULLIF(COUNT(c.id), 0))::numeric, 1) AS resolution_rate_pct,
                ROUND((COUNT(CASE 
                    WHEN c.status = 'RESOLVED' AND c.updated_at <= c.sla_due_at THEN 1 
                    WHEN c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') AND c.sla_due_at >= NOW() THEN 1 
                    END) * 100.0 / NULLIF(COUNT(c.id), 0))::numeric, 1) AS sla_compliance_pct
            FROM complaints c
            WHERE c.deleted_at IS NULL
            GROUP BY year_label
            ORDER BY year_label ASC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int year = rs.getInt("year_label");
            long received = rs.getLong("received_count");
            long resolved = rs.getLong("resolved_count");
            long pending = rs.getLong("pending_count");
            long overdue = rs.getLong("overdue_count");
            double resRate = rs.getDouble("resolution_rate_pct");
            double compRate = rs.getDouble("sla_compliance_pct");
            return new YearlyAnalyticsItem(year, received, resolved, pending, overdue, resRate, compRate);
        });
    }

    public String generateCsv(String reportType, String department, String ward, Integer year, OffsetDateTime startDate, OffsetDateTime endDate) {
        StringBuilder csv = new StringBuilder();
        String type = (reportType != null) ? reportType.toLowerCase().trim() : "staff";

        switch (type) {
            case "area" -> {
                csv.append("District,Ward,Total Received,Resolved,Pending,Overdue,Resolution Rate (%)\n");
                List<AreaPerformanceItem> items = getAreaPerformance(ward, startDate, endDate);
                for (AreaPerformanceItem item : items) {
                    csv.append(escapeCsv(item.getDistrict())).append(",")
                       .append(escapeCsv(item.getWard())).append(",")
                       .append(item.getTotalReceived()).append(",")
                       .append(item.getResolvedCount()).append(",")
                       .append(item.getPendingCount()).append(",")
                       .append(item.getOverdueCount()).append(",")
                       .append(item.getResolutionRatePercent()).append("\n");
                }
            }
            case "category" -> {
                csv.append("Category,Total Received,Resolved,Pending,Overdue,Avg Resolution Hours,SLA Breach Rate (%)\n");
                List<CategoryPerformanceItem> items = getCategoryPerformance(startDate, endDate);
                for (CategoryPerformanceItem item : items) {
                    csv.append(escapeCsv(item.getCategory())).append(",")
                       .append(item.getTotalReceived()).append(",")
                       .append(item.getResolvedCount()).append(",")
                       .append(item.getPendingCount()).append(",")
                       .append(item.getOverdueCount()).append(",")
                       .append(item.getAvgResolutionHours()).append(",")
                       .append(item.getSlaBreachRatePercent()).append("\n");
                }
            }
            case "monthly" -> {
                csv.append("Month,Received,Resolved,Pending,Overdue,Resolution Rate (%),SLA Compliance (%)\n");
                List<MonthlyAnalyticsItem> items = getMonthlyAnalytics(year);
                for (MonthlyAnalyticsItem item : items) {
                    csv.append(escapeCsv(item.getYearMonth())).append(",")
                       .append(item.getReceivedCount()).append(",")
                       .append(item.getResolvedCount()).append(",")
                       .append(item.getPendingCount()).append(",")
                       .append(item.getOverdueCount()).append(",")
                       .append(item.getResolutionRatePercent()).append(",")
                       .append(item.getSlaCompliancePercent()).append("\n");
                }
            }
            case "yearly" -> {
                csv.append("Year,Received,Resolved,Pending,Overdue,Resolution Rate (%),SLA Compliance (%)\n");
                List<YearlyAnalyticsItem> items = getYearlyAnalytics();
                for (YearlyAnalyticsItem item : items) {
                    csv.append(item.getYear()).append(",")
                       .append(item.getReceivedCount()).append(",")
                       .append(item.getResolvedCount()).append(",")
                       .append(item.getPendingCount()).append(",")
                       .append(item.getOverdueCount()).append(",")
                       .append(item.getResolutionRatePercent()).append(",")
                       .append(item.getSlaCompliancePercent()).append("\n");
                }
            }
            default -> { // staff
                csv.append("Staff ID,Full Name,Email,Role,Department,Assigned,Completed,Pending,Overdue,Rework,Avg Resolution Hours\n");
                List<StaffPerformanceItem> items = getStaffPerformance(department, startDate, endDate);
                for (StaffPerformanceItem item : items) {
                    csv.append(item.getStaffId()).append(",")
                       .append(escapeCsv(item.getFullName())).append(",")
                       .append(escapeCsv(item.getEmail())).append(",")
                       .append(escapeCsv(item.getRole())).append(",")
                       .append(escapeCsv(item.getDepartment())).append(",")
                       .append(item.getAssignedCount()).append(",")
                       .append(item.getCompletedCount()).append(",")
                       .append(item.getPendingCount()).append(",")
                       .append(item.getOverdueCount()).append(",")
                       .append(item.getReworkCount()).append(",")
                       .append(item.getAvgResolutionHours()).append("\n");
                }
            }
        }
        return csv.toString();
    }

    public String escapeCsv(String value) {
        return escapeCsvText(value);
    }

    public String escapeCsvText(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value;
        // Inspect leading whitespace and control characters when identifying dangerous formula prefixes
        String trimmed = sanitized.stripLeading();
        if (!trimmed.isEmpty()) {
            char firstChar = trimmed.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
                sanitized = "'" + sanitized;
            }
        }
        // RFC 4180 quoting: quote if field contains comma, quote, LF, or CR
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    public String escapeCsvNumeric(Number value) {
        if (value == null) {
            return "0";
        }
        return value.toString();
    }
}
