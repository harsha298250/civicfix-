package com.civicfix.auth;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.GoogleAuthRequest;
import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.auth.dto.RegisterCitizenRequest;
import com.civicfix.auth.dto.ResendVerificationRequest;
import com.civicfix.auth.dto.VerifyEmailRequest;
import com.civicfix.auth.google.GoogleTokenClaims;
import com.civicfix.auth.google.GoogleTokenVerifier;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.mail.EmailOutboxService;
import com.civicfix.mail.EmailService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import com.civicfix.security.ratelimit.LoginLockoutService;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.RoleRepository;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Production Readiness Automated Test Suite for Authentication, Google OAuth,
 * Remember Me, and Email Verification Lifecycle.
 */
class ProductionAuthAndEmailSecurityTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private AuditService auditService;
    private LoginLockoutService loginLockoutService;
    private ClientIpResolver clientIpResolver;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private EmailService emailService;
    private EmailOutboxService emailOutboxService;
    private SessionRegistry sessionRegistry;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private GoogleTokenVerifier googleTokenVerifier;

    private HttpServletRequest httpRequest;
    private HttpServletResponse httpResponse;
    private HttpSession httpSession;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        auditService = mock(AuditService.class);
        loginLockoutService = mock(LoginLockoutService.class);
        clientIpResolver = mock(ClientIpResolver.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        emailService = mock(EmailService.class);
        emailOutboxService = mock(EmailOutboxService.class);
        sessionRegistry = mock(SessionRegistry.class);
        emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);

        httpRequest = mock(HttpServletRequest.class);
        httpResponse = mock(HttpServletResponse.class);
        httpSession = mock(HttpSession.class);

        when(httpRequest.getSession()).thenReturn(httpSession);
        when(httpRequest.getSession(anyBoolean())).thenReturn(httpSession);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed_" + inv.getArgument(0));

        Role citizenRole = new Role(RoleName.CITIZEN);
        citizenRole.setId(1);
        when(roleRepository.findByName(RoleName.CITIZEN)).thenReturn(Optional.of(citizenRole));

        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                authenticationManager,
                auditService,
                loginLockoutService,
                clientIpResolver,
                passwordResetTokenRepository,
                emailService,
                emailOutboxService,
                sessionRegistry,
                emailVerificationTokenRepository,
                googleTokenVerifier
        );
    }

    @Test
    @DisplayName("1. Citizen registration creates account in PENDING_VERIFICATION state and dispatches verification token")
    void testCitizenRegistrationCreatesPendingAccountWithToken() {
        RegisterCitizenRequest request = new RegisterCitizenRequest();
        request.setName("Kavitha Raman");
        request.setEmail("kavitha@example.com");
        request.setPassword("StrongSecure#2026!");
        request.setWard("Ward 08, Anna Nagar, Chennai");

        when(userRepository.existsByEmailIgnoreCase("kavitha@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthUserResponse response = authService.registerCitizen(request, httpRequest, httpResponse);

        assertNotNull(response);
        assertEquals("PENDING_VERIFICATION", response.getAccountStatus(), "Registration must be PENDING_VERIFICATION");
        assertFalse(response.isEmailVerified(), "Email must not be marked verified yet");

        // Verify token was generated and persisted
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));

        // Verify verification email was enqueued in outbox
        verify(emailOutboxService).enqueueEmail(
                startsWith("verify_"),
                eq("kavitha@example.com"),
                eq("Verify Your CivicFix Account"),
                eq("EMAIL_VERIFICATION"),
                anyMap()
        );
    }

    @Test
    @DisplayName("2. Email verification activates account and consumes single-use token")
    void testVerifyEmailActivatesAccount() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("citizen@example.com");
        user.setAccountStatus("PENDING_VERIFICATION");
        user.setEmailVerified(false);

        EmailVerificationToken token = new EmailVerificationToken(user, "valid_hash", OffsetDateTime.now().plusHours(24), "127.0.0.1");

        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        VerifyEmailRequest request = new VerifyEmailRequest("raw_token_xyz");
        String message = authService.verifyEmail(request, "127.0.0.1");

        assertTrue(message.contains("successfully verified"));
        assertEquals("ACTIVE", user.getAccountStatus(), "Account status must become ACTIVE");
        assertTrue(user.isEmailVerified(), "User emailVerified must become true");
        assertTrue(token.isConsumed(), "Token must be marked consumed");
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    @DisplayName("3. Email verification rejects expired and already consumed tokens")
    void testVerifyEmailRejectsExpiredOrConsumedToken() {
        User user = new User();
        user.setId(UUID.randomUUID());

        // Expired token
        EmailVerificationToken expiredToken = new EmailVerificationToken(user, "hash", OffsetDateTime.now().minusHours(1), "127.0.0.1");
        when(emailVerificationTokenRepository.findByTokenHash("expired_hash")).thenReturn(Optional.of(expiredToken));

        VerifyEmailRequest expiredReq = new VerifyEmailRequest("expired_token");
        // Using anyString() for hash token lookup
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThrows(BadRequestException.class, () -> authService.verifyEmail(expiredReq, "127.0.0.1"));

        // Already consumed token
        EmailVerificationToken consumedToken = new EmailVerificationToken(user, "hash", OffsetDateTime.now().plusHours(10), "127.0.0.1");
        consumedToken.setConsumedAt(OffsetDateTime.now().minusMinutes(5));
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(consumedToken));

        VerifyEmailRequest consumedReq = new VerifyEmailRequest("consumed_token");
        assertThrows(BadRequestException.class, () -> authService.verifyEmail(consumedReq, "127.0.0.1"));
    }

    @Test
    @DisplayName("4. Resend verification email enforces rate limiting (cooldown window)")
    void testResendVerificationRateLimiting() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("pending@example.com");
        user.setAccountStatus("PENDING_VERIFICATION");

        when(userRepository.findByEmailIgnoreCase("pending@example.com")).thenReturn(Optional.of(user));
        // Simulate recent token sent within 5 minutes
        when(emailVerificationTokenRepository.countRecentTokensForUser(eq(user), any(OffsetDateTime.class))).thenReturn(1L);

        ResendVerificationRequest request = new ResendVerificationRequest("pending@example.com");
        String result = authService.resendVerification(request, "127.0.0.1");

        // Returns generic message without error and does NOT send email
        assertNotNull(result);
        verify(emailOutboxService, never()).enqueueEmail(startsWith("verify_resend_"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("5. Remember Me checkbox sets 30-day session timeout vs 1-hour standard session")
    void testRememberMeSessionTimeout() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("active@civicfix.gov");
        user.setFullName("Priya Sharma");
        user.setPasswordHash("hashed_pw");
        user.setAccountStatus("ACTIVE");
        user.setRoles(Set.of(new Role(RoleName.CITIZEN)));

        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        // Test 1: rememberMe = true -> 30 days (2,592,000s)
        LoginRequest reqRemember = new LoginRequest("active@civicfix.gov", "password", true);
        authService.login(reqRemember, httpRequest, httpResponse);
        verify(httpSession).setMaxInactiveInterval(30 * 24 * 3600);

        // Test 2: rememberMe = false -> 1 hour (3600s)
        LoginRequest reqStandard = new LoginRequest("active@civicfix.gov", "password", false);
        authService.login(reqStandard, httpRequest, httpResponse);
        verify(httpSession).setMaxInactiveInterval(3600);
    }

    @Test
    @DisplayName("6. Google OAuth creates new citizen account with strictly ROLE_CITIZEN")
    void testGoogleOAuthCreatesCitizenOnly() {
        when(googleTokenVerifier.verify("valid_google_jwt")).thenReturn(
                new GoogleTokenClaims("sub-123", "newcitizen@gmail.com", true, "New Google Citizen", "https://pic")
        );
        when(userRepository.findByEmailIgnoreCase("newcitizen@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        GoogleAuthRequest request = new GoogleAuthRequest("valid_google_jwt", false);
        AuthUserResponse response = authService.loginWithGoogle(request, httpRequest, httpResponse);

        assertNotNull(response);
        assertEquals("newcitizen@gmail.com", response.getEmail());
        assertEquals("CITIZEN", response.getRole(), "New Google OAuth account must strictly receive CITIZEN role");
        assertTrue(response.isEmailVerified());
        assertEquals("ACTIVE", response.getAccountStatus());
    }

    @Test
    @DisplayName("7. Google OAuth preserves existing staff permissions without privilege escalation")
    void testGoogleOAuthPreservesExistingStaffRole() {
        Role officerRole = new Role(RoleName.OFFICER);
        officerRole.setId(2);

        User existingOfficer = new User();
        existingOfficer.setId(UUID.randomUUID());
        existingOfficer.setEmail("officer@civicfix.gov");
        existingOfficer.setFullName("Elena Vance");
        existingOfficer.setAccountStatus("ACTIVE");
        existingOfficer.setRoles(Set.of(officerRole));

        when(googleTokenVerifier.verify("officer_google_jwt")).thenReturn(
                new GoogleTokenClaims("sub-456", "officer@civicfix.gov", true, "Elena Vance", null)
        );
        when(userRepository.findByEmailIgnoreCase("officer@civicfix.gov")).thenReturn(Optional.of(existingOfficer));
        when(userRepository.save(any(User.class))).thenReturn(existingOfficer);

        GoogleAuthRequest request = new GoogleAuthRequest("officer_google_jwt", true);
        AuthUserResponse response = authService.loginWithGoogle(request, httpRequest, httpResponse);

        assertEquals("OFFICER", response.getRole(), "Existing staff officer must preserve OFFICER role");
        verify(httpSession).setMaxInactiveInterval(30 * 24 * 3600); // Remember Me honored on Google login
    }

    @Test
    @DisplayName("8. Google OAuth rejects invalid or unverified Google token")
    void testGoogleOAuthRejectsInvalidToken() {
        when(googleTokenVerifier.verify("invalid_jwt")).thenThrow(new UnauthorizedException("Invalid Google token signature."));

        GoogleAuthRequest request = new GoogleAuthRequest("invalid_jwt", false);
        assertThrows(UnauthorizedException.class, () -> authService.loginWithGoogle(request, httpRequest, httpResponse));
    }
}
