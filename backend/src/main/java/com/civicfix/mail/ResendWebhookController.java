package com.civicfix.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svix.Webhook;
import com.svix.exceptions.WebhookVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * ResendWebhookController
 *
 * Ingests inbound delivery webhooks from Resend with official Svix cryptographic
 * signature verification, updating transactional outbox delivery statuses in real-time.
 */
@RestController
@RequestMapping("/api/webhooks")
public class ResendWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookController.class);

    private final EmailOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${resend.webhook.signing-secret:${RESEND_WEBHOOK_SIGNING_SECRET:}}")
    private String webhookSecret;

    @Autowired
    public ResendWebhookController(EmailOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Testing constructor allowing custom secret injection.
     */
    public ResendWebhookController(EmailOutboxRepository outboxRepository, String webhookSecret) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper();
        this.webhookSecret = webhookSecret;
    }

    @PostMapping(value = "/resend", consumes = "application/json")
    @Transactional
    public ResponseEntity<?> handleResendWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
            @RequestHeader(value = "svix-signature", required = false) String svixSignature) {

        if (webhookSecret != null && !webhookSecret.trim().isEmpty()) {
            if (svixId == null || svixTimestamp == null || svixSignature == null) {
                log.warn("[WEBHOOK] Missing Svix verification headers on inbound Resend webhook");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing webhook signature headers");
            }

            try {
                Map<String, List<String>> headerMap = new HashMap<>();
                headerMap.put("svix-id", List.of(svixId));
                headerMap.put("svix-timestamp", List.of(svixTimestamp));
                headerMap.put("svix-signature", List.of(svixSignature));

                java.net.http.HttpHeaders httpHeaders = java.net.http.HttpHeaders.of(headerMap, (k, v) -> true);
                Webhook webhook = new Webhook(webhookSecret.trim());
                webhook.verify(rawPayload, httpHeaders);
            } catch (WebhookVerificationException ex) {
                log.warn("[WEBHOOK] Svix signature verification failed for Resend webhook: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
            } catch (Exception ex) {
                log.error("[WEBHOOK] Unexpected error verifying webhook: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Verification error");
            }
        } else {
            log.warn("[WEBHOOK] RESEND_WEBHOOK_SIGNING_SECRET is not configured. Webhook accepted without signature validation (development mode).");
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.path("type").asText("");
            JsonNode dataNode = root.path("data");
            String emailId = dataNode.path("email_id").asText(null);

            if (emailId == null || emailId.isBlank()) {
                emailId = dataNode.path("id").asText(null);
            }

            log.info("[WEBHOOK] Inbound Resend event received: type={}, emailId={}", eventType, emailId);

            if (emailId != null && !emailId.isBlank()) {
                OffsetDateTime now = OffsetDateTime.now();
                if ("email.delivered".equalsIgnoreCase(eventType)) {
                    int updated = outboxRepository.updateStatusByProviderMessageId(emailId, "DELIVERED", now);
                    log.info("[WEBHOOK] Marked outbox record with provider ID {} as DELIVERED (rows updated: {})", emailId, updated);
                } else if ("email.bounced".equalsIgnoreCase(eventType) || "email.complained".equalsIgnoreCase(eventType)) {
                    int updated = outboxRepository.updateStatusByProviderMessageId(emailId, "BOUNCED", now);
                    log.warn("[WEBHOOK] Marked outbox record with provider ID {} as BOUNCED (rows updated: {})", emailId, updated);
                }
            }

            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception ex) {
            log.error("[WEBHOOK] Error processing Resend webhook payload: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed webhook payload");
        }
    }
}
