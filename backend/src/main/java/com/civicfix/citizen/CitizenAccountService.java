package com.civicfix.citizen;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.citizen.dto.CitizenDataExportResponse;
import com.civicfix.citizen.dto.DeleteAccountRequest;
import com.civicfix.citizen.dto.UpdateCitizenProfileRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.complaint.ComplaintStatusHistory;
import com.civicfix.complaint.ComplaintStatusHistoryRepository;
import com.civicfix.security.UserPrincipal;
import com.civicfix.storage.StorageResult;
import com.civicfix.storage.StorageService;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final StorageService storageService;

    public CitizenAccountService(UserRepository userRepository,
                                 ComplaintRepository complaintRepository,
                                 ComplaintStatusHistoryRepository historyRepository,
                                 AuditService auditService,
                                 PasswordEncoder passwordEncoder) {
        this(userRepository, complaintRepository, historyRepository, auditService, passwordEncoder, null);
    }

    @Autowired
    public CitizenAccountService(UserRepository userRepository,
                                 ComplaintRepository complaintRepository,
                                 ComplaintStatusHistoryRepository historyRepository,
                                 AuditService auditService,
                                 PasswordEncoder passwordEncoder,
                                 @Autowired(required = false) StorageService storageService) {
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
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

    @Transactional
    public AuthUserResponse updateProfile(UpdateCitizenProfileRequest req, UserPrincipal principal, String ipAddress) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required to update profile.");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found."));

        if (req.getName() != null && !req.getName().isBlank()) {
            user.setFullName(req.getName().trim());
        }
        if (req.getPhone() != null) {
            String trimmedPhone = req.getPhone().trim();
            if (!trimmedPhone.isEmpty() && !trimmedPhone.equals(user.getPhone()) && userRepository.existsByPhone(trimmedPhone)) {
                throw new BadRequestException("This phone number is already associated with another account.");
            }
            user.setPhone(trimmedPhone.isEmpty() ? null : trimmedPhone);
        }
        if (req.getDistrict() != null) {
            user.setDistrict(req.getDistrict().isBlank() ? null : req.getDistrict().trim());
        }
        if (req.getZone() != null) {
            user.setZone(req.getZone().isBlank() ? null : req.getZone().trim());
        }
        if (req.getWard() != null) {
            user.setWard(req.getWard().isBlank() ? null : req.getWard().trim());
        }

        User saved = userRepository.save(user);
        if (auditService != null) {
            auditService.logAction(
                    saved.getId(),
                    saved.getFullName(),
                    "CITIZEN",
                    "UPDATE_PROFILE",
                    "USER",
                    saved.getId().toString(),
                    "Updated resident profile location/contact details.",
                    ipAddress,
                    "SUCCESS"
            );
        }
        return AuthUserResponse.fromEntity(saved);
    }

    @Transactional
    public AuthUserResponse uploadProfilePhoto(MultipartFile file, UserPrincipal principal, String ipAddress) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required to upload profile photo.");
        }
        if (storageService == null) {
            throw new BadRequestException("Object storage service is not configured.");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select an image file to upload.");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new BadRequestException("Profile photo must not exceed 5 MB.");
        }
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!Set.of("image/jpeg", "image/jpg", "image/png", "image/webp").contains(ct)) {
            throw new BadRequestException("Only JPEG, PNG, and WebP images are supported for profile photos.");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found."));

        String subDir = "profiles/" + user.getId();
        String previousUrl = user.getProfilePhotoUrl();
        try {
            StorageResult sResult = storageService.storeImage(file, subDir, user.getId().toString());
            String storedPath = sResult.resourceUrl();
            String filename = storedPath.substring(storedPath.lastIndexOf('/') + 1);
            user.setProfilePhotoObjectKey(sResult.objectKey());
            user.setProfilePhotoUrl("/api/citizen/account/profile/photo/" + user.getId() + "/" + filename);
            User saved = userRepository.save(user);

            if (previousUrl != null && previousUrl.contains("/")) {
                String oldFilename = previousUrl.substring(previousUrl.lastIndexOf('/') + 1);
                if (!oldFilename.equals(filename)) {
                    try {
                        storageService.deleteFile(oldFilename, subDir);
                    } catch (Exception ignored) {}
                }
            }

            if (auditService != null) {
                auditService.logAction(
                        saved.getId(),
                        saved.getFullName(),
                        "CITIZEN",
                        "UPLOAD_PROFILE_PHOTO",
                        "USER",
                        saved.getId().toString(),
                        "Uploaded resident profile photo to object storage.",
                        ipAddress,
                        "SUCCESS"
                );
            }
            return AuthUserResponse.fromEntity(saved);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException re) throw re;
            throw new BadRequestException("Failed to store profile photo: " + ex.getMessage());
        }
    }

    @Transactional
    public AuthUserResponse deleteProfilePhoto(UserPrincipal principal, String ipAddress) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required to remove profile photo.");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found."));

        String previousUrl = user.getProfilePhotoUrl();
        if (storageService != null && previousUrl != null && previousUrl.contains("/")) {
            String filename = previousUrl.substring(previousUrl.lastIndexOf('/') + 1);
            try {
                storageService.deleteFile(filename, "profiles/" + user.getId());
            } catch (Exception ex) {
                log.warn("Non-fatal error deleting old profile photo {}: {}", filename, ex.getMessage());
            }
        }
        user.setProfilePhotoObjectKey(null);
        user.setProfilePhotoUrl(null);
        User saved = userRepository.save(user);
        return AuthUserResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Resource loadProfilePhoto(UUID targetUserId, String filename, UserPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required.");
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!principal.getId().equals(targetUserId) && !isAdmin) {
            throw new ForbiddenException("You are not authorized to access another resident's profile photo.");
        }
        if (storageService == null) {
            throw new ResourceNotFoundException("Storage service unavailable.");
        }
        try {
            return storageService.loadFileAsResource(filename, "profiles/" + targetUserId);
        } catch (Exception ex) {
            throw new ResourceNotFoundException("Profile photo not found.");
        }
    }
}
