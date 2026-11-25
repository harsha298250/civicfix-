package com.civicfix.auth.firebase;

import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.mail.EmailOutboxService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import com.civicfix.user.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseAuthenticationFilterTest {

    @Mock
    private FirebaseTokenVerifier tokenVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private AuditService auditService;

    @Mock
    private EmailOutboxService emailOutboxService;

    private FirebaseAuthenticationFilter filter;

    private Role citizenRole;
    private Role officerRole;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        citizenRole = new Role(RoleName.CITIZEN);
        officerRole = new Role(RoleName.OFFICER);

        filter = new FirebaseAuthenticationFilter(
                tokenVerifier,
                userRepository,
                roleRepository,
                passwordEncoder,
                clientIpResolver,
                auditService,
                emailOutboxService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("1. Valid Bearer Token authenticates existing user mapped by Firebase UID")
    void testValidBearerTokenExistingUser() throws Exception {
        String uid = "uid-filter-12345";
        String email = "existing.citizen@civicfix.in";

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Existing Citizen");
        user.setEmail(email);
        user.setWard("Ward 08, Chennai");
        user.setAccountStatus("ACTIVE");
        user.setEmailVerified(true);
        user.setFirebaseUid(uid);
        user.setRoles(Set.of(citizenRole));

        when(tokenVerifier.verifyIdToken("valid-token"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Existing Citizen", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set in SecurityContext");
        assertTrue(auth.getPrincipal() instanceof UserPrincipal);
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals(email, principal.getEmail());
        assertTrue(principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CITIZEN")));
    }

    @Test
    @DisplayName("2. Valid Bearer Token auto-provisions new CITIZEN account with CITIZEN role only")
    void testValidBearerTokenAutoProvisionNewCitizen() throws Exception {
        String uid = "uid-auto-citizen-67890";
        String email = "auto.new.citizen@civicfix.in";

        when(tokenVerifier.verifyIdToken("auto-token"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Auto Citizen", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.CITIZEN)).thenReturn(Optional.of(citizenRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedPasswordHashMock");
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer auto-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set in SecurityContext");
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals(email, principal.getEmail());
        assertTrue(principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CITIZEN")));

        // Verify Welcome Email enqueued
        verify(emailOutboxService, times(1)).enqueueEmail(
                startsWith("welcome_"),
                eq(email),
                anyString(),
                eq("WELCOME"),
                anyMap()
        );

        // Verify audit log recorded
        verify(auditService, times(1)).logAction(
                any(UUID.class),
                eq("Auto Citizen"),
                eq("CITIZEN"),
                eq("AUTH_FIREBASE_FILTER_REGISTER"),
                eq("USER"),
                anyString(),
                anyString(),
                eq("127.0.0.1"),
                eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("3. Valid Bearer Token safely links existing staff account and preserves officer role")
    void testValidBearerTokenLinkExistingStaffPreservesRole() throws Exception {
        String uid = "uid-officer-99999";
        String email = "officer.link@civicfix.gov";

        User officer = new User();
        officer.setId(UUID.randomUUID());
        officer.setFullName("Officer Link Test");
        officer.setEmail(email);
        officer.setWard("Ward 04, Anna Nagar");
        officer.setDepartment("Roads & Traffic");
        officer.setAccountStatus("ACTIVE");
        officer.setEmailVerified(true);
        officer.setRoles(Set.of(officerRole));

        when(tokenVerifier.verifyIdToken("officer-token"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Officer Link Test", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(officer));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer officer-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals(email, principal.getEmail());
        assertTrue(principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER")));
        assertFalse(principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CITIZEN")));

        assertEquals(uid, officer.getFirebaseUid());
        verify(userRepository, times(1)).save(officer);
    }

    @Test
    @DisplayName("4. Unverified Firebase email Bearer token rejects linking existing account")
    void testUnverifiedFirebaseEmailBearerTokenRejectsAccountLinking() throws Exception {
        String uid = "uid-unverified-11111";
        String email = "staff.unverified@civicfix.gov";

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setFullName("Staff Unverified");
        staff.setEmail(email);
        staff.setAccountStatus("ACTIVE");
        staff.setRoles(Set.of(officerRole));

        // Firebase reports emailVerified = false!
        when(tokenVerifier.verifyIdToken("unverified-token"))
                .thenReturn(new FirebaseTokenClaims(uid, email, false, "Staff Unverified", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(staff));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer unverified-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Unverified Firebase email must NOT authenticate existing staff account");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. Invalid or expired Bearer Token leaves SecurityContext empty without breaking filter chain")
    void testInvalidBearerTokenLeavesSecurityContextEmpty() throws Exception {
        when(tokenVerifier.verifyIdToken("expired-token"))
                .thenThrow(new UnauthorizedException("Firebase ID token has expired."));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Expired token must leave SecurityContext unauthenticated");
    }

    @Test
    @DisplayName("6. Request without Bearer token allows unauthenticated request without error")
    void testRequestWithoutBearerTokenPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No token should leave SecurityContext unauthenticated");
        verifyNoInteractions(tokenVerifier);
    }

    @Test
    @DisplayName("7. Inactive or disabled account status leaves SecurityContext unauthenticated")
    void testInactiveUserAccountStatusLeavesSecurityContextEmpty() throws Exception {
        String uid = "uid-disabled-999";
        String email = "disabled.user@civicfix.in";

        User disabledUser = new User();
        disabledUser.setId(UUID.randomUUID());
        disabledUser.setFullName("Disabled Citizen");
        disabledUser.setEmail(email);
        disabledUser.setAccountStatus("SUSPENDED");
        disabledUser.setEmailVerified(true);
        disabledUser.setFirebaseUid(uid);
        disabledUser.setRoles(Set.of(citizenRole));

        when(tokenVerifier.verifyIdToken("disabled-user-token"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Disabled Citizen", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.of(disabledUser));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer disabled-user-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Suspended/inactive user must NOT be authenticated in SecurityContext");
    }

    @Test
    @DisplayName("8. Syncs emailVerified flag to true when Firebase token reports email is verified")
    void testSyncEmailVerifiedWhenTokenIsVerified() throws Exception {
        String uid = "uid-unverified-to-verified";
        String email = "verify.sync@civicfix.in";

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Sync User");
        user.setEmail(email);
        user.setAccountStatus("ACTIVE");
        user.setEmailVerified(false); // Initially false in database
        user.setFirebaseUid(uid);
        user.setRoles(Set.of(citizenRole));

        when(tokenVerifier.verifyIdToken("verified-token"))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Sync User", Map.of()));
        when(userRepository.findByFirebaseUid(uid)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer verified-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(user.isEmailVerified(), "User emailVerified should be updated to true in Neon");
        verify(userRepository, times(1)).save(user);
    }
}
