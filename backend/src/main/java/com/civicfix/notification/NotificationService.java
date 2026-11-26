package com.civicfix.notification;

import com.civicfix.common.PagedResponse;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.event.ComplaintEvent;
import com.civicfix.notification.dto.NotificationResponse;
import com.civicfix.security.UserPrincipal;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DEDUPLICATION_WINDOW_SECONDS = 30;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final com.civicfix.mail.EmailService emailService;
    private final com.civicfix.mail.EmailOutboxService emailOutboxService;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) com.civicfix.mail.EmailService emailService,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) com.civicfix.mail.EmailOutboxService emailOutboxService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.emailOutboxService = emailOutboxService;
    }

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               com.civicfix.mail.EmailService emailService) {
        this(notificationRepository, userRepository, emailService, null);
    }

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this(notificationRepository, userRepository, null, null);
    }

    /**
     * Handles complaint lifecycle events after transaction commit in an isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleComplaintEvent(ComplaintEvent event) {
        if (event == null || event.getComplaint() == null) {
            return;
        }

        Complaint complaint = event.getComplaint();
        User actor = event.getActor();
        String refId = complaint.getReferenceId();

        try {
            switch (event.getEventType()) {
                case COMPLAINT_CREATED:
                    // 1. Notify author citizen
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Complaint Submitted #" + refId,
                            "Your civic complaint has been registered and is pending municipal review.",
                            NotificationType.COMPLAINT_CREATED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchComplaintSubmittedEmail(complaint, refId);
                        } catch (Exception ex) {
                            log.warn("Non-fatal email error on complaint intake receipt #{}: {}", refId, ex.getMessage());
                        }
                    }

                    // 2. Notify municipal officers
                    NotificationSeverity officerSeverity = (complaint.getPriority() == ComplaintPriority.CRITICAL || complaint.getPriority() == ComplaintPriority.HIGH)
                            ? NotificationSeverity.ACTION_REQUIRED
                            : NotificationSeverity.INFO;
                    String wardDesc = complaint.getWard() != null ? complaint.getWard() : "City";
                    List<User> officers = userRepository.findActiveUsersByRoleName(RoleName.OFFICER);
                    for (User officer : officers) {
                        sendNotification(
                                officer,
                                actor,
                                complaint,
                                refId,
                                "New Complaint Intake #" + refId,
                                "New intake defect in " + wardDesc + ": " + complaint.getTitle(),
                                NotificationType.COMPLAINT_CREATED,
                                officerSeverity,
                                "/officer/complaints/" + refId + "/review"
                        );
                        if (officer.getEmail() != null && !officer.getEmail().isBlank()) {
                            try {
                                dispatchNewComplaintAlertEmail(officer, complaint, refId, wardDesc);
                            } catch (Exception ex) {
                                log.warn("Non-fatal email alert to officer {}: {}", officer.getEmail(), ex.getMessage());
                            }
                        }
                    }
                    break;

                case COMPLAINT_UNDER_REVIEW:
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Complaint Accepted for Review #" + refId,
                            "Your complaint has been accepted by municipal intake and is now under officer review.",
                            NotificationType.STATUS_CHANGED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchStatusUpdateEmail(
                                    complaint.getCitizen(),
                                    complaint,
                                    refId,
                                    "SUBMITTED",
                                    "UNDER_REVIEW",
                                    event.getNotes() != null && !event.getNotes().isBlank()
                                            ? event.getNotes().trim()
                                            : "Accepted by municipal intake for review and crew assignment."
                            );
                        } catch (Exception ex) {
                            log.warn("Non-fatal email error on UNDER_REVIEW #{}: {}", refId, ex.getMessage());
                        }
                    }
                    break;

                case WORKER_ASSIGNED:
                    // 1. Notify assigned field technician
                    if (complaint.getAssignedWorker() != null) {
                        sendNotification(
                                complaint.getAssignedWorker(),
                                actor,
                                complaint,
                                refId,
                                "New Task Dispatched #" + refId,
                                "You have been assigned to repair: " + complaint.getTitle(),
                                NotificationType.WORKER_ASSIGNED,
                                NotificationSeverity.INFO,
                                "/worker/tasks/" + refId
                        );
                        if (complaint.getAssignedWorker().getEmail() != null) {
                            try {
                                dispatchComplaintAssignmentEmail(complaint.getAssignedWorker(), complaint, refId);
                            } catch (Exception ex) {
                                log.warn("Non-fatal email dispatch to technician: {}", ex.getMessage());
                            }
                        }
                    }

                    // 2. Notify citizen
                    String workerName = complaint.getAssignedWorker() != null ? complaint.getAssignedWorker().getFullName() : "field personnel";
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Field Technician Dispatched #" + refId,
                            "Technician " + workerName + " has been assigned to your ticket.",
                            NotificationType.WORKER_ASSIGNED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchStatusUpdateEmail(complaint.getCitizen(), complaint, refId, "SUBMITTED", "WORKER_ASSIGNED", "Technician " + workerName + " has been assigned to your ticket.");
                        } catch (Exception ex) {
                            log.warn("Non-fatal email to citizen on worker assignment: {}", ex.getMessage());
                        }
                    }
                    break;

                case WORK_STARTED:
                    // 1. Notify citizen that repairs began
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Work Order In Progress #" + refId,
                            "Field technician has arrived on scene and begun repair operations.",
                            NotificationType.STATUS_CHANGED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchStatusUpdateEmail(complaint.getCitizen(), complaint, refId, "ASSIGNED", "WORK_STARTED", "Field technician has arrived on scene and begun repair operations.");
                        } catch (Exception ex) {
                            log.warn("Non-fatal email error on work started #{}: {}", refId, ex.getMessage());
                        }
                    }

                    // 2. Notify assigned officer
                    if (complaint.getAssignedOfficer() != null) {
                        sendNotification(
                                complaint.getAssignedOfficer(),
                                actor,
                                complaint,
                                refId,
                                "Repair Work Started #" + refId,
                                "Field technician has arrived on scene and begun repair operations.",
                                NotificationType.STATUS_CHANGED,
                                NotificationSeverity.INFO,
                                "/officer/complaints/" + refId + "/review"
                        );
                    }
                    break;

                case RESOLUTION_SUBMITTED:
                    // 1. Notify reviewing officer
                    if (complaint.getAssignedOfficer() != null) {
                        sendNotification(
                                complaint.getAssignedOfficer(),
                                actor,
                                complaint,
                                refId,
                                "Resolution Proof Awaiting Verification #" + refId,
                                "Field technician submitted repair evidence for inspection.",
                                NotificationType.RESOLUTION_SUBMITTED,
                                NotificationSeverity.ACTION_REQUIRED,
                                "/officer/verifications/" + refId
                        );
                    } else {
                        List<User> activeOfficers = userRepository.findActiveUsersByRoleName(RoleName.OFFICER);
                        for (User off : activeOfficers) {
                            sendNotification(
                                    off,
                                    actor,
                                    complaint,
                                    refId,
                                    "Resolution Proof Awaiting Verification #" + refId,
                                    "Field technician submitted repair evidence for inspection.",
                                    NotificationType.RESOLUTION_SUBMITTED,
                                    NotificationSeverity.ACTION_REQUIRED,
                                    "/officer/verifications/" + refId
                            );
                        }
                    }

                    // 2. Notify citizen
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Repair Completed — Verification Pending #" + refId,
                            "Field repairs have completed. Municipal officer verification is underway.",
                            NotificationType.STATUS_CHANGED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchStatusUpdateEmail(complaint.getCitizen(), complaint, refId, "IN_PROGRESS", "RESOLUTION_SUBMITTED", "Field repairs have completed. Municipal officer verification is underway.");
                        } catch (Exception ex) {
                            log.warn("Non-fatal email error on resolution submission #{}: {}", refId, ex.getMessage());
                        }
                    }
                    break;

                case COMPLAINT_RESOLVED:
                    if (complaint.getId() != null) {
                        notificationRepository.markComplaintNotificationsAsReadByType(
                                complaint.getId(),
                                NotificationType.COMPLAINT_CREATED,
                                OffsetDateTime.now()
                        );
                    }
                    // 1. Notify citizen of official closure
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Complaint Resolved & Closed #" + refId,
                            "Resolution evidence verified and approved by municipal operations.",
                            NotificationType.COMPLAINT_RESOLVED,
                            NotificationSeverity.SUCCESS,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchStatusUpdateEmail(complaint.getCitizen(), complaint, refId, "VERIFIED", "RESOLVED", "Resolution evidence has been verified and formally closed.");
                        } catch (Exception ex) {
                            log.warn("Non-fatal email error on complaint resolution #{}: {}", refId, ex.getMessage());
                        }
                    }

                    // 2. Notify field technician of approval
                    if (complaint.getAssignedWorker() != null) {
                        sendNotification(
                                complaint.getAssignedWorker(),
                                actor,
                                complaint,
                                refId,
                                "Resolution Verified & Approved #" + refId,
                                "Your submitted repair proof has been formally accepted and closed.",
                                NotificationType.COMPLAINT_RESOLVED,
                                NotificationSeverity.SUCCESS,
                                "/worker/tasks/" + refId
                        );
                    }
                    break;

                case REWORK_REQUESTED:
                    // Notify field technician of rework requirement
                    if (complaint.getAssignedWorker() != null) {
                        String reworkReason = event.getNotes() != null && !event.getNotes().isBlank()
                                ? event.getNotes().trim()
                                : "Submitted evidence does not satisfy quality standards.";
                        sendNotification(
                                complaint.getAssignedWorker(),
                                actor,
                                complaint,
                                refId,
                                "Rework Requested #" + refId,
                                "Officer requested rework on submitted evidence. Reason: " + reworkReason,
                                NotificationType.REWORK_REQUESTED,
                                NotificationSeverity.WARNING,
                                "/worker/tasks/" + refId + "/progress"
                        );
                    }
                    break;

                case COMPLAINT_REJECTED:
                    // Mark earlier unread intake notifications for this rejected complaint as read while preserving audit history
                    if (complaint.getId() != null) {
                        notificationRepository.markComplaintNotificationsAsReadByType(
                                complaint.getId(),
                                NotificationType.COMPLAINT_CREATED,
                                OffsetDateTime.now()
                        );
                    }
                    // Notify citizen of rejection
                    String rejectReason = event.getNotes() != null && !event.getNotes().isBlank()
                            ? event.getNotes().trim()
                            : "Complaint does not meet municipal guidelines.";
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Complaint Rejected #" + refId,
                            "Your complaint was reviewed and closed without action. Reason: " + rejectReason,
                            NotificationType.COMPLAINT_REJECTED,
                            NotificationSeverity.WARNING,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchStatusUpdateEmail(complaint.getCitizen(), complaint, refId, "UNDER_REVIEW", "REJECTED", "Complaint closed without action: " + rejectReason);
                        } catch (Exception ex) {
                            log.warn("Non-fatal email error on complaint rejection #{}: {}", refId, ex.getMessage());
                        }
                    }
                    break;

                case DUPLICATE_LINKED:
                    // Notify citizen that ticket was linked to primary incident
                    String masterRef = complaint.getDuplicateOf() != null ? complaint.getDuplicateOf().getReferenceId() : "master ticket";
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Report Linked as Duplicate #" + refId,
                            "Your complaint was verified as a duplicate and linked to primary incident #" + masterRef + ".",
                            NotificationType.DUPLICATE_LINKED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    break;

                case FIX_PENDING_CONFIRMATION:
                    // Notify citizen that repair evidence was verified and awaiting their confirmation
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Repair Completed — Please Confirm #" + refId,
                            "Municipal officers verified repair evidence. Please check the site and confirm if the issue is fixed.",
                            NotificationType.FIX_PENDING_CONFIRMATION,
                            NotificationSeverity.ACTION_REQUIRED,
                            "/citizen/complaints/" + refId
                    );
                    break;

                case RESOLUTION_CONFIRMED:
                    // 1. Notify assigned officer
                    if (complaint.getAssignedOfficer() != null) {
                        sendNotification(
                                complaint.getAssignedOfficer(),
                                actor,
                                complaint,
                                refId,
                                "Resolution Confirmed by Citizen #" + refId,
                                "Reporting citizen verified and confirmed defect resolution for ticket #" + refId + ".",
                                NotificationType.RESOLUTION_CONFIRMED,
                                NotificationSeverity.SUCCESS,
                                "/officer/complaints/" + refId + "/review"
                        );
                    }
                    // 2. Notify assigned worker
                    if (complaint.getAssignedWorker() != null) {
                        sendNotification(
                                complaint.getAssignedWorker(),
                                actor,
                                complaint,
                                refId,
                                "Work Verified by Citizen #" + refId,
                                "Citizen confirmed completion for ticket #" + refId + ".",
                                NotificationType.RESOLUTION_CONFIRMED,
                                NotificationSeverity.SUCCESS,
                                "/worker/tasks/" + refId
                        );
                    }
                    break;

                case RESOLUTION_REJECTED:
                    String rejReason = event.getNotes() != null && !event.getNotes().isBlank()
                            ? event.getNotes().trim()
                            : "Citizen reported defect is not resolved.";
                    // 1. Notify assigned officer
                    if (complaint.getAssignedOfficer() != null) {
                        sendNotification(
                                complaint.getAssignedOfficer(),
                                actor,
                                complaint,
                                refId,
                                "Fix Rejected — Reopened #" + refId,
                                "Citizen reported issue is not fixed: \"" + rejReason + "\". Ticket reopened and escalated.",
                                NotificationType.RESOLUTION_REJECTED,
                                NotificationSeverity.WARNING,
                                "/officer/complaints/" + refId + "/review"
                        );
                    }
                    // 2. Notify assigned worker if present
                    if (complaint.getAssignedWorker() != null) {
                        sendNotification(
                                complaint.getAssignedWorker(),
                                actor,
                                complaint,
                                refId,
                                "Resolution Rejected by Citizen #" + refId,
                                "Citizen reported issue is not fixed: \"" + rejReason + "\". Additional repair work required.",
                                NotificationType.RESOLUTION_REJECTED,
                                NotificationSeverity.WARNING,
                                "/worker/tasks/" + refId + "/progress"
                        );
                    }
                    // 3. Notify all active supervisors
                    List<User> supervisors = userRepository.findActiveUsersByRoleName(RoleName.SUPERVISOR);
                    for (User sup : supervisors) {
                        sendNotification(
                                sup,
                                actor,
                                complaint,
                                refId,
                                "Citizen Rejected Fix #" + refId,
                                "Citizen reported issue is not fixed on ticket #" + refId + ": " + rejReason,
                                NotificationType.RESOLUTION_REJECTED,
                                NotificationSeverity.ACTION_REQUIRED,
                                "/supervisor/escalations"
                        );
                    }
                    break;

                case COMPLAINT_REOPENED:
                    String reopenReason = event.getNotes() != null && !event.getNotes().isBlank()
                            ? event.getNotes().trim()
                            : "Complaint reopened for additional remediation.";
                    // 1. Notify reporting citizen
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Complaint Reopened #" + refId,
                            "Your complaint has been reopened: " + reopenReason,
                            NotificationType.COMPLAINT_REOPENED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    if (complaint.getCitizen() != null && complaint.getCitizen().getEmail() != null) {
                        try {
                            dispatchStatusUpdateEmail(complaint.getCitizen(), complaint, refId, "RESOLVED", "REOPENED", reopenReason);
                        } catch (Exception ex) {
                            log.warn("Non-fatal email error on complaint reopen #{}: {}", refId, ex.getMessage());
                        }
                    }

                    // 2. Notify assigned officer
                    if (complaint.getAssignedOfficer() != null) {
                        sendNotification(
                                complaint.getAssignedOfficer(),
                                actor,
                                complaint,
                                refId,
                                "Complaint Reopened #" + refId,
                                "Complaint #" + refId + " was reopened: " + reopenReason,
                                NotificationType.COMPLAINT_REOPENED,
                                NotificationSeverity.WARNING,
                                "/officer/complaints/" + refId + "/review"
                        );
                    }

                    // 3. Notify assigned worker if present
                    if (complaint.getAssignedWorker() != null) {
                        sendNotification(
                                complaint.getAssignedWorker(),
                                actor,
                                complaint,
                                refId,
                                "Work Order Reopened #" + refId,
                                "Work order #" + refId + " was reopened: " + reopenReason,
                                NotificationType.COMPLAINT_REOPENED,
                                NotificationSeverity.WARNING,
                                "/worker/tasks/" + refId + "/progress"
                        );
                    }

                    // 4. Notify supervisors
                    List<User> activeSupervisors = userRepository.findActiveUsersByRoleName(RoleName.SUPERVISOR);
                    for (User sup : activeSupervisors) {
                        sendNotification(
                                sup,
                                actor,
                                complaint,
                                refId,
                                "Reopened Complaint Alert #" + refId,
                                "Complaint #" + refId + " was reopened. Reason: " + reopenReason,
                                NotificationType.COMPLAINT_REOPENED,
                                NotificationSeverity.ACTION_REQUIRED,
                                "/supervisor/escalations"
                        );
                    }
                    break;

                case AUTO_CONFIRMED:
                    // 1. Notify citizen of auto-confirmation and 30-day dispute window
                    sendNotification(
                            complaint.getCitizen(),
                            actor,
                            complaint,
                            refId,
                            "Resolution Auto-Confirmed #" + refId,
                            "Ticket #" + refId + " resolution was automatically confirmed after 7 days without response. You may dispute and reopen within 30 days if still unresolved.",
                            NotificationType.AUTO_CONFIRMED,
                            NotificationSeverity.INFO,
                            "/citizen/complaints/" + refId
                    );
                    // 2. Notify assigned officer
                    if (complaint.getAssignedOfficer() != null) {
                        sendNotification(
                                complaint.getAssignedOfficer(),
                                actor,
                                complaint,
                                refId,
                                "Resolution Auto-Confirmed #" + refId,
                                "Ticket #" + refId + " resolution was auto-confirmed after 7 days without citizen response.",
                                NotificationType.AUTO_CONFIRMED,
                                NotificationSeverity.INFO,
                                "/officer/complaints/" + refId + "/review"
                        );
                    }
                    break;

                default:
                    log.debug("No notification mapping for event type: {}", event.getEventType());
                    break;
            }
        } catch (Exception ex) {
            log.error("Failed to process notification event {} for complaint #{}: {}",
                    event.getEventType(), refId, ex.getMessage(), ex);
        }
    }

    /**
     * Dispatches escalation notifications to all active supervisors and the assigned officer.
     */
    public void notifyEscalation(Complaint complaint, String reason, User actor) {
        if (complaint == null) return;
        String refId = complaint.getReferenceId();
        String title = "Escalation Alert #" + refId;
        String message = "Complaint #" + refId + " was escalated. Reason: " + reason;

        // 1. Notify all active supervisors
        List<User> supervisors = userRepository.findActiveUsersByRoleName(RoleName.SUPERVISOR);
        for (User sup : supervisors) {
            sendNotification(
                    sup,
                    actor,
                    complaint,
                    refId,
                    title,
                    message,
                    NotificationType.ESCALATION,
                    NotificationSeverity.ACTION_REQUIRED,
                    "/supervisor/escalations"
            );
        }

        // 2. Notify assigned officer
        if (complaint.getAssignedOfficer() != null) {
            sendNotification(
                    complaint.getAssignedOfficer(),
                    actor,
                    complaint,
                    refId,
                    title,
                    message,
                    NotificationType.ESCALATION,
                    NotificationSeverity.ACTION_REQUIRED,
                    "/officer/complaints/" + refId + "/review"
            );
        }
    }

    /**
     * Dispatches notifications when a worker is reassigned by a supervisor.
     */
    public void notifyWorkerReassigned(Complaint complaint, User oldWorker, User newWorker, User supervisor, String reason) {
        if (complaint == null) return;
        String refId = complaint.getReferenceId();

        // 1. Notify old worker if present and different from new worker
        if (oldWorker != null && !oldWorker.getId().equals(newWorker.getId())) {
            sendNotification(
                    oldWorker,
                    supervisor,
                    complaint,
                    refId,
                    "Work Order Reassigned #" + refId,
                    "Work order #" + refId + " was reassigned. Reason: " + reason,
                    NotificationType.WORKER_REASSIGNED,
                    NotificationSeverity.INFO,
                    "/worker/tasks"
            );
        }

        // 2. Notify new worker
        sendNotification(
                newWorker,
                supervisor,
                complaint,
                refId,
                "New Work Order Assigned #" + refId,
                "You have been assigned to work order #" + refId + ". Reason: " + reason,
                NotificationType.WORKER_ASSIGNED,
                NotificationSeverity.ACTION_REQUIRED,
                "/worker/tasks"
        );

        // 3. Notify assigned officer
        if (complaint.getAssignedOfficer() != null) {
            sendNotification(
                    complaint.getAssignedOfficer(),
                    supervisor,
                    complaint,
                    refId,
                    "Technician Reassigned #" + refId,
                    "Technician for complaint #" + refId + " was updated to " + newWorker.getFullName() + ". Reason: " + reason,
                    NotificationType.WORKER_REASSIGNED,
                    NotificationSeverity.INFO,
                    "/officer/complaints/" + refId + "/review"
            );
        }
    }

    /**
     * Persists an individual notification with short-window deduplication protection.
     */
    public boolean sendNotification(User recipient,
                                    User actor,
                                    Complaint complaint,
                                    String referenceId,
                                    String title,
                                    String message,
                                    NotificationType type,
                                    NotificationSeverity severity,
                                    String targetUrl) {
        if (recipient == null) {
            log.warn("Cannot send notification: recipient is null for complaint #{}", referenceId);
            return false;
        }

        try {
            // Deduplication guard: suppress identical notifications within recent time window
            OffsetDateTime window = OffsetDateTime.now().minusSeconds(DEDUPLICATION_WINDOW_SECONDS);
            if (complaint != null && complaint.getId() != null) {
                boolean duplicate = notificationRepository.existsByRecipientIdAndComplaintIdAndNotificationTypeAndCreatedAtAfter(
                        recipient.getId(), complaint.getId(), type, window);
                if (duplicate) {
                    log.info("Deduplication: Suppressed duplicate {} notification for user {} on complaint #{}",
                            type, recipient.getId(), referenceId);
                    return false;
                }
            }

            Notification notification = new Notification(
                    recipient,
                    actor,
                    complaint,
                    referenceId,
                    title,
                    message,
                    type,
                    severity != null ? severity : NotificationSeverity.INFO,
                    targetUrl
            );

            notificationRepository.save(notification);
            log.debug("Persisted notification {} for user {} (complaint #{})", type, recipient.getId(), referenceId);
            return true;
        } catch (Exception e) {
            log.error("Error persisting notification {} for user {}: {}", type, recipient.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Retrieves paginated notifications strictly for the authenticated user, newest first.
     */
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getUserNotifications(UserPrincipal principal, boolean unreadOnly, Pageable pageable) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedException("Authentication required to access notifications.");
        }

        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(principal.getId(), false, pageable)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(principal.getId(), pageable);

        List<NotificationResponse> content = page.getContent().stream()
                .map(NotificationResponse::fromEntity)
                .toList();

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    /**
     * Retrieves the unread notification count strictly for the authenticated user.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedException("Authentication required to access notifications.");
        }
        return notificationRepository.countByRecipientIdAndIsReadFalse(principal.getId());
    }

    /**
     * Marks a single notification as read if and only if it belongs to the authenticated user.
     * Prevents ID guessing enumeration by throwing ResourceNotFoundException (404) for unauthorized or missing IDs.
     */
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedException("Authentication required to modify notifications.");
        }
        if (notificationId == null) {
            throw new BadRequestException("Notification ID must not be null.");
        }

        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!notification.isRead()) {
            notification.markAsRead();
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.fromEntity(notification);
    }

    /**
     * Marks all unread notifications belonging to the authenticated user as read.
     * Executes an efficient database-level bulk update without loading entities into memory.
     */
    @Transactional
    public int markAllAsRead(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedException("Authentication required to modify notifications.");
        }

        return notificationRepository.markAllAsReadByRecipientId(principal.getId(), OffsetDateTime.now());
    }

    private void dispatchComplaintSubmittedEmail(Complaint complaint, String refId) {
        if (complaint.getCitizen() == null || complaint.getCitizen().getEmail() == null || complaint.getCitizen().getEmail().isBlank()) {
            return;
        }
        String toEmail = complaint.getCitizen().getEmail();
        String citizenName = complaint.getCitizen().getFullName();
        String title = complaint.getTitle();
        String category = complaint.getCategory() != null ? complaint.getCategory() : "General";
        String ward = complaint.getWard();

        if (emailOutboxService != null) {
            String idemKey = "intake_" + complaint.getId();
            emailOutboxService.enqueueEmail(
                    idemKey,
                    toEmail,
                    "CivicFix Complaint Receipt #" + refId,
                    "COMPLAINT_SUBMITTED",
                    java.util.Map.of(
                            "citizenName", citizenName != null ? citizenName : "Resident",
                            "referenceId", refId != null ? refId : "",
                            "title", title != null ? title : "",
                            "category", category,
                            "ward", ward != null ? ward : ""
                    )
            );
        } else if (emailService != null) {
            emailService.sendComplaintSubmittedEmail(toEmail, citizenName, refId, title, category, ward);
        }
    }

    private void dispatchNewComplaintAlertEmail(User officer, Complaint complaint, String refId, String wardDesc) {
        if (officer == null || officer.getEmail() == null || officer.getEmail().isBlank()) {
            return;
        }
        String toEmail = officer.getEmail();
        String officerName = officer.getFullName();
        String title = complaint.getTitle();
        String priority = complaint.getPriority() != null ? complaint.getPriority().name() : "MEDIUM";

        if (emailOutboxService != null) {
            String idemKey = "alert_" + complaint.getId() + "_" + officer.getId();
            emailOutboxService.enqueueEmail(
                    idemKey,
                    toEmail,
                    "New Complaint Alert: #" + refId + " in " + wardDesc,
                    "NEW_COMPLAINT_ALERT",
                    java.util.Map.of(
                            "officerName", officerName != null ? officerName : "",
                            "referenceId", refId != null ? refId : "",
                            "title", title != null ? title : "",
                            "priority", priority,
                            "ward", wardDesc != null ? wardDesc : ""
                    )
            );
        } else if (emailService != null) {
            emailService.sendNewComplaintAlertEmail(toEmail, officerName, refId, title, priority, wardDesc);
        }
    }

    private void dispatchComplaintAssignmentEmail(User worker, Complaint complaint, String refId) {
        if (worker == null || worker.getEmail() == null || worker.getEmail().isBlank()) {
            return;
        }
        String toEmail = worker.getEmail();
        String recipientName = worker.getFullName();
        String role = "FIELD_WORKER";
        String title = complaint.getTitle();
        String priority = complaint.getPriority() != null ? complaint.getPriority().name() : "MEDIUM";
        String ward = complaint.getWard();

        if (emailOutboxService != null) {
            long minuteBucket = System.currentTimeMillis() / 60000L;
            String idemKey = "assign_" + complaint.getId() + "_" + worker.getId() + "_" + minuteBucket;
            emailOutboxService.enqueueEmail(
                    idemKey,
                    toEmail,
                    "Task Assigned: Complaint #" + refId + " (" + priority + ")",
                    "COMPLAINT_ASSIGNMENT",
                    java.util.Map.of(
                            "recipientName", recipientName != null ? recipientName : "Field Technician",
                            "role", role,
                            "referenceId", refId != null ? refId : "",
                            "title", title != null ? title : "",
                            "priority", priority,
                            "ward", ward != null ? ward : ""
                    )
            );
        } else if (emailService != null) {
            emailService.sendComplaintAssignmentEmail(toEmail, recipientName, role, refId, title, priority, ward);
        }
    }

    private void dispatchStatusUpdateEmail(User citizen, Complaint complaint, String refId, String oldStatus, String newStatus, String notes) {
        if (citizen == null || citizen.getEmail() == null || citizen.getEmail().isBlank()) {
            return;
        }
        String toEmail = citizen.getEmail();
        String citizenName = citizen.getFullName();
        String title = complaint.getTitle();

        if (emailOutboxService != null) {
            long minuteBucket = System.currentTimeMillis() / 60000L;
            String idemKey = "status_" + complaint.getId() + "_" + newStatus + "_" + minuteBucket;
            emailOutboxService.enqueueEmail(
                    idemKey,
                    toEmail,
                    "Status Update: Complaint #" + refId + " is now " + newStatus,
                    "COMPLAINT_STATUS_UPDATE",
                    java.util.Map.of(
                            "citizenName", citizenName != null ? citizenName : "Resident",
                            "referenceId", refId != null ? refId : "",
                            "title", title != null ? title : "",
                            "oldStatus", oldStatus != null ? oldStatus : "",
                            "newStatus", newStatus != null ? newStatus : "",
                            "notes", notes != null ? notes : ""
                    )
            );
        } else if (emailService != null) {
            emailService.sendComplaintStatusUpdateEmail(toEmail, citizenName, refId, title, oldStatus, newStatus, notes);
        }
    }

    public void notifySlaApproachingDeadline(Complaint complaint) {
        if (complaint == null) return;
        String refId = complaint.getReferenceId();
        String title = "SLA Approaching Deadline #" + refId;
        String message = "Complaint #" + refId + " (" + complaint.getTitle() + ") is nearing its SLA resolution deadline.";

        if (complaint.getAssignedOfficer() != null) {
            sendNotification(
                    complaint.getAssignedOfficer(),
                    null,
                    complaint,
                    refId,
                    title,
                    message,
                    NotificationType.SLA_APPROACHING_DEADLINE,
                    NotificationSeverity.WARNING,
                    "/officer/complaints/" + refId + "/review"
            );
        }

        if (complaint.getAssignedWorker() != null) {
            sendNotification(
                    complaint.getAssignedWorker(),
                    null,
                    complaint,
                    refId,
                    "Task Approaching Deadline #" + refId,
                    "Assigned task #" + refId + " is nearing its SLA resolution deadline. Please submit repair evidence.",
                    NotificationType.SLA_APPROACHING_DEADLINE,
                    NotificationSeverity.WARNING,
                    "/worker/tasks/" + refId + "/progress"
            );
        }
    }
}

