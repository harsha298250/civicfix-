package com.civicfix.security.ratelimit;

import com.civicfix.auth.AuthService;
import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.common.exception.AuthenticationServiceUnavailableException;
import com.civicfix.common.exception.RateLimitExceededException;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.RoleRepository;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PostgreSQL End-to-End Login Concurrency & Transactional Failure Integration Tests")
public class PostgresAuthConcurrencyIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginLockoutService loginLockoutService;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private AccountLoginAttemptRepository accountLoginAttemptRepository;

    @Autowired
    private IpLoginAttemptRepository ipLoginAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("ALTER TABLE account_login_attempts DROP CONSTRAINT IF EXISTS fail_always");
        loginAttemptRepository.deleteAll();
        accountLoginAttemptRepository.deleteAll();
        ipLoginAttemptRepository.deleteAll();
        rateLimiterService.clearAll();

        properties.getLogin().setCapacity(5);
        properties.getLogin().setDuration(Duration.ofMinutes(15));

        properties.getAccountLogin().setCapacity(20);
        properties.getAccountLogin().setDuration(Duration.ofHours(1));

        properties.getIpLogin().setCapacity(30);
        properties.getIpLogin().setDuration(Duration.ofMinutes(15));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("ALTER TABLE account_login_attempts DROP CONSTRAINT IF EXISTS fail_always");
        loginAttemptRepository.deleteAll();
        accountLoginAttemptRepository.deleteAll();
        ipLoginAttemptRepository.deleteAll();
        rateLimiterService.clearAll();

        properties.getLogin().setCapacity(10000);
        properties.getLogin().setDuration(Duration.ofMinutes(15));

        properties.getAccountLogin().setCapacity(10000);
        properties.getAccountLogin().setDuration(Duration.ofHours(1));

        properties.getIpLogin().setCapacity(10000);
        properties.getIpLogin().setDuration(Duration.ofMinutes(15));
    }

    private User ensureUser(String email, String rawPassword) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User();
            user.setFullName("Test User " + email);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setAccountStatus("ACTIVE");
            Role role = roleRepository.findByName(RoleName.CITIZEN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.CITIZEN)));
            user.getRoles().add(role);
            return userRepository.save(user);
        });
    }

    // =========================================================================
    // 1. Account-wide threshold race: begin just below threshold & issue concurrent requests
    // =========================================================================
    @Test
    @DisplayName("1. Account-wide threshold race: requests just below threshold do not pass stale checks")
    void testAccountWideThresholdRaceBlocksStaleRequests() throws Exception {
        String targetAccount = "victim.race@civicfix.org";
        properties.getAccountLogin().setCapacity(5);

        // Pre-condition: Drive account to 4 failures (just below threshold of 5)
        for (int i = 1; i <= 4; i++) {
            loginLockoutService.recordFailedAttempt(targetAccount, "10.0.1." + i);
        }

        // Verify account has 4 failures and is NOT yet locked
        AccountLoginAttempt initial = accountLoginAttemptRepository.findByIdentifierIgnoreCase(targetAccount).orElseThrow();
        assertEquals(4, initial.getFailedAttempts());
        assertNull(initial.getLockedUntil());

        // Issue 8 concurrent invalid login requests from 8 distinct IPs
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger badCredentialsCount = new AtomicInteger(0);
        AtomicInteger rateLimitExceededCount = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= threadCount; i++) {
            final String remoteIp = "10.0.2." + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
                request.setRemoteAddr(remoteIp);
                MockHttpServletResponse response = new MockHttpServletResponse();

                try {
                    authService.login(new LoginRequest(targetAccount, "WrongPassword#123"), request, response);
                } catch (BadCredentialsException ex) {
                    badCredentialsCount.incrementAndGet();
                } catch (RateLimitExceededException ex) {
                    rateLimitExceededCount.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        try {
            for (Future<Void> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Exactly 1 request reaches and completes the 5th attempt (threshold)
        assertEquals(1, badCredentialsCount.get(),
                "Exactly 1 request must consume the remaining threshold capacity");

        // The remaining 7 requests must be blocked by RateLimitExceededException rather than passing stale checks
        assertEquals(7, rateLimitExceededCount.get(),
                "All subsequent concurrent requests must be rejected at the gate without passing stale checks");

        // In PostgreSQL, failed_attempts is strictly 5 (not 12!)
        AccountLoginAttempt finalAttempt = accountLoginAttemptRepository.findByIdentifierIgnoreCase(targetAccount).orElseThrow();
        assertEquals(5, finalAttempt.getFailedAttempts(),
                "Account counter must strictly reflect 5 attempts, preventing lost or runaway increments");
        assertNotNull(finalAttempt.getLockedUntil());
        assertTrue(finalAttempt.getLockedUntil().isAfter(OffsetDateTime.now()));
    }

    // =========================================================================
    // 2. IP-wide threshold race across multiple account identifiers
    // =========================================================================
    @Test
    @DisplayName("2. IP-wide threshold race: concurrent requests from same IP do not pass stale checks")
    void testIpWideThresholdRaceBlocksStaleRequests() throws Exception {
        String attackerIp = "198.51.100.99";
        properties.getIpLogin().setCapacity(5);

        // Pre-condition: Drive IP to 4 failures (just below threshold of 5)
        for (int i = 1; i <= 4; i++) {
            loginLockoutService.recordFailedAttempt("user." + i + "@civicfix.org", attackerIp);
        }

        IpLoginAttempt initial = ipLoginAttemptRepository.findByIpAddress(attackerIp).orElseThrow();
        assertEquals(4, initial.getFailedAttempts());
        assertNull(initial.getLockedUntil());

        // Issue 8 concurrent invalid login requests targeting 8 different accounts from attackerIp
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger badCredentialsCount = new AtomicInteger(0);
        AtomicInteger rateLimitExceededCount = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= threadCount; i++) {
            final String account = "target.burst." + i + "@civicfix.org";
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
                request.setRemoteAddr(attackerIp);
                MockHttpServletResponse response = new MockHttpServletResponse();

                try {
                    authService.login(new LoginRequest(account, "WrongPassword#123"), request, response);
                } catch (BadCredentialsException ex) {
                    badCredentialsCount.incrementAndGet();
                } catch (RateLimitExceededException ex) {
                    rateLimitExceededCount.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        try {
            for (Future<Void> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, badCredentialsCount.get(),
                "Exactly 1 request must consume the remaining IP capacity");
        assertEquals(7, rateLimitExceededCount.get(),
                "Remaining requests from that IP must be blocked by rate limit check");

        IpLoginAttempt finalIp = ipLoginAttemptRepository.findByIpAddress(attackerIp).orElseThrow();
        assertEquals(5, finalIp.getFailedAttempts());
        assertNotNull(finalIp.getLockedUntil());
        assertTrue(finalIp.getLockedUntil().isAfter(OffsetDateTime.now()));
    }

    // =========================================================================
    // 3. Same-account concurrent requests from different IPs
    // =========================================================================
    @Test
    @DisplayName("3. Same-account concurrent requests from different IPs coordinate safely")
    void testSameAccountConcurrentRequestsFromDifferentIps() throws Exception {
        String targetAccount = "distributed.account@civicfix.org";
        properties.getAccountLogin().setCapacity(20);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 1; i <= threadCount; i++) {
            final String ip = "10.50.1." + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
                request.setRemoteAddr(ip);
                MockHttpServletResponse response = new MockHttpServletResponse();

                try {
                    authService.login(new LoginRequest(targetAccount, "WrongPassword#123"), request, response);
                    return false;
                } catch (BadCredentialsException ex) {
                    return true;
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        try {
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(15, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        // Database verification
        AccountLoginAttempt accountAttempt = accountLoginAttemptRepository.findByIdentifierIgnoreCase(targetAccount).orElseThrow();
        assertEquals(threadCount, accountAttempt.getFailedAttempts(),
                "All 10 concurrent requests from distinct IPs must be accumulated with zero lost updates");
        assertEquals(threadCount, loginAttemptRepository.count());
    }

    // =========================================================================
    // 4. Same-IP concurrent requests against different accounts
    // =========================================================================
    @Test
    @DisplayName("4. Same-IP concurrent requests against different accounts coordinate safely")
    void testSameIpConcurrentRequestsAgainstDifferentAccounts() throws Exception {
        String attackerIp = "198.51.100.77";
        properties.getIpLogin().setCapacity(30);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 1; i <= threadCount; i++) {
            final String user = "spray.user." + i + "@civicfix.org";
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
                request.setRemoteAddr(attackerIp);
                MockHttpServletResponse response = new MockHttpServletResponse();

                try {
                    authService.login(new LoginRequest(user, "WrongPassword#123"), request, response);
                    return false;
                } catch (BadCredentialsException ex) {
                    return true;
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        try {
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(15, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        IpLoginAttempt ipAttempt = ipLoginAttemptRepository.findByIpAddress(attackerIp).orElseThrow();
        assertEquals(threadCount, ipAttempt.getFailedAttempts(),
                "All 10 concurrent requests from same IP must be accumulated with zero lost updates");
        assertEquals(threadCount, loginAttemptRepository.count());
    }

    // =========================================================================
    // 5. Successful login does not increase failed-attempt counters
    // =========================================================================
    @Test
    @DisplayName("5. Successful login does not increase failed-attempt counters")
    void testSuccessfulLoginDoesNotIncreaseFailedAttemptCounters() {
        String email = "john.citizen@civicfix.org";
        String password = "ValidPassword#123";
        ensureUser(email, password);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthUserResponse authUser = authService.login(new LoginRequest(email, password), request, response);
        assertNotNull(authUser);
        assertEquals(email, authUser.getEmail());

        // Zero failed attempt rows created
        assertEquals(0, loginAttemptRepository.count());
        assertEquals(0, accountLoginAttemptRepository.count());
        assertEquals(0, ipLoginAttemptRepository.count());
    }

    // =========================================================================
    // 6. Legitimate successful login resets only intended account/pair state
    // =========================================================================
    @Test
    @DisplayName("6. Legitimate successful login resets only the intended account and pair state")
    void testLegitimateSuccessfulLoginResetsOnlyIntendedAccountAndPairState() {
        String email = "sarah.officer@civicfix.org";
        String password = "OfficerPassword#123";
        ensureUser(email, password);

        String legitimateIp = "192.168.1.15";
        String attackerIp = "203.0.113.88";

        // Record 3 failed attempts from legitimateIp and 2 from attackerIp
        for (int i = 0; i < 3; i++) {
            loginLockoutService.recordFailedAttempt(email, legitimateIp);
        }
        for (int i = 0; i < 2; i++) {
            loginLockoutService.recordFailedAttempt(email, attackerIp);
        }

        assertEquals(5, accountLoginAttemptRepository.findByIdentifierIgnoreCase(email).orElseThrow().getFailedAttempts());
        assertEquals(3, loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(email, legitimateIp).orElseThrow().getFailedAttempts());
        assertEquals(2, loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(email, attackerIp).orElseThrow().getFailedAttempts());

        // Legitimate user logs in successfully from legitimateIp
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(legitimateIp);
        MockHttpServletResponse response = new MockHttpServletResponse();

        authService.login(new LoginRequest(email, password), request, response);

        // Verification:
        // 1. Account-level failure state is completely cleared
        assertTrue(accountLoginAttemptRepository.findByIdentifierIgnoreCase(email).isEmpty(),
                "account_login_attempts row must be cleared on successful authentication");

        // 2. Per-pair record for legitimateIp is cleared
        assertTrue(loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(email, legitimateIp).isEmpty(),
                "login_attempts row for legitimate IP must be cleared on successful authentication");

        // 3. Attacker pair record is STRICTLY preserved
        Optional<LoginAttempt> attackerPair = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(email, attackerIp);
        assertTrue(attackerPair.isPresent(), "Attacker pair record must NOT be deleted");
        assertEquals(2, attackerPair.get().getFailedAttempts());
    }

    // =========================================================================
    // 7. IP abuse history remains preserved after successful login from another IP
    // =========================================================================
    @Test
    @DisplayName("7. IP abuse history remains preserved after successful login from another IP")
    void testIpAbuseHistoryRemainsPreserved() {
        String email = "victim.corp@civicfix.org";
        String password = "CorpPassword#123";
        ensureUser(email, password);

        String attackerIp = "203.0.113.99";
        String cleanIp = "10.0.0.50";

        // Attacker IP attacks victim and another user (6 failures)
        for (int i = 0; i < 3; i++) {
            loginLockoutService.recordFailedAttempt(email, attackerIp);
            loginLockoutService.recordFailedAttempt("other.user@civicfix.org", attackerIp);
        }

        assertEquals(6, ipLoginAttemptRepository.findByIpAddress(attackerIp).orElseThrow().getFailedAttempts());

        // Victim logs in successfully from cleanIp
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(cleanIp);
        MockHttpServletResponse response = new MockHttpServletResponse();

        authService.login(new LoginRequest(email, password), request, response);

        // IP abuse history for attackerIp is strictly preserved
        Optional<IpLoginAttempt> attackerIpAttempt = ipLoginAttemptRepository.findByIpAddress(attackerIp);
        assertTrue(attackerIpAttempt.isPresent());
        assertEquals(6, attackerIpAttempt.get().getFailedAttempts(),
                "Attacker IP abuse history must not be erased when targeted user authenticates from another IP");
    }

    // =========================================================================
    // 8. Database failure during counter recording produces controlled response
    // =========================================================================
    @Test
    @DisplayName("8. Database failure during counter recording produces controlled 503 response")
    void testDatabaseFailureDuringCounterRecordingProducesControlledResponse() {
        String email = "db.fail.test@civicfix.org";
        String ip = "192.168.10.10";

        // Simulate database write failure by introducing an impossible CHECK constraint on PostgreSQL table
        jdbcTemplate.execute("ALTER TABLE account_login_attempts ADD CONSTRAINT fail_always CHECK (failed_attempts < 0)");

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthenticationServiceUnavailableException ex = assertThrows(
                    AuthenticationServiceUnavailableException.class,
                    () -> authService.login(new LoginRequest(email, "WrongPassword#123"), request, response)
            );

            assertEquals("Authentication service is temporarily unavailable. Please try again shortly.",
                    ex.getMessage(),
                    "Controlled service unavailable message must be returned without leaking SQL errors");
        } finally {
            jdbcTemplate.execute("ALTER TABLE account_login_attempts DROP CONSTRAINT IF EXISTS fail_always");
        }
    }

    // =========================================================================
    // 9. Transaction rollback leaves no partial counter updates
    // =========================================================================
    @Test
    @DisplayName("9. Transaction rollback leaves no partial counter updates on failure")
    void testTransactionRollbackLeavesNoPartialCounterUpdates() {
        String email = "rollback.test@civicfix.org";
        String ip = "192.168.10.20";

        // Add impossible check constraint on account_login_attempts
        jdbcTemplate.execute("ALTER TABLE account_login_attempts ADD CONSTRAINT fail_always CHECK (failed_attempts < 0)");

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThrows(AuthenticationServiceUnavailableException.class, () ->
                    authService.login(new LoginRequest(email, "WrongPassword#123"), request, response)
            );

            // Verify transaction rolled back: login_attempts pair record must NOT remain committed
            assertEquals(0, loginAttemptRepository.count(),
                    "Pair record in login_attempts must be rolled back; zero partial state permitted");
            assertEquals(0, accountLoginAttemptRepository.count());
            assertEquals(0, ipLoginAttemptRepository.count());
        } finally {
            jdbcTemplate.execute("ALTER TABLE account_login_attempts DROP CONSTRAINT IF EXISTS fail_always");
        }
    }

    // =========================================================================
    // 10. Lock release occurs after both successful and failed authentication
    // =========================================================================
    @Test
    @DisplayName("10. Lock release occurs after failed, successful, and rate-limited authentication")
    void testLockReleaseOccursAfterAuthentication() {
        String email = "lock.release@civicfix.org";
        String password = "ValidPassword#123";
        ensureUser(email, password);
        String ip = "192.168.10.30";

        // Part A: Lock release after failed authentication
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr(ip);
        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest(email, "WrongPassword"), req1, new MockHttpServletResponse())
        );

        // Verification: Another transaction must be able to acquire the advisory lock immediately
        Boolean lockAcquiredAfterFail = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(1001, hashtext(?))", Boolean.class, email);
        assertNotNull(lockAcquiredAfterFail);
        assertTrue(lockAcquiredAfterFail, "Advisory lock must be released after failed authentication");

        // Part B: Lock release after successful authentication
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr(ip);
        authService.login(new LoginRequest(email, password), req2, new MockHttpServletResponse());

        Boolean lockAcquiredAfterSuccess = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(1001, hashtext(?))", Boolean.class, email);
        assertNotNull(lockAcquiredAfterSuccess);
        assertTrue(lockAcquiredAfterSuccess, "Advisory lock must be released after successful authentication");

        // Part C: Lock release after rate limit lockout
        properties.getLogin().setCapacity(1);
        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", "/api/auth/login");
        req3.setRemoteAddr(ip);
        // First invalid attempt reaches capacity 1
        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest(email, "WrongPassword"), req3, new MockHttpServletResponse())
        );
        // Second attempt is blocked by rate limit check before authentication
        assertThrows(RateLimitExceededException.class, () ->
                authService.login(new LoginRequest(email, "WrongPassword"), req3, new MockHttpServletResponse())
        );

        Boolean lockAcquiredAfterRateLimit = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(1001, hashtext(?))", Boolean.class, email);
        assertNotNull(lockAcquiredAfterRateLimit);
        assertTrue(lockAcquiredAfterRateLimit, "Advisory lock must be released after rate-limit block");
    }
}
