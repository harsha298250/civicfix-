package com.civicfix.security;

import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.officer.OfficerAnalyticsService;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Officer Analytics Ward Authorization Security Tests (Phase 1.2 Task 1)")
class OfficerAnalyticsWardSecurityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AuditService auditService;

    private OfficerAnalyticsService analyticsService;

    private User officerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        analyticsService = new OfficerAnalyticsService(jdbcTemplate, auditService);

        Role officerRole = new Role(RoleName.OFFICER);
        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setFullName("Officer Shankar");
        officerUser.setEmail("shankar@metro.gov");
        officerUser.setWard("Ward 08, Anna Nagar");
        officerUser.setCrossWardAccess(false);
        officerUser.setCrossWardExpiresAt(null);
        officerUser.setRoles(Set.of(officerRole));

        Role adminRole = new Role(RoleName.ADMIN);
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setFullName("Admin Priya");
        adminUser.setEmail("priya@metro.gov");
        adminUser.setRoles(Set.of(adminRole));
    }

    @Test
    @DisplayName("Officer accessing own ward returns assigned ward without audit logging")
    void officerAccessingOwnWardSucceeds() {
        UserPrincipal principal = new UserPrincipal(officerUser);

        String ward = analyticsService.resolveAuthorizedWard("Ward 08, Anna Nagar", principal, "ANALYTICS_SUMMARY");
        assertEquals("Ward 08, Anna Nagar", ward);
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Officer with no explicit ward in request defaults to assigned ward without audit logging")
    void officerDefaultWardSucceeds() {
        UserPrincipal principal = new UserPrincipal(officerUser);

        String ward = analyticsService.resolveAuthorizedWard(null, principal, "ANALYTICS_SUMMARY");
        assertEquals("Ward 08, Anna Nagar", ward);

        String wardEmpty = analyticsService.resolveAuthorizedWard("", principal, "ANALYTICS_SUMMARY");
        assertEquals("Ward 08, Anna Nagar", wardEmpty);

        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Officer requesting another ward without cross-ward permission is rejected with 403 Forbidden")
    void officerRequestingAnotherWardForbidden() {
        officerUser.setCrossWardAccess(false);
        UserPrincipal principal = new UserPrincipal(officerUser);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                analyticsService.resolveAuthorizedWard("Ward 12, Adyar", principal, "ANALYTICS_SUMMARY")
        );

        assertTrue(ex.getMessage().contains("outside their assigned ward"));
        assertTrue(ex.getMessage().contains("without authorized cross-ward permission"));
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Officer with missing or blank ward assignment is rejected with 403 Forbidden (fail-closed)")
    void officerWithMissingWardAssignmentForbidden() {
        officerUser.setWard(null);
        UserPrincipal principalNullWard = new UserPrincipal(officerUser);

        ForbiddenException exNull = assertThrows(ForbiddenException.class, () ->
                analyticsService.resolveAuthorizedWard("Ward 08, Anna Nagar", principalNullWard, "ANALYTICS_SUMMARY")
        );
        assertTrue(exNull.getMessage().contains("Officer has no assigned ward"));

        officerUser.setWard("   ");
        UserPrincipal principalBlankWard = new UserPrincipal(officerUser);

        ForbiddenException exBlank = assertThrows(ForbiddenException.class, () ->
                analyticsService.resolveAuthorizedWard("Ward 08, Anna Nagar", principalBlankWard, "ANALYTICS_SUMMARY")
        );
        assertTrue(exBlank.getMessage().contains("Officer has no assigned ward"));
    }

    @Test
    @DisplayName("Officer requesting municipal-wide ALL without cross-ward permission is rejected with 403 Forbidden")
    void officerRequestingAllWardsForbidden() {
        officerUser.setCrossWardAccess(false);
        UserPrincipal principal = new UserPrincipal(officerUser);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                analyticsService.resolveAuthorizedWard("ALL", principal, "ANALYTICS_SUMMARY")
        );

        assertTrue(ex.getMessage().contains("Officers cannot query municipal-wide analytics without authorized cross-ward permission"));
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Officer with active crossWardAccess can query another ward and action is audited")
    void officerWithActiveCrossWardAccessAudited() {
        officerUser.setCrossWardAccess(true);
        officerUser.setCrossWardExpiresAt(OffsetDateTime.now().plusHours(2));
        UserPrincipal principal = new UserPrincipal(officerUser);

        String ward = analyticsService.resolveAuthorizedWard("Ward 12, Adyar", principal, "ANALYTICS_SUMMARY");
        assertEquals("Ward 12, Adyar", ward);

        verify(auditService, times(1)).logAction(
                eq(officerUser.getId()),
                eq("Officer Shankar"),
                eq("OFFICER"),
                eq("OFFICER_CROSS_WARD_ANALYTICS_ACCESS"),
                eq("WARD"),
                eq("Ward 12, Adyar"),
                contains("Ward 12, Adyar"),
                isNull(),
                eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Officer with active crossWardAccess can query municipal-wide ALL and action is audited")
    void officerWithActiveCrossWardAccessQueryAllAudited() {
        officerUser.setCrossWardAccess(true);
        officerUser.setCrossWardExpiresAt(OffsetDateTime.now().plusHours(1));
        UserPrincipal principal = new UserPrincipal(officerUser);

        String ward = analyticsService.resolveAuthorizedWard("ALL", principal, "CLOSURE_TRUST");
        assertNull(ward); // Municipal-wide ALL returns null for SQL filter

        verify(auditService, times(1)).logAction(
                eq(officerUser.getId()),
                eq("Officer Shankar"),
                eq("OFFICER"),
                eq("OFFICER_CROSS_WARD_ANALYTICS_ACCESS"),
                eq("WARD"),
                eq("ALL"),
                contains("Authorized municipal-wide analytics access"),
                isNull(),
                eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Officer with expired crossWardAccess is rejected with 403 Forbidden")
    void officerWithExpiredCrossWardAccessRejected() {
        officerUser.setCrossWardAccess(true);
        officerUser.setCrossWardExpiresAt(OffsetDateTime.now().minusMinutes(10));
        UserPrincipal principal = new UserPrincipal(officerUser);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                analyticsService.resolveAuthorizedWard("Ward 12, Adyar", principal, "ANALYTICS_SUMMARY")
        );
        assertTrue(ex.getMessage().contains("without authorized cross-ward permission"));
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Admin has universal ward access for specific ward, ALL, and null without restriction")
    void adminUniversalAccess() {
        UserPrincipal principal = new UserPrincipal(adminUser);

        assertEquals("Ward 12, Adyar", analyticsService.resolveAuthorizedWard("Ward 12, Adyar", principal, "ANALYTICS_SUMMARY"));
        assertNull(analyticsService.resolveAuthorizedWard("ALL", principal, "ANALYTICS_SUMMARY"));
        assertNull(analyticsService.resolveAuthorizedWard(null, principal, "ANALYTICS_SUMMARY"));
        assertNull(analyticsService.resolveAuthorizedWard("   ", principal, "ANALYTICS_SUMMARY"));

        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
