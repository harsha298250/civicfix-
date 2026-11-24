package com.civicfix.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * EmailOutboxRepository
 *
 * High-concurrency Spring Data JPA repository for transactional email outbox jobs.
 * Provides atomic two-phase claims using FOR UPDATE SKIP LOCKED and idempotent state transitions.
 */
@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    Optional<EmailOutbox> findByIdempotencyKey(String idempotencyKey);

    Optional<EmailOutbox> findByProviderMessageId(String providerMessageId);

    /**
     * Atomically selects pending or retryable outbox job IDs, or expired processing leases,
     * skipping rows locked by other worker instances.
     */
    @Query(value = "SELECT id FROM email_outbox " +
                   "WHERE (status IN ('PENDING', 'RETRYING') AND next_retry_at <= :now) " +
                   "   OR (status = 'PROCESSING' AND locked_at < :leaseExpiredBefore AND attempts < max_attempts) " +
                   "ORDER BY next_retry_at ASC " +
                   "LIMIT :batchSize FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<UUID> claimJobIds(@Param("now") OffsetDateTime now,
                           @Param("leaseExpiredBefore") OffsetDateTime leaseExpiredBefore,
                           @Param("batchSize") int batchSize);

    /**
     * Marks claimed job IDs as PROCESSING with worker identification and lock timestamp.
     */
    @Transactional
    @Modifying
    @Query("UPDATE EmailOutbox e SET e.status = 'PROCESSING', e.lockedAt = :now, e.lockedBy = :workerId, e.updatedAt = :now WHERE e.id IN :ids")
    int lockJobs(@Param("ids") List<UUID> ids, @Param("workerId") String workerId, @Param("now") OffsetDateTime now);

    /**
     * Settles successful transmission. Uses conditional status update so that a race condition
     * with an earlier webhook delivered/bounced event does not overwrite the final status with ACCEPTED.
     * Scrubs payload_json to {} to ensure tokens are not retained at rest after transmission.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE email_outbox SET " +
                   "status = CASE WHEN status IN ('DELIVERED', 'BOUNCED') THEN status ELSE 'ACCEPTED' END, " +
                   "provider_message_id = :providerMessageId, " +
                   "payload_json = '{}', " +
                   "locked_at = NULL, " +
                   "locked_by = NULL, " +
                   "updated_at = :now " +
                   "WHERE id = :id", nativeQuery = true)
    int settleSuccess(@Param("id") UUID id,
                      @Param("providerMessageId") String providerMessageId,
                      @Param("now") OffsetDateTime now);

    /**
     * Settles retryable transmission failures with exponential backoff.
     */
    @Transactional
    @Modifying
    @Query("UPDATE EmailOutbox e SET " +
           "e.status = CASE WHEN e.attempts + 1 >= e.maxAttempts THEN 'FAILED' ELSE 'RETRYING' END, " +
           "e.attempts = e.attempts + 1, " +
           "e.nextRetryAt = :nextRetryAt, " +
           "e.lastError = :lastError, " +
           "e.lockedAt = NULL, " +
           "e.lockedBy = NULL, " +
           "e.updatedAt = :now " +
           "WHERE e.id = :id")
    int settleRetryableFailure(@Param("id") UUID id,
                               @Param("nextRetryAt") OffsetDateTime nextRetryAt,
                               @Param("lastError") String lastError,
                               @Param("now") OffsetDateTime now);

    /**
     * Settles non-retryable failures (e.g. invalid recipient or 4xx rejection).
     */
    @Transactional
    @Modifying
    @Query("UPDATE EmailOutbox e SET " +
           "e.status = 'FAILED', " +
           "e.attempts = e.attempts + 1, " +
           "e.lastError = :lastError, " +
           "e.lockedAt = NULL, " +
           "e.lockedBy = NULL, " +
           "e.updatedAt = :now " +
           "WHERE e.id = :id")
    int settlePermanentFailure(@Param("id") UUID id,
                               @Param("lastError") String lastError,
                               @Param("now") OffsetDateTime now);

    /**
     * Fails jobs stuck in PROCESSING whose lease has expired and max attempts have been exhausted.
     */
    @Transactional
    @Modifying
    @Query("UPDATE EmailOutbox e SET " +
           "e.status = 'FAILED', " +
           "e.lastError = 'Processing lease expired after max attempts reached', " +
           "e.lockedAt = NULL, " +
           "e.lockedBy = NULL, " +
           "e.updatedAt = :now " +
           "WHERE e.status = 'PROCESSING' AND e.lockedAt < :leaseExpiredBefore AND e.attempts >= e.maxAttempts")
    int failExhaustedLeases(@Param("leaseExpiredBefore") OffsetDateTime leaseExpiredBefore,
                            @Param("now") OffsetDateTime now);

    /**
     * Updates delivery status based on inbound Resend webhooks. Prevents reverting DELIVERED or BOUNCED.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE email_outbox SET " +
                   "status = :newStatus, " +
                   "updated_at = :now " +
                   "WHERE provider_message_id = :providerMessageId " +
                   "  AND status NOT IN ('DELIVERED', 'BOUNCED')", nativeQuery = true)
    int updateStatusByProviderMessageId(@Param("providerMessageId") String providerMessageId,
                                        @Param("newStatus") String newStatus,
                                        @Param("now") OffsetDateTime now);
}
