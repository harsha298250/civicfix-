package com.civicfix.auth;

import com.civicfix.admin.AdminService;
import com.civicfix.admin.dto.AdminUserResponse;
import com.civicfix.admin.dto.CreateStaffRequest;
import com.civicfix.auth.dto.ActivateStaffRequest;
import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffActivationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Staff Onboarding Flow - Pending Activation, Verification, Activation and Login")
    void testCompleteStaffOnboardingFlow() throws Exception {
        String uniqueSuffix = System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        String staffEmail = "fieldworker_" + uniqueSuffix + "@civicfix.internal";

        CreateStaffRequest createReq = new CreateStaffRequest();
        createReq.setFullName("Muthu Vel");
        createReq.setEmail(staffEmail);
        createReq.setRole("WORKER");
        createReq.setDepartment("Road Maintenance");
        createReq.setWard("Ward 15, Guindy");

        User adminUser = userRepository.findByEmailIgnoreCase("priya.sharma@admin.metro.gov").orElseThrow();

        // 1. Admin creates staff
        AdminUserResponse created = adminService.createStaff(
                createReq,
                adminUser.getId(),
                adminUser.getFullName(),
                "127.0.0.1"
        );

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("PENDING_ACTIVATION", created.getAccountStatus());
        String rawToken = created.getActivationToken();
        assertNotNull(rawToken, "Activation token must be attached in test/dev environment");

        // 2. Worker attempts login before activation - must be blocked
        LoginRequest unactivatedLogin = new LoginRequest(staffEmail, "AnyPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unactivatedLogin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("pending activation")));

        // 3. Verify activation token endpoint (GET legacy)
        mockMvc.perform(get("/api/auth/staff-activation/verify")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(staffEmail)))
                .andExpect(jsonPath("$.data.fullName", is("Muthu Vel")))
                .andExpect(jsonPath("$.data.role", is("WORKER")))
                .andExpect(jsonPath("$.data.department", is("Road Maintenance")));

        // 3b. Verify activation token via secure POST endpoint (body payload prevents query string URL exposure)
        mockMvc.perform(post("/api/auth/staff-activation/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("token", rawToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(staffEmail)))
                .andExpect(jsonPath("$.data.fullName", is("Muthu Vel")))
                .andExpect(jsonPath("$.data.role", is("WORKER")));

        // 4. Invalid token verification
        mockMvc.perform(get("/api/auth/staff-activation/verify")
                        .param("token", "invalid_token_12345678901234567890123456789012"))
                .andExpect(status().isBadRequest());

        // 5. Activate staff with new permanent password
        String newPassword = "PermanentSecurePass#2026";
        ActivateStaffRequest activateReq = new ActivateStaffRequest(rawToken, newPassword);

        mockMvc.perform(post("/api/auth/staff-activation/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("activated successfully")));

        // 6. Verify user in database is now ACTIVE
        User activatedUser = userRepository.findById(created.getId()).orElseThrow();
        assertEquals("ACTIVE", activatedUser.getAccountStatus());

        // 7. Token cannot be reused (idempotency / one-time use)
        mockMvc.perform(post("/api/auth/staff-activation/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activateReq)))
                .andExpect(status().isBadRequest());

        // 8. Staff can now log in successfully with permanent password
        LoginRequest activatedLogin = new LoginRequest(staffEmail, newPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activatedLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(staffEmail)))
                .andExpect(jsonPath("$.data.role", is("WORKER")));
    }
}
