package com.civicfix.auth.firebase;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.FirebaseSessionRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ConflictException;
import com.civicfix.mail.EmailOutboxService;
import com.civicfix.security.ratelimit.ClientIpResolver;
import com.civicfix.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @Mock
    private FirebaseTokenVerifier tokenVerifier;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private EmailOutboxService emailOutboxService;

    private FirebaseAuthService authService;

    private Role citizenRole;
    private Role supervisorRole;

    @BeforeEach
    void setUp() {
        citizenRole = new Role(RoleName.CITIZEN);
        supervisorRole = new Role(RoleName.SUPERVISOR);

        authService = new FirebaseAuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                auditService,
                tokenVerifier,
                clientIpResolver,
                emailOutboxService
        );
    }

    @Test
    @DisplayName("1. Rejects account linking when Firebase email is unverified")
    void testRejectsLinkingWhenEmailUnverified() {
        String uid = "fb-uid-101";
        String email = "staff.unverified@civicfix.gov";

        User existingStaff = new User();
        existingStaff.setId(UUID.randomUUID());
        existingStaff.setEmail(email);
        existingStaff.setRoles(Set.of(supervisorRole));

        when(tokenVerifier.verifyIdToken("token-unverified"))
                .thenReturn(new FirebaseTokenClaims(uid, email, false, "Staff", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existingStaff));

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("token-unverified");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse()));

        assertTrue(ex.getMessage().contains("Firebase email is not verified"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("2. Rejects authentication when account is pending activation or disabled")
    void testRejectsAuthenticationWhenAccountNotActive() {
        String uid = "fb-uid-inactive";
        String email = "pending.staff@civicfix.gov";

        User pendingUser = new User();
        pendingUser.setId(UUID.randomUUID());
        pendingUser.setEmail(email);
        pendingUser.setAccountStatus("PENDING_ACTIVATION");
        pendingUser.setFirebaseUid(uid);
        pendingUser.setRoles(Set.of(supervisorRole));

        when(tokenVerifier.verifyIdToken("token-pending"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Staff", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.of(pendingUser));

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("token-pending");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse()));

        assertTrue(ex.getMessage().contains("pending activation or disabled"));
    }

    @Test
    @DisplayName("3. Links verified Firebase identity to existing staff account and preserves municipal roles")
    void testLinksVerifiedEmailToExistingStaffAndPreservesRoles() {
        String uid = "fb-uid-supervisor-777";
        String email = "supervisor.active@civicfix.gov";

        User supervisor = new User();
        supervisor.setId(UUID.randomUUID());
        supervisor.setFullName("Sarah Jenkins");
        supervisor.setEmail(email);
        supervisor.setAccountStatus("ACTIVE");
        supervisor.setEmailVerified(false);
        supervisor.setRoles(Set.of(supervisorRole));

        when(tokenVerifier.verifyIdToken("valid-token"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Sarah Jenkins", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(supervisor));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("valid-token");

        AuthUserResponse response = authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertNotNull(response);
        assertEquals("SUPERVISOR", response.getRole(), "Existing staff role must be preserved");
        assertEquals(uid, supervisor.getFirebaseUid());
        assertTrue(supervisor.isEmailVerified());
        verify(userRepository, times(1)).save(supervisor);
    }

    @Test
    @DisplayName("4. Provisions new Citizen account in Neon PostgreSQL with ROLE_CITIZEN only")
    void testProvisionsNewCitizenWithCitizenRoleOnly() {
        String uid = "fb-uid-new-citizen-888";
        String email = "new.citizen@civicfix.in";

        when(tokenVerifier.verifyIdToken("token-new"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "New Citizen", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.CITIZEN)).thenReturn(Optional.of(citizenRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedUnguessable");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("token-new");
        req.setName("New Citizen");
        req.setWard("Ward 12, T. Nagar");

        AuthUserResponse response = authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertNotNull(response);
        assertEquals("CITIZEN", response.getRole());
        assertEquals("Ward 12, T. Nagar", response.getWard(), "Requested ward must be persisted accurately");
        verify(emailOutboxService, times(1)).enqueueEmail(
                startsWith("welcome_"),
                eq(email),
                anyString(),
                eq("WELCOME"),
                anyMap()
        );
    }

    @Test
    @DisplayName("5. Rejects account linking when already bound to a different Firebase UID")
    void testRejectsLinkingWhenBoundToDifferentFirebaseUid() {
        String newUid = "fb-uid-intruder";
        String email = "citizen.bound@civicfix.in";

        User boundUser = new User();
        boundUser.setId(UUID.randomUUID());
        boundUser.setEmail(email);
        boundUser.setAccountStatus("ACTIVE");
        boundUser.setFirebaseUid("fb-uid-legitimate-owner");
        boundUser.setRoles(Set.of(citizenRole));

        when(tokenVerifier.verifyIdToken("token-intruder"))
                .thenReturn(new FirebaseTokenClaims(newUid, email, true, "Intruder", Map.of()));
        when(userRepository.findByFirebaseUid(newUid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(boundUser));

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("token-intruder");

        assertThrows(ConflictException.class, () ->
                authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("6. Provisions Royapuram resident location accurately without Anna Nagar fallback")
    void testProvisionsRoyapuramResidentLocationAccurately() {
        String uid = "fb-uid-royapuram-resident-999";
        String email = "resident.royapuram@civicfix.in";

        when(tokenVerifier.verifyIdToken("token-royapuram"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Royapuram Resident", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.CITIZEN)).thenReturn(Optional.of(citizenRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedUnguessable");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("token-royapuram");
        req.setName("Royapuram Resident");
        req.setWard("Zone 05, Royapuram, Chennai");

        AuthUserResponse response = authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertNotNull(response);
        assertEquals("CITIZEN", response.getRole());
        assertEquals("Zone 05, Royapuram, Chennai", response.getWard());
        assertNotEquals("Ward 08, Anna Nagar, Chennai", response.getWard());
    }

    @Test
    @DisplayName("7. Preserves null ward when resident has not selected a location (no silent fallback)")
    void testPreservesNullWardWhenLocationMissing() {
        String uid = "fb-uid-no-ward-1000";
        String email = "resident.noward@civicfix.in";

        when(tokenVerifier.verifyIdToken("token-noward"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "No Ward Resident", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.CITIZEN)).thenReturn(Optional.of(citizenRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedUnguessable");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("token-noward");
        req.setName("No Ward Resident");
        req.setWard("   ");

        AuthUserResponse response = authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertNotNull(response);
        assertEquals("CITIZEN", response.getRole());
        assertNull(response.getWard(), "Missing location must remain null without silent ward assignment");
    }

    @Test
    @DisplayName("8. Rejects Firebase UID login when email is not verified")
    void testRejectsUidLoginWhenEmailNotVerified() {
        String uid = "fb-uid-unverified-login";
        String email = "unverified.resident@civicfix.in";

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setAccountStatus("ACTIVE");
        user.setFirebaseUid(uid);
        user.setRoles(Set.of(citizenRole));

        when(tokenVerifier.verifyIdToken("token-unverified-uid"))
                .thenReturn(new FirebaseTokenClaims(uid, email, false, "Resident", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.of(user));

        FirebaseSessionRequest req = new FirebaseSessionRequest();
        req.setIdToken("token-unverified-uid");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.authenticateFirebaseUser(req, new MockHttpServletRequest(), new MockHttpServletResponse()));

        assertTrue(ex.getMessage().contains("not verified"));
    }
}
