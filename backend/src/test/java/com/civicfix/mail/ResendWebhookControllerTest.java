package com.civicfix.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResendWebhookControllerTest {

    private EmailOutboxRepository outboxRepository;
    private ResendWebhookController controller;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(EmailOutboxRepository.class);
        // Using null webhook secret for developer/testing mode verification of logic
        controller = new ResendWebhookController(outboxRepository, null);
    }

    @Test
    @DisplayName("Webhook updates outbox status to DELIVERED on email.delivered event")
    void testDeliveredEventUpdatesStatus() {
        String payload = """
            {
              "type": "email.delivered",
              "created_at": "2026-11-23T04:00:00.000Z",
              "data": {
                "email_id": "resend_msg_789",
                "from": "notifications@civicfix.org",
                "to": ["citizen@example.com"],
                "subject": "Status Update"
              }
            }
            """;

        ResponseEntity<?> response = controller.handleResendWebhook(payload, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(outboxRepository).updateStatusByProviderMessageId(eq("resend_msg_789"), eq("DELIVERED"), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Webhook updates outbox status to BOUNCED on email.bounced event")
    void testBouncedEventUpdatesStatus() {
        String payload = """
            {
              "type": "email.bounced",
              "created_at": "2026-11-23T04:00:00.000Z",
              "data": {
                "email_id": "resend_msg_bounce_123"
              }
            }
            """;

        ResponseEntity<?> response = controller.handleResendWebhook(payload, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(outboxRepository).updateStatusByProviderMessageId(eq("resend_msg_bounce_123"), eq("BOUNCED"), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Webhook rejects missing Svix signature headers when secret is configured")
    void testMissingSvixHeadersRejectedWhenSecretConfigured() {
        ResendWebhookController secureController = new ResendWebhookController(outboxRepository, "whsec_test_secret_12345678901234567890123456789012");
        String payload = "{\"type\": \"email.delivered\"}";

        ResponseEntity<?> response = secureController.handleResendWebhook(payload, null, null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(outboxRepository, never()).updateStatusByProviderMessageId(anyString(), anyString(), any(OffsetDateTime.class));
    }
}
