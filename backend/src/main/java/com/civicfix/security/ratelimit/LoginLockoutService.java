package com.civicfix.security.ratelimit;

import com.civicfix.common.exception.AuthenticationServiceUnavailableException;
import com.civicfix.common.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class LoginLockoutService {

    private static final Logger log = LoggerFactory.getLogger(LoginLockoutService.class);

    private static final int LOCK_NAMESPACE_ACCOUNT = 1001;
    private static final int LOCK_NAMESPACE_IP = 1002;

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AccountLoginAttemptRepository accountLoginAttemptRepository;
    private final IpLoginAttemptRepository ipLoginAttemptRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LoginLockoutService(RateLimiterService rateLimiterService,
                               RateLimitProperties properties,
                               @Autowired(required = false) LoginAttemptRepository loginAttemptRepository,
                               @Autowired(required = false) AccountLoginAttemptRepository accountLoginAttemptRepository,
                               @Autowired(required = false) IpLoginAttemptRepository ipLoginAttemptRepository,
                               @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.loginAttemptRepository = loginAttemptRepository;
        this.accountLoginAttemptRepository = accountLoginAttemptRepository;
        this.ipLoginAttemptRepository = ipLoginAttemptRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginLockoutService(RateLimiterService rateLimiterService,
                               RateLimitProperties properties,
                               LoginAttemptRepository loginAttemptRepository,
                               AccountLoginAttemptRepository accountLoginAttemptRepository,
                               IpLoginAttemptRepository ipLoginAttemptRepository) {
        this(rateLimiterService, properties, loginAttemptRepository, accountLoginAttemptRepository, ipLoginAttemptRepository, null);
    }

    public LoginLockoutService(RateLimiterService rateLimiterService,
                               RateLimitProperties properties,
                               LoginAttemptRepository loginAttemptRepository) {
        this(rateLimiterService, properties, loginAttemptRepository, null, null, null);
    }

    public LoginLockoutService(RateLimiterService rateLimiterService, RateLimitProperties properties) {
        this(rateLimiterService, properties, null, null, null, null);
    }

    /**
     * Acquires PostgreSQL-backed transaction-level advisory locks to coordinate lockout check,
     * password verification, and failure recording for the given account and IP.
     *
     * Strict partial ordering: always acquire Account lock (1001) first, then IP lock (1002) second.
     * This mathematical hierarchy guarantees no circular-wait deadlock cycles can ever form.
     * Locks are automatically and safely released when the enclosing transaction commits or rolls back.
     */
    @Transactional
    public void acquireCoordinationLocks(String identifier, String ipAddress) {
        if (jdbcTemplate == null) {
            return;
        }
        String normId = identifier != null ? identifier.trim().toLowerCase() : "";
        String normIp = ipAddress != null ? ipAddress.trim() : "";

        if (!normId.isEmpty()) {
            jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
                try (PreparedStatement ps = con.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))")) {
                    ps.setInt(1, LOCK_NAMESPACE_ACCOUNT);
                    ps.setString(2, normId);
                    ps.execute();
                }
                return null;
            });
        }

        if (!normIp.isEmpty()) {
            jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
                try (PreparedStatement ps = con.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))")) {
                    ps.setInt(1, LOCK_NAMESPACE_IP);
                    ps.setString(2, normIp);
                    ps.execute();
                }
                return null;
            });
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS, noRollbackFor = RateLimitExceededException.class)
    public void checkLockout(String identifier, String ipAddress) {
        String normId = identifier != null ? identifier.trim().toLowerCase() : "";
        String normIp = ipAddress != null ? ipAddress.trim() : "unknown";
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Persistent Database Check (Survives process restart / horizontal instances)
        // A. Check IP + identifier lockout (Specific attack on this identifier from this IP)
        if (loginAttemptRepository != null) {
            Optional<LoginAttempt> persistentAttempt = loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(normId, normIp);
            if (persistentAttempt.isPresent()) {
                LoginAttempt attempt = persistentAttempt.get();
                if (attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(now)) {
                    long retryAfterSeconds = Math.max(1, Duration.between(now, attempt.getLockedUntil()).getSeconds());
                    log.warn("Rate limit block (DB-backed): IP-based login lockout active for identifier [{}] from IP [{}]. Retry after {} seconds.",
                            sanitizeForLog(identifier), ipAddress, retryAfterSeconds);
                    throw new RateLimitExceededException(
                            "Account temporarily locked due to multiple failed login attempts. Please try again in " + retryAfterSeconds + " seconds.",
                            retryAfterSeconds
                    );
                }
            }
        }

        // B. Check IP-wide lockout across all accounts (Password Spraying protection)
        if (ipLoginAttemptRepository != null) {
            Optional<IpLoginAttempt> ipAttempt = ipLoginAttemptRepository.findByIpAddress(normIp);
            if (ipAttempt.isPresent()) {
                IpLoginAttempt attempt = ipAttempt.get();
                Duration ipDuration = properties.getIpLogin().getDuration();
                OffsetDateTime ipWindowStart = now.minus(ipDuration);
                boolean isLocked = attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(now);
                boolean thresholdExceeded = attempt.getLastAttemptAt().isAfter(ipWindowStart)
                        && attempt.getFailedAttempts() >= properties.getIpLogin().getCapacity();

                if (isLocked || thresholdExceeded) {
                    long retryAfterSeconds = isLocked
                            ? Math.max(1, Duration.between(now, attempt.getLockedUntil()).getSeconds())
                            : Math.max(1, Duration.between(now, attempt.getLastAttemptAt().plus(ipDuration)).getSeconds());
                    log.warn("Rate limit block (DB-backed): IP-wide login lockout active for IP [{}]. Failures: {}. Retry after {} seconds.",
                            normIp, attempt.getFailedAttempts(), retryAfterSeconds);
                    throw new RateLimitExceededException(
                            "Too many failed login attempts from this IP address. Please try again in " + retryAfterSeconds + " seconds.",
                            retryAfterSeconds
                    );
                }
            }
        } else if (loginAttemptRepository != null) {
            Duration ipDuration = properties.getIpLogin().getDuration();
            OffsetDateTime ipWindowStart = now.minus(ipDuration);
            long ipFailures = loginAttemptRepository.sumRecentAttemptsByIpAddress(normIp, ipWindowStart);
            if (ipFailures >= properties.getIpLogin().getCapacity()) {
                Optional<OffsetDateTime> latestIpAttempt = loginAttemptRepository.findLatestAttemptByIpAddress(normIp, ipWindowStart);
                long retryAfterSeconds = latestIpAttempt
                        .map(latest -> Math.max(1, Duration.between(now, latest.plus(ipDuration)).getSeconds()))
                        .orElse(ipDuration.toSeconds());
                log.warn("Rate limit block (DB-backed): IP-wide login lockout active for IP [{}]. Total failures: {}. Retry after {} seconds.",
                        normIp, ipFailures, retryAfterSeconds);
                throw new RateLimitExceededException(
                        "Too many failed login attempts from this IP address. Please try again in " + retryAfterSeconds + " seconds.",
                        retryAfterSeconds
                );
            }
        }

        // C. Check account-wide lockout across all IPs (Distributed Brute Force protection)
        if (accountLoginAttemptRepository != null) {
            Optional<AccountLoginAttempt> accountAttempt = accountLoginAttemptRepository.findByIdentifierIgnoreCase(normId);
            if (accountAttempt.isPresent()) {
                AccountLoginAttempt attempt = accountAttempt.get();
                Duration accountDuration = properties.getAccountLogin().getDuration();
                OffsetDateTime accountWindowStart = now.minus(accountDuration);
                boolean isLocked = attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(now);
                boolean thresholdExceeded = attempt.getLastAttemptAt().isAfter(accountWindowStart)
                        && attempt.getFailedAttempts() >= properties.getAccountLogin().getCapacity();

                if (isLocked || thresholdExceeded) {
                    long retryAfterSeconds = isLocked
                            ? Math.max(1, Duration.between(now, attempt.getLockedUntil()).getSeconds())
                            : Math.max(1, Duration.between(now, attempt.getLastAttemptAt().plus(accountDuration)).getSeconds());
                    log.warn("Rate limit block (DB-backed): Account-wide login lockout active for identifier [{}]. Failures: {}. Retry after {} seconds.",
                            sanitizeForLog(identifier), attempt.getFailedAttempts(), retryAfterSeconds);
                    throw new RateLimitExceededException(
                            "Account temporarily locked due to multiple failed login attempts. Please try again in " + retryAfterSeconds + " seconds.",
                            retryAfterSeconds
                    );
                }
            }
        } else if (loginAttemptRepository != null) {
            Duration accountDuration = properties.getAccountLogin().getDuration();
            OffsetDateTime accountWindowStart = now.minus(accountDuration);
            long accountFailures = loginAttemptRepository.sumRecentAttemptsByIdentifier(normId, accountWindowStart);
            if (accountFailures >= properties.getAccountLogin().getCapacity()) {
                Optional<OffsetDateTime> latestAccountAttempt = loginAttemptRepository.findLatestAttemptByIdentifier(normId, accountWindowStart);
                long retryAfterSeconds = latestAccountAttempt
                        .map(latest -> Math.max(1, Duration.between(now, latest.plus(accountDuration)).getSeconds()))
                        .orElse(accountDuration.toSeconds());
                log.warn("Rate limit block (DB-backed): Account-wide login lockout active for identifier [{}]. Total failures: {}. Retry after {} seconds.",
                        sanitizeForLog(identifier), accountFailures, retryAfterSeconds);
                throw new RateLimitExceededException(
                        "Account temporarily locked due to multiple failed login attempts. Please try again in " + retryAfterSeconds + " seconds.",
                        retryAfterSeconds
                );
            }
        }

        // 2. In-memory Token Bucket Check
        // IP + identifier limit (default 5 failed attempts per 15 mins)
        String ipKey = buildIpKey(identifier, ipAddress);
        Bucket ipBucket = rateLimiterService.resolveBucket(
                ipKey,
                properties.getLogin().getCapacity(),
                properties.getLogin().getDuration()
        );

        EstimationProbe ipProbe = ipBucket.estimateAbilityToConsume(1);
        if (!ipProbe.canBeConsumed()) {
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(ipProbe.getNanosToWaitForRefill()));
            log.warn("Rate limit block: IP-based login lockout triggered for identifier [{}] from IP [{}]. Retry after {} seconds.",
                    sanitizeForLog(identifier), ipAddress, retryAfterSeconds);
            throw new RateLimitExceededException(
                    "Account temporarily locked due to multiple failed login attempts. Please try again in " + retryAfterSeconds + " seconds.",
                    retryAfterSeconds
            );
        }

        // Pure IP-level limit across all accounts (default 30 failed attempts per 15 mins)
        String pureIpKey = buildPureIpKey(ipAddress);
        Bucket pureIpBucket = rateLimiterService.resolveBucket(
                pureIpKey,
                properties.getIpLogin().getCapacity(),
                properties.getIpLogin().getDuration()
        );

        EstimationProbe pureIpProbe = pureIpBucket.estimateAbilityToConsume(1);
        if (!pureIpProbe.canBeConsumed()) {
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(pureIpProbe.getNanosToWaitForRefill()));
            log.warn("Rate limit block: Pure-IP login lockout triggered for IP [{}]. Retry after {} seconds.",
                    ipAddress, retryAfterSeconds);
            throw new RateLimitExceededException(
                    "Too many failed login attempts from this IP address. Please try again in " + retryAfterSeconds + " seconds.",
                    retryAfterSeconds
            );
        }

        // Per-account limit across all IPs (default 20 failed attempts per hour)
        String accountKey = buildAccountKey(identifier);
        Bucket accountBucket = rateLimiterService.resolveBucket(
                accountKey,
                properties.getAccountLogin().getCapacity(),
                properties.getAccountLogin().getDuration()
        );

        EstimationProbe accountProbe = accountBucket.estimateAbilityToConsume(1);
        if (!accountProbe.canBeConsumed()) {
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(accountProbe.getNanosToWaitForRefill()));
            log.warn("Rate limit block: Per-account login lockout triggered for identifier [{}]. Retry after {} seconds.",
                    sanitizeForLog(identifier), retryAfterSeconds);
            throw new RateLimitExceededException(
                    "Account temporarily locked due to multiple failed login attempts. Please try again in " + retryAfterSeconds + " seconds.",
                    retryAfterSeconds
            );
        }
    }

    @Transactional
    public void recordFailedAttempt(String identifier, String ipAddress) {
        String normId = identifier != null ? identifier.trim().toLowerCase() : "";
        String normIp = ipAddress != null ? ipAddress.trim() : "unknown";
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Persistent Database Record (Concurrency-safe atomic UPSERTs)
        // Fail-closed policy: Execute all counter updates within the active transaction.
        // If any database operation fails, do not swallow the exception; fail closed,
        // log detailed diagnostic error on the server, and trigger transaction rollback.
        if (loginAttemptRepository != null || accountLoginAttemptRepository != null || ipLoginAttemptRepository != null) {
            try {
                // A. Per (identifier, IP) pair atomic record
                if (loginAttemptRepository != null) {
                    OffsetDateTime windowStart = now.minus(properties.getLogin().getDuration());
                    int maxAttempts = (int) properties.getLogin().getCapacity();
                    long baseDurationSeconds = properties.getLogin().getDuration().toSeconds();

                    loginAttemptRepository.recordFailedAttemptAtomic(
                            normId,
                            normIp,
                            now,
                            windowStart,
                            maxAttempts,
                            baseDurationSeconds
                    );
                }

                // B. Account-wide atomic record across all IPs
                if (accountLoginAttemptRepository != null && !normId.isEmpty()) {
                    OffsetDateTime windowStart = now.minus(properties.getAccountLogin().getDuration());
                    int maxAttempts = (int) properties.getAccountLogin().getCapacity();
                    long baseDurationSeconds = properties.getAccountLogin().getDuration().toSeconds();

                    accountLoginAttemptRepository.recordAccountFailedAttemptAtomic(
                            normId,
                            now,
                            windowStart,
                            maxAttempts,
                            baseDurationSeconds
                    );
                }

                // C. IP-wide atomic record across all accounts
                if (ipLoginAttemptRepository != null && !normIp.isEmpty()) {
                    OffsetDateTime windowStart = now.minus(properties.getIpLogin().getDuration());
                    int maxAttempts = (int) properties.getIpLogin().getCapacity();
                    long baseDurationSeconds = properties.getIpLogin().getDuration().toSeconds();

                    ipLoginAttemptRepository.recordIpFailedAttemptAtomic(
                            normIp,
                            now,
                            windowStart,
                            maxAttempts,
                            baseDurationSeconds
                    );
                }
            } catch (Exception ex) {
                log.error("CRITICAL: Failed to record login attempt to database for identifier [{}] from IP [{}]. Database operation failed: {}",
                        sanitizeForLog(identifier), normIp, ex.getMessage(), ex);
                throw new AuthenticationServiceUnavailableException(
                        "Authentication service is temporarily unavailable. Please try again shortly.",
                        ex
                );
            }
        }

        // 2. In-memory Token Bucket Decrement
        String ipKey = buildIpKey(identifier, ipAddress);
        Bucket ipBucket = rateLimiterService.resolveBucket(
                ipKey,
                properties.getLogin().getCapacity(),
                properties.getLogin().getDuration()
        );
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        long ipRemaining = ipProbe.getRemainingTokens();

        String pureIpKey = buildPureIpKey(ipAddress);
        Bucket pureIpBucket = rateLimiterService.resolveBucket(
                pureIpKey,
                properties.getIpLogin().getCapacity(),
                properties.getIpLogin().getDuration()
        );
        ConsumptionProbe pureIpProbe = pureIpBucket.tryConsumeAndReturnRemaining(1);
        long pureIpRemaining = pureIpProbe.getRemainingTokens();

        String accountKey = buildAccountKey(identifier);
        Bucket accountBucket = rateLimiterService.resolveBucket(
                accountKey,
                properties.getAccountLogin().getCapacity(),
                properties.getAccountLogin().getDuration()
        );
        ConsumptionProbe accountProbe = accountBucket.tryConsumeAndReturnRemaining(1);
        long accountRemaining = accountProbe.getRemainingTokens();

        log.warn("Failed login attempt for identifier [{}] from IP [{}]. IP-Account remaining: {}, Pure-IP remaining: {}, Account remaining: {}.",
                sanitizeForLog(identifier), ipAddress, ipRemaining, pureIpRemaining, accountRemaining);
    }

    @Transactional
    public void recordSuccessfulLogin(String identifier, String ipAddress) {
        String normId = identifier != null ? identifier.trim().toLowerCase() : "";
        String normIp = ipAddress != null ? ipAddress.trim() : "unknown";

        // 1. Clear database attempts for this specific (identifier, IP) pair
        if (loginAttemptRepository != null) {
            try {
                loginAttemptRepository.deleteByIdentifierAndIpAddress(normId, normIp);
                // CRITICAL FIX: Do NOT delete all records for this identifier across other IPs!
                // Wiping other IPs' records erased attacker IP abuse history and weakened password spraying protection.
            } catch (Exception e) {
                log.error("Failed to clear login attempts in database for {}: {}", sanitizeForLog(identifier), e.getMessage());
            }
        }

        // 2. Clear account-specific failure state (allows legitimate user to authenticate freely)
        if (accountLoginAttemptRepository != null && !normId.isEmpty()) {
            try {
                accountLoginAttemptRepository.deleteByIdentifierIgnoreCase(normId);
            } catch (Exception e) {
                log.error("Failed to clear account login attempts in database for {}: {}", sanitizeForLog(identifier), e.getMessage());
            }
        }

        // CRITICAL FIX: Do NOT touch ipLoginAttemptRepository!
        // IP abuse history remains intact so attackers cannot evade IP lockout via valid logins elsewhere.

        // 3. Reset in-memory buckets for this pair and this account
        rateLimiterService.reset(buildIpKey(identifier, ipAddress));
        rateLimiterService.reset(buildAccountKey(identifier));
        // Note: pureIpKey is intentionally not reset to protect against password spraying.
    }

    /**
     * Bounded cleanup of expired login attempt records.
     * Guarantees active lockouts are never removed.
     *
     * @param retentionPeriod retention cutoff before which non-locked records are eligible for deletion
     * @param batchSize       maximum number of records to delete in one batch
     * @return count of deleted records
     */
    @Transactional
    public int cleanupExpiredAttempts(Duration retentionPeriod, int batchSize) {
        int totalDeleted = 0;
        int effectiveBatch = batchSize > 0 ? Math.min(batchSize, 1000) : 500;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minus(retentionPeriod != null ? retentionPeriod : Duration.ofHours(24));

        if (loginAttemptRepository != null) {
            try {
                int deleted = loginAttemptRepository.deleteExpiredAttempts(cutoff, now, effectiveBatch);
                totalDeleted += deleted;
            } catch (Exception e) {
                log.error("Failed to cleanup expired login attempts: {}", e.getMessage());
            }
        }

        if (accountLoginAttemptRepository != null) {
            try {
                int deleted = accountLoginAttemptRepository.deleteExpiredAttempts(cutoff, now, effectiveBatch);
                totalDeleted += deleted;
            } catch (Exception e) {
                log.error("Failed to cleanup expired account login attempts: {}", e.getMessage());
            }
        }

        if (ipLoginAttemptRepository != null) {
            try {
                int deleted = ipLoginAttemptRepository.deleteExpiredAttempts(cutoff, now, effectiveBatch);
                totalDeleted += deleted;
            } catch (Exception e) {
                log.error("Failed to cleanup expired IP login attempts: {}", e.getMessage());
            }
        }

        if (totalDeleted > 0) {
            log.info("Cleaned up {} expired login attempt records older than {}.", totalDeleted, cutoff);
        }
        return totalDeleted;
    }

    @Transactional
    public int cleanupExpiredAttempts() {
        return cleanupExpiredAttempts(Duration.ofHours(24), 500);
    }

    @Scheduled(cron = "${civicfix.rate-limit.cleanup-cron:0 0 3 * * *}")
    public void scheduledCleanup() {
        cleanupExpiredAttempts();
    }

    private String buildIpKey(String identifier, String ipAddress) {
        String normId = identifier != null ? identifier.trim().toLowerCase() : "";
        String normIp = ipAddress != null ? ipAddress.trim() : "unknown";
        return "login_lockout:" + normId + ":" + normIp;
    }

    private String buildPureIpKey(String ipAddress) {
        String normIp = ipAddress != null ? ipAddress.trim() : "unknown";
        return "login_lockout:ip:" + normIp;
    }

    private String buildAccountKey(String identifier) {
        String normId = identifier != null ? identifier.trim().toLowerCase() : "";
        return "login_lockout:account:" + normId;
    }

    private String sanitizeForLog(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\\r\\n]", "_");
    }
}
