package com.civicfix.security.ratelimit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    Optional<LoginAttempt> findByIdentifierIgnoreCaseAndIpAddress(String identifier, String ipAddress);

    @Query("SELECT COUNT(l) FROM LoginAttempt l WHERE LOWER(l.identifier) = LOWER(:identifier) AND l.lastAttemptAt > :since")
    long countRecentAttemptsByIdentifier(@Param("identifier") String identifier, @Param("since") OffsetDateTime since);

    @Query("SELECT COALESCE(SUM(l.failedAttempts), 0) FROM LoginAttempt l WHERE LOWER(l.identifier) = LOWER(:identifier) AND l.lastAttemptAt > :since")
    long sumRecentAttemptsByIdentifier(@Param("identifier") String identifier, @Param("since") OffsetDateTime since);

    @Query("SELECT COALESCE(SUM(l.failedAttempts), 0) FROM LoginAttempt l WHERE l.ipAddress = :ipAddress AND l.lastAttemptAt > :since")
    long sumRecentAttemptsByIpAddress(@Param("ipAddress") String ipAddress, @Param("since") OffsetDateTime since);

    @Query("SELECT MAX(l.lastAttemptAt) FROM LoginAttempt l WHERE LOWER(l.identifier) = LOWER(:identifier) AND l.lastAttemptAt > :since")
    Optional<OffsetDateTime> findLatestAttemptByIdentifier(@Param("identifier") String identifier, @Param("since") OffsetDateTime since);

    @Query("SELECT MAX(l.lastAttemptAt) FROM LoginAttempt l WHERE l.ipAddress = :ipAddress AND l.lastAttemptAt > :since")
    Optional<OffsetDateTime> findLatestAttemptByIpAddress(@Param("ipAddress") String ipAddress, @Param("since") OffsetDateTime since);

    @Query("SELECT l FROM LoginAttempt l WHERE LOWER(l.identifier) = LOWER(:identifier) AND l.lockedUntil > :now")
    Optional<LoginAttempt> findActiveLockoutByIdentifier(@Param("identifier") String identifier, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM LoginAttempt l WHERE LOWER(l.identifier) = LOWER(:identifier) AND l.ipAddress = :ipAddress")
    void deleteByIdentifierAndIpAddress(@Param("identifier") String identifier, @Param("ipAddress") String ipAddress);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM LoginAttempt l WHERE LOWER(l.identifier) = LOWER(:identifier)")
    void deleteAllByIdentifier(@Param("identifier") String identifier);

    /**
     * Concurrency-safe atomic UPSERT for recording failed login attempts.
     * Uses PostgreSQL INSERT ... ON CONFLICT (identifier, ip_address) DO UPDATE
     * to prevent lost updates, handle unique constraint races, and calculate lockouts atomically.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO login_attempts (id, identifier, ip_address, failed_attempts, first_attempt_at, last_attempt_at, locked_until)
        VALUES (gen_random_uuid(), :identifier, :ipAddress, 1,
                CAST(:now AS TIMESTAMP WITH TIME ZONE),
                CAST(:now AS TIMESTAMP WITH TIME ZONE),
                CASE
                    WHEN 1 >= :maxAttempts THEN CAST(:now AS TIMESTAMP WITH TIME ZONE) + (CAST(:baseDurationSeconds AS double precision) * INTERVAL '1 second')
                    ELSE NULL
                END)
        ON CONFLICT (identifier, ip_address)
        DO UPDATE SET
            failed_attempts = CASE
                WHEN login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1
                ELSE login_attempts.failed_attempts + 1
            END,
            first_attempt_at = CASE
                WHEN login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN CAST(:now AS TIMESTAMP WITH TIME ZONE)
                ELSE login_attempts.first_attempt_at
            END,
            last_attempt_at = CAST(:now AS TIMESTAMP WITH TIME ZONE),
            locked_until = CASE
                WHEN (CASE WHEN login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1 ELSE login_attempts.failed_attempts + 1 END) >= :maxAttempts
                THEN CAST(:now AS TIMESTAMP WITH TIME ZONE) + (
                    (CAST(:baseDurationSeconds AS double precision) *
                        CASE
                            WHEN (CASE WHEN login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1 ELSE login_attempts.failed_attempts + 1 END) >= (:maxAttempts * 3) THEN 4
                            WHEN (CASE WHEN login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1 ELSE login_attempts.failed_attempts + 1 END) >= (:maxAttempts * 2) THEN 2
                            ELSE 1
                        END
                    ) * INTERVAL '1 second'
                )
                ELSE login_attempts.locked_until
            END
        """, nativeQuery = true)
    int recordFailedAttemptAtomic(
            @Param("identifier") String identifier,
            @Param("ipAddress") String ipAddress,
            @Param("now") OffsetDateTime now,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("maxAttempts") int maxAttempts,
            @Param("baseDurationSeconds") long baseDurationSeconds
    );

    /**
     * Bounded cleanup of expired login attempt records.
     * Guarantees active lockouts are NEVER deleted.
     */
    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        DELETE FROM login_attempts
        WHERE id IN (
            SELECT id FROM login_attempts
            WHERE (locked_until IS NULL OR locked_until <= CAST(:now AS TIMESTAMP WITH TIME ZONE))
              AND last_attempt_at < CAST(:retentionCutoff AS TIMESTAMP WITH TIME ZONE)
            ORDER BY last_attempt_at ASC
            LIMIT :batchSize
        )
        """, nativeQuery = true)
    int deleteExpiredAttempts(
            @Param("retentionCutoff") OffsetDateTime retentionCutoff,
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize
    );
}
