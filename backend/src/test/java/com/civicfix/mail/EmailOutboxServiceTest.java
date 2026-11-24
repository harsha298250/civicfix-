package com.civicfix.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailOutboxServiceTest {

    private EmailOutboxRepository outboxRepository;
    private ResendEmailService resendEmailService;
    private AesGcmEncryptionHelper encryptionHelper;
    private EmailOutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(EmailOutboxRepository.class);
        resendEmailService = mock(ResendEmailService.class);
        encryptionHelper = new AesGcmEncryptionHelper("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        outboxService = new EmailOutboxService(outboxRepository, resendEmailService, encryptionHelper);
    }

    @Test
    @DisplayName("enqueueEmail encrypts resetUrl and persists outbox job atomically")
    void testEnqueueEmailEncryptsSensitiveTokens() {
        when(outboxRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(outboxRepository.save(any(EmailOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = new HashMap<>();
        payload.put("resetUrl", "https://civicfix.org/reset?token=raw_secret_token");
        payload.put("rawToken", "raw_secret_token");

        EmailOutbox outbox = outboxService.enqueueEmail(
                "key_001",
                "citizen@example.com",
                "Reset Password",
                "PASSWORD_RESET",
                payload
        );

        assertNotNull(outbox);
        assertEquals("key_001", outbox.getIdempotencyKey());
        assertEquals("PENDING", outbox.getStatus());
        assertFalse(outbox.getPayloadJson().contains("raw_secret_token"),
                "Raw tokens must not be stored in plaintext in the payload");
        assertTrue(outbox.getPayloadJson().contains("resetUrlEncrypted"),
                "Payload must contain encrypted token field");

        verify(outboxRepository).save(any(EmailOutbox.class));
    }

    @Test
    @DisplayName("enqueueEmail returns existing record on duplicate idempotency key")
    void testEnqueueDuplicateKeyReturnsExisting() {
        EmailOutbox existing = new EmailOutbox("dup_key", "existing@example.com", "Subject", "WELCOME", "{}");
        when(outboxRepository.findByIdempotencyKey("dup_key")).thenReturn(Optional.of(existing));

        EmailOutbox result = outboxService.enqueueEmail("dup_key", "another@example.com", "Sub", "WELCOME", Map.of());

        assertEquals(existing, result);
        verify(outboxRepository, never()).save(any(EmailOutbox.class));
    }

    @Test
    @DisplayName("processJob successfully dispatches via Resend and settles as ACCEPTED")
    void testProcessJobSuccessSettlesAccepted() {
        UUID jobId = UUID.randomUUID();
        EmailOutbox job = new EmailOutbox("idem_123", "citizen@example.com", "Intake Receipt", "WELCOME", "{\"name\":\"Citizen Alex\"}");
        job.setId(jobId);

        when(resendEmailService.buildWelcomeHtml("Citizen Alex")).thenReturn("<p>Welcome Alex</p>");
        when(resendEmailService.sendEmailWithDetails(eq("citizen@example.com"), eq("Intake Receipt"), anyString(), eq("idem_123"), anyMap()))
                .thenReturn(new ResendEmailService.ResendDispatchResult(true, "resend_msg_456", 200, null, false));

        outboxService.processJob(job);

        verify(outboxRepository).settleSuccess(eq(jobId), eq("resend_msg_456"), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("processJob on retryable error schedules exponential backoff and increments attempts")
    void testProcessJobRetryableErrorBackoff() {
        UUID jobId = UUID.randomUUID();
        EmailOutbox job = new EmailOutbox("idem_retry", "citizen@example.com", "Intake Receipt", "WELCOME", "{}");
        job.setId(jobId);
        job.setAttempts(0);

        when(resendEmailService.buildWelcomeHtml(anyString())).thenReturn("<p>Welcome</p>");
        when(resendEmailService.sendEmailWithDetails(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new ResendEmailService.ResendDispatchResult(false, null, 500, "Internal Server Error", true));

        outboxService.processJob(job);

        ArgumentCaptor<OffsetDateTime> retryCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outboxRepository).settleRetryableFailure(eq(jobId), retryCaptor.capture(), eq("Internal Server Error"), any(OffsetDateTime.class));

        // First attempt backoff should be ~30s in the future
        OffsetDateTime nextRetry = retryCaptor.getValue();
        assertTrue(nextRetry.isAfter(OffsetDateTime.now().plusSeconds(25)));
    }

    @Test
    @DisplayName("processJob on non-retryable 4xx permanently fails the job without further retries")
    void testProcessJobNonRetryableFailsPermanently() {
        UUID jobId = UUID.randomUUID();
        EmailOutbox job = new EmailOutbox("idem_fail", "invalid_email", "Subject", "WELCOME", "{}");
        job.setId(jobId);

        when(resendEmailService.buildWelcomeHtml(anyString())).thenReturn("<p>Welcome</p>");
        when(resendEmailService.sendEmailWithDetails(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new ResendEmailService.ResendDispatchResult(false, null, 422, "Domain not verified", false));

        outboxService.processJob(job);

        verify(outboxRepository).settlePermanentFailure(eq(jobId), eq("Domain not verified"), any(OffsetDateTime.class));
    }
}
