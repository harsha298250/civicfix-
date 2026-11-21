package com.civicfix.citizen;

import com.civicfix.audit.AuditService;
import com.civicfix.citizen.dto.CitizenDataExportResponse;
import com.civicfix.citizen.dto.DeleteAccountRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.complaint.ComplaintStatus;
import com.civicfix.complaint.ComplaintStatusHistory;
import com.civicfix.complaint.ComplaintStatusHistoryRepository;
import com.civicfix.security.UserPrincipal;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CitizenAccountServiceTest {

    private UserRepository userRepository;
    private ComplaintRepository complaintRepository;
    private ComplaintStatusHistoryRepository historyRepository;
    private AuditService auditService;
    private PasswordEncoder passwordEncoder;
    private CitizenAccountService citizenAccountService;

    private User citizenUser;
    private UserPrincipal citizenPrincipal;
    private User officerUser;
    private UserPrincipal officerPrincipal;
    private Complaint testComplaint;

    private final AtomicBoolean auditLogged = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        auditLogged.set(false);

        UUID citizenId = UUID.randomUUID();
        citizenUser = new User();
        citizenUser.setId(citizenId);
        citizenUser.setFullName("Alex Morgan");
        citizenUser.setEmail("alex.m@citizen.org");
        citizenUser.setPhone("+91 98401 23456");
        citizenUser.setWard("Ward 08, Anna Nagar, Chennai");
        citizenUser.setPasswordHash("$2a$10$hashedPassword123");
        citizenUser.setAccountStatus("ACTIVE");
        citizenUser.setTrustScore(98);
        citizenUser.setRoles(Set.of(new Role(RoleName.CITIZEN)));
        citizenPrincipal = new UserPrincipal(citizenUser);

        UUID officerId = UUID.randomUUID();
        officerUser = new User();
        officerUser.setId(officerId);
        officerUser.setFullName("Elena Vance");
        officerUser.setEmail("elena.vance@metro.gov");
        officerUser.setRoles(Set.of(new Role(RoleName.OFFICER)));
        officerPrincipal = new UserPrincipal(officerUser);

        testComplaint = new Complaint();
        testComplaint.setId(UUID.randomUUID());
        testComplaint.setReferenceId("CF-2026-11223");
        testComplaint.setTitle("Large pothole at junction");
        testComplaint.setDescription("Hazardous roadway depression.");
        testComplaint.setCategory("Pothole & Road Hazard");
        testComplaint.setPriority(ComplaintPriority.HIGH);
        testComplaint.setStatus(ComplaintStatus.SUBMITTED);
        testComplaint.setAddress("4th Main Road, Anna Nagar");
        testComplaint.setWard("Ward 08, Anna Nagar, Chennai");
        testComplaint.setCitizen(citizenUser);
        testComplaint.setCreatedAt(OffsetDateTime.now().minusDays(3));

        ComplaintStatusHistory history1 = new ComplaintStatusHistory();
        history1.setComplaint(testComplaint);
        history1.setNewStatus("SUBMITTED");
        history1.setChangedByName("Alex Morgan");
        history1.setNotes("Citizen intake submission.");
        history1.setCreatedAt(OffsetDateTime.now().minusDays(3));
        testComplaint.getStatusHistory().add(history1);

        // Dynamic Proxy for UserRepository
        userRepository = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        UUID target = (UUID) args[0];
                        if (target.equals(citizenId)) return Optional.of(citizenUser);
                        if (target.equals(officerId)) return Optional.of(officerUser);
                        return Optional.empty();
                    }
                    if ("save".equals(method.getName())) {
                        return args[0];
                    }
                    return null;
                }
        );

        // Dynamic Proxy for ComplaintRepository
        complaintRepository = (ComplaintRepository) Proxy.newProxyInstance(
                ComplaintRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintRepository.class},
                (proxy, method, args) -> {
                    if ("findByCitizenIdOrderByCreatedAtDesc".equals(method.getName())) {
                        UUID target = (UUID) args[0];
                        if (target.equals(citizenId)) return List.of(testComplaint);
                        return Collections.emptyList();
                    }
                    return null;
                }
        );

        // Dynamic Proxy for ComplaintStatusHistoryRepository
        historyRepository = (ComplaintStatusHistoryRepository) Proxy.newProxyInstance(
                ComplaintStatusHistoryRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintStatusHistoryRepository.class},
                (proxy, method, args) -> {
                    if ("findByComplaintIdOrderByCreatedAtAsc".equals(method.getName())) {
                        return testComplaint.getStatusHistory();
                    }
                    return Collections.emptyList();
                }
        );

        // Dynamic Proxy for AuditService
        auditService = new AuditService(null) {
            @Override
            public void logAction(UUID userId, String userName, String userRole, String action, String resourceType, String resourceId, String details, String ipAddress, String status) {
                auditLogged.set(true);
            }
        };

        // Custom PasswordEncoder stub
        passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "$2a$10$hashedPassword123";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return "CivicSecure#2026!".equals(rawPassword.toString());
            }
        };

        citizenAccountService = new CitizenAccountService(
                userRepository,
                complaintRepository,
                historyRepository,
                auditService,
                passwordEncoder
        );
    }

    @Test
    @DisplayName("Export: Authenticated resident receives full JSON export of profile and complaints")
    void testExportCitizenData_Success() {
        CitizenDataExportResponse resp = citizenAccountService.exportCitizenData(citizenPrincipal);

        assertNotNull(resp);
        assertEquals(citizenUser.getId(), resp.getResidentId());
        assertEquals("Alex Morgan", resp.getFullName());
        assertEquals("alex.m@citizen.org", resp.getEmail());
        assertEquals("+91 98401 23456", resp.getPhone());
        assertEquals(1, resp.getTotalComplaintsFiled());
        assertEquals(1, resp.getComplaints().size());

        CitizenDataExportResponse.ExportedComplaint exportedComp = resp.getComplaints().get(0);
        assertEquals("CF-2026-11223", exportedComp.getReferenceId());
        assertEquals("Large pothole at junction", exportedComp.getTitle());
        assertEquals(1, exportedComp.getTimeline().size());
        assertEquals("SUBMITTED", exportedComp.getTimeline().get(0).getNewStatus());
    }

    @Test
    @DisplayName("Export: Unauthenticated call throws 403 Forbidden")
    void testExportCitizenData_UnauthenticatedForbidden() {
        assertThrows(ForbiddenException.class, () ->
                citizenAccountService.exportCitizenData(null));
    }

    @Test
    @DisplayName("Delete: Password verification succeeds, soft-anonymizes resident, writes audit and invalidates session")
    void testDeleteCitizenAccount_Success() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        DeleteAccountRequest req = new DeleteAccountRequest("CivicSecure#2026!");

        Map<String, Object> result = citizenAccountService.deleteCitizenAccount(req, citizenPrincipal, request, "127.0.0.1");

        assertNotNull(result);
        assertEquals("Deleted Resident", citizenUser.getFullName());
        assertTrue(citizenUser.getEmail().startsWith("deleted_"));
        assertTrue(citizenUser.getEmail().endsWith("@anonymized.civicfix.local"));
        assertNull(citizenUser.getPhone());
        assertEquals("DELETED", citizenUser.getAccountStatus());
        assertTrue(auditLogged.get(), "Tamper-evident audit log must be recorded");
        assertTrue(session.isInvalid(), "Active session must be invalidated upon deletion");
    }

    @Test
    @DisplayName("Delete: Incorrect password throws 400 Bad Request and aborts deletion")
    void testDeleteCitizenAccount_IncorrectPasswordThrowsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        DeleteAccountRequest req = new DeleteAccountRequest("WrongPassword123!");

        assertThrows(BadRequestException.class, () ->
                citizenAccountService.deleteCitizenAccount(req, citizenPrincipal, request, "127.0.0.1"));

        assertEquals("ACTIVE", citizenUser.getAccountStatus());
        assertEquals("Alex Morgan", citizenUser.getFullName());
        assertFalse(auditLogged.get());
    }

    @Test
    @DisplayName("Delete: Non-citizen accounts cannot be self-deleted via resident endpoint")
    void testDeleteCitizenAccount_NonCitizenForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        DeleteAccountRequest req = new DeleteAccountRequest("CivicSecure#2026!");

        assertThrows(ForbiddenException.class, () ->
                citizenAccountService.deleteCitizenAccount(req, officerPrincipal, request, "127.0.0.1"));
    }

    @Test
    @DisplayName("Delete: Already deleted account throws 400 Bad Request")
    void testDeleteCitizenAccount_AlreadyDeletedThrowsBadRequest() {
        citizenUser.setAccountStatus("DELETED");
        MockHttpServletRequest request = new MockHttpServletRequest();
        DeleteAccountRequest req = new DeleteAccountRequest("CivicSecure#2026!");

        assertThrows(BadRequestException.class, () ->
                citizenAccountService.deleteCitizenAccount(req, citizenPrincipal, request, "127.0.0.1"));
    }
}
