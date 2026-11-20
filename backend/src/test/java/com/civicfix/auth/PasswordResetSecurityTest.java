package com.civicfix.auth;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.ForgotPasswordRequest;
import com.civicfix.auth.dto.ResetPasswordRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.mail.EmailService;
import com.civicfix.security.ratelimit.ClientIpResolver;
import com.civicfix.security.ratelimit.LoginLockoutService;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.RoleRepository;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetSecurityTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private AuditService auditService;
    private LoginLockoutService loginLockoutService;
    private ClientIpResolver clientIpResolver;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private EmailService emailService;

    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(10);
        authenticationManager = mock(AuthenticationManager.class);
        auditService = mock(AuditService.class);
        loginLockoutService = mock(LoginLockoutService.class);
        clientIpResolver = mock(ClientIpResolver.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        emailService = mock(EmailService.class);

        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                authenticationManager,
                auditService,
                loginLockoutService,
                clientIpResolver,
                passwordResetTokenRepository,
                emailService
        );
        authService.setPasswordResetValidityMinutes(15);
        authService.setAppBaseUrl("http://localhost:5173");

        // Forward findByEmailIgnoreCaseWithLock to findByEmailIgnoreCase by default
        when(userRepository.findByEmailIgnoreCaseWithLock(any()))
                .thenAnswer(inv -> userRepository.findByEmailIgnoreCase(inv.getArgument(0)));

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("alex.m@citizen.org");
        testUser.setFullName("Alex Morgan");
        testUser.setPasswordHash(passwordEncoder.encode("OldPassword#123"));
    }

    private String sha256Hex(String raw) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("Request reset for registered email: Generates SHA-256 hashed token, single-use, 15m expiry, dispatches email")
    void requestPasswordReset_ValidEmail_Success() {
        when(userRepository.findByEmailIgnoreCase("alex.m@citizen.org")).thenReturn(Optional.of(testUser));

        ForgotPasswordRequest req = new ForgotPasswordRequest("alex.m@citizen.org");
        String message = authService.requestPasswordReset(req, "192.168.1.50");

        assertEquals("If an account with that email exists, password reset instructions have been sent.", message);

        // Verify existing tokens for user are invalidated
        verify(passwordResetTokenRepository, times(1)).invalidateOutstandingTokensForUser(eq(testUser), any(OffsetDateTime.class));

        // Capture saved token
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository, times(1)).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertNotNull(savedToken);
        assertEquals(testUser, savedToken.getUser());
        assertEquals("192.168.1.50", savedToken.getCreatedByIp());
        assertNotNull(savedToken.getTokenHash());
        assertEquals(64, savedToken.getTokenHash().length(), "Token hash must be SHA-256 hex (64 chars)");
        assertNull(savedToken.getConsumedAt(), "Token must start unconsumed");
        assertTrue(savedToken.getExpiresAt().isAfter(OffsetDateTime.now().plusMinutes(14)), "Must expire in ~15 mins");

        // Verify email was sent with link
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendPasswordResetEmail(eq("alex.m@citizen.org"), linkCaptor.capture());

        String resetLink = linkCaptor.getValue();
        assertTrue(resetLink.startsWith("http://localhost:5173/reset-password?token="));
        String rawTokenInLink = resetLink.substring("http://localhost:5173/reset-password?token=".length());
        assertNotEquals(savedToken.getTokenHash(), rawTokenInLink, "Raw token in email must NEVER equal database hash");
    }

    @Test
    @DisplayName("Request reset for non-registered email: Returns identical generic response without enumeration")
    void requestPasswordReset_NonExistentEmail_PreventsEnumeration() {
        when(userRepository.findByEmailIgnoreCase("unknown@nowhere.org")).thenReturn(Optional.empty());

        ForgotPasswordRequest req = new ForgotPasswordRequest("unknown@nowhere.org");
        String message = authService.requestPasswordReset(req, "192.168.1.50");

        // Exactly the same message returned
        assertEquals("If an account with that email exists, password reset instructions have been sent.", message);

        // Verify zero tokens saved and zero emails sent
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    @DisplayName("Reset password with valid token: Successfully updates password hash and consumes token")
    void resetPassword_ValidToken_Success() throws Exception {
        String rawToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken(testUser, tokenHash, OffsetDateTime.now().plusMinutes(10), "192.168.1.50");
        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordResetTokenRepository.consumeTokenAtomic(eq(tokenHash), any(OffsetDateTime.class))).thenReturn(1);

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "BrandNewSecurePass#2026!");
        String result = authService.resetPassword(req);

        assertTrue(result.contains("Password has been reset successfully"));
        assertTrue(token.isConsumed(), "Token must be marked consumed");
        assertNotNull(token.getConsumedAt());

        // Verify password hash updated
        verify(userRepository, times(1)).save(testUser);
        assertTrue(passwordEncoder.matches("BrandNewSecurePass#2026!", testUser.getPasswordHash()));

        // Verify remaining tokens for user invalidated
        verify(passwordResetTokenRepository, times(1)).invalidateOutstandingTokensForUser(eq(testUser), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Reset password with expired token: Throws BadRequestException")
    void resetPassword_ExpiredToken_ThrowsBadRequest() throws Exception {
        String rawToken = "expired_raw_token_test_1234567890";
        String tokenHash = sha256Hex(rawToken);

        // Expired 5 minutes ago
        PasswordResetToken token = new PasswordResetToken(testUser, tokenHash, OffsetDateTime.now().minusMinutes(5), "192.168.1.50");
        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewSecurePassword#2026!");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resetPassword(req));
        assertTrue(ex.getMessage().contains("expired"));

        // Password not updated
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with already consumed token (replay attack): Throws BadRequestException")
    void resetPassword_AlreadyConsumedToken_ThrowsBadRequest() throws Exception {
        String rawToken = "already_consumed_token_test_12345";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken(testUser, tokenHash, OffsetDateTime.now().plusMinutes(10), "192.168.1.50");
        token.setConsumedAt(OffsetDateTime.now().minusMinutes(2)); // Already used
        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewSecurePassword#2026!");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resetPassword(req));
        assertTrue(ex.getMessage().contains("already been used"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with weak password (< 8 chars): Throws BadRequestException")
    void resetPassword_WeakPassword_ThrowsBadRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest("any_token", "short");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resetPassword(req));
        assertTrue(ex.getMessage().contains("at least 8 characters"));
    }

    @Test
    @DisplayName("Reset password with unknown/tampered token: Throws BadRequestException")
    void resetPassword_UnknownToken_ThrowsBadRequest() throws Exception {
        String rawToken = "tampered_token_xyz_987654321";
        String tokenHash = sha256Hex(rawToken);

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "ValidPassword#2026!");
        assertThrows(BadRequestException.class, () -> authService.resetPassword(req));
    }

    @Test
    @DisplayName("Request reset for suspended/inactive user: Suppresses token generation and returns identical generic response")
    void requestPasswordReset_SuspendedUser_SuppressesTokenGeneration() {
        testUser.setAccountStatus("SUSPENDED");
        when(userRepository.findByEmailIgnoreCase("alex.m@citizen.org")).thenReturn(Optional.of(testUser));

        ForgotPasswordRequest req = new ForgotPasswordRequest("alex.m@citizen.org");
        String message = authService.requestPasswordReset(req, "192.168.1.50");

        assertEquals("If an account with that email exists, password reset instructions have been sent.", message);
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    @DisplayName("Concurrent redemption race protection: If atomic token consumption returns 0 rows, fails closed")
    void resetPassword_ConcurrentRedemptionRace_FailsClosed() throws Exception {
        String rawToken = "concurrent_race_token_12345";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken(testUser, tokenHash, OffsetDateTime.now().plusMinutes(10), "192.168.1.50");
        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        // Simulate concurrent redemption: another transaction updated the token first
        when(passwordResetTokenRepository.consumeTokenAtomic(eq(tokenHash), any(OffsetDateTime.class))).thenReturn(0);

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "SecurePassword#2026!");
        assertThrows(BadRequestException.class, () -> authService.resetPassword(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Account Rate Limiting: Repeated reset requests within cooldown are throttled while returning uniform message")
    void requestPasswordReset_AccountRateLimit_CooldownBlocksRepeatedDispatch() {
        when(userRepository.findByEmailIgnoreCase("alex.m@citizen.org")).thenReturn(Optional.of(testUser));
        // Simulate that 1 token was already issued within the last 5 minutes
        when(passwordResetTokenRepository.countRecentTokensForUser(eq(testUser), any(OffsetDateTime.class))).thenReturn(1L);

        ForgotPasswordRequest req = new ForgotPasswordRequest("alex.m@citizen.org");
        String message = authService.requestPasswordReset(req, "192.168.1.100");

        assertEquals("If an account with that email exists, password reset instructions have been sent.", message);
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Account Rate Limiting: Different IP addresses cannot bypass account-level cooldown")
    void requestPasswordReset_DifferentIpsCannotBypassAccountCooldown() {
        when(userRepository.findByEmailIgnoreCase("alex.m@citizen.org")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.countRecentTokensForUser(eq(testUser), any(OffsetDateTime.class))).thenReturn(1L);

        // Request from different attacker IP addresses targeting the same victim
        authService.requestPasswordReset(new ForgotPasswordRequest("alex.m@citizen.org"), "10.0.0.1");
        authService.requestPasswordReset(new ForgotPasswordRequest("alex.m@citizen.org"), "10.0.0.2");
        authService.requestPasswordReset(new ForgotPasswordRequest("alex.m@citizen.org"), "10.0.0.3");

        verify(emailService, never()).sendPasswordResetEmail(any(), any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Account Rate Limiting: Independent accounts have separate rate limits")
    void requestPasswordReset_DifferentAccountsIndependentlyLimited() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("priya.s@metro.gov");
        otherUser.setFullName("Priya Sharma");

        when(userRepository.findByEmailIgnoreCase("alex.m@citizen.org")).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmailIgnoreCase("priya.s@metro.gov")).thenReturn(Optional.of(otherUser));

        // alex.m is throttled (count = 1)
        when(passwordResetTokenRepository.countRecentTokensForUser(eq(testUser), any(OffsetDateTime.class))).thenReturn(1L);
        // priya.s is not throttled (count = 0)
        when(passwordResetTokenRepository.countRecentTokensForUser(eq(otherUser), any(OffsetDateTime.class))).thenReturn(0L);

        authService.requestPasswordReset(new ForgotPasswordRequest("alex.m@citizen.org"), "192.168.1.50");
        authService.requestPasswordReset(new ForgotPasswordRequest("priya.s@metro.gov"), "192.168.1.50");

        // Verify only priya.s got an email
        verify(emailService, times(1)).sendPasswordResetEmail(eq("priya.s@metro.gov"), any());
        verify(emailService, never()).sendPasswordResetEmail(eq("alex.m@citizen.org"), any());
    }

    @Test
    @DisplayName("Session Invalidation: Successful password reset invalidates all active sessions for the user")
    void resetPassword_InvalidatesActiveUserSessions() throws Exception {
        org.springframework.security.core.session.SessionRegistry mockRegistry = mock(org.springframework.security.core.session.SessionRegistry.class);
        authService.setSessionRegistry(mockRegistry);

        String rawToken = "session_invalidation_token_12345";
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken token = new PasswordResetToken(testUser, tokenHash, OffsetDateTime.now().plusMinutes(10), "192.168.1.50");
        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordResetTokenRepository.consumeTokenAtomic(eq(tokenHash), any(OffsetDateTime.class))).thenReturn(1);

        // Setup session mocks: testUser has 2 active sessions, otherUser has 1 active session
        com.civicfix.security.UserPrincipal testPrincipal = new com.civicfix.security.UserPrincipal(testUser);

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@metro.gov");
        com.civicfix.security.UserPrincipal otherPrincipal = new com.civicfix.security.UserPrincipal(otherUser);

        when(mockRegistry.getAllPrincipals()).thenReturn(java.util.List.of(testPrincipal, otherPrincipal));

        org.springframework.security.core.session.SessionInformation session1 =
                new org.springframework.security.core.session.SessionInformation(testPrincipal, "sess-1", new java.util.Date());
        org.springframework.security.core.session.SessionInformation session2 =
                new org.springframework.security.core.session.SessionInformation(testPrincipal, "sess-2", new java.util.Date());
        org.springframework.security.core.session.SessionInformation otherSession =
                new org.springframework.security.core.session.SessionInformation(otherPrincipal, "sess-other", new java.util.Date());

        when(mockRegistry.getAllSessions(eq(testPrincipal), eq(false))).thenReturn(java.util.List.of(session1, session2));
        when(mockRegistry.getAllSessions(eq(otherPrincipal), eq(false))).thenReturn(java.util.List.of(otherSession));

        ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewSecurePass#2026!");
        String result = authService.resetPassword(req);

        assertTrue(result.contains("successfully"));
        assertTrue(session1.isExpired(), "Session 1 of target user must be expired");
        assertTrue(session2.isExpired(), "Session 2 of target user must be expired");
        assertFalse(otherSession.isExpired(), "Session of other user must NOT be expired");
    }
}
