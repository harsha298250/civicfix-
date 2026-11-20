package com.civicfix.security;

import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.complaint.*;
import com.civicfix.complaint.dto.ComplaintResponse;
import com.civicfix.complaint.dto.EscalateComplaintRequest;
import com.civicfix.complaint.sla.SlaEscalationService;
import com.civicfix.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Complaint Escalation Security Tests (Phase 1.2 Task 4)")
class ComplaintEscalationSecurityTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private SlaEscalationService slaEscalationService;

    private ComplaintService complaintService;

    private User officerUser;
    private User supervisorRoads;
    private User supervisorCrossDept;
    private User citizenUser;
    private User workerUser;
    private Complaint ward8RoadsComplaint;
    private Complaint ward12LightComplaint;
    private Complaint terminalComplaint;

    @BeforeEach
    void setUp() {
        complaintService = new ComplaintService(
                complaintRepository,
                null,
                null,
                null,
                userRepository,
                auditService,
                null,
                null,
                null,
                null,
                slaEscalationService
        );

        Role officerRole = new Role(RoleName.OFFICER);
        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        Role citizenRole = new Role(RoleName.CITIZEN);
        Role workerRole = new Role(RoleName.WORKER);

        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setFullName("Officer Shankar");
        officerUser.setWard("Ward 08, Anna Nagar");
        officerUser.setCrossWardAccess(false);
        officerUser.setRoles(Set.of(officerRole));

        supervisorRoads = new User();
        supervisorRoads.setId(UUID.randomUUID());
        supervisorRoads.setFullName("Supervisor Ramesh");
        supervisorRoads.setDepartment("SDOT Road Maintenance");
        supervisorRoads.setCrossDepartmentAccess(false);
        supervisorRoads.setRoles(Set.of(supervisorRole));

        supervisorCrossDept = new User();
        supervisorCrossDept.setId(UUID.randomUUID());
        supervisorCrossDept.setFullName("Supervisor Sarah Jenkins");
        supervisorCrossDept.setDepartment("Central Municipal Operations");
        supervisorCrossDept.setCrossDepartmentAccess(true);
        supervisorCrossDept.setRoles(Set.of(supervisorRole));

        citizenUser = new User();
        citizenUser.setId(UUID.randomUUID());
        citizenUser.setFullName("Citizen Alex");
        citizenUser.setRoles(Set.of(citizenRole));

        workerUser = new User();
        workerUser.setId(UUID.randomUUID());
        workerUser.setFullName("Worker Marcus");
        workerUser.setRoles(Set.of(workerRole));

        ward8RoadsComplaint = new Complaint();
        ward8RoadsComplaint.setId(UUID.randomUUID());
        ward8RoadsComplaint.setReferenceId("CF-2026-08001");
        ward8RoadsComplaint.setTitle("Pothole on 2nd Avenue");
        ward8RoadsComplaint.setCategory("ROADS");
        ward8RoadsComplaint.setPriority(ComplaintPriority.HIGH);
        ward8RoadsComplaint.setStatus(ComplaintStatus.IN_PROGRESS);
        ward8RoadsComplaint.setWard("Ward 08, Anna Nagar");

        ward12LightComplaint = new Complaint();
        ward12LightComplaint.setId(UUID.randomUUID());
        ward12LightComplaint.setReferenceId("CF-2026-12001");
        ward12LightComplaint.setTitle("Broken streetlight in Adyar");
        ward12LightComplaint.setCategory("STREET_LIGHT");
        ward12LightComplaint.setPriority(ComplaintPriority.MEDIUM);
        ward12LightComplaint.setStatus(ComplaintStatus.ASSIGNED);
        ward12LightComplaint.setWard("Ward 12, Adyar");

        terminalComplaint = new Complaint();
        terminalComplaint.setId(UUID.randomUUID());
        terminalComplaint.setReferenceId("CF-2026-99999");
        terminalComplaint.setTitle("Resolved issue");
        terminalComplaint.setCategory("ROADS");
        terminalComplaint.setStatus(ComplaintStatus.RESOLVED);
        terminalComplaint.setWard("Ward 08, Anna Nagar");

        when(userRepository.findById(officerUser.getId())).thenReturn(Optional.of(officerUser));
        when(userRepository.findById(supervisorRoads.getId())).thenReturn(Optional.of(supervisorRoads));
        when(userRepository.findById(supervisorCrossDept.getId())).thenReturn(Optional.of(supervisorCrossDept));
        when(userRepository.findById(citizenUser.getId())).thenReturn(Optional.of(citizenUser));
        when(userRepository.findById(workerUser.getId())).thenReturn(Optional.of(workerUser));

        when(complaintRepository.findById(ward8RoadsComplaint.getId())).thenReturn(Optional.of(ward8RoadsComplaint));
        when(complaintRepository.findByReferenceIdIgnoreCase("CF-2026-08001")).thenReturn(Optional.of(ward8RoadsComplaint));

        when(complaintRepository.findById(ward12LightComplaint.getId())).thenReturn(Optional.of(ward12LightComplaint));
        when(complaintRepository.findByReferenceIdIgnoreCase("CF-2026-12001")).thenReturn(Optional.of(ward12LightComplaint));

        when(complaintRepository.findById(terminalComplaint.getId())).thenReturn(Optional.of(terminalComplaint));
        when(complaintRepository.findByReferenceIdIgnoreCase("CF-2026-99999")).thenReturn(Optional.of(terminalComplaint));
    }

    @Test
    @DisplayName("Officer escalating complaint within assigned ward succeeds and triggers SLA escalation")
    void officerEscalatingInOwnWardSucceeds() {
        when(slaEscalationService.escalateComplaint(eq(ward8RoadsComplaint.getId()), anyString(), any(), anyString()))
                .thenReturn(true);

        UserPrincipal principal = new UserPrincipal(officerUser);
        EscalateComplaintRequest req = new EscalateComplaintRequest("Urgent traffic disruption requires supervisor review.");

        ComplaintResponse response = complaintService.escalateComplaint("CF-2026-08001", req, principal, "127.0.0.1");

        assertNotNull(response);
        verify(slaEscalationService, times(1)).escalateComplaint(
                eq(ward8RoadsComplaint.getId()),
                eq("Urgent traffic disruption requires supervisor review."),
                eq(officerUser),
                eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("Officer escalating complaint outside assigned ward without permission is rejected with 403 Forbidden")
    void officerEscalatingOutsideWardForbidden() {
        officerUser.setCrossWardAccess(false);
        UserPrincipal principal = new UserPrincipal(officerUser);
        EscalateComplaintRequest req = new EscalateComplaintRequest("Urgent cross ward escalation without authority.");

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                complaintService.escalateComplaint("CF-2026-12001", req, principal, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("outside their assigned ward"));
        verify(slaEscalationService, never()).escalateComplaint(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Officer with active crossWardAccess can escalate complaint outside assigned ward with audit")
    void officerWithActiveCrossWardAccessEscalatingAudited() {
        officerUser.setCrossWardAccess(true);
        officerUser.setCrossWardExpiresAt(OffsetDateTime.now().plusHours(2));
        when(slaEscalationService.escalateComplaint(eq(ward12LightComplaint.getId()), anyString(), any(), anyString()))
                .thenReturn(true);

        UserPrincipal principal = new UserPrincipal(officerUser);
        EscalateComplaintRequest req = new EscalateComplaintRequest("Cross ward emergency requires immediate escalation.");

        ComplaintResponse response = complaintService.escalateComplaint("CF-2026-12001", req, principal, "127.0.0.1");

        assertNotNull(response);
        verify(slaEscalationService, times(1)).escalateComplaint(any(), any(), any(), any());
        verify(auditService, times(1)).logAction(
                eq(officerUser.getId()),
                eq("Officer Shankar"),
                eq("OFFICER"),
                eq("OFFICER_CROSS_WARD_ACCESS"),
                eq("COMPLAINT"),
                eq(ward12LightComplaint.getId().toString()),
                contains("CF-2026-12001"),
                isNull(),
                eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Supervisor escalating complaint within authorized department succeeds")
    void supervisorEscalatingInOwnDepartmentSucceeds() {
        when(slaEscalationService.escalateComplaint(eq(ward8RoadsComplaint.getId()), anyString(), any(), anyString()))
                .thenReturn(true);

        UserPrincipal principal = new UserPrincipal(supervisorRoads);
        EscalateComplaintRequest req = new EscalateComplaintRequest("Material delay requiring contractor escalation.");

        ComplaintResponse response = complaintService.escalateComplaint("CF-2026-08001", req, principal, "127.0.0.1");

        assertNotNull(response);
        verify(slaEscalationService, times(1)).escalateComplaint(
                eq(ward8RoadsComplaint.getId()),
                eq("Material delay requiring contractor escalation."),
                eq(supervisorRoads),
                eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("Supervisor escalating complaint in unauthorized department is rejected with 403 Forbidden")
    void supervisorEscalatingInUnauthorizedDepartmentForbidden() {
        UserPrincipal principal = new UserPrincipal(supervisorRoads);
        EscalateComplaintRequest req = new EscalateComplaintRequest("Road supervisor trying to escalate lighting ticket.");

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                complaintService.escalateComplaint("CF-2026-12001", req, principal, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Supervisors cannot access or manage complaints outside their authorized department"));
        verify(slaEscalationService, never()).escalateComplaint(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Supervisor with active crossDepartmentAccess can escalate complaint in any department")
    void crossDepartmentSupervisorEscalatingSucceeds() {
        when(slaEscalationService.escalateComplaint(eq(ward12LightComplaint.getId()), anyString(), any(), anyString()))
                .thenReturn(true);

        UserPrincipal principal = new UserPrincipal(supervisorCrossDept);
        EscalateComplaintRequest req = new EscalateComplaintRequest("Central ops supervisor escalating electrical ticket.");

        ComplaintResponse response = complaintService.escalateComplaint("CF-2026-12001", req, principal, "127.0.0.1");

        assertNotNull(response);
        verify(slaEscalationService, times(1)).escalateComplaint(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Citizen and Worker attempting escalation are rejected with 403 Forbidden")
    void citizenAndWorkerEscalationForbidden() {
        EscalateComplaintRequest req = new EscalateComplaintRequest("Citizen requesting urgent escalation.");

        UserPrincipal citizenPrincipal = new UserPrincipal(citizenUser);
        ForbiddenException citizenEx = assertThrows(ForbiddenException.class, () ->
                complaintService.escalateComplaint("CF-2026-08001", req, citizenPrincipal, "127.0.0.1")
        );
        assertTrue(citizenEx.getMessage().contains("Only municipal officers, supervisors, and administrators can escalate complaints"));

        UserPrincipal workerPrincipal = new UserPrincipal(workerUser);
        ForbiddenException workerEx = assertThrows(ForbiddenException.class, () ->
                complaintService.escalateComplaint("CF-2026-08001", req, workerPrincipal, "127.0.0.1")
        );
        assertTrue(workerEx.getMessage().contains("Only municipal officers, supervisors, and administrators can escalate complaints"));

        verify(slaEscalationService, never()).escalateComplaint(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Escalating a terminal complaint (RESOLVED, REJECTED) is rejected with 400 Bad Request")
    void terminalComplaintEscalationRejected() {
        UserPrincipal principal = new UserPrincipal(officerUser);
        EscalateComplaintRequest req = new EscalateComplaintRequest("Officer trying to escalate already resolved ticket.");

        terminalComplaint.setStatus(ComplaintStatus.RESOLVED);
        BadRequestException ex1 = assertThrows(BadRequestException.class, () ->
                complaintService.escalateComplaint("CF-2026-99999", req, principal, "127.0.0.1")
        );
        assertTrue(ex1.getMessage().contains("Cannot escalate a terminal complaint"));

        terminalComplaint.setStatus(ComplaintStatus.REJECTED);
        BadRequestException ex2 = assertThrows(BadRequestException.class, () ->
                complaintService.escalateComplaint("CF-2026-99999", req, principal, "127.0.0.1")
        );
        assertTrue(ex2.getMessage().contains("Cannot escalate a terminal complaint"));

        verify(slaEscalationService, never()).escalateComplaint(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Escalation request with invalid reason length (<10 or >500 chars) is rejected with 400 Bad Request")
    void invalidReasonLengthRejected() {
        UserPrincipal principal = new UserPrincipal(officerUser);

        EscalateComplaintRequest tooShort = new EscalateComplaintRequest("Too short");
        assertThrows(BadRequestException.class, () ->
                complaintService.escalateComplaint("CF-2026-08001", tooShort, principal, "127.0.0.1")
        );

        EscalateComplaintRequest nullReason = new EscalateComplaintRequest(null);
        assertThrows(BadRequestException.class, () ->
                complaintService.escalateComplaint("CF-2026-08001", nullReason, principal, "127.0.0.1")
        );

        String longString = "A".repeat(501);
        EscalateComplaintRequest tooLong = new EscalateComplaintRequest(longString);
        assertThrows(BadRequestException.class, () ->
                complaintService.escalateComplaint("CF-2026-08001", tooLong, principal, "127.0.0.1")
        );
    }
}
