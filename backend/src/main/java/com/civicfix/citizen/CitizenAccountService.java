package com.civicfix.citizen;

import com.civicfix.audit.AuditService;
import com.civicfix.citizen.dto.CitizenDataExportResponse;
import com.civicfix.citizen.dto.DeleteAccountRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.complaint.ComplaintStatusHistory;
import com.civicfix.complaint.ComplaintStatusHistoryRepository;
import com.civicfix.security.UserPrincipal;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CitizenAccountService {

    private static final Logger log = LoggerFactory.getLogger(CitizenAccountService.class);

    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public CitizenAccountService(UserRepository userRepository,
                                 ComplaintRepository complaintRepository,
                                 ComplaintStatusHistoryRepository historyRepository,
                                 AuditService auditService,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public CitizenDataExportResponse exportCitizenData(UserPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required to export account data.");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resident profile not found."));

        List<Complaint> complaints = complaintRepository.findByCitizenIdOrderByCreatedAtDesc(user.getId());

        List<CitizenDataExportResponse.ExportedComplaint> exportedComplaints = new ArrayList<>();
        for (Complaint c : complaints) {
            List<ComplaintStatusHistory> histories;
            if (historyRepository != null) {
                histories = historyRepository.findByComplaintIdOrderByCreatedAtAsc(c.getId());
            } else {
                histories = c.getStatusHistory() != null ? c.getStatusHistory() : Collections.emptyList();
            }

            List<CitizenDataExportResponse.ExportedHistory> timeline = histories.stream()
                    .map(h -> new CitizenDataExportResponse.ExportedHistory(
                            h.getPreviousStatus(),
                            h.getNewStatus(),
                            h.getChangedByName(),
                            h.getNotes(),
                            h.getCreatedAt()
                    ))
                    .toList();

            exportedComplaints.add(new CitizenDataExportResponse.ExportedComplaint(
                    c.getId() != null ? c.getId().toString() : null,
                    c.getReferenceId(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getCategory(),
                    c.getPriority() != null ? c.getPriority().name() : null,
                    c.getStatus() != null ? c.getStatus().name() : null,
                    c.getAddress(),
                    c.getWard(),
                    c.getCreatedAt(),
                    c.getConfirmedAt(),
                    timeline
            ));
        }

        return new CitizenDataExportResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getWard(),
                user.getTrustScore(),
                user.getCreatedAt(),
                OffsetDateTime.now(),
                exportedComplaints.size(),
                exportedComplaints
        );
    }

    @Transactional
    public Map<String, Object> deleteCitizenAccount(DeleteAccountRequest req, UserPrincipal principal, HttpServletRequest request, String ipAddress) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required to delete account.");
        }

        if (req == null || req.getPassword() == null || req.getPassword().isBlank()) {
            throw new BadRequestException("Current password confirmation is required to delete your account.");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resident profile not found."));

        if (!user.hasRole(RoleName.CITIZEN)) {
            throw new ForbiddenException("Only citizen resident accounts can be self-deleted via resident portal.");
        }

        if ("DELETED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new BadRequestException("Account is already deleted.");
        }

        // Verify password
        if (passwordEncoder != null && !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password verification failed. Account deletion aborted.");
        }

        UUID userId = user.getId();
        String originalEmail = user.getEmail();

        // Soft-anonymize user
        user.setFullName("Deleted Resident");
        user.setEmail("deleted_" + userId.toString() + "@anonymized.civicfix.local");
        user.setPhone(null);
        user.setAccountStatus("DELETED");

        userRepository.save(user);

        // Tamper-evident audit logging
        if (auditService != null) {
            auditService.logAction(
                    userId,
                    "Deleted Resident",
                    "CITIZEN",
                    "DELETE_ACCOUNT",
                    "USER",
                    userId.toString(),
                    "Citizen requested soft-anonymization and account deletion. PII removed; civic complaint records retained for public auditability.",
                    ipAddress,
                    "SUCCESS"
            );
        }

        // Invalidate HTTP session and SecurityContext
        if (request != null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        SecurityContextHolder.clearContext();

        log.info("Resident account {} (formerly {}) successfully soft-anonymized and deleted.", userId, originalEmail);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Citizen account anonymized and deleted successfully.");
        result.put("deletedAt", OffsetDateTime.now());
        result.put("residentId", userId.toString());
        return result;
    }
}
