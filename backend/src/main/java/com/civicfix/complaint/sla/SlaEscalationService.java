package com.civicfix.complaint.sla;

import com.civicfix.audit.AuditService;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.notification.NotificationService;
import com.civicfix.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * SlaEscalationService — Separate transactional Spring bean for executing complaint escalations.
 * Using REQUIRES_NEW guarantees each escalation commits in its own transaction, ensuring
 * that @TransactionalEventListener(phase = AFTER_COMMIT) notification listeners fire reliably.
 */
@Service
public class SlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

    private final ComplaintRepository complaintRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public SlaEscalationService(
            ComplaintRepository complaintRepository,
            AuditService auditService,
            NotificationService notificationService) {
        this.complaintRepository = complaintRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Atomically escalates a single complaint.
     * @return true if this invocation successfully escalated the complaint; false if it was already escalated.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean escalateComplaint(UUID complaintId, String reason, User actor, String ipAddress) {
        OffsetDateTime now = OffsetDateTime.now();

        // Atomic update ensures idempotency: only one thread/invocation can ever flip escalated_at from null to now
        int rowsUpdated = complaintRepository.markEscalated(complaintId, now);
        if (rowsUpdated == 0) {
            log.debug("Complaint {} is already escalated or not found. Skipping.", complaintId);
            return false;
        }

        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            return false;
        }

        UUID actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getFullName() : "SYSTEM";
        String actorRole = actor != null && !actor.getRoles().isEmpty()
                ? actor.getRoles().iterator().next().getName().name()
                : "SYSTEM";

        auditService.logAction(
                actorId,
                actorName,
                actorRole,
                "ESCALATE",
                "COMPLAINT",
                complaint.getId().toString(),
                "Escalated complaint #" + complaint.getReferenceId() + ". Reason: " + reason,
                ipAddress != null ? ipAddress : "127.0.0.1",
                "SUCCESS"
        );

        notificationService.notifyEscalation(complaint, reason, actor);
        log.info("Complaint #{} ({}) successfully escalated. Reason: {}", complaint.getReferenceId(), complaintId, reason);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean warnApproachingDeadline(UUID complaintId) {
        OffsetDateTime now = OffsetDateTime.now();
        int rowsUpdated = complaintRepository.markSlaWarningSent(complaintId, now);
        if (rowsUpdated == 0) {
            return false;
        }

        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            return false;
        }

        auditService.logAction(
                null,
                "SYSTEM",
                "SYSTEM",
                "SLA_WARNING_DISPATCHED",
                "COMPLAINT",
                complaint.getId().toString(),
                "Dispatched SLA approaching-deadline warning for complaint #" + complaint.getReferenceId(),
                "127.0.0.1",
                "SUCCESS"
        );

        notificationService.notifySlaApproachingDeadline(complaint);
        log.info("Complaint #{} ({}) successfully flagged with SLA approaching-deadline warning.", complaint.getReferenceId(), complaintId);
        return true;
    }
}

