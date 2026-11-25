package com.civicfix.auth;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.ActivateStaffRequest;
import com.civicfix.auth.dto.VerifyActivationTokenResponse;
import com.civicfix.auth.firebase.FirebaseTokenClaims;
import com.civicfix.auth.firebase.FirebaseTokenVerifier;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.mail.EmailOutboxService;
import com.civicfix.mail.EmailService;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffActivationServiceTest {

    @Mock
    private StaffActivationTokenRepository staffActivationTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @Mock
    private EmailOutboxService emailOutboxService;

    @Mock
    private EmailService emailService;

    @Mock
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private StaffActivationService staffActivationService;

    private Role officerRole;

    @BeforeEach
    void setUp() {
        officerRole = new Role(RoleName.OFFICER);
        staffActivationService = new StaffActivationService(
                staffActivationTokenRepository,
                userRepository,
                passwordEncoder,
                auditService,
                emailOutboxService,
                emailService,
                firebaseTokenVerifier
        );
    }

    @Test
    @DisplayName("1. Successfully activates staff, links Firebase UID, sets ACTIVE, and preserves municipal officer role")
    void testSuccessfulStaffActivationWithFirebaseLinking() {
        String rawToken = "raw-token-1234567890abcdef1234567890abcdef";
        String staffEmail = "elena.vance@civicfix.gov";
        String fbUid = "fb-uid-officer-vance";

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setFullName("Elena Vance");
        staff.setEmail(staffEmail);
        staff.setAccountStatus("PENDING_ACTIVATION");
        staff.setEmailVerified(false);
        staff.setRoles(Set.of(officerRole));

        StaffActivationToken token = new StaffActivationToken(
                staff,
                "token-hash",
                OffsetDateTime.now().plusDays(2),
                null
        );

        when(staffActivationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(staffActivationTokenRepository.consumeTokenAtomic(anyString(), any())).thenReturn(1);
        when(firebaseTokenVerifier.verifyIdToken("valid-id-token"))
                .thenReturn(new FirebaseTokenClaims(fbUid, staffEmail, true, "Elena Vance", Map.of()));
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$10$hashed");

        ActivateStaffRequest req = new ActivateStaffRequest(rawToken, "SecurePass123!", "valid-id-token");
        staffActivationService.activateStaff(req, "127.0.0.1");

        assertEquals("ACTIVE", staff.getAccountStatus());
        assertEquals(fbUid, staff.getFirebaseUid());
        assertTrue(staff.isEmailVerified());
        assertTrue(staff.hasRole(RoleName.OFFICER), "Officer role must be preserved");

        verify(userRepository, times(1)).save(staff);
        verify(staffActivationTokenRepository, times(1)).consumeTokenAtomic(anyString(), any());
        verify(staffActivationTokenRepository, times(1)).invalidateOutstandingTokensForUser(eq(staff), any());
    }

    @Test
    @DisplayName("2. Rejects activation if Firebase authenticated email does not match invitation email")
    void testRejectsActivationWhenFirebaseEmailMismatches() {
        String rawToken = "raw-token-email-mismatch";
        String invitedEmail = "officer@civicfix.gov";
        String attackerEmail = "attacker@otherdomain.com";

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setEmail(invitedEmail);
        staff.setAccountStatus("PENDING_ACTIVATION");
        staff.setRoles(Set.of(officerRole));

        StaffActivationToken token = new StaffActivationToken(
                staff,
                "token-hash",
                OffsetDateTime.now().plusDays(2),
                null
        );

        when(staffActivationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(staffActivationTokenRepository.consumeTokenAtomic(anyString(), any())).thenReturn(1);
        when(firebaseTokenVerifier.verifyIdToken("attacker-token"))
                .thenReturn(new FirebaseTokenClaims("attacker-uid", attackerEmail, true, "Attacker", Map.of()));

        ActivateStaffRequest req = new ActivateStaffRequest(rawToken, "SecurePass123!", "attacker-token");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                staffActivationService.activateStaff(req, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("does not match the invitation email"));
    }

    @Test
    @DisplayName("3. Rejects activation when token was already consumed")
    void testRejectsActivationWhenTokenAlreadyConsumed() {
        User staff = new User();
        staff.setEmail("already.active@civicfix.gov");

        StaffActivationToken token = new StaffActivationToken(
                staff,
                "token-hash",
                OffsetDateTime.now().plusDays(2),
                null
        );
        token.setConsumedAt(OffsetDateTime.now());

        when(staffActivationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ActivateStaffRequest req = new ActivateStaffRequest("used-token", "SecurePass123!");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                staffActivationService.activateStaff(req, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("already been used"));
    }

    @Test
    @DisplayName("4. Rejects activation when token is expired")
    void testRejectsActivationWhenTokenExpired() {
        User staff = new User();
        staff.setEmail("expired@civicfix.gov");

        StaffActivationToken token = new StaffActivationToken(
                staff,
                "token-hash",
                OffsetDateTime.now().minusDays(1), // Expired
                null
        );

        when(staffActivationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        ActivateStaffRequest req = new ActivateStaffRequest("expired-token", "SecurePass123!");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                staffActivationService.activateStaff(req, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("expired"));
    }
}
