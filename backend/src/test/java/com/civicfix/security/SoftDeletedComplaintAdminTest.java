package com.civicfix.security;

import com.civicfix.admin.dto.AdminDeletedComplaintResponse;
import com.civicfix.admin.dto.RestoreComplaintRequest;
import com.civicfix.audit.AuditService;
import com.civicfix.common.PagedResponse;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.complaint.*;
import com.civicfix.complaint.dto.ComplaintResponse;
import com.civicfix.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Soft-Deleted Complaint Administration Security Tests (Phase 1.2 Task 5)")
class SoftDeletedComplaintAdminTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private ComplaintService complaintService;

    private User adminUser;
    private User officerUser;
    private User citizenUser;

    private UUID complaintId;
    private String refId;
    private Complaint softDeletedComplaint;

    @BeforeEach
    void setUp() {
        complaintService = new ComplaintService(
                complaintRepository,
                null,
                null,
                null,
                userRepository,
                auditService,
                null
        );

        Role adminRole = new Role(RoleName.ADMIN);
        Role officerRole = new Role(RoleName.OFFICER);
        Role citizenRole = new Role(RoleName.CITIZEN);

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setFullName("Admin Priya");
        adminUser.setEmail("priya@metro.gov");
        adminUser.setRoles(Set.of(adminRole));

        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setFullName("Officer Shankar");
        officerUser.setEmail("shankar@metro.gov");
        officerUser.setRoles(Set.of(officerRole));

        citizenUser = new User();
        citizenUser.setId(UUID.randomUUID());
        citizenUser.setFullName("Citizen Alex");
        citizenUser.setEmail("alex@citizen.org");
        citizenUser.setRoles(Set.of(citizenRole));

        complaintId = UUID.randomUUID();
        refId = "CF-2026-08888";

        softDeletedComplaint = new Complaint();
        softDeletedComplaint.setId(complaintId);
        softDeletedComplaint.setReferenceId(refId);
        softDeletedComplaint.setTitle("Soft deleted illegal dumping complaint");
        softDeletedComplaint.setCategory("SOLID_WASTE");
        softDeletedComplaint.setStatus(ComplaintStatus.SUBMITTED);
        softDeletedComplaint.setWard("Ward 08");
        softDeletedComplaint.setDeletedAt(OffsetDateTime.now().minusHours(1));
        softDeletedComplaint.setDeletedBy(adminUser);
        softDeletedComplaint.setDeletionReason("Duplicate spam ticket");
        softDeletedComplaint.setCitizen(citizenUser);
    }

    @Test
    @DisplayName("Non-admin user cannot inspect soft-deleted complaints (403 Forbidden)")
    void nonAdminCannotInspectSoftDeletedComplaints() {
        UserPrincipal officerPrincipal = new UserPrincipal(officerUser);
        Pageable pageable = PageRequest.of(0, 10);

        ForbiddenException ex1 = assertThrows(ForbiddenException.class, () ->
                complaintService.getSoftDeletedComplaints(pageable, officerPrincipal)
        );
        assertTrue(ex1.getMessage().contains("Only municipal administrators have permission to inspect soft-deleted complaint records"));

        UserPrincipal citizenPrincipal = new UserPrincipal(citizenUser);
        ForbiddenException ex2 = assertThrows(ForbiddenException.class, () ->
                complaintService.getSoftDeletedComplaints(pageable, citizenPrincipal)
        );
        assertTrue(ex2.getMessage().contains("Only municipal administrators have permission to inspect soft-deleted complaint records"));

        verify(complaintRepository, never()).findSoftDeletedComplaints(any());
    }

    @Test
    @DisplayName("Admin can inspect soft-deleted complaints successfully")
    void adminCanInspectSoftDeletedComplaints() {
        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);
        Pageable pageable = PageRequest.of(0, 10);

        Page<Complaint> pageResult = new PageImpl<>(List.of(softDeletedComplaint), pageable, 1);
        when(complaintRepository.findSoftDeletedComplaints(pageable)).thenReturn(pageResult);

        PagedResponse<AdminDeletedComplaintResponse> responses = complaintService.getSoftDeletedComplaints(pageable, adminPrincipal);

        assertNotNull(responses);
        assertEquals(1, responses.getTotalElements());
        AdminDeletedComplaintResponse item = responses.getContent().get(0);
        assertEquals(complaintId, item.getId());
        assertEquals(refId, item.getReferenceId());
        assertEquals("Soft deleted illegal dumping complaint", item.getTitle());
        assertEquals("SOLID_WASTE", item.getCategory());
        assertEquals("SUBMITTED", item.getStatus());
        assertEquals("Ward 08", item.getWard());
        assertNotNull(item.getDeletedAt());
        assertEquals(adminUser.getId(), item.getDeletedById());
        assertEquals("Admin Priya", item.getDeletedByName());
        assertEquals("Duplicate spam ticket", item.getDeletionReason());
    }

    @Test
    @DisplayName("Non-admin user cannot restore soft-deleted complaints (403 Forbidden)")
    void nonAdminCannotRestoreSoftDeletedComplaint() {
        UserPrincipal officerPrincipal = new UserPrincipal(officerUser);
        RestoreComplaintRequest req = new RestoreComplaintRequest("Attempting restoration without admin authority");

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                complaintService.restoreComplaint(refId, req.getReason(), officerPrincipal, "127.0.0.1")
        );
        assertTrue(ex.getMessage().contains("Only municipal administrators have permission to restore soft-deleted complaints"));

        verify(complaintRepository, never()).restoreSoftDeletedComplaintNative(any());
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Restoring soft-deleted complaint requires valid reason of at least 5 characters (400 Bad Request)")
    void restoreReasonValidation() {
        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);

        BadRequestException exNull = assertThrows(BadRequestException.class, () ->
                complaintService.restoreComplaint(refId, null, adminPrincipal, "127.0.0.1")
        );
        assertTrue(exNull.getMessage().contains("A valid restoration reason of at least 5 characters is required"));

        BadRequestException exShort = assertThrows(BadRequestException.class, () ->
                complaintService.restoreComplaint(refId, "abc", adminPrincipal, "127.0.0.1")
        );
        assertTrue(exShort.getMessage().contains("A valid restoration reason of at least 5 characters is required"));
    }

    @Test
    @DisplayName("Attempting to restore non-deleted or non-existent complaint returns 404 Not Found")
    void restoreNonExistentOrNotDeletedComplaintNotFound() {
        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);
        when(complaintRepository.findSoftDeletedByIdOrRefNative(any(), anyString()))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                complaintService.restoreComplaint("NON-EXISTENT", "Legitimate restoration reason here", adminPrincipal, "127.0.0.1")
        );
        assertTrue(ex.getMessage().contains("Soft-deleted complaint not found"));
        verify(complaintRepository, never()).restoreSoftDeletedComplaintNative(any());
    }

    @Test
    @DisplayName("Admin restores soft-deleted complaint: native restore executes and audit record is logged")
    void adminRestoresSoftDeletedComplaintSuccessfully() {
        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);

        when(complaintRepository.findSoftDeletedByIdOrRefNative(any(), eq(refId)))
                .thenReturn(Optional.of(softDeletedComplaint));
        when(complaintRepository.restoreSoftDeletedComplaintNative(complaintId))
                .thenReturn(1);

        Complaint restored = new Complaint();
        restored.setId(complaintId);
        restored.setReferenceId(refId);
        restored.setTitle("Restored complaint");
        restored.setCategory("ROADS");
        restored.setStatus(ComplaintStatus.SUBMITTED);
        restored.setWard("Ward 08");
        restored.setDeletedAt(null);
        restored.setCitizen(citizenUser);

        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(restored));

        ComplaintResponse response = complaintService.restoreComplaint(
                refId,
                "Restoring false positive deletion requested by citizen ombudsman.",
                adminPrincipal,
                "10.0.0.1"
        );

        assertNotNull(response);
        assertEquals(complaintId, response.getId());
        assertEquals(refId, response.getReferenceId());

        verify(complaintRepository, times(1)).restoreSoftDeletedComplaintNative(complaintId);
        verify(auditService, times(1)).logAction(
                eq(adminUser.getId()),
                eq("Admin Priya"),
                eq("ADMIN"),
                eq("RESTORE_COMPLAINT"),
                eq("COMPLAINT"),
                eq(complaintId.toString()),
                contains("Restoring false positive deletion"),
                eq("10.0.0.1"),
                eq("SUCCESS")
        );
    }
}
