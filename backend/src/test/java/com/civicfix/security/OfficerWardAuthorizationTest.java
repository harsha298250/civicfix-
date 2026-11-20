package com.civicfix.security;

import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.ComplaintService;
import com.civicfix.complaint.ComplaintStatus;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Officer Ward Authorization Tests (Decision A)")
class OfficerWardAuthorizationTest {

    private ComplaintService complaintService;
    private TestAuditService auditService;

    private User officerUser;
    private Complaint ward8Complaint;
    private Complaint ward12Complaint;

    static class TestAuditService extends AuditService {
        int loggedCount = 0;
        String lastAction = null;

        TestAuditService() {
            super(null);
        }

        @Override
        public void logAction(UUID actorId, String actorName, String actorRole,
                              String action, String targetType, String targetId,
                              String details, String ipAddress, String status) {
            loggedCount++;
            lastAction = action;
        }
    }

    @BeforeEach
    void setUp() {
        auditService = new TestAuditService();

        complaintService = new ComplaintService(
                null,
                null,
                null,
                null,
                null,
                auditService,
                null
        );

        Role officerRole = new Role(RoleName.OFFICER);

        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setFullName("Officer Shankar");
        officerUser.setEmail("shankar@metro.gov");
        officerUser.setWard("Ward 08, Anna Nagar");
        officerUser.setCrossWardAccess(false);
        officerUser.setCrossWardExpiresAt(null);
        officerUser.setRoles(Set.of(officerRole));

        ward8Complaint = new Complaint();
        ward8Complaint.setId(UUID.randomUUID());
        ward8Complaint.setReferenceId("CF-2026-08001");
        ward8Complaint.setTitle("Pothole on 2nd Avenue");
        ward8Complaint.setDescription("Deep pothole");
        ward8Complaint.setCategory("ROADS");
        ward8Complaint.setPriority(ComplaintPriority.MEDIUM);
        ward8Complaint.setStatus(ComplaintStatus.SUBMITTED);
        ward8Complaint.setWard("Ward 08, Anna Nagar");

        ward12Complaint = new Complaint();
        ward12Complaint.setId(UUID.randomUUID());
        ward12Complaint.setReferenceId("CF-2026-12001");
        ward12Complaint.setTitle("Broken light in Adyar");
        ward12Complaint.setDescription("Street light fixture broken");
        ward12Complaint.setCategory("STREET_LIGHT");
        ward12Complaint.setPriority(ComplaintPriority.HIGH);
        ward12Complaint.setStatus(ComplaintStatus.SUBMITTED);
        ward12Complaint.setWard("Ward 12, Adyar");
    }

    @Test
    @DisplayName("Officer accessing complaint within assigned ward succeeds without audit warning")
    void officerWithinWardSucceeds() {
        UserPrincipal principal = new UserPrincipal(officerUser);

        assertDoesNotThrow(() -> complaintService.enforceOfficerWardAccess(ward8Complaint, principal));
        assertEquals(0, auditService.loggedCount, "No cross-ward audit log should be emitted for intra-ward access");
    }

    @Test
    @DisplayName("Officer accessing complaint in different ward without crossWardAccess is rejected with 403 Forbidden")
    void officerOutsideWardWithoutPermissionRejected() {
        officerUser.setCrossWardAccess(false);
        UserPrincipal principal = new UserPrincipal(officerUser);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                complaintService.enforceOfficerWardAccess(ward12Complaint, principal)
        );

        assertTrue(ex.getMessage().contains("outside their assigned ward"));
        assertTrue(ex.getMessage().contains("without authorized cross-ward permission"));
        assertEquals(0, auditService.loggedCount);
    }

    @Test
    @DisplayName("Officer with active crossWardAccess can access different ward and action is audited")
    void officerWithActiveCrossWardAccessAudited() {
        officerUser.setCrossWardAccess(true);
        officerUser.setCrossWardExpiresAt(OffsetDateTime.now().plusHours(2));
        UserPrincipal principal = new UserPrincipal(officerUser);

        assertDoesNotThrow(() -> complaintService.enforceOfficerWardAccess(ward12Complaint, principal));

        assertEquals(1, auditService.loggedCount);
        assertEquals("OFFICER_CROSS_WARD_ACCESS", auditService.lastAction);
    }

    @Test
    @DisplayName("Officer with expired crossWardAccess is rejected with 403 Forbidden")
    void officerWithExpiredCrossWardAccessRejected() {
        officerUser.setCrossWardAccess(true);
        officerUser.setCrossWardExpiresAt(OffsetDateTime.now().minusMinutes(5)); // expired!
        UserPrincipal principal = new UserPrincipal(officerUser);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                complaintService.enforceOfficerWardAccess(ward12Complaint, principal)
        );

        assertTrue(ex.getMessage().contains("outside their assigned ward"));
    }

    @Test
    @DisplayName("Administrator has universal ward access without restrictions")
    void adminHasUniversalWardAccess() {
        Role adminRole = new Role(RoleName.ADMIN);
        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setFullName("Admin Priya");
        adminUser.setEmail("priya@metro.gov");
        adminUser.setRoles(Set.of(adminRole));

        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);

        assertDoesNotThrow(() -> complaintService.enforceOfficerWardAccess(ward12Complaint, adminPrincipal));
    }
}
