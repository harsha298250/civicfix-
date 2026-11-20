package com.civicfix.security.ratelimit;

import com.civicfix.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Persistent Database-Backed Login Lockout Tests (ISSUE-SEC-03)")
class PersistentLoginLockoutTest {

    private RateLimiterService rateLimiterService;
    private RateLimitProperties properties;
    private FakeLoginAttemptRepository loginAttemptRepository;
    private LoginLockoutService loginLockoutService;

    static class FakeLoginAttemptRepository implements LoginAttemptRepository {
        Map<String, LoginAttempt> storage = new HashMap<>();
        int saveCount = 0;
        int deleteCount = 0;

        private String key(String id, String ip) {
            return (id != null ? id.toLowerCase().trim() : "") + "|" + (ip != null ? ip.trim() : "");
        }

        @Override
        public Optional<LoginAttempt> findByIdentifierIgnoreCaseAndIpAddress(String identifier, String ipAddress) {
            return Optional.ofNullable(storage.get(key(identifier, ipAddress)));
        }

        @Override
        public Optional<LoginAttempt> findActiveLockoutByIdentifier(String identifier, OffsetDateTime now) {
            String norm = identifier != null ? identifier.toLowerCase().trim() : "";
            for (LoginAttempt attempt : storage.values()) {
                if (attempt.getIdentifier().equalsIgnoreCase(norm) && attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(now)) {
                    return Optional.of(attempt);
                }
            }
            return Optional.empty();
        }

        @Override
        public void deleteByIdentifierAndIpAddress(String identifier, String ipAddress) {
            deleteCount++;
            storage.remove(key(identifier, ipAddress));
        }

        @Override
        public void deleteAllByIdentifier(String identifier) {
            String norm = identifier != null ? identifier.toLowerCase().trim() : "";
            storage.entrySet().removeIf(e -> e.getValue().getIdentifier().equalsIgnoreCase(norm));
        }

        @Override
        public long countRecentAttemptsByIdentifier(String identifier, OffsetDateTime since) {
            return sumRecentAttemptsByIdentifier(identifier, since);
        }

        @Override
        public long sumRecentAttemptsByIdentifier(String identifier, OffsetDateTime since) {
            String norm = identifier != null ? identifier.toLowerCase().trim() : "";
            return storage.values().stream()
                    .filter(a -> a.getIdentifier().equalsIgnoreCase(norm) && a.getLastAttemptAt().isAfter(since))
                    .mapToLong(LoginAttempt::getFailedAttempts)
                    .sum();
        }

        @Override
        public long sumRecentAttemptsByIpAddress(String ipAddress, OffsetDateTime since) {
            String normIp = ipAddress != null ? ipAddress.trim() : "";
            return storage.values().stream()
                    .filter(a -> a.getIpAddress().equalsIgnoreCase(normIp) && a.getLastAttemptAt().isAfter(since))
                    .mapToLong(LoginAttempt::getFailedAttempts)
                    .sum();
        }

        @Override
        public Optional<OffsetDateTime> findLatestAttemptByIdentifier(String identifier, OffsetDateTime since) {
            String norm = identifier != null ? identifier.toLowerCase().trim() : "";
            return storage.values().stream()
                    .filter(a -> a.getIdentifier().equalsIgnoreCase(norm) && a.getLastAttemptAt().isAfter(since))
                    .map(LoginAttempt::getLastAttemptAt)
                    .max(OffsetDateTime::compareTo);
        }

        @Override
        public Optional<OffsetDateTime> findLatestAttemptByIpAddress(String ipAddress, OffsetDateTime since) {
            String normIp = ipAddress != null ? ipAddress.trim() : "";
            return storage.values().stream()
                    .filter(a -> a.getIpAddress().equalsIgnoreCase(normIp) && a.getLastAttemptAt().isAfter(since))
                    .map(LoginAttempt::getLastAttemptAt)
                    .max(OffsetDateTime::compareTo);
        }

        @Override
        public int recordFailedAttemptAtomic(String identifier, String ipAddress, OffsetDateTime now, OffsetDateTime windowStart, int maxAttempts, long baseDurationSeconds) {
            saveCount++;
            String k = key(identifier, ipAddress);
            LoginAttempt attempt = storage.get(k);
            if (attempt == null) {
                attempt = new LoginAttempt(identifier, ipAddress);
                attempt.setId(UUID.randomUUID());
                attempt.setFailedAttempts(1);
                attempt.setFirstAttemptAt(now);
                attempt.setLastAttemptAt(now);
                if (1 >= maxAttempts) {
                    attempt.setLockedUntil(now.plusSeconds(baseDurationSeconds));
                }
            } else {
                if (attempt.getLastAttemptAt().isBefore(windowStart)) {
                    attempt.setFailedAttempts(1);
                    attempt.setFirstAttemptAt(now);
                } else {
                    attempt.setFailedAttempts(attempt.getFailedAttempts() + 1);
                }
                attempt.setLastAttemptAt(now);
                if (attempt.getFailedAttempts() >= maxAttempts) {
                    long multiplier = 1;
                    if (attempt.getFailedAttempts() >= maxAttempts * 3) multiplier = 4;
                    else if (attempt.getFailedAttempts() >= maxAttempts * 2) multiplier = 2;
                    attempt.setLockedUntil(now.plusSeconds(baseDurationSeconds * multiplier));
                }
            }
            storage.put(k, attempt);
            return 1;
        }

