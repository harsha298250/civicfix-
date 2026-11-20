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
public interface AccountLoginAttemptRepository extends JpaRepository<AccountLoginAttempt, UUID> {

    Optional<AccountLoginAttempt> findByIdentifierIgnoreCase(String identifier);

    Optional<AccountLoginAttempt> findByIdentifier(String identifier);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AccountLoginAttempt a WHERE LOWER(a.identifier) = LOWER(:identifier)")
    void deleteByIdentifierIgnoreCase(@Param("identifier") String identifier);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO account_login_attempts (id, identifier, failed_attempts, first_attempt_at, last_attempt_at, locked_until)
        VALUES (gen_random_uuid(), :identifier, 1,
                CAST(:now AS TIMESTAMP WITH TIME ZONE),
                CAST(:now AS TIMESTAMP WITH TIME ZONE),
                CASE
                    WHEN 1 >= :maxAttempts THEN CAST(:now AS TIMESTAMP WITH TIME ZONE) + (CAST(:baseDurationSeconds AS double precision) * INTERVAL '1 second')
                    ELSE NULL
                END)
        ON CONFLICT (identifier)
        DO UPDATE SET
            failed_attempts = CASE
                WHEN account_login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1
                ELSE account_login_attempts.failed_attempts + 1
            END,
            first_attempt_at = CASE
                WHEN account_login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN CAST(:now AS TIMESTAMP WITH TIME ZONE)
                ELSE account_login_attempts.first_attempt_at
            END,
            last_attempt_at = CAST(:now AS TIMESTAMP WITH TIME ZONE),
            locked_until = CASE
                WHEN (CASE WHEN account_login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1 ELSE account_login_attempts.failed_attempts + 1 END) >= :maxAttempts
                THEN CAST(:now AS TIMESTAMP WITH TIME ZONE) + (
                    (CAST(:baseDurationSeconds AS double precision) *
                        CASE
                            WHEN (CASE WHEN account_login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1 ELSE account_login_attempts.failed_attempts + 1 END) >= (:maxAttempts * 3) THEN 4
                            WHEN (CASE WHEN account_login_attempts.last_attempt_at < CAST(:windowStart AS TIMESTAMP WITH TIME ZONE) THEN 1 ELSE account_login_attempts.failed_attempts + 1 END) >= (:maxAttempts * 2) THEN 2
                            ELSE 1
                        END
                    ) * INTERVAL '1 second'
                )
                ELSE account_login_attempts.locked_until
            END
        """, nativeQuery = true)
    int recordAccountFailedAttemptAtomic(
            @Param("identifier") String identifier,
            @Param("now") OffsetDateTime now,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("maxAttempts") int maxAttempts,
            @Param("baseDurationSeconds") long baseDurationSeconds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        DELETE FROM account_login_attempts
        WHERE id IN (
            SELECT id FROM account_login_attempts
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
