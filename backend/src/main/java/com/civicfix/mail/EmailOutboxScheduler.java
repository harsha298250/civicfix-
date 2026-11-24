package com.civicfix.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EmailOutboxScheduler
 *
 * Periodic background daemon that polls for pending/retryable email outbox entries
 * and expired worker leases, dispatching them with bounded concurrency.
 */
@Component
@ConditionalOnProperty(name = "civicfix.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class EmailOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxScheduler.class);

    private final EmailOutboxService emailOutboxService;

    @Autowired
    public EmailOutboxScheduler(EmailOutboxService emailOutboxService) {
        this.emailOutboxService = emailOutboxService;
    }

    @Scheduled(fixedDelayString = "${civicfix.outbox.scheduler-interval-ms:5000}")
    public void runOutboxCycle() {
        try {
            int processedCount = emailOutboxService.processPendingBatch();
            if (processedCount > 0) {
                log.info("[OUTBOX_SCHEDULER] Successfully dispatched {} queued outbox email(s)", processedCount);
            }
        } catch (Exception ex) {
            log.error("[OUTBOX_SCHEDULER] Error during outbox background dispatch cycle: {}", ex.getMessage(), ex);
        }
    }
}