        @Override
        public int deleteExpiredAttempts(OffsetDateTime retentionCutoff, OffsetDateTime now, int batchSize) {
            List<String> toRemove = storage.entrySet().stream()
                    .filter(e -> {
                        LoginAttempt a = e.getValue();
                        boolean notLocked = a.getLockedUntil() == null || !a.getLockedUntil().isAfter(now);
                        boolean expired = a.getLastAttemptAt().isBefore(retentionCutoff);
                        return notLocked && expired;
                    })
                    .limit(batchSize)
                    .map(Map.Entry::getKey)
                    .toList();
            toRemove.forEach(storage::remove);
            deleteCount += toRemove.size();
            return toRemove.size();
        }

        @Override
        public <S extends LoginAttempt> S save(S entity) {
            saveCount++;
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            storage.put(key(entity.getIdentifier(), entity.getIpAddress()), entity);
            return entity;
        }

        @Override public void flush() {}
        @Override public <S extends LoginAttempt> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends LoginAttempt> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<LoginAttempt> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public LoginAttempt getOne(UUID uuid) { return null; }
        @Override public LoginAttempt getById(UUID uuid) { return null; }
        @Override public LoginAttempt getReferenceById(UUID uuid) { return null; }
        @Override public <S extends LoginAttempt> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends LoginAttempt> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends LoginAttempt> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends LoginAttempt> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends LoginAttempt> long count(Example<S> example) { return 0; }
        @Override public <S extends LoginAttempt> boolean exists(Example<S> example) { return false; }
        @Override public <S extends LoginAttempt, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends LoginAttempt> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public Optional<LoginAttempt> findById(UUID uuid) { return Optional.empty(); }
        @Override public boolean existsById(UUID uuid) { return false; }
        @Override public List<LoginAttempt> findAll() { return List.of(); }
        @Override public List<LoginAttempt> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public long count() { return storage.size(); }
        @Override public void deleteById(UUID uuid) {}
        @Override public void delete(LoginAttempt entity) {}
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends LoginAttempt> entities) {}
        @Override public void deleteAll() { storage.clear(); }
        @Override public List<LoginAttempt> findAll(Sort sort) { return List.of(); }
        @Override public Page<LoginAttempt> findAll(Pageable pageable) { return Page.empty(); }
    }

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        properties = new RateLimitProperties();

        // 5 failed attempts per 15 minutes for IP
        RateLimitProperties.LimitConfig loginConfig = new RateLimitProperties.LimitConfig();
        loginConfig.setCapacity(5);
        loginConfig.setDuration(Duration.ofMinutes(15));
        properties.setLogin(loginConfig);

        // 20 failed attempts per 1 hour for account
        RateLimitProperties.LimitConfig accountConfig = new RateLimitProperties.LimitConfig();
        accountConfig.setCapacity(20);
        accountConfig.setDuration(Duration.ofHours(1));
        properties.setAccountLogin(accountConfig);

        loginAttemptRepository = new FakeLoginAttemptRepository();
        loginLockoutService = new LoginLockoutService(rateLimiterService, properties, loginAttemptRepository);
    }

    @Test
    @DisplayName("Active database lockout blocks login attempt even after in-memory cache reset (process restart)")
    void activeDatabaseLockoutSurvivesProcessRestart() {
        String identifier = "target.user@civicfix.org";
        String ipAddress = "192.168.1.100";

        LoginAttempt dbAttempt = new LoginAttempt(identifier, ipAddress);
        dbAttempt.setFailedAttempts(5);
        dbAttempt.setLockedUntil(OffsetDateTime.now().plusMinutes(10)); // Locked in DB for another 10 mins

        loginAttemptRepository.save(dbAttempt);

        // In-memory cache is empty (simulating cold JVM startup after restart)
        rateLimiterService.clearAll();

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                loginLockoutService.checkLockout(identifier, ipAddress)
        );

        assertTrue(ex.getMessage().contains("Account temporarily locked due to multiple failed login attempts"));
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Expired database lockout allows login attempt")
    void expiredDatabaseLockoutAllowsLogin() {
        String identifier = "target.user@civicfix.org";
        String ipAddress = "192.168.1.100";

        LoginAttempt dbAttempt = new LoginAttempt(identifier, ipAddress);
        dbAttempt.setFailedAttempts(5);
        dbAttempt.setLockedUntil(OffsetDateTime.now().minusMinutes(1)); // Expired lockout

        loginAttemptRepository.save(dbAttempt);

        assertDoesNotThrow(() -> loginLockoutService.checkLockout(identifier, ipAddress));
    }

    @Test
    @DisplayName("Failed attempts are recorded to persistent database repository")
    void failedAttemptRecordedToRepository() {
        String identifier = "target.user@civicfix.org";
        String ipAddress = "192.168.1.100";

        loginLockoutService.recordFailedAttempt(identifier, ipAddress);

        assertEquals(1, loginAttemptRepository.saveCount);
        assertTrue(loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(identifier, ipAddress).isPresent());
    }

    @Test
    @DisplayName("Successful login clears database records and resets lockout")
    void successfulLoginClearsRepository() {
        String identifier = "target.user@civicfix.org";
        String ipAddress = "192.168.1.100";

        LoginAttempt dbAttempt = new LoginAttempt(identifier, ipAddress);
        loginAttemptRepository.save(dbAttempt);

        loginLockoutService.recordSuccessfulLogin(identifier, ipAddress);

        assertEquals(1, loginAttemptRepository.deleteCount);
        assertFalse(loginAttemptRepository.findByIdentifierIgnoreCaseAndIpAddress(identifier, ipAddress).isPresent());
    }
}
