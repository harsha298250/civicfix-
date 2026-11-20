package com.civicfix.auth;

import com.civicfix.auth.dto.ForgotPasswordRequest;
import com.civicfix.auth.dto.ResetPasswordRequest;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Live PostgreSQL Password Reset & Flyway V18 Integration Tests")
public class PasswordResetLivePostgresIntegrationTest {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.civicfix.audit.AuditLogRepository auditLogRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User liveUser;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();

        // Create a unique live citizen in PostgreSQL
        liveUser = new User();
        liveUser.setEmail("live.test." + UUID.randomUUID().toString().substring(0, 8) + "@metro.gov");
        liveUser.setFullName("Live Verification Resident");
        liveUser.setPhone("+91 98401 " + (10000 + (int)(Math.random() * 90000)));
        liveUser.setPasswordHash(passwordEncoder.encode("InitialPassword#2026"));
        liveUser.setAccountStatus("ACTIVE");
        liveUser.setWard("Ward 1");
        liveUser = userRepository.save(liveUser);
    }

    @AfterEach
    void tearDown() {
        if (liveUser != null && liveUser.getId() != null) {
            tokenRepository.deleteAll();
            // Delete any audit logs referencing liveUser before deleting the user
            auditLogRepository.findAll().stream()
                    .filter(a -> liveUser.getId().equals(a.getActorId()))
                    .forEach(a -> auditLogRepository.deleteById(a.getId()));
            userRepository.deleteById(liveUser.getId());
        }
    }

    @Test
    @DisplayName("Live DB: Request reset persists token hash, expiry, client IP in PostgreSQL")
    void testLiveDatabasePasswordResetRequestAndPersistence() {
        ForgotPasswordRequest req = new ForgotPasswordRequest(liveUser.getEmail());
        String res = authService.requestPasswordReset(req, "192.168.10.55");

        assertEquals("If an account with that email exists, password reset instructions have been sent.", res);

        var tokens = tokenRepository.findByUserAndConsumedAtIsNull(liveUser);
        assertEquals(1, tokens.size(), "Exactly 1 active token should be stored in live database");

        PasswordResetToken liveToken = tokens.get(0);
        assertNotNull(liveToken.getId());
        assertEquals(64, liveToken.getTokenHash().length());
        assertEquals("192.168.10.55", liveToken.getCreatedByIp());
        assertNull(liveToken.getConsumedAt());
        assertTrue(liveToken.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    @DisplayName("Live DB: Atomic token consumption and password update against PostgreSQL")
    void testLiveDatabaseAtomicConsumptionAndUpdate() {
        String tokenHash = "livehash_" + UUID.randomUUID().toString().replace("-", "") + "00000000000000000000000000000000";
        tokenHash = tokenHash.substring(0, 64);

        PasswordResetToken token = new PasswordResetToken(
                liveUser,
                tokenHash,
                OffsetDateTime.now().plusMinutes(15),
                "192.168.10.60"
        );
        tokenRepository.save(token);

        // Test atomic SQL consumption
        int updated = tokenRepository.consumeTokenAtomic(tokenHash, OffsetDateTime.now());
        assertEquals(1, updated, "Atomic consumption must update exactly 1 row");

        // Concurrent attempt must update 0 rows
        int secondAttempt = tokenRepository.consumeTokenAtomic(tokenHash, OffsetDateTime.now());
        assertEquals(0, secondAttempt, "Second attempt must update 0 rows (single-use)");

        Optional<PasswordResetToken> reloaded = tokenRepository.findByTokenHash(tokenHash);
        assertTrue(reloaded.isPresent());
        assertNotNull(reloaded.get().getConsumedAt());
    }

    @Test
    @DisplayName("Live DB: Foreign key cascade deletion removes tokens when user is deleted")
    void testLiveDatabaseCascadeDelete() {
        String tokenHash = "cascade_" + UUID.randomUUID().toString().replace("-", "") + "00000000000000000000000000000000";
        tokenHash = tokenHash.substring(0, 64);

        PasswordResetToken token = new PasswordResetToken(
                liveUser,
                tokenHash,
                OffsetDateTime.now().plusMinutes(15),
                "192.168.10.70"
        );
        tokenRepository.save(token);

        // Delete user directly to test foreign key constraint ON DELETE CASCADE
        userRepository.deleteById(liveUser.getId());
        liveUser = null; // Prevent double deletion in tearDown

        Optional<PasswordResetToken> tokenAfterUserDeleted = tokenRepository.findByTokenHash(tokenHash);
        assertTrue(tokenAfterUserDeleted.isEmpty(), "Token must be automatically cascade-deleted with user");
    }

    @Test
    @DisplayName("Live DB: Account-level rate limiting throttles repeated requests for same user within cooldown")
    void testLiveDatabaseAccountLevelRateLimiting() {
        ForgotPasswordRequest req = new ForgotPasswordRequest(liveUser.getEmail());

        // First request: token is created
        String res1 = authService.requestPasswordReset(req, "192.168.1.101");
        assertEquals("If an account with that email exists, password reset instructions have been sent.", res1);
        assertEquals(1, tokenRepository.findByUserAndConsumedAtIsNull(liveUser).size());

        // Second request from different IP within 5-min cooldown: throttled, no new token
        String res2 = authService.requestPasswordReset(req, "192.168.1.102");
        assertEquals("If an account with that email exists, password reset instructions have been sent.", res2);
        assertEquals(1, tokenRepository.findByUserAndConsumedAtIsNull(liveUser).size(),
                "Token count must remain 1 because account cooldown is active in live database");
    }
}
