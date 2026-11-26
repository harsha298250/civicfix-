package com.civicfix.supervisor;

import com.civicfix.security.UserPrincipal;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupervisorAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private UserPrincipal buildPrincipal(RoleName roleName, boolean crossAccess) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName("Test " + roleName.name());
        u.setEmail(roleName.name().toLowerCase() + "@civicfix.gov");
        u.setAccountStatus("ACTIVE");
        u.setDepartment("Public Works & Infrastructure Oversight");
        u.setWard("All Zones Central Supervisory");
        u.setCrossWardAccess(crossAccess);
        u.setCrossDepartmentAccess(crossAccess);
        u.setRoles(Set.of(new Role(roleName)));
        return new UserPrincipal(u);
    }

    @Test
    @DisplayName("Authorized SUPERVISOR can fetch /api/supervisor/escalations with null ward/category and /api/officer/analytics/summary")
    void testSupervisorCanAccessEscalationsAndAnalytics() throws Exception {
        UserPrincipal supervisor = buildPrincipal(RoleName.SUPERVISOR, true);

        mockMvc.perform(get("/api/supervisor/escalations")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(supervisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/supervisor/analytics/summary")
                        .with(user(supervisor)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPERVISOR cannot access ADMIN-only endpoints (403 Forbidden)")
    void testSupervisorCannotAccessAdminRoutes() throws Exception {
        UserPrincipal supervisor = buildPrincipal(RoleName.SUPERVISOR, true);

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .with(user(supervisor)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OFFICER, WORKER, and CITIZEN receive 403 Forbidden on /api/supervisor/escalations, unauthenticated receives 401")
    void testUnauthorizedRolesRejectedCleanlyOnSupervisorEscalations() throws Exception {
        mockMvc.perform(get("/api/supervisor/escalations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/supervisor/escalations").with(user(buildPrincipal(RoleName.OFFICER, true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/supervisor/escalations").with(user(buildPrincipal(RoleName.WORKER, false))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/supervisor/escalations").with(user(buildPrincipal(RoleName.CITIZEN, false))))
                .andExpect(status().isForbidden());
    }
}
