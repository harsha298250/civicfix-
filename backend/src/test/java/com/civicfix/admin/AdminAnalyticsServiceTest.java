package com.civicfix.admin;

import com.civicfix.admin.dto.analytics.*;
import com.civicfix.auth.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAnalyticsServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminAnalyticsService analyticsService;

    private static final String ADMIN_EMAIL = "priya.sharma@admin.metro.gov";
    private static final String DEFAULT_PASSWORD = "CivicSecure#2026!";

    private MockHttpSession loginAsAdmin() throws Exception {
        LoginRequest req = new LoginRequest(ADMIN_EMAIL, DEFAULT_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    @DisplayName("Admin Analytics Service - Queries execute without SQL errors")
    void testAnalyticsQueriesExecution() {
        List<StaffPerformanceItem> staffItems = analyticsService.getStaffPerformance(null, null, null);
        assertNotNull(staffItems);

        List<AreaPerformanceItem> areaItems = analyticsService.getAreaPerformance(null, null, null);
        assertNotNull(areaItems);

        List<CategoryPerformanceItem> categoryItems = analyticsService.getCategoryPerformance(null, null);
        assertNotNull(categoryItems);

        List<MonthlyAnalyticsItem> monthlyItems = analyticsService.getMonthlyAnalytics(2026);
        assertNotNull(monthlyItems);

        List<YearlyAnalyticsItem> yearlyItems = analyticsService.getYearlyAnalytics();
        assertNotNull(yearlyItems);
    }

    @Test
    @DisplayName("Admin Analytics Service - CSV export generates valid structured headers and content")
    void testCsvExportGeneration() {
        String staffCsv = analyticsService.generateCsv("staff", null, null, null, null, null);
        assertTrue(staffCsv.startsWith("Staff ID,Full Name,Email,Role,Department"));

        String areaCsv = analyticsService.generateCsv("area", null, null, null, null, null);
        assertTrue(areaCsv.startsWith("District,Ward,Total Received,Resolved,Pending,Overdue"));

        String catCsv = analyticsService.generateCsv("category", null, null, null, null, null);
        assertTrue(catCsv.startsWith("Category,Total Received,Resolved,Pending,Overdue,Avg Resolution Hours"));

        String monthlyCsv = analyticsService.generateCsv("monthly", null, null, 2026, null, null);
        assertTrue(monthlyCsv.startsWith("Month,Received,Resolved,Pending,Overdue"));

        String yearlyCsv = analyticsService.generateCsv("yearly", null, null, null, null, null);
        assertTrue(yearlyCsv.startsWith("Year,Received,Resolved,Pending,Overdue"));
    }

    @Test
    @DisplayName("Admin Analytics Controller - REST endpoints require ADMIN and return data")
    void testAnalyticsControllerRestEndpoints() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(get("/api/admin/analytics/staff-performance").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", isA(List.class)));

        mockMvc.perform(get("/api/admin/analytics/area-performance").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", isA(List.class)));

        mockMvc.perform(get("/api/admin/analytics/category-performance").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", isA(List.class)));

        mockMvc.perform(get("/api/admin/analytics/monthly").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", isA(List.class)));

        mockMvc.perform(get("/api/admin/analytics/yearly").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", isA(List.class)));

        mockMvc.perform(get("/api/admin/analytics/export/csv").param("reportType", "staff").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=")));
    }

    @Test
    @DisplayName("Issue 1: CSV Formula Injection & Escaping - Comprehensive Protection")
    void testCsvFormulaInjectionSanitization() {
        // 1. Dangerous formula prefixes
        assertEquals("'=1+1", analyticsService.escapeCsv("=1+1"));
        assertEquals("'+SUM(A1:A2)", analyticsService.escapeCsv("+SUM(A1:A2)"));
        assertEquals("'-1+2", analyticsService.escapeCsv("-1+2"));
        assertEquals("'@SUM(A1:A2)", analyticsService.escapeCsv("@SUM(A1:A2)"));

        // 2. Tab followed by formula
        assertEquals("'\t=1+1", analyticsService.escapeCsv("\t=1+1"));

        // 3. Leading spaces followed by formula
        assertEquals("'   =1+1", analyticsService.escapeCsv("   =1+1"));

        // 4. Embedded commas, quotes, newlines (RFC 4180)
        assertEquals("\"Hello, World\"", analyticsService.escapeCsv("Hello, World"));
        assertEquals("\"Hello \"\"World\"\"\"", analyticsService.escapeCsv("Hello \"World\""));
        assertEquals("\"Line1\nLine2\"", analyticsService.escapeCsv("Line1\nLine2"));
        assertEquals("\"Line1\rLine2\"", analyticsService.escapeCsv("Line1\rLine2"));
        assertEquals("\"Line1\r\nLine2\"", analyticsService.escapeCsv("Line1\r\nLine2"));

        // 5. Empty and null
        assertEquals("", analyticsService.escapeCsv(null));
        assertEquals("", analyticsService.escapeCsv(""));

        // 6. Unicode text and Tamil names
        assertEquals("முருகன்", analyticsService.escapeCsv("முருகன்"));
        assertEquals("Priya Sharma", analyticsService.escapeCsv("Priya Sharma"));

        // 7. Legitimate negative numbers must not be corrupted
        assertEquals("-42", analyticsService.escapeCsvNumeric(-42));
        assertEquals("-12.5", analyticsService.escapeCsvNumeric(-12.5));
        assertEquals("0", analyticsService.escapeCsvNumeric(null));
    }

    @Test
    @DisplayName("Issue 7: District Resolution - Multiple Tamil Nadu Districts & Fallback")
    void testDistrictResolution() {
        assertEquals("Coimbatore", analyticsService.resolveDistrict("Ward 12", "Gandhipuram, Coimbatore, Tamil Nadu"));
        assertEquals("Madurai", analyticsService.resolveDistrict("Ward 5", "KK Nagar, Madurai"));
        assertEquals("Salem", analyticsService.resolveDistrict("Salem North", "Four Roads, Salem"));
        assertEquals("Chennai", analyticsService.resolveDistrict("Ward 104", "Anna Nagar"));
        assertEquals("Chennai", analyticsService.resolveDistrict("Zone 5", "George Town"));
        assertEquals("Unassigned District", analyticsService.resolveDistrict("Unassigned Ward", ""));
        assertEquals("Tamil Nadu (General)", analyticsService.resolveDistrict("State Highway 49", "East Coast Road"));
    }
}
