package com.civicfix.security.ratelimit;

import com.civicfix.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@DisplayName("PostgreSQL-backed Login Lockout & Security Hardening Integration Tests")
public class PostgresLoginLockoutIntegrationTest {

    @Autowired
    private LoginLockoutService loginLockoutService;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private AccountLoginAttemptRepository accountLoginAttemptRepository;

    @Autowired
    private IpLoginAttemptRepository ipLoginAttemptRepository;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
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

    // =========================================================================
    // 1. Concurrent attempts against one account from different IP addresses
    // =========================================================================
    @Test
    @DisplayName("1. Concurrent attempts against one account from different IP addresses")
    void testConcurrentAttemptsAgainstOneAccountFromDifferentIps() throws Exception {
        String targetAccount = "distributed.target@civicfix.org";
        int threadCount = 12;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String ip = "10.0.1." + (i + 1);
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                loginLockoutService.recordFailedAttempt(targetAccount, ip);
                return true;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Database Verification:
        // account_login_attempts must have exactly 1 row with failed_attempts == threadCount
        Optional<AccountLoginAttempt> accountAttemptOpt = accountLoginAttemptRepository.findByIdentifierIgnoreCase(targetAccount);
        assertTrue(accountAttemptOpt.isPresent(), "Account login attempt row must exist in PostgreSQL");
        assertEquals(threadCount, accountAttemptOpt.get().getFailedAttempts(),
                "All concurrent attempts from distinct IPs must be atomically accumulated with no lost updates");

        // login_attempts must have threadCount distinct rows (one per IP)
        assertEquals(threadCount, loginAttemptRepository.count(),
                "Each distinct (account, IP) pair must be recorded in login_attempts");
    }

    // =========================================================================
    // 2. Concurrent attempts against multiple accounts from one IP address
    // =========================================================================
    @Test
    @DisplayName("2. Concurrent attempts against multiple accounts from one IP address")
    void testConcurrentAttemptsAgainstMultipleAccountsFromOneIp() throws Exception {
        String attackerIp = "198.51.100.77";
        int threadCount = 12;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String account = "spray.target." + (i + 1) + "@civicfix.org";
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                loginLockoutService.recordFailedAttempt(account, attackerIp);
                return true;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Database Verification:
        // ip_login_attempts must have exactly 1 row with failed_attempts == threadCount
        Optional<IpLoginAttempt> ipAttemptOpt = ipLoginAttemptRepository.findByIpAddress(attackerIp);
        assertTrue(ipAttemptOpt.isPresent(), "IP login attempt row must exist in PostgreSQL");
        assertEquals(threadCount, ipAttemptOpt.get().getFailedAttempts(),
                "All concurrent attempts against different accounts from the same IP must be atomically accumulated");

        // login_attempts must have threadCount distinct rows (one per account)
        assertEquals(threadCount, loginAttemptRepository.count());
    }

