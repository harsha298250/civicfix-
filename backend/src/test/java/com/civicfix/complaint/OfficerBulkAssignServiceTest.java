package com.civicfix.complaint;

import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.complaint.dto.BulkAssignRequest;
import com.civicfix.complaint.dto.BulkAssignResponse;
import com.civicfix.security.UserPrincipal;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OfficerBulkAssignServiceTest {

    private ComplaintRepository complaintRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private ComplaintService complaintService;

    private User officerUser;
    private UserPrincipal officerPrincipal;
    private User citizenUser;
    private UserPrincipal citizenPrincipal;
    private User activeWorker;
    private User inactiveWorker;
    private User nonWorker;
    private Complaint complaint1;
    private Complaint complaint2;
    private Complaint terminalComplaint;

    private final AtomicBoolean auditLogged = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        auditLogged.set(false);

        UUID officerId = UUID.randomUUID();
        officerUser = new User();
        officerUser.setId(officerId);
        officerUser.setFullName("Elena Vance");
        officerUser.setEmail("elena.vance@metro.gov");
        officerUser.setWard("Ward 08, Anna Nagar, Chennai");
        officerUser.setRoles(Set.of(new Role(RoleName.OFFICER)));
        officerPrincipal = new UserPrincipal(officerUser);

        UUID citizenId = UUID.randomUUID();
        citizenUser = new User();
        citizenUser.setId(citizenId);
        citizenUser.setFullName("Alex Morgan");
        citizenUser.setEmail("alex.m@citizen.org");
        citizenUser.setRoles(Set.of(new Role(RoleName.CITIZEN)));
        citizenPrincipal = new UserPrincipal(citizenUser);

        activeWorker = new User();
        activeWorker.setId(UUID.randomUUID());
        activeWorker.setFullName("Marcus Chen");
        activeWorker.setEmail("marcus.chen@fieldops.gov");
        activeWorker.setAccountStatus("ACTIVE");
        activeWorker.setRoles(Set.of(new Role(RoleName.WORKER)));

        inactiveWorker = new User();
        inactiveWorker.setId(UUID.randomUUID());
        inactiveWorker.setFullName("Suspended Worker");
        inactiveWorker.setEmail("suspended@fieldops.gov");
        inactiveWorker.setAccountStatus("SUSPENDED");
        inactiveWorker.setRoles(Set.of(new Role(RoleName.WORKER)));

        nonWorker = new User();
        nonWorker.setId(UUID.randomUUID());
        nonWorker.setFullName("Civilian User");
        nonWorker.setEmail("civilian@citizen.org");
        nonWorker.setAccountStatus("ACTIVE");
        nonWorker.setRoles(Set.of(new Role(RoleName.CITIZEN)));

        complaint1 = new Complaint();
        complaint1.setId(UUID.randomUUID());
        complaint1.setReferenceId("CF-2026-00101");
        complaint1.setTitle("Pothole on 4th Main");
        complaint1.setWard("Ward 08, Anna Nagar, Chennai");
        complaint1.setStatus(ComplaintStatus.SUBMITTED);
        complaint1.setVersion(0L);

        complaint2 = new Complaint();
        complaint2.setId(UUID.randomUUID());
        complaint2.setReferenceId("CF-2026-00102");
        complaint2.setTitle("Damaged street signage");
        complaint2.setWard("Ward 08, Anna Nagar, Chennai");
        complaint2.setStatus(ComplaintStatus.UNDER_REVIEW);
        complaint2.setVersion(0L);

        terminalComplaint = new Complaint();
        terminalComplaint.setId(UUID.randomUUID());
        terminalComplaint.setReferenceId("CF-2026-00103");
        terminalComplaint.setTitle("Closed streetlight issue");
        terminalComplaint.setWard("Ward 08, Anna Nagar, Chennai");
        terminalComplaint.setStatus(ComplaintStatus.RESOLVED);
        terminalComplaint.setVersion(0L);

        // Dynamic Proxy for UserRepository
        userRepository = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        UUID target = (UUID) args[0];
                        if (target.equals(officerId)) return Optional.of(officerUser);
                        if (target.equals(citizenId)) return Optional.of(citizenUser);
                        if (target.equals(activeWorker.getId())) return Optional.of(activeWorker);
                        if (target.equals(inactiveWorker.getId())) return Optional.of(inactiveWorker);
                        if (target.equals(nonWorker.getId())) return Optional.of(nonWorker);
                        return Optional.empty();
                    }
                    return null;
                }
        );

        // Dynamic Proxy for ComplaintRepository
        complaintRepository = (ComplaintRepository) Proxy.newProxyInstance(
                ComplaintRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        UUID target = (UUID) args[0];
                        if (target.equals(complaint1.getId())) return Optional.of(complaint1);
                        if (target.equals(complaint2.getId())) return Optional.of(complaint2);
                        if (target.equals(terminalComplaint.getId())) return Optional.of(terminalComplaint);
                        return Optional.empty();
                    }
                    if ("findByReferenceIdIgnoreCase".equals(method.getName())) {
                        String ref = (String) args[0];
                        if (complaint1.getReferenceId().equalsIgnoreCase(ref)) return Optional.of(complaint1);
                        if (complaint2.getReferenceId().equalsIgnoreCase(ref)) return Optional.of(complaint2);
                        if (terminalComplaint.getReferenceId().equalsIgnoreCase(ref)) return Optional.of(terminalComplaint);
                        return Optional.empty();
                    }
                    if ("save".equals(method.getName())) {
                        return args[0];
                    }
                    if ("flush".equals(method.getName())) {
                        return null;
                    }
                    return null;
                }
        );

        // Dynamic Proxy for AuditService
        auditService = new AuditService(null) {
            @Override
            public void logAction(UUID userId, String userName, String userRole, String action, String resourceType, String resourceId, String details, String ipAddress, String status) {
                auditLogged.set(true);
            }
        };

        complaintService = new ComplaintService(
                complaintRepository,
                null,
                null,
                null,
                userRepository,
                auditService,
                null
        );
    }

    @Test
    @DisplayName("Bulk Assign: Officer successfully dispatches multiple eligible complaints to active worker")
    void testBulkAssign_Success() {
        BulkAssignRequest req = new BulkAssignRequest(
                List.of(complaint1.getId().toString(), complaint2.getId().toString()),
                activeWorker.getId(),
                "Batch asphalt compaction order"
        );

        BulkAssignResponse resp = complaintService.bulkAssignWorkers(req, officerPrincipal, "127.0.0.1");

        assertNotNull(resp);
        assertEquals(2, resp.getTotalRequested());
        assertEquals(2, resp.getSuccessCount());
        assertEquals(0, resp.getFailureCount());
        assertEquals(2, resp.getResults().size());
        assertTrue(resp.getResults().get(0).isSuccess());
        assertTrue(resp.getResults().get(1).isSuccess());
        assertEquals(ComplaintStatus.ASSIGNED, complaint1.getStatus());
        assertEquals(ComplaintStatus.ASSIGNED, complaint2.getStatus());
        assertEquals(activeWorker, complaint1.getAssignedWorker());
        assertEquals(activeWorker, complaint2.getAssignedWorker());
        assertTrue(auditLogged.get());
    }

    @Test
    @DisplayName("Bulk Assign: Non-officer citizen receives 403 Forbidden")
    void testBulkAssign_ForbiddenForCitizen() {
        BulkAssignRequest req = new BulkAssignRequest(
                List.of(complaint1.getId().toString()),
                activeWorker.getId(),
                "Unauthorized citizen dispatch"
        );

        assertThrows(ForbiddenException.class, () ->
                complaintService.bulkAssignWorkers(req, citizenPrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Bulk Assign: Empty complaint list throws 400 Bad Request")
    void testBulkAssign_EmptyListThrowsBadRequest() {
        BulkAssignRequest req = new BulkAssignRequest(
                Collections.emptyList(),
                activeWorker.getId(),
                "Empty batch"
        );

        assertThrows(BadRequestException.class, () ->
                complaintService.bulkAssignWorkers(req, officerPrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Bulk Assign: Target user without WORKER role throws 400 Bad Request")
    void testBulkAssign_TargetNotWorkerThrowsBadRequest() {
        BulkAssignRequest req = new BulkAssignRequest(
                List.of(complaint1.getId().toString()),
                nonWorker.getId(),
                "Dispatch to non-worker"
        );

        assertThrows(BadRequestException.class, () ->
                complaintService.bulkAssignWorkers(req, officerPrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Bulk Assign: Suspended/inactive worker account throws 400 Bad Request")
    void testBulkAssign_InactiveWorkerThrowsBadRequest() {
        BulkAssignRequest req = new BulkAssignRequest(
                List.of(complaint1.getId().toString()),
                inactiveWorker.getId(),
                "Dispatch to suspended worker"
        );

        assertThrows(BadRequestException.class, () ->
                complaintService.bulkAssignWorkers(req, officerPrincipal, "127.0.0.1"));
    }

    @Test
    @DisplayName("Bulk Assign: Terminal complaint is recorded as individual item failure without failing the batch")
    void testBulkAssign_TerminalComplaintHandledGracefully() {
        BulkAssignRequest req = new BulkAssignRequest(
                List.of(complaint1.getId().toString(), terminalComplaint.getId().toString()),
                activeWorker.getId(),
                "Mixed batch with terminal item"
        );

        BulkAssignResponse resp = complaintService.bulkAssignWorkers(req, officerPrincipal, "127.0.0.1");

        assertNotNull(resp);
        assertEquals(2, resp.getTotalRequested());
        assertEquals(1, resp.getSuccessCount());
        assertEquals(1, resp.getFailureCount());
        assertTrue(resp.getResults().get(0).isSuccess());
        assertFalse(resp.getResults().get(1).isSuccess());
        assertTrue(resp.getResults().get(1).getMessage().contains("Cannot assign worker to terminal complaint"));
    }

    @Test
    @DisplayName("Bulk Assign: Cross-ward complaint without permission is recorded as item failure")
    void testBulkAssign_CrossWardAccessEnforced() {
        Complaint otherWardComplaint = new Complaint();
        otherWardComplaint.setId(UUID.randomUUID());
        otherWardComplaint.setReferenceId("CF-2026-99999");
        otherWardComplaint.setTitle("Pothole in Ward 15");
        otherWardComplaint.setWard("Ward 15, Adyar, Chennai");
        otherWardComplaint.setStatus(ComplaintStatus.SUBMITTED);

        // Add to repository lookup
        complaintRepository = (ComplaintRepository) Proxy.newProxyInstance(
                ComplaintRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        UUID target = (UUID) args[0];
                        if (target.equals(otherWardComplaint.getId())) return Optional.of(otherWardComplaint);
                        return Optional.empty();
                    }
                    return null;
                }
        );

        complaintService = new ComplaintService(
                complaintRepository, null, null, null, userRepository, auditService, null
        );

        BulkAssignRequest req = new BulkAssignRequest(
                List.of(otherWardComplaint.getId().toString()),
                activeWorker.getId(),
                "Cross-ward unauthorized dispatch"
        );

        BulkAssignResponse resp = complaintService.bulkAssignWorkers(req, officerPrincipal, "127.0.0.1");

        assertNotNull(resp);
        assertEquals(1, resp.getTotalRequested());
        assertEquals(0, resp.getSuccessCount());
        assertEquals(1, resp.getFailureCount());
        assertFalse(resp.getResults().get(0).isSuccess());
        assertTrue(resp.getResults().get(0).getMessage().contains("outside their assigned ward"));
    }
}
