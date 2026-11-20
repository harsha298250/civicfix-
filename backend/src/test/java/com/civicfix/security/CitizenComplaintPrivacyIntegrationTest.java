package com.civicfix.security;

import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.auth.dto.RegisterCitizenRequest;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.dto.AssignComplaintRequest;
import com.civicfix.complaint.dto.CreateComplaintRequest;
import com.civicfix.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
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
@DisplayName("Citizen Complaint Privacy & 8-Role Access Matrix Integration Tests (Phase 1.2 Task 3 & 7)")
class CitizenComplaintPrivacyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String testComplaintId;
    private String testReferenceId;
    private MockHttpSession owningCitizenSession;
    private MockHttpSession centralOfficerSession;
    private MockHttpSession assignedWorkerSession;
    private MockHttpSession adminSession;

    private User unassignedWorker;
    private User ward12Officer;
    private User lightingSupervisor;
    private String nonOwningCitizenEmail;

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

    @BeforeEach
    void setUp() throws Exception {
        // 1. Owning Citizen Alex Morgan logs in
        owningCitizenSession = loginAs("alex.m@citizen.org", "CivicSecure#2026!");

        // 2. Central Officer Elena Vance logs in
        centralOfficerSession = loginAs("elena.vance@metro.gov", "CivicSecure#2026!");

        // 3. Admin Priya Sharma logs in
        adminSession = loginAs("priya.sharma@admin.metro.gov", "CivicSecure#2026!");

        // 4. Create a test complaint in Ward 08 ("ROADS")
        CreateComplaintRequest createReq = new CreateComplaintRequest();
        createReq.setTitle("Large pothole on 4th Main Road");
        createReq.setDescription("Dangerous road crater requiring asphalt repair.");
        createReq.setCategory("Pothole & Road Damage");
        createReq.setPriority(ComplaintPriority.HIGH);
        createReq.setAddress("4th Main Road, Anna Nagar West");
        createReq.setWard("Ward 08");
        createReq.setLatitude(new BigDecimal("13.0855000"));
        createReq.setLongitude(new BigDecimal("80.2155000"));

        MvcResult createResult = mockMvc.perform(post("/api/complaints")
                        .with(csrf())
                        .session(owningCitizenSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        testComplaintId = json.get("data").get("id").asText();
        testReferenceId = json.get("data").get("referenceId").asText();

        // 5. Assign to Marcus Chen (usr-003)
        AssignComplaintRequest assignReq = new AssignComplaintRequest();
        assignReq.setWorkerId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        assignReq.setNotes("Assigned to Marcus Chen for road repair.");

        mockMvc.perform(post("/api/complaints/" + testComplaintId + "/assign")
                        .with(csrf())
                        .session(centralOfficerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk());

        assignedWorkerSession = loginAs("marcus.chen@fieldops.gov", "CivicSecure#2026!");

        // 6. Provision unassigned worker, ward-12 officer, and lighting supervisor if not present
        Role workerRole = roleRepository.findByName(RoleName.WORKER).orElseThrow();
        Role officerRole = roleRepository.findByName(RoleName.OFFICER).orElseThrow();
        Role supervisorRole = roleRepository.findByName(RoleName.SUPERVISOR).orElseThrow();

        String unassignedWorkerEmail = "unassigned.worker@fieldops.gov";
        unassignedWorker = userRepository.findByEmailIgnoreCase(unassignedWorkerEmail).orElseGet(() -> {
            User u = new User();
            u.setFullName("Unassigned Worker David");
            u.setEmail(unassignedWorkerEmail);
            u.setPasswordHash(passwordEncoder.encode("CivicSecure#2026!"));
            u.setDepartment("Parks Maintenance");
            u.setWard("Ward 15");
            u.setAccountStatus("ACTIVE");
            u.setRoles(Set.of(workerRole));
            return userRepository.save(u);
        });

        String ward12OfficerEmail = "shankar.ward12@metro.gov";
        ward12Officer = userRepository.findByEmailIgnoreCase(ward12OfficerEmail).orElseGet(() -> {
            User u = new User();
            u.setFullName("Officer Shankar Ward 12");
            u.setEmail(ward12OfficerEmail);
            u.setPasswordHash(passwordEncoder.encode("CivicSecure#2026!"));
            u.setDepartment("District 12 Operations");
            u.setWard("Ward 12, Adyar");
            u.setCrossWardAccess(false);
            u.setAccountStatus("ACTIVE");
            u.setRoles(Set.of(officerRole));
            return userRepository.save(u);
        });

        String lightingSupervisorEmail = "ramesh.lighting@metro.gov";
        lightingSupervisor = userRepository.findByEmailIgnoreCase(lightingSupervisorEmail).orElseGet(() -> {
            User u = new User();
            u.setFullName("Supervisor Ramesh Lighting");
            u.setEmail(lightingSupervisorEmail);
            u.setPasswordHash(passwordEncoder.encode("CivicSecure#2026!"));
            u.setDepartment("Electrical & Street Lighting");
            u.setWard("District 04 Headquarters");
            u.setCrossDepartmentAccess(false);
            u.setAccountStatus("ACTIVE");
            u.setRoles(Set.of(supervisorRole));
            return userRepository.save(u);
        });

        // 7. Register a non-owning citizen
        String randId = UUID.randomUUID().toString().substring(0, 8);
        nonOwningCitizenEmail = "kavitha." + randId + "@citizen.org";
        RegisterCitizenRequest regReq = new RegisterCitizenRequest();
        regReq.setName("Kavitha Selvam");
        regReq.setEmail(nonOwningCitizenEmail);
        regReq.setPassword("CivicSecure#2026!");
        regReq.setPhone("+91 97" + (10000000 + (int)(Math.random() * 89999999)));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Role 1: Owning citizen can inspect their complaint, evidence, history and sees own contact info")
    void testOwningCitizenAccessAndContactVisibility() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(owningCitizenSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(testComplaintId)))
                .andExpect(jsonPath("$.data.citizen.email", is("alex.m@citizen.org")))
                .andExpect(jsonPath("$.data.citizen.phone", is("+91 98401 23456")))
                .andReturn();

        // Owning citizen can inspect history
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/history")
                        .session(owningCitizenSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Owning citizen can inspect evidence
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/evidence")
                        .session(owningCitizenSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Role 2: Non-owning citizen is rejected with 403 Forbidden for details, evidence, and history")
    void testNonOwningCitizenForbidden() throws Exception {
        MockHttpSession nonOwnerSession = loginAs(nonOwningCitizenEmail, "CivicSecure#2026!");

        // Non-owning citizen cannot read complaint details
        mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(nonOwnerSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Private record")));

        // Non-owning citizen cannot read complaint history
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/history")
                        .session(nonOwnerSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Private record")));

        // Non-owning citizen cannot read complaint evidence
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/evidence")
                        .session(nonOwnerSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Private record")));
    }

    @Test
    @DisplayName("Role 3: Assigned worker can inspect complaint, evidence, history, but citizen email and phone are sanitized")
    void testAssignedWorkerDataMinimization() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(assignedWorkerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(testComplaintId)))
                .andExpect(jsonPath("$.data.assignedWorker.name", is("Marcus Chen")))
                .andExpect(jsonPath("$.data.citizen.name", is("Alex Morgan")))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode citizen = node.get("data").get("citizen");
        assertNotNull(citizen);
        assertNull(citizen.get("email"), "Assigned worker must not receive citizen email");
        assertNull(citizen.get("phone"), "Assigned worker must not receive citizen phone");

        // Assigned worker can inspect history and evidence
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/history")
                        .session(assignedWorkerSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/evidence")
                        .session(assignedWorkerSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Role 4: Unassigned worker is rejected with 403 Forbidden for details, evidence, and history")
    void testUnassignedWorkerForbidden() throws Exception {
        MockHttpSession unassignedSession = loginAs(unassignedWorker.getEmail(), "CivicSecure#2026!");

        // Unassigned worker cannot view details
        mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(unassignedSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Workers may only access repair orders assigned to their crew")));

        // Unassigned worker cannot view history
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/history")
                        .session(unassignedSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Workers may only access repair orders assigned to their crew")));

        // Unassigned worker cannot view evidence
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/evidence")
                        .session(unassignedSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Workers may only access repair orders assigned to their crew")));
    }

    @Test
    @DisplayName("Role 5: Officer ward authorization enforces boundaries and audits cross-ward access")
    void testOfficerWardAuthorization() throws Exception {
        // Officer in another ward (Ward 12) without cross-ward access cannot access Ward 08 complaint
        MockHttpSession ward12Session = loginAs(ward12Officer.getEmail(), "CivicSecure#2026!");
        mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(ward12Session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Officers cannot access or manage complaints outside their assigned ward")));

        // Central officer with active cross-ward access (Elena Vance) can access Ward 08 complaint
        mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(centralOfficerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(testComplaintId)));
    }

    @Test
    @DisplayName("Role 6: Supervisor department authorization enforces boundaries")
    void testSupervisorDepartmentAuthorization() throws Exception {
        // Supervisor in lighting department cannot access ROADS complaint
        MockHttpSession lightingSupervisorSession = loginAs(lightingSupervisor.getEmail(), "CivicSecure#2026!");
        mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(lightingSupervisorSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Supervisors cannot access or manage complaints outside their authorized department")));
    }

    @Test
    @DisplayName("Role 7: Administrator has universal access to complaint, history, and evidence")
    void testAdminUniversalAccess() throws Exception {
        mockMvc.perform(get("/api/complaints/" + testComplaintId)
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(testComplaintId)));

        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/history")
                        .session(adminSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/evidence")
                        .session(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Role 8: Anonymous visitor cannot access details (401), but can access sanitized public map feed")
    void testAnonymousVisitorAccessControl() throws Exception {
        // Unauthenticated access to details is rejected with 401 Unauthorized
        mockMvc.perform(get("/api/complaints/" + testComplaintId))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to history is rejected with 401 Unauthorized
        mockMvc.perform(get("/api/complaints/" + testComplaintId + "/history"))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to public map feed succeeds
        MvcResult mapResult = mockMvc.perform(get("/api/complaints/public/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn();

        JsonNode root = objectMapper.readTree(mapResult.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertTrue(data.isArray());

        // Verify that public map items never expose citizen email, phone, or internal notes
        for (JsonNode item : data) {
            assertNull(item.get("citizenEmail"), "Public map must not expose citizenEmail");
            assertNull(item.get("citizenPhone"), "Public map must not expose citizenPhone");
            assertNull(item.get("phone"), "Public map must not expose phone");
            assertNull(item.get("internalNotes"), "Public map must not expose internalNotes");
        }
    }
}
