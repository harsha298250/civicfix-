package com.civicfix.complaint;

import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.complaint.dto.AssignComplaintRequest;
import com.civicfix.security.UserPrincipal;
import com.civicfix.supervisor.SupervisorService;
import com.civicfix.supervisor.dto.SupervisorWorkerResponse;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssignmentHardeningAndWorkloadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.civicfix.user.RoleRepository roleRepository;

    @Autowired
    private SupervisorService supervisorService;

    @Autowired
    private com.civicfix.admin.AdminAnalyticsService analyticsService;

    private static final String OFFICER_EMAIL = "elena.vance@metro.gov";
    private static final String SUPERVISOR_EMAIL = "priya.sharma@admin.metro.gov";
    private static final String DEFAULT_PASSWORD = "CivicSecure#2026!";

    private MockHttpSession loginAs(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    @DisplayName("Inactive worker cannot be assigned tasks - throws BadRequestException")
    void testInactiveWorkerAssignmentBlocked() {
        User inactiveWorker = new User();
        inactiveWorker.setFullName("Inactive Tech");
        inactiveWorker.setEmail("inactive." + System.currentTimeMillis() + "@fieldops.gov");
        inactiveWorker.setPasswordHash("hashedpass");
        inactiveWorker.setAccountStatus("PENDING_ACTIVATION");
        inactiveWorker.setDepartment("Sanitation");
        inactiveWorker.setWard("Ward 08");
        Role workerRole = roleRepository.findByName(RoleName.WORKER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.WORKER)));
        inactiveWorker.setRoles(Set.of(workerRole));
        User savedWorker = userRepository.save(inactiveWorker);

        User officer = userRepository.findByEmailIgnoreCase(OFFICER_EMAIL).orElseThrow();
        UserPrincipal officerPrincipal = new UserPrincipal(officer);

        AssignComplaintRequest req = new AssignComplaintRequest();
        req.setWorkerId(savedWorker.getId());
        req.setVersion(0L);

        // Create a real active complaint
        Complaint testComplaint = new Complaint();
        testComplaint.setReferenceId("CMP-TEST-" + System.currentTimeMillis());
        testComplaint.setTitle("Pothole on Main Road");
        testComplaint.setDescription("Needs urgent patching");
        testComplaint.setAddress("100 Anna Salai, Chennai");
        testComplaint.setCategory("ROADS");
        testComplaint.setPriority(ComplaintPriority.HIGH);
        testComplaint.setStatus(ComplaintStatus.SUBMITTED);
        testComplaint.setWard("Ward 08");
        testComplaint.setCitizen(officer);
        testComplaint.setSlaDueAt(java.time.OffsetDateTime.now().plusDays(2));
        Complaint savedComplaint = complaintRepository.save(testComplaint);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            complaintService.assignWorker(savedComplaint.getId().toString(), req, officerPrincipal, "127.0.0.1");
        });
        assertTrue(ex.getMessage().contains("ACTIVE"));
    }

    @Test
    @DisplayName("Supervisor workers endpoint returns live workload counters")
    void testSupervisorWorkerWorkloadCounters() throws Exception {
        MockHttpSession supervisorSession = loginAs(SUPERVISOR_EMAIL, DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/supervisor/workers")
                        .session(supervisorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", isA(List.class)))
                .andExpect(jsonPath("$.data[0].activeOrdersCount", notNullValue()))
                .andExpect(jsonPath("$.data[0].pendingOrdersCount", notNullValue()))
                .andExpect(jsonPath("$.data[0].inProgressOrdersCount", notNullValue()))
                .andExpect(jsonPath("$.data[0].completedOrdersCount", notNullValue()))
                .andExpect(jsonPath("$.data[0].overdueOrdersCount", notNullValue()));
    }

    @Test
    @DisplayName("Issue 2: Staff performance does not multiply counts when complaint has multiple status history entries")
    void testStaffPerformanceNoDuplicateHistoryMultiplication() {
        User activeWorker = new User();
        activeWorker.setFullName("MultiHistory Tech");
        activeWorker.setEmail("tech.history." + System.currentTimeMillis() + "@fieldops.gov");
        activeWorker.setPasswordHash("hashedpass");
        activeWorker.setAccountStatus("ACTIVE");
        activeWorker.setDepartment("Road Maintenance");
        activeWorker.setWard("Ward 12");
        Role workerRole = roleRepository.findByName(RoleName.WORKER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.WORKER)));
        activeWorker.setRoles(Set.of(workerRole));
        User savedWorker = userRepository.save(activeWorker);

        User officer = userRepository.findByEmailIgnoreCase(OFFICER_EMAIL).orElseThrow();

        Complaint testComplaint = new Complaint();
        testComplaint.setReferenceId("CMP-HIST-" + System.currentTimeMillis());
        testComplaint.setTitle("Multiple Transitions Streetlight");
        testComplaint.setDescription("History multiplier test");
        testComplaint.setAddress("42 Mount Road, Chennai");
        testComplaint.setCategory("STREET_LIGHT");
        testComplaint.setPriority(ComplaintPriority.MEDIUM);
        testComplaint.setStatus(ComplaintStatus.IN_PROGRESS);
        testComplaint.setWard("Ward 12");
        testComplaint.setCitizen(officer);
        testComplaint.setAssignedWorker(savedWorker);
        testComplaint.setSlaDueAt(java.time.OffsetDateTime.now().plusDays(3));

        // Add 3 separate history entries to simulate multiple transitions
        testComplaint.addStatusHistory(ComplaintStatus.SUBMITTED, ComplaintStatus.ASSIGNED, officer, "Assigned to worker");
        testComplaint.addStatusHistory(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS, savedWorker, "Worker started work");
        testComplaint.addStatusHistory(ComplaintStatus.IN_PROGRESS, ComplaintStatus.REWORK_REQUESTED, officer, "Patch needs roller");

        complaintRepository.save(testComplaint);

        List<com.civicfix.admin.dto.analytics.StaffPerformanceItem> staffList =
                analyticsService.getStaffPerformance("Road Maintenance", null, null);

        var workerItem = staffList.stream()
                .filter(s -> s.getStaffId().equals(savedWorker.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, workerItem.getAssignedCount(), "Assigned count must be exactly 1 despite multiple history rows");
        assertEquals(1, workerItem.getPendingCount(), "Pending/active count must be exactly 1");
        assertEquals(1, workerItem.getReworkCount(), "Rework count must be exactly 1");
    }

    @Test
    @DisplayName("Issue 4: SLA Approaching Deadline dual threshold (<= 4h remaining OR >= 80% duration elapsed)")
    void testApproachingDeadlineDualThreshold() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        java.time.OffsetDateTime threshold = now.plusHours(4);
        User officer = userRepository.findByEmailIgnoreCase(OFFICER_EMAIL).orElseThrow();

        // 1. Complaint <= 4h remaining (e.g. 2h remaining) -> Must match
        Complaint c1 = new Complaint();
        c1.setReferenceId("CMP-SLA4H-" + System.currentTimeMillis());
        c1.setTitle("Due in 2h");
        c1.setDescription("Testing 4h window");
        c1.setAddress("Anna Salai");
        c1.setCategory("ROADS");
        c1.setStatus(ComplaintStatus.IN_PROGRESS);
        c1.setCitizen(officer);
        c1.setCreatedAt(now.minusHours(10));
        c1.setSlaDueAt(now.plusHours(2));
        Complaint saved1 = complaintRepository.save(c1);

        // 2. Complaint > 4h remaining BUT >= 80% duration elapsed (e.g. 48h total, 40h elapsed, 8h remaining = 83.3% elapsed) -> Must match
        Complaint c2 = new Complaint();
        c2.setReferenceId("CMP-SLA80P-" + System.currentTimeMillis());
        c2.setTitle("Due in 8h of 48h total (83% elapsed)");
        c2.setDescription("Testing 80% duration window");
        c2.setAddress("Kamarajar Salai");
        c2.setCategory("ROADS");
        c2.setStatus(ComplaintStatus.IN_PROGRESS);
        c2.setCitizen(officer);
        c2.setCreatedAt(now.minusHours(40));
        c2.setSlaDueAt(now.plusHours(8));
        Complaint saved2 = complaintRepository.save(c2);

        // 3. Complaint > 4h remaining AND < 80% duration elapsed (e.g. 48h total, 4h elapsed, 44h remaining = 8.3% elapsed) -> Must NOT match
        Complaint c3 = new Complaint();
        c3.setReferenceId("CMP-SLASAFE-" + System.currentTimeMillis());
        c3.setTitle("Due in 44h of 48h total (8% elapsed)");
        c3.setDescription("Safe complaint");
        c3.setAddress("Poonamallee High Road");
        c3.setCategory("ROADS");
        c3.setStatus(ComplaintStatus.IN_PROGRESS);
        c3.setCitizen(officer);
        c3.setCreatedAt(now.minusHours(4));
        c3.setSlaDueAt(now.plusHours(44));
        Complaint saved3 = complaintRepository.save(c3);

        List<UUID> matchingIds = complaintRepository.findApproachingDeadlineComplaintIds(
                now, threshold, org.springframework.data.domain.PageRequest.of(0, 2000));

        assertTrue(matchingIds.contains(saved1.getId()), "Complaint with <= 4h remaining must match");
        assertTrue(matchingIds.contains(saved2.getId()), "Complaint with >= 80% duration elapsed must match even if > 4h remaining");
        assertFalse(matchingIds.contains(saved3.getId()), "Complaint with < 80% elapsed and > 4h remaining must not match");
    }
}
