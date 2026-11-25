package com.civicfix.mail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * EmailOutboxService
 *
 * Implements high-reliability transactional outbox delivery for all outbound emails.
 * Uses a Two-Phase Claim & Lease pattern with FOR UPDATE SKIP LOCKED to ensure:
 * 1. Outbox jobs are enqueued atomically with business transactions (zero dual-write inconsistency).
 * 2. Database connections and locks are NOT held during external HTTP requests to Resend.
 * 3. Expired leases and crashes are recovered automatically without duplicate transmissions.
 * 4. Tokens at rest in the payload are encrypted with AES-256-GCM and scrubbed upon delivery acceptance.
 */
@Service
public class EmailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository outboxRepository;
    private final ResendEmailService resendEmailService;
    private final AesGcmEncryptionHelper encryptionHelper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${civicfix.outbox.lease-duration-seconds:300}")
    private int leaseDurationSeconds = 300; // 5 minutes

    @Value("${civicfix.outbox.batch-size:25}")
    private int batchSize = 25;

    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    public EmailOutboxService(EmailOutboxRepository outboxRepository,
                              ResendEmailService resendEmailService,
                              AesGcmEncryptionHelper encryptionHelper) {
        this(outboxRepository, resendEmailService, encryptionHelper, null);
    }

    @Autowired
    public EmailOutboxService(EmailOutboxRepository outboxRepository,
                              ResendEmailService resendEmailService,
                              AesGcmEncryptionHelper encryptionHelper,
                              @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.resendEmailService = resendEmailService;
        this.encryptionHelper = encryptionHelper;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isDeliveryConfigured() {
        return resendEmailService != null && resendEmailService.isConfigured();
    }

    /**
     * Enqueues an email outbox record atomically within the caller's active database transaction.
     * Encrypts sensitive fields (e.g. password reset URLs) using AES-256-GCM before writing to PostgreSQL.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public EmailOutbox enqueueEmail(String idempotencyKey, String recipientEmail, String subject,
                                    String emailType, Map<String, Object> payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = "outbox_" + UUID.randomUUID();
        }

        Optional<EmailOutbox> existing = outboxRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("[OUTBOX] Job with idempotency key {} already exists. Skipping duplicate enqueue.", idempotencyKey);
            return existing.get();
        }

        Map<String, Object> sanitizedPayload = new HashMap<>(payload != null ? payload : Collections.emptyMap());

        // Encrypt sensitive token fields if present
        if (sanitizedPayload.containsKey("resetUrl")) {
            String rawUrl = String.valueOf(sanitizedPayload.get("resetUrl"));
            sanitizedPayload.put("resetUrlEncrypted", encryptionHelper.encrypt(rawUrl));
            sanitizedPayload.remove("resetUrl");
        }
        if (sanitizedPayload.containsKey("verificationUrl")) {
            String rawUrl = String.valueOf(sanitizedPayload.get("verificationUrl"));
            sanitizedPayload.put("verificationUrlEncrypted", encryptionHelper.encrypt(rawUrl));
            sanitizedPayload.remove("verificationUrl");
        }
        if (sanitizedPayload.containsKey("rawToken")) {
            String rawToken = String.valueOf(sanitizedPayload.get("rawToken"));
            sanitizedPayload.put("rawTokenEncrypted", encryptionHelper.encrypt(rawToken));
            sanitizedPayload.remove("rawToken");
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(sanitizedPayload);
        } catch (Exception ex) {
            log.error("[OUTBOX] Failed to serialize email payload for {}: {}", recipientEmail, ex.getMessage());
            payloadJson = "{}";
        }

        EmailOutbox outbox = new EmailOutbox(idempotencyKey, recipientEmail, subject, emailType, payloadJson);
        EmailOutbox saved = outboxRepository.save(outbox);
        log.info("[OUTBOX] Enqueued {} email for recipient {} with idempotencyKey {}",
                emailType, recipientEmail, idempotencyKey);
        return saved;
    }

    /**
     * Two-Phase Claim: Claims up to batchSize jobs with FOR UPDATE SKIP LOCKED.
     * The transaction commits immediately in ~5ms, releasing the database connection
     * before any HTTP calls are initiated.
     */
    public List<EmailOutbox> claimBatch(int limit) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> doClaimBatch(limit));
        }
        return doClaimBatch(limit);
    }

    private List<EmailOutbox> doClaimBatch(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseExpiredBefore = now.minusSeconds(leaseDurationSeconds);

        // Fail any expired leases where max attempts have already been reached
        outboxRepository.failExhaustedLeases(leaseExpiredBefore, now);

        List<UUID> claimedIds = outboxRepository.claimJobIds(now, leaseExpiredBefore, limit > 0 ? limit : batchSize);
        if (claimedIds.isEmpty()) {
            return Collections.emptyList();
        }

        outboxRepository.lockJobs(claimedIds, workerId, now);
        return outboxRepository.findAllById(claimedIds);
    }

    /**
     * Dispatches a single claimed outbox job via Resend REST API and settles the status conditionally.
     */
    public void processJob(EmailOutbox job) {
        try {
            String htmlContent = buildHtmlContent(job);

            Map<String, String> tags = new HashMap<>();
            tags.put("idempotency_key", job.getIdempotencyKey());
            tags.put("outbox_id", job.getId().toString());
            tags.put("email_type", job.getEmailType());

            ResendEmailService.ResendDispatchResult result = resendEmailService.sendEmailWithDetails(
                    job.getRecipientEmail(),
                    job.getSubject(),
                    htmlContent,
                    job.getIdempotencyKey(),
                    tags
            );

            settleJob(job.getId(), job.getAttempts(), result);
        } catch (Exception ex) {
            log.error("[OUTBOX] Unexpected error processing outbox job {}: {}", job.getId(), ex.getMessage(), ex);
            ResendEmailService.ResendDispatchResult fallbackResult = new ResendEmailService.ResendDispatchResult(
                    false, null, 0, "Internal processor error: " + ex.getMessage(), true);
            settleJob(job.getId(), job.getAttempts(), fallbackResult);
        }
    }

    /**
     * Settles the state of an outbox job in an independent short transaction (~5ms).
     */
    public void settleJob(UUID jobId, int currentAttempts, ResendEmailService.ResendDispatchResult result) {
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> doSettleJob(jobId, currentAttempts, result));
        } else {
            doSettleJob(jobId, currentAttempts, result);
        }
    }

    private void doSettleJob(UUID jobId, int currentAttempts, ResendEmailService.ResendDispatchResult result) {
        OffsetDateTime now = OffsetDateTime.now();
        if (result.success()) {
            outboxRepository.settleSuccess(jobId, result.providerMessageId(), now);
            log.info("[OUTBOX] Job {} settled as ACCEPTED with provider ID {}", jobId, result.providerMessageId());
        } else if (result.retryable()) {
            long backoffSeconds = calculateBackoffSeconds(currentAttempts + 1);
            OffsetDateTime nextRetry = now.plusSeconds(backoffSeconds);
            outboxRepository.settleRetryableFailure(jobId, nextRetry, result.errorMessage(), now);
            log.warn("[OUTBOX] Job {} failed (attempt {}). Next retry at {} (+{}s): {}",
                    jobId, currentAttempts + 1, nextRetry, backoffSeconds, result.errorMessage());
        } else {
            outboxRepository.settlePermanentFailure(jobId, result.errorMessage(), now);
            log.error("[OUTBOX] Job {} permanently failed (non-retryable): {}", jobId, result.errorMessage());
        }
    }

    /**
     * Executes a batch delivery cycle.
     * Note: Intentionally NOT annotated with @Transactional to ensure that external HTTP calls
     * to Resend are executed without holding open any database connection or transaction.
     */
    public int processPendingBatch() {
        List<EmailOutbox> claimedJobs = claimBatch(batchSize);
        if (claimedJobs.isEmpty()) {
            return 0;
        }

        log.debug("[OUTBOX] Worker {} claimed {} jobs for dispatch", workerId, claimedJobs.size());
        for (EmailOutbox job : claimedJobs) {
            processJob(job);
        }
        return claimedJobs.size();
    }

    private String buildHtmlContent(EmailOutbox job) {
        Map<String, Object> data = parsePayload(job.getPayloadJson());

        return switch (job.getEmailType()) {
            case "EMAIL_VERIFICATION" -> {
                String name = (String) data.getOrDefault("name", "Citizen");
                String verificationUrl = resolveDecryptedField(data, "verificationUrlEncrypted", "verificationUrl");
                yield resendEmailService.buildEmailVerificationHtml(name, verificationUrl);
            }
            case "PASSWORD_RESET" -> {
                String resetUrl = resolveDecryptedField(data, "resetUrlEncrypted", "resetUrl");
                yield resendEmailService.buildPasswordResetHtml(resetUrl);
            }
            case "PASSWORD_RESET_CONFIRMATION" -> {
                String name = (String) data.getOrDefault("name", "Citizen");
                yield resendEmailService.buildPasswordResetConfirmationHtml(name);
            }
            case "WELCOME" -> {
                String name = (String) data.getOrDefault("name", "Citizen");
                yield resendEmailService.buildWelcomeHtml(name);
            }
            case "COMPLAINT_SUBMITTED" -> {
                String citizenName = (String) data.getOrDefault("citizenName", "Resident");
                String refId = (String) data.getOrDefault("referenceId", "");
                String title = (String) data.getOrDefault("title", "");
                String category = (String) data.getOrDefault("category", "");
                String ward = (String) data.getOrDefault("ward", "");
                yield resendEmailService.buildComplaintSubmittedHtml(citizenName, refId, title, category, ward);
            }
            case "COMPLAINT_STATUS_UPDATE" -> {
                String citizenName = (String) data.getOrDefault("citizenName", "Resident");
                String refId = (String) data.getOrDefault("referenceId", "");
                String title = (String) data.getOrDefault("title", "");
                String oldStatus = (String) data.getOrDefault("oldStatus", "");
                String newStatus = (String) data.getOrDefault("newStatus", "");
                String notes = (String) data.getOrDefault("notes", "");
                yield resendEmailService.buildComplaintStatusUpdateHtml(citizenName, refId, title, oldStatus, newStatus, notes);
            }
            case "COMPLAINT_ASSIGNMENT" -> {
                String recipientName = (String) data.getOrDefault("recipientName", "Field Worker");
                String role = (String) data.getOrDefault("role", "Technician");
                String refId = (String) data.getOrDefault("referenceId", "");
                String title = (String) data.getOrDefault("title", "");
                String priority = (String) data.getOrDefault("priority", "MEDIUM");
                String ward = (String) data.getOrDefault("ward", "");
                yield resendEmailService.buildComplaintAssignmentHtml(recipientName, role, refId, title, priority, ward);
            }
            case "NEW_COMPLAINT_ALERT" -> {
                String officerName = (String) data.getOrDefault("officerName", "");
                String refId = (String) data.getOrDefault("referenceId", "");
                String title = (String) data.getOrDefault("title", "");
                String priority = (String) data.getOrDefault("priority", "MEDIUM");
                String ward = (String) data.getOrDefault("ward", "");
                yield resendEmailService.buildNewComplaintAlertHtml(officerName, refId, title, priority, ward);
            }
            default -> (String) data.getOrDefault("htmlContent", "<p>" + job.getSubject() + "</p>");
        };
    }

    private String resolveDecryptedField(Map<String, Object> data, String encryptedKey, String plainKey) {
        if (data.containsKey(encryptedKey)) {
            String encrypted = String.valueOf(data.get(encryptedKey));
            return encryptionHelper.decrypt(encrypted);
        }
        if (data.containsKey(plainKey)) {
            return String.valueOf(data.get(plainKey));
        }
        return "";
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank() || "{}".equals(payloadJson.trim())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[OUTBOX] Failed to deserialize payload JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private long calculateBackoffSeconds(int attempt) {
        return switch (attempt) {
            case 1 -> 30L;
            case 2 -> 120L;
            case 3 -> 600L;
            case 4 -> 1800L;
            default -> 7200L;
        };
    }
}