    // =========================================================================
    // 3. Simultaneous requests at the account-wide threshold
    // =========================================================================
    @Test
    @DisplayName("3. Simultaneous requests at the account-wide threshold")
    void testSimultaneousRequestsAtAccountWideThreshold() throws Exception {
        String targetAccount = "threshold.account@civicfix.org";
        properties.getAccountLogin().setCapacity(5);

        // Pre-condition: Drive account to threshold (5 failed attempts from 5 distinct IPs)
        for (int i = 1; i <= 5; i++) {
            loginLockoutService.recordFailedAttempt(targetAccount, "10.10.1." + i);
        }

        // Verify account is locked in PostgreSQL
        Optional<AccountLoginAttempt> accountAttemptOpt = accountLoginAttemptRepository.findByIdentifierIgnoreCase(targetAccount);
        assertTrue(accountAttemptOpt.isPresent());
        assertTrue(accountAttemptOpt.get().getLockedUntil().isAfter(OffsetDateTime.now()),
                "Account must be locked in PostgreSQL after hitting threshold");

        // Simultaneous burst: 10 concurrent requests from 10 fresh IPs
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger blockedCount = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String freshIp = "172.16.1." + (i + 1);
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    loginLockoutService.checkLockout(targetAccount, freshIp);
                } catch (RateLimitExceededException ex) {
                    if (ex.getMessage().contains("Account temporarily locked") && ex.getRetryAfterSeconds() > 0) {
                        blockedCount.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(threadCount, blockedCount.get(),
                "All simultaneous requests at the account-wide threshold must be deterministically blocked");
    }

    // =========================================================================
    // 4. Simultaneous requests at the IP-wide threshold
    // =========================================================================
    @Test
    @DisplayName("4. Simultaneous requests at the IP-wide threshold")
    void testSimultaneousRequestsAtIpWideThreshold() throws Exception {
        String attackerIp = "198.51.100.88";
        properties.getIpLogin().setCapacity(5);

        // Pre-condition: Drive IP to threshold (5 failed attempts across 5 distinct accounts)
        for (int i = 1; i <= 5; i++) {
            loginLockoutService.recordFailedAttempt("victim." + i + "@civicfix.org", attackerIp);
        }

        // Verify IP is locked in PostgreSQL
        Optional<IpLoginAttempt> ipAttemptOpt = ipLoginAttemptRepository.findByIpAddress(attackerIp);
        assertTrue(ipAttemptOpt.isPresent());
        assertTrue(ipAttemptOpt.get().getLockedUntil().isAfter(OffsetDateTime.now()),
                "IP must be locked in PostgreSQL after hitting threshold");

        // Simultaneous burst: 10 concurrent requests from this IP targeting 10 fresh accounts
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger blockedCount = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String freshAccount = "burst.target." + (i + 1) + "@civicfix.org";
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    loginLockoutService.checkLockout(freshAccount, attackerIp);
                } catch (RateLimitExceededException ex) {
                    if (ex.getMessage().contains("Too many failed login attempts from this IP address")
                            && ex.getRetryAfterSeconds() > 0) {
                        blockedCount.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(threadCount, blockedCount.get(),
                "All simultaneous requests at the IP-wide threshold must be deterministically blocked");
    }

    // =========================================================================
    // 5. Successful login from clean IP while separate attacker IP recorded abuse
    // =========================================================================
    @Test
    @DisplayName("5. Successful login from a clean IP while a separate attacker IP has recorded abuse")
    void testSuccessfulLoginFromCleanIpWhileSeparateAttackerIpRecordedAbuse() {
        String legitimateUser = "alice.supervisor@metro.gov";
        String attackerIp = "203.0.113.50";
        String cleanIp = "192.168.1.100";

        // Attacker attempts 4 failed logins from attackerIp
        for (int i = 0; i < 4; i++) {
            loginLockoutService.recordFailedAttempt(legitimateUser, attackerIp);
        }

        // Verify attacker abuse is recorded in DB
        Optional<IpLoginAttempt> attackerIpAttempt = ipLoginAttemptRepository.findByIpAddress(attackerIp);
        assertTrue(attackerIpAttempt.isPresent());
        assertEquals(4, attackerIpAttempt.get().getFailedAttempts());

        Optional<LoginAttempt> attackerPair = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(legitimateUser, attackerIp);
        assertTrue(attackerPair.isPresent());
        assertEquals(4, attackerPair.get().getFailedAttempts());

        // Legitimate user logs in from cleanIp: checkLockout must pass cleanly
        assertDoesNotThrow(() -> loginLockoutService.checkLockout(legitimateUser, cleanIp),
                "Clean IP must pass lockout check without exception");

        // Record successful login from cleanIp
        loginLockoutService.recordSuccessfulLogin(legitimateUser, cleanIp);

        // Crucial: Attacker IP abuse state must NOT be erased!
        Optional<IpLoginAttempt> preservedAttackerIp = ipLoginAttemptRepository.findByIpAddress(attackerIp);
        assertTrue(preservedAttackerIp.isPresent(), "Attacker IP abuse record must remain intact");
        assertEquals(4, preservedAttackerIp.get().getFailedAttempts(), "Attacker IP failure count must be preserved");

        Optional<LoginAttempt> preservedPair = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(legitimateUser, attackerIp);
        assertTrue(preservedPair.isPresent(), "Pair record for attacker IP must remain intact");
        assertEquals(4, preservedPair.get().getFailedAttempts(), "Attacker pair failure count must not be erased");

        // Account-level failure state was reset by legitimate login
        assertTrue(accountLoginAttemptRepository.findByIdentifierIgnoreCase(legitimateUser).isEmpty(),
                "Account-level failure state must be cleared after successful authentication");
    }

    // =========================================================================
    // 6. Successful login resets the appropriate account-specific state
    // =========================================================================
    @Test
    @DisplayName("6. Successful login resets the appropriate account-specific state")
    void testSuccessfulLoginResetsAppropriateAccountSpecificState() {
        String user = "bob.resident@civicfix.org";
        String userIp = "192.168.1.120";

        // Record 3 failed attempts
        for (int i = 0; i < 3; i++) {
            loginLockoutService.recordFailedAttempt(user, userIp);
        }

        assertEquals(3, accountLoginAttemptRepository.findByIdentifierIgnoreCase(user).orElseThrow().getFailedAttempts());
        assertEquals(3, loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(user, userIp).orElseThrow().getFailedAttempts());

        // User enters correct credentials and authenticates successfully
        loginLockoutService.recordSuccessfulLogin(user, userIp);

        // Account-specific DB record is deleted
        assertTrue(accountLoginAttemptRepository.findByIdentifierIgnoreCase(user).isEmpty(),
                "account_login_attempts row must be deleted upon successful login");

        // Per-pair DB record for (user, userIp) is deleted
        assertTrue(loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(user, userIp).isEmpty(),
                "login_attempts row for this IP must be deleted upon successful login");

        // Subsequent lockout check succeeds immediately
        assertDoesNotThrow(() -> loginLockoutService.checkLockout(user, userIp));

        // Next failure starts fresh at 1
        loginLockoutService.recordFailedAttempt(user, userIp);
        assertEquals(1, accountLoginAttemptRepository.findByIdentifierIgnoreCase(user).orElseThrow().getFailedAttempts(),
                "Counter must start fresh at 1 after reset");
    }

    // =========================================================================
    // 7. IP abuse history remains available after successful login from another IP
    // =========================================================================
    @Test
    @DisplayName("7. IP abuse history remains available after successful login from another IP")
    void testIpAbuseHistoryRemainsAvailableAfterSuccessfulLoginFromAnotherIp() {
        String attackerIp = "203.0.113.99";
        String cleanOfficeIp = "10.50.1.1";
        String victim1 = "victim1@civicfix.org";
        String victim2 = "victim2@civicfix.org";

        // Attacker IP sprays passwords: 3 attempts on victim1, 3 attempts on victim2
        for (int i = 0; i < 3; i++) {
            loginLockoutService.recordFailedAttempt(victim1, attackerIp);
            loginLockoutService.recordFailedAttempt(victim2, attackerIp);
        }

        // IP-wide abuse record has 6 failures
        assertEquals(6, ipLoginAttemptRepository.findByIpAddress(attackerIp).orElseThrow().getFailedAttempts());

        // Victim 1 logs in successfully from their clean office IP
        loginLockoutService.recordSuccessfulLogin(victim1, cleanOfficeIp);

        // Verification:
        // 1. IP abuse history for attackerIp is STILL 6
        Optional<IpLoginAttempt> attackerIpAttempt = ipLoginAttemptRepository.findByIpAddress(attackerIp);
        assertTrue(attackerIpAttempt.isPresent());
        assertEquals(6, attackerIpAttempt.get().getFailedAttempts(),
                "Attacker IP abuse history must not be erased when victim1 logs in from a clean IP");

        // 2. The pair attempts against victim1 from attackerIp are STILL preserved
        Optional<LoginAttempt> attackerVictim1Pair = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(victim1, attackerIp);
        assertTrue(attackerVictim1Pair.isPresent(), "Attacker attempts against victim1 must remain recorded");
        assertEquals(3, attackerVictim1Pair.get().getFailedAttempts());

        // 3. Attacker attempts against victim2 remain preserved
        Optional<LoginAttempt> attackerVictim2Pair = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(victim2, attackerIp);
        assertTrue(attackerVictim2Pair.isPresent());
        assertEquals(3, attackerVictim2Pair.get().getFailedAttempts());
    }

    // =========================================================================
    // 8. Legitimate users can still authenticate from a clean IP
    // =========================================================================
    @Test
    @DisplayName("8. Legitimate users can still authenticate from a clean IP")
    void testLegitimateUsersCanStillAuthenticateFromCleanIp() {
        String targetUser = "david.officer@civicfix.org";
        String attackerIp = "203.0.113.77";
        String cleanHomeIp = "192.168.1.250";

        // Attacker hits 5 failures from attackerIp (locking attackerIp and pair)
        for (int i = 0; i < 5; i++) {
            loginLockoutService.recordFailedAttempt(targetUser, attackerIp);
        }

        // Attacker IP is locked out
        RateLimitExceededException attackerEx = assertThrows(RateLimitExceededException.class, () ->
                loginLockoutService.checkLockout(targetUser, attackerIp)
        );
        assertTrue(attackerEx.getMessage().contains("Account temporarily locked"));

        // Crucial: Legitimate user checking in from cleanHomeIp is NOT locked out
        assertDoesNotThrow(() -> loginLockoutService.checkLockout(targetUser, cleanHomeIp),
                "Legitimate user from separate clean IP must not be locked out by attacker IP");

        // Successful authentication succeeds cleanly
        loginLockoutService.recordSuccessfulLogin(targetUser, cleanHomeIp);
        assertDoesNotThrow(() -> loginLockoutService.checkLockout(targetUser, cleanHomeIp));
    }

    // =========================================================================
    // 9. Existing same-pair UPSERT concurrency tests continue passing
    // =========================================================================
    @Test
    @DisplayName("9a. Existing same-pair UPSERT concurrency: concurrent first-time insertions")
    void testConcurrentFirstTimeInsertions() throws Exception {
        String identifier = "concurrent.new@civicfix.org";
        String ipAddress = "192.168.100.1";

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                loginLockoutService.recordFailedAttempt(identifier, ipAddress);
                return true;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Verify exactly 1 row exists in login_attempts with atomic counter == threadCount
        Optional<LoginAttempt> attemptOpt = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(identifier, ipAddress);
        assertTrue(attemptOpt.isPresent(), "Row must exist in PostgreSQL");
        LoginAttempt attempt = attemptOpt.get();
        assertEquals(threadCount, attempt.getFailedAttempts(),
                "All concurrent first-time attempts must be atomically accounted for (no lost updates)");
        assertNotNull(attempt.getLockedUntil(), "Lockout must be set once max attempts exceeded");

        // Verify account_login_attempts and ip_login_attempts also have counter == threadCount
        assertEquals(threadCount, accountLoginAttemptRepository.findByIdentifierIgnoreCase(identifier).orElseThrow().getFailedAttempts());
        assertEquals(threadCount, ipLoginAttemptRepository.findByIpAddress(ipAddress).orElseThrow().getFailedAttempts());
    }

    @Test
    @DisplayName("9b. Existing same-pair UPSERT concurrency: simultaneous increments on existing row")
    void testMultipleSimultaneousFailedAttemptsAtomicIncrements() throws Exception {
        String identifier = "concurrent.existing@civicfix.org";
        String ipAddress = "192.168.100.2";

        // Seed 1 attempt first
        loginLockoutService.recordFailedAttempt(identifier, ipAddress);

        int concurrentThreads = 9;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch readyLatch = new CountDownLatch(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentThreads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                loginLockoutService.recordFailedAttempt(identifier, ipAddress);
                return true;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        Optional<LoginAttempt> attemptOpt = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(identifier, ipAddress);
        assertTrue(attemptOpt.isPresent());
        LoginAttempt attempt = attemptOpt.get();
        assertEquals(10, attempt.getFailedAttempts(),
                "Atomic UPSERT must ensure 1 initial + 9 concurrent = exactly 10 failed attempts");
        assertTrue(attempt.getLockedUntil().isAfter(OffsetDateTime.now()),
                "Account must be locked in database");
    }

    // =========================================================================
    // 10. Cleanup still preserves active lockouts across all tables
    // =========================================================================
    @Test
    @DisplayName("10. Cleanup still preserves active lockouts across login_attempts, account_login_attempts, and ip_login_attempts")
    void testCleanupPreservingActiveRestrictions() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. In login_attempts:
        LoginAttempt activePair = new LoginAttempt("active.lockout@civicfix.org", "192.168.1.1");
        activePair.setFailedAttempts(5);
        activePair.setFirstAttemptAt(now.minusDays(2));
        activePair.setLastAttemptAt(now.minusDays(2));
        activePair.setLockedUntil(now.plusHours(1));
        loginAttemptRepository.save(activePair);

        LoginAttempt expiredPair = new LoginAttempt("expired.lockout@civicfix.org", "192.168.1.2");
        expiredPair.setFailedAttempts(5);
        expiredPair.setFirstAttemptAt(now.minusDays(2));
        expiredPair.setLastAttemptAt(now.minusDays(2));
        expiredPair.setLockedUntil(now.minusHours(1));
        loginAttemptRepository.save(expiredPair);

        LoginAttempt oldUnlockedPair = new LoginAttempt("old.unlocked@civicfix.org", "192.168.1.3");
        oldUnlockedPair.setFailedAttempts(1);
        oldUnlockedPair.setFirstAttemptAt(now.minusDays(3));
        oldUnlockedPair.setLastAttemptAt(now.minusDays(3));
        oldUnlockedPair.setLockedUntil(null);
        loginAttemptRepository.save(oldUnlockedPair);

        LoginAttempt recentPair = new LoginAttempt("recent.attempt@civicfix.org", "192.168.1.4");
        recentPair.setFailedAttempts(1);
        recentPair.setFirstAttemptAt(now.minusMinutes(5));
        recentPair.setLastAttemptAt(now.minusMinutes(5));
        recentPair.setLockedUntil(null);
        loginAttemptRepository.saveAndFlush(recentPair);

        // 2. In account_login_attempts:
        AccountLoginAttempt activeAccount = new AccountLoginAttempt("active.account@civicfix.org");
        activeAccount.setFailedAttempts(20);
        activeAccount.setFirstAttemptAt(now.minusDays(2));
        activeAccount.setLastAttemptAt(now.minusDays(2));
        activeAccount.setLockedUntil(now.plusHours(1));
        accountLoginAttemptRepository.save(activeAccount);

        AccountLoginAttempt expiredAccount = new AccountLoginAttempt("expired.account@civicfix.org");
        expiredAccount.setFailedAttempts(20);
        expiredAccount.setFirstAttemptAt(now.minusDays(2));
        expiredAccount.setLastAttemptAt(now.minusDays(2));
        expiredAccount.setLockedUntil(now.minusHours(1));
        accountLoginAttemptRepository.save(expiredAccount);

        // 3. In ip_login_attempts:
        IpLoginAttempt activeIp = new IpLoginAttempt("192.168.99.1");
        activeIp.setFailedAttempts(30);
        activeIp.setFirstAttemptAt(now.minusDays(2));
        activeIp.setLastAttemptAt(now.minusDays(2));
        activeIp.setLockedUntil(now.plusHours(1));
        ipLoginAttemptRepository.save(activeIp);

        IpLoginAttempt expiredIp = new IpLoginAttempt("192.168.99.2");
        expiredIp.setFailedAttempts(30);
        expiredIp.setFirstAttemptAt(now.minusDays(2));
        expiredIp.setLastAttemptAt(now.minusDays(2));
        expiredIp.setLockedUntil(now.minusHours(1));
        ipLoginAttemptRepository.saveAndFlush(expiredIp);

        // Execute bounded cleanup with batchSize = 10, retention = 1 day
        int deleted = loginLockoutService.cleanupExpiredAttempts(Duration.ofDays(1), 10);
        assertEquals(4, deleted, "Must delete exactly 4 expired/old records (2 from pair, 1 from account, 1 from ip)");

        // Active lockouts MUST STILL EXIST in all 3 tables!
        Optional<LoginAttempt> preservedActivePair = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(
                "active.lockout@civicfix.org", "192.168.1.1");
        assertTrue(preservedActivePair.isPresent(), "Active pair lockout must NOT be deleted by cleanup!");
        assertTrue(preservedActivePair.get().getLockedUntil().isAfter(now));

        Optional<AccountLoginAttempt> preservedActiveAccount = accountLoginAttemptRepository.findByIdentifierIgnoreCase("active.account@civicfix.org");
        assertTrue(preservedActiveAccount.isPresent(), "Active account lockout must NOT be deleted by cleanup!");
        assertTrue(preservedActiveAccount.get().getLockedUntil().isAfter(now));

        Optional<IpLoginAttempt> preservedActiveIp = ipLoginAttemptRepository.findByIpAddress("192.168.99.1");
        assertTrue(preservedActiveIp.isPresent(), "Active IP lockout must NOT be deleted by cleanup!");
        assertTrue(preservedActiveIp.get().getLockedUntil().isAfter(now));

        // Expired records MUST BE DELETED
        assertTrue(loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress("expired.lockout@civicfix.org", "192.168.1.2").isEmpty());
        assertTrue(accountLoginAttemptRepository.findByIdentifierIgnoreCase("expired.account@civicfix.org").isEmpty());
        assertTrue(ipLoginAttemptRepository.findByIpAddress("192.168.99.2").isEmpty());
    }

    // =========================================================================
    // Progressive restrictions and expiry test
    // =========================================================================
    @Test
    @DisplayName("Progressive restrictions and expiry: lockout duration increases with repeated failures, expires cleanly")
    void testProgressiveRestrictionsAndExpiry() {
        String identifier = "progressive.test@civicfix.org";
        String ipAddress = "192.168.200.1";

        OffsetDateTime now = OffsetDateTime.now();

        // Tier 1: 5 failures -> 1x duration (900 seconds)
        for (int i = 0; i < 5; i++) {
            loginLockoutService.recordFailedAttempt(identifier, ipAddress);
        }
        LoginAttempt tier1 = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(identifier, ipAddress).orElseThrow();
        assertEquals(5, tier1.getFailedAttempts());
        assertNotNull(tier1.getLockedUntil());
        long tier1Duration = Duration.between(now, tier1.getLockedUntil()).getSeconds();
        assertTrue(tier1Duration >= 890 && tier1Duration <= 910, "Tier 1 lockout should be ~900s: " + tier1Duration);

        // Tier 2: 10 failures -> 2x duration (1800 seconds)
        for (int i = 0; i < 5; i++) {
            loginLockoutService.recordFailedAttempt(identifier, ipAddress);
        }
        LoginAttempt tier2 = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(identifier, ipAddress).orElseThrow();
        assertEquals(10, tier2.getFailedAttempts());
        long tier2Duration = Duration.between(now, tier2.getLockedUntil()).getSeconds();
        assertTrue(tier2Duration >= 1790 && tier2Duration <= 1810, "Tier 2 lockout should be ~1800s: " + tier2Duration);

        // Tier 3: 15 failures -> 4x duration (3600 seconds)
        for (int i = 0; i < 5; i++) {
            loginLockoutService.recordFailedAttempt(identifier, ipAddress);
        }
        LoginAttempt tier3 = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(identifier, ipAddress).orElseThrow();
        assertEquals(15, tier3.getFailedAttempts());
        long tier3Duration = Duration.between(now, tier3.getLockedUntil()).getSeconds();
        assertTrue(tier3Duration >= 3590 && tier3Duration <= 3610, "Tier 3 lockout should be ~3600s: " + tier3Duration);

        // Verify Expiry: Manually update locked_until to past to simulate time passing
        tier3.setLockedUntil(OffsetDateTime.now().minusMinutes(2));
        loginAttemptRepository.saveAndFlush(tier3);
        rateLimiterService.clearAll(); // clear in-memory

        // Lockout is now expired; login check must pass
        assertDoesNotThrow(() -> loginLockoutService.checkLockout(identifier, ipAddress),
                "Expired lockout must permit login check");
    }
}
