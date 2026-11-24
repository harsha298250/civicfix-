package com.civicfix.complaint;

import com.civicfix.admin.dto.AdminDeletedComplaintResponse;
import com.civicfix.ai.AiTriageService;
import com.civicfix.audit.AuditService;
import com.civicfix.common.PagedResponse;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ConflictException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.common.util.GeoUtils;
import com.civicfix.complaint.dto.*;
import com.civicfix.complaint.sla.SlaEscalationService;
import com.civicfix.complaint.sla.SlaService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.storage.StorageService;
import com.civicfix.supervisor.SupervisorService;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.civicfix.complaint.event.ComplaintEvent;
import com.civicfix.complaint.event.ComplaintEventType;

import com.civicfix.ai.dto.DuplicateCandidate;
import com.civicfix.ai.summary.ComplaintSummaryResponse;
import com.civicfix.ai.summary.ComplaintSummaryService;
import com.civicfix.ai.vision.VisionTriageProperties;
import com.civicfix.ai.vision.pothole.PotholeDetectionService;
import com.civicfix.ai.vision.pothole.dto.CalibrationRequest;
import com.civicfix.ai.vision.pothole.dto.PotholeAnalysisResponse;
import com.civicfix.gis.GisService;
import com.civicfix.gis.dto.WardValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintService.class);

    private final ComplaintRepository complaintRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final ResolutionEvidenceRepository evidenceRepository;
    private final ComplaintPhotoRepository complaintPhotoRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final StorageService storageService;
    private final AiTriageService aiTriageService;
    private final ApplicationEventPublisher eventPublisher;
    private final SlaService slaService;
    private final SlaEscalationService slaEscalationService;
    private final VisionTriageProperties visionProperties;
    private GisService gisService;
    @Autowired(required = false)
    private PotholeDetectionService potholeDetectionService;

    @org.springframework.beans.factory.annotation.Value("${civicfix.evidence.max-distance-meters:100.0}")
    private double maxDistanceMeters = 100.0;

    @org.springframework.beans.factory.annotation.Value("${civicfix.evidence.max-accuracy-meters:100.0}")
    private double maxAccuracyMeters = 100.0;

    @org.springframework.beans.factory.annotation.Value("${public-map.minimum-group-size:5}")
    private int publicMapMinimumGroupSize = 5;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PdfReceiptService pdfReceiptService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.civicfix.storage.FileTransactionHelper fileTransactionHelper;

    public void setFileTransactionHelper(com.civicfix.storage.FileTransactionHelper fileTransactionHelper) {
        this.fileTransactionHelper = fileTransactionHelper;
    }

    public void setMaxDistanceMeters(double maxDistanceMeters) {
        this.maxDistanceMeters = maxDistanceMeters;
    }

    public void setMaxAccuracyMeters(double maxAccuracyMeters) {
        this.maxAccuracyMeters = maxAccuracyMeters;
    }

    public void setEvidenceLocationLimits(double maxDistance, double maxAccuracy) {
        this.maxDistanceMeters = maxDistance;
        this.maxAccuracyMeters = maxAccuracy;
    }
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Value("${civicfix.reopen.window-days:30}")
    private int reopenWindowDays = 30;

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintStatusHistoryRepository historyRepository,
                            ResolutionEvidenceRepository evidenceRepository,
                            ComplaintPhotoRepository complaintPhotoRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            StorageService storageService) {
        this(complaintRepository, historyRepository, evidenceRepository, complaintPhotoRepository, userRepository, auditService, storageService, null, null, null, null, null);
    }

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintStatusHistoryRepository historyRepository,
                            ResolutionEvidenceRepository evidenceRepository,
                            ComplaintPhotoRepository complaintPhotoRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            StorageService storageService,
                            AiTriageService aiTriageService) {
        this(complaintRepository, historyRepository, evidenceRepository, complaintPhotoRepository, userRepository, auditService, storageService, aiTriageService, null, null, null, null);
    }

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintStatusHistoryRepository historyRepository,
                            ResolutionEvidenceRepository evidenceRepository,
                            ComplaintPhotoRepository complaintPhotoRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            StorageService storageService,
                            AiTriageService aiTriageService,
                            ApplicationEventPublisher eventPublisher) {
        this(complaintRepository, historyRepository, evidenceRepository, complaintPhotoRepository, userRepository, auditService, storageService, aiTriageService, eventPublisher, null, null, null);
    }

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintStatusHistoryRepository historyRepository,
                            ResolutionEvidenceRepository evidenceRepository,
                            ComplaintPhotoRepository complaintPhotoRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            StorageService storageService,
                            AiTriageService aiTriageService,
                            ApplicationEventPublisher eventPublisher,
                            SlaService slaService,
                            SlaEscalationService slaEscalationService) {
        this(complaintRepository, historyRepository, evidenceRepository, complaintPhotoRepository, userRepository, auditService, storageService, aiTriageService, eventPublisher, slaService, slaEscalationService, null);
    }

    @Autowired
    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintStatusHistoryRepository historyRepository,
                            ResolutionEvidenceRepository evidenceRepository,
                            ComplaintPhotoRepository complaintPhotoRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            StorageService storageService,
                            @Autowired(required = false) AiTriageService aiTriageService,
                            @Autowired(required = false) ApplicationEventPublisher eventPublisher,
                            @Autowired(required = false) SlaService slaService,
                            @Autowired(required = false) SlaEscalationService slaEscalationService,
                            @Autowired(required = false) VisionTriageProperties visionProperties,
                            @Autowired(required = false) GisService gisService) {
        this.complaintRepository = complaintRepository;
        this.historyRepository = historyRepository;
        this.evidenceRepository = evidenceRepository;
        this.complaintPhotoRepository = complaintPhotoRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.storageService = storageService;
        this.aiTriageService = aiTriageService;
        this.eventPublisher = eventPublisher;
        this.slaService = slaService;
        this.slaEscalationService = slaEscalationService;
        this.visionProperties = visionProperties;
        this.gisService = gisService;
    }

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintStatusHistoryRepository historyRepository,
                            ResolutionEvidenceRepository evidenceRepository,
                            ComplaintPhotoRepository complaintPhotoRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            StorageService storageService,
                            AiTriageService aiTriageService,
                            ApplicationEventPublisher eventPublisher,
                            SlaService slaService,
                            SlaEscalationService slaEscalationService,
                            VisionTriageProperties visionProperties) {
        this(complaintRepository, historyRepository, evidenceRepository, complaintPhotoRepository, userRepository, auditService, storageService, aiTriageService, eventPublisher, slaService, slaEscalationService, visionProperties, null);
    }

    public void setGisService(GisService gisService) {
        this.gisService = gisService;
    }

    public void setPotholeDetectionService(PotholeDetectionService potholeDetectionService) {
        this.potholeDetectionService = potholeDetectionService;
    }

    private void publishEvent(ComplaintEvent event) {
        if (eventPublisher != null && event != null) {
            try {
                eventPublisher.publishEvent(event);
            } catch (Exception ex) {
                log.warn("Failed to publish complaint domain event: {}", ex.getMessage());
            }
        }
    }

    private ComplaintResponse toResponse(Complaint c, UserPrincipal principal) {
        return toResponse(c, principal, true);
    }

    private ComplaintResponse toResponse(Complaint c, UserPrincipal principal, boolean isDetailView) {
        if (c == null) return null;
        return ComplaintResponse.fromEntity(c, principal, isDetailView);
    }

    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest req, UserPrincipal principal, String ipAddress) {
        return createComplaint(req, null, principal, ipAddress);
    }

    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest req, List<MultipartFile> files, UserPrincipal principal, String ipAddress) {
        User citizen = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        // Generate human-readable reference ID e.g. CF-2026-00124
        String referenceId;
        do {
            int num = 10000 + random.nextInt(90000);
            referenceId = "CF-2026-" + num;
        } while (complaintRepository.findByReferenceIdIgnoreCase(referenceId).isPresent());

        Complaint complaint = new Complaint();
        complaint.setReferenceId(referenceId);
        complaint.setCitizen(citizen);
        complaint.setTitle(req.getTitle().trim());
        complaint.setDescription(req.getDescription().trim());
        complaint.setCategory(req.getCategory().trim());
        complaint.setPriority(req.getPriority() != null ? req.getPriority() : ComplaintPriority.MEDIUM);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setAddress(req.getAddress().trim());
        complaint.setLatitude(req.getLatitude());
        complaint.setLongitude(req.getLongitude());
        complaint.setWard(req.getWard() != null ? req.getWard().trim() : "Ward 08, Anna Nagar");
        boolean visionEnabled = visionProperties != null && visionProperties.isEnabled();
        boolean consent = visionEnabled && Boolean.TRUE.equals(req.getVisionConsent());
        complaint.setVisionConsent(consent);
        complaint.setVisionStatus("NOT_RUN");
        complaint.setFinalPrioritySource("RULE_ENGINE");

        // Spatial boundary validation (Sub-Phase 2.4.6)
        if (gisService != null && complaint.getLatitude() != null && complaint.getLongitude() != null) {
            try {
                WardValidationResult wardResult = gisService.validateWardContainment(
                        complaint.getLatitude(),
                        complaint.getLongitude(),
                        complaint.getWard()
                );
                if (wardResult != null) {
                    complaint.setWardValidationStatus(wardResult.getStatus());
                    complaint.setWardBoundarySource(wardResult.getBoundarySource());
                    if (wardResult.getDistanceMeters() != null) {
                        complaint.setWardBoundaryDistanceMeters(BigDecimal.valueOf(wardResult.getDistanceMeters()));
                    }
                }
            } catch (Exception ex) {
                log.warn("Spatial boundary evaluation failed gracefully: {}", ex.getMessage());
            }
        }

        // Advisory automated triage evaluation (non-destructive)
        if (aiTriageService != null) {
            aiTriageService.triageComplaint(complaint);
        }

        // Compute SLA due date based on final priority and category
        if (slaService != null && complaint.getPriority() != null) {
            complaint.setSlaDueAt(slaService.computeSlaDueAt(complaint.getCategory(), complaint.getPriority(), complaint.getCreatedAt()));
        }

        // Immutable status history initial entry
        complaint.addStatusHistory(null, ComplaintStatus.SUBMITTED, citizen, "Citizen filed initial civic report via portal.");

        Complaint saved = complaintRepository.save(complaint);

        // Process optional citizen intake photographs
        if (files != null && !files.isEmpty()) {
            List<MultipartFile> activeFiles = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
            if (activeFiles.size() > 5) {
                throw new BadRequestException("A maximum of 5 incident photographs can be submitted.");
            }

            List<String> storedUrls = new ArrayList<>();
            String subDir = "complaints/" + saved.getId();
            try {
                for (MultipartFile file : activeFiles) {
                    String storedUrl = storageService.storeFile(file, subDir);
                    storedUrls.add(storedUrl);
                    String fname = storedUrl.substring(storedUrl.lastIndexOf('/') + 1);
                    if (fileTransactionHelper != null) {
                        fileTransactionHelper.registerRollbackCleanup(fname, subDir);
                    }

                    ComplaintPhoto photo = new ComplaintPhoto();
                    photo.setComplaint(saved);
                    photo.setPhotoUrl(storedUrl);
                    photo.setFileName(file.getOriginalFilename());
                    photo.setFileSize(file.getSize());
                    photo.setContentType(file.getContentType());
                    complaintPhotoRepository.save(photo);
                    saved.getPhotos().add(photo);
                }
            } catch (Exception e) {
                // Roll back any physical files created during this partial batch
                for (String url : storedUrls) {
                    String fname = url.substring(url.lastIndexOf('/') + 1);
                    storageService.deleteFile(fname, subDir);
                }
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException("Failed to store complaint photographs. Intake aborted.", e);
            }
        }

        auditService.logAction(
                citizen.getId(),
                citizen.getFullName(),
                "CITIZEN",
                "CREATE_COMPLAINT",
                "COMPLAINT",
                saved.getId().toString(),
                "Created complaint reference #" + saved.getReferenceId() + ": " + saved.getTitle() +
                        (saved.getPhotos().isEmpty() ? "" : " with " + saved.getPhotos().size() + " photo(s)"),
                ipAddress,
                "SUCCESS"
        );

        publishEvent(new ComplaintEvent(
                ComplaintEventType.COMPLAINT_CREATED,
                saved,
                citizen,
                null,
                saved.getStatus(),
                "Citizen filed initial civic report via portal."
        ));

        return toResponse(saved, principal);
    }

    @Transactional
    public ComplaintResponse addComplaintPhotos(String idOrRef, List<MultipartFile> files, UserPrincipal principal, String ipAddress) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required to upload photographs.");
        }
        Complaint complaint = resolveComplaint(idOrRef);

        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOwner = complaint.getCitizen() != null && complaint.getCitizen().getId().equals(principal.getId());

        if (!isOwner && !isOfficer && !isAdmin) {
            throw new ForbiddenException("Only the complaint author, an officer, or an administrator can add photographs.");
        }

        if (files == null || files.isEmpty()) {
            throw new BadRequestException("At least one photograph must be provided.");
        }

        List<MultipartFile> activeFiles = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (activeFiles.isEmpty()) {
            throw new BadRequestException("At least one non-empty photograph must be provided.");
        }

        if (complaint.getPhotos().size() + activeFiles.size() > 5) {
            throw new BadRequestException("A maximum of 5 incident photographs is permitted per complaint.");
        }

        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<String> storedUrls = new ArrayList<>();
        String subDir = "complaints/" + complaint.getId();
        try {
            for (MultipartFile file : activeFiles) {
                String storedUrl = storageService.storeFile(file, subDir);
                storedUrls.add(storedUrl);
                String fname = storedUrl.substring(storedUrl.lastIndexOf('/') + 1);
                if (fileTransactionHelper != null) {
                    fileTransactionHelper.registerRollbackCleanup(fname, subDir);
                }

                ComplaintPhoto photo = new ComplaintPhoto();
                photo.setComplaint(complaint);
                photo.setPhotoUrl(storedUrl);
                photo.setFileName(file.getOriginalFilename());
                photo.setFileSize(file.getSize());
                photo.setContentType(file.getContentType());
                complaintPhotoRepository.save(photo);
                complaint.getPhotos().add(photo);
            }
        } catch (Exception e) {
            for (String url : storedUrls) {
                String fname = url.substring(url.lastIndexOf('/') + 1);
                storageService.deleteFile(fname, subDir);
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Failed to store complaint photographs. Upload aborted.", e);
        }

        auditService.logAction(
                actor.getId(),
                actor.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "UPLOAD_PHOTOS",
                "COMPLAINT",
                complaint.getReferenceId(),
                String.format("User %s uploaded %d photograph(s) for complaint #%s.",
                        actor.getFullName(), activeFiles.size(), complaint.getReferenceId()),
                ipAddress,
                "SUCCESS"
        );

        return toResponse(complaint, principal);
    }

    private void validateBoundingBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
        if (minLat == null && maxLat == null && minLng == null && maxLng == null) {
            return;
        }
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            throw new BadRequestException("All bounding box coordinates (minLat, maxLat, minLng, maxLng) must be provided together.");
        }
        if (minLat.compareTo(new BigDecimal("-90.0")) < 0 || maxLat.compareTo(new BigDecimal("90.0")) > 0) {
            throw new BadRequestException("Latitude values must be between -90.0 and 90.0.");
        }
        if (minLng.compareTo(new BigDecimal("-180.0")) < 0 || maxLng.compareTo(new BigDecimal("180.0")) > 0) {
            throw new BadRequestException("Longitude values must be between -180.0 and 180.0.");
        }
        if (minLat.compareTo(maxLat) > 0) {
            throw new BadRequestException("minLat cannot be greater than maxLat.");
        }
        if (minLng.compareTo(maxLng) > 0) {
            throw new BadRequestException("minLng cannot be greater than maxLng.");
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ComplaintResponse> getComplaints(UserPrincipal principal,
                                                          ComplaintStatus status,
                                                          String category,
                                                          Pageable pageable) {
        return getComplaints(principal, status, category, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ComplaintResponse> getComplaints(UserPrincipal principal,
                                                          ComplaintStatus status,
                                                          String category,
                                                          String ward,
                                                          BigDecimal minLat,
                                                          BigDecimal maxLat,
                                                          BigDecimal minLng,
                                                          BigDecimal maxLng,
                                                          Pageable pageable) {
        validateBoundingBox(minLat, maxLat, minLng, maxLng);

        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isWorker = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WORKER"));

        Page<Complaint> page;

        if (isAdmin || isOfficer) {
            String targetWard = ward;
            if (isOfficer && !isAdmin) {
                if (!principal.hasActiveCrossWardAccess()) {
                    // Default rule: Officers can ONLY access complaints in their assigned ward
                    targetWard = principal.getWard();
                } else if (targetWard != null && !targetWard.trim().isEmpty() && !targetWard.trim().equalsIgnoreCase(principal.getWard())) {
                    // Audited cross-ward access
                    auditService.logAction(
                            principal.getId(),
                            principal.getFullName(),
                            "OFFICER",
                            "OFFICER_CROSS_WARD_ACCESS",
                            "WARD",
                            targetWard.trim(),
                            "Officer accessed complaints in cross-ward: " + targetWard.trim(),
                            null,
                            "SUCCESS"
                    );
                }
            }

            if (minLat != null) {
                if (targetWard != null && !targetWard.trim().isEmpty()) {
                    page = complaintRepository.findBySpatialBoundsAndWard(minLat, maxLat, minLng, maxLng, targetWard.trim(), pageable);
                } else {
                    page = complaintRepository.findBySpatialBounds(minLat, maxLat, minLng, maxLng, pageable);
                }
            } else if (targetWard != null && !targetWard.trim().isEmpty()) {
                page = complaintRepository.findByWardIgnoreCaseOrderByCreatedAtDesc(targetWard.trim(), pageable);
            } else if (status != null) {
                page = complaintRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
            } else if (category != null && !category.trim().isEmpty()) {
                page = complaintRepository.findByCategoryIgnoreCaseOrderByCreatedAtDesc(category.trim(), pageable);
            } else {
                page = complaintRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        } else if (isWorker) {
            if (minLat != null) {
                page = complaintRepository.findByAssignedWorkerIdAndSpatialBounds(
                        principal.getId(), minLat, maxLat, minLng, maxLng, pageable);
            } else if (status != null) {
                page = complaintRepository.findByAssignedWorkerIdAndStatusOrderByCreatedAtDesc(principal.getId(), status, pageable);
            } else {
                page = complaintRepository.findByAssignedWorkerIdOrderByCreatedAtDesc(principal.getId(), pageable);
            }
        } else {
            // Citizen: Strictly isolated to own complaints
            if (minLat != null) {
                page = complaintRepository.findByCitizenIdAndSpatialBounds(
                        principal.getId(), minLat, maxLat, minLng, maxLng, pageable);
            } else {
                page = complaintRepository.findByCitizenIdOrderByCreatedAtDesc(principal.getId(), pageable);
            }
        }

        // List view: apply data minimization (isDetailView = false)
        List<ComplaintResponse> dtoList = page.getContent().stream()
                .map(c -> toResponse(c, principal, false))
                .collect(Collectors.toList());

        return new PagedResponse<>(dtoList, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public List<PublicMapComplaintResponse> getPublicMapComplaints(BigDecimal minLat,
                                                                   BigDecimal maxLat,
                                                                   BigDecimal minLng,
                                                                   BigDecimal maxLng,
                                                                   ComplaintStatus status,
                                                                   String category,
                                                                   int limit) {
        validateBoundingBox(minLat, maxLat, minLng, maxLng);
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, safeLimit);

        List<Complaint> complaints;
        if (minLat != null) {
            if (status != null) {
                complaints = complaintRepository.findPublicMapBySpatialBoundsAndStatus(minLat, maxLat, minLng, maxLng, status, pageable);
            } else {
                complaints = complaintRepository.findPublicMapBySpatialBounds(minLat, maxLat, minLng, maxLng, pageable);
            }
        } else {
            if (status != null) {
                complaints = complaintRepository.findPublicMapByStatus(status, pageable);
            } else {
                complaints = complaintRepository.findAllWithCoordinates(pageable);
            }
        }

        if (category != null && !category.trim().isEmpty()) {
            String catTrimmed = category.trim();
            complaints = complaints.stream()
                    .filter(c -> c.getCategory() != null && c.getCategory().equalsIgnoreCase(catTrimmed))
                    .collect(Collectors.toList());
        }

        int threshold = this.publicMapMinimumGroupSize > 0 ? this.publicMapMinimumGroupSize : 5;

        // Group complaints strictly by stable spatial boundaries (Ward or fixed 0.05-deg grid)
        Map<String, List<Complaint>> groups = new LinkedHashMap<>();
        for (Complaint c : complaints) {
            String key = getSpatialAggregationKey(c);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        List<PublicMapComplaintResponse> result = new ArrayList<>();
        int mapIndex = 1;

        for (Map.Entry<String, List<Complaint>> entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            List<Complaint> group = entry.getValue();
            int groupSize = group.size();

            if (groupSize < threshold) {
                // Low-density group (< k complaints). Coordinates are strictly suppressed to protect resident privacy.
                for (Complaint c : group) {
                    result.add(PublicMapComplaintResponse.suppressed(
                            c,
                            mapIndex++,
                            "Low complaint density (< " + threshold + " reports in area). Exact location suppressed for resident privacy."
                    ));
                }
            } else {
                // Eligible for safe display (>= k complaints).
                // Centroid coordinates for the cluster prevent individual location deduction
                BigDecimal sumLat = BigDecimal.ZERO;
                BigDecimal sumLng = BigDecimal.ZERO;
                int validCoords = 0;
                for (Complaint c : group) {
                    if (c.getLatitude() != null && c.getLongitude() != null) {
                        sumLat = sumLat.add(c.getLatitude());
                        sumLng = sumLng.add(c.getLongitude());
                        validCoords++;
                    }
                }
                BigDecimal centroidLat = validCoords > 0
                        ? sumLat.divide(BigDecimal.valueOf(validCoords), 3, RoundingMode.HALF_UP)
                        : null;
                BigDecimal centroidLng = validCoords > 0
                        ? sumLng.divide(BigDecimal.valueOf(validCoords), 3, RoundingMode.HALF_UP)
                        : null;

                for (Complaint c : group) {
                    PublicMapComplaintResponse resp = PublicMapComplaintResponse.fromEntity(c, mapIndex++);
                    resp.setCount(groupSize);
                    resp.setAggregated(true);
                    resp.setLocationSuppressed(false);
                    if (centroidLat != null && centroidLng != null) {
                        resp.setLatitude(centroidLat);
                        resp.setLongitude(centroidLng);
                    }
                    result.add(resp);
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<NearbyComplaintResponse> getNearbyComplaints(
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm,
            String category,
            ComplaintStatus status,
            Integer limit) {
        return getNearbyComplaints(lat, lng, radiusKm, category, status, limit, null);
    }

    @Transactional(readOnly = true)
    public List<NearbyComplaintResponse> getNearbyComplaints(
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm,
            String category,
            ComplaintStatus status,
            Integer limit,
            UserPrincipal principal) {
        if (lat == null || lng == null || !GeoUtils.isValidCoordinatePair(lat, lng)) {
            throw new BadRequestException("Valid geographic coordinates (lat, lng) are required.");
        }
        if (radiusKm != null) {
            if (Double.isNaN(radiusKm) || Double.isInfinite(radiusKm) || radiusKm < 0.1 || radiusKm > 50.0) {
                throw new BadRequestException("Radius must be a finite positive number between 0.1 and 50.0 kilometers.");
            }
        }
        if (limit != null && (limit < 1 || limit > 100)) {
            throw new BadRequestException("Limit must be between 1 and 100.");
        }
        double safeRadiusKm = radiusKm != null ? radiusKm : 2.0;
        int safeLimit = limit != null ? limit : 20;

        // Bounding box approximation for 1 degree ~ 111.0 km
        double dLat = safeRadiusKm / 111.0;
        double dLng = safeRadiusKm / (111.0 * Math.max(0.1, Math.cos(Math.toRadians(lat.doubleValue()))));

        BigDecimal minLat = lat.subtract(BigDecimal.valueOf(dLat));
        BigDecimal maxLat = lat.add(BigDecimal.valueOf(dLat));
        BigDecimal minLng = lng.subtract(BigDecimal.valueOf(dLng));
        BigDecimal maxLng = lng.add(BigDecimal.valueOf(dLng));

        org.springframework.data.domain.PageRequest pageable =
                org.springframework.data.domain.PageRequest.of(0, Math.min(safeLimit * 4, 400));
        List<Complaint> candidates;
        if (status != null) {
            candidates = complaintRepository.findPublicMapBySpatialBoundsAndStatus(minLat, maxLat, minLng, maxLng, status, pageable);
        } else {
            candidates = complaintRepository.findPublicMapBySpatialBounds(minLat, maxLat, minLng, maxLng, pageable);
        }

        // Role-based geographic authorization
        if (principal != null) {
            boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
            boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
            boolean isWorker = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WORKER"));

            if (isOfficer && !isAdmin) {
                String officerWard = principal.getWard();
                if (officerWard != null && !officerWard.isBlank() && !principal.hasActiveCrossWardAccess()) {
                    candidates = candidates.stream()
                            .filter(c -> c.getWard() != null && c.getWard().trim().equalsIgnoreCase(officerWard.trim()))
                            .collect(Collectors.toList());
                }
            } else if (isSupervisor && !isAdmin) {
                String dept = principal.getDepartment();
                if (dept != null && !dept.isBlank() && !principal.hasActiveCrossDepartmentAccess()) {
                    candidates = candidates.stream()
                            .filter(c -> SupervisorService.isCategoryAllowedForDepartment(c.getCategory(), dept, principal))
                            .collect(Collectors.toList());
                }
            } else if (isWorker && !isAdmin) {
                // Strict assignment scoping: workers are strictly restricted to repair orders assigned to their account.
                // Merely belonging to a ward does NOT expose unassigned complaints in that ward. Missing assignment fails closed.
                candidates = candidates.stream()
                        .filter(c -> c.getAssignedWorker() != null
                                && c.getAssignedWorker().getId() != null
                                && c.getAssignedWorker().getId().equals(principal.getId()))
                        .collect(Collectors.toList());
            }
        }

        double maxDistanceMeters = safeRadiusKm * 1000.0;
        double queryLat = lat.doubleValue();
        double queryLng = lng.doubleValue();

        List<NearbyComplaintResponse> results = new ArrayList<>();
        for (Complaint c : candidates) {
            if (category != null && !category.isBlank()) {
                if (c.getCategory() == null || !c.getCategory().equalsIgnoreCase(category.trim())) {
                    continue;
                }
            }
            if (c.getLatitude() != null && c.getLongitude() != null) {
                double dist = GeoUtils.haversineDistance(queryLat, queryLng, c.getLatitude().doubleValue(), c.getLongitude().doubleValue());
                if (dist <= maxDistanceMeters) {
                    NearbyComplaintResponse resp = NearbyComplaintResponse.fromEntity(c, Math.round(dist * 10.0) / 10.0);
                    // If caller is CITIZEN and doesn't own this complaint, apply public privacy sanitization
                    if (principal != null) {
                        boolean isCitizen = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CITIZEN"));
                        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
                        boolean isWorker = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WORKER"));
                        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
                        if (isCitizen && !isAdmin && !isOfficer && !isWorker && !isSupervisor) {
                            boolean isOwner = c.getCitizen() != null && c.getCitizen().getId() != null && c.getCitizen().getId().equals(principal.getId());
                            if (!isOwner) {
                                resp.sanitizeForPublic(false);
                            }
                        }
                    }
                    results.add(resp);
                }
            }
        }

        results.sort(Comparator.comparingDouble(NearbyComplaintResponse::getDistanceMeters));
        if (results.size() > safeLimit) {
            return results.subList(0, safeLimit);
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<NearbyComplaintResponse> getPublicNearbyComplaints(
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm,
            String category,
            ComplaintStatus status,
            Integer limit) {
        if (lat == null || lng == null || !GeoUtils.isValidCoordinatePair(lat, lng)) {
            throw new BadRequestException("Valid geographic coordinates (lat, lng) are required.");
        }
        if (radiusKm != null) {
            if (Double.isNaN(radiusKm) || Double.isInfinite(radiusKm) || radiusKm < 0.1 || radiusKm > 50.0) {
                throw new BadRequestException("Radius must be a finite positive number between 0.1 and 50.0 kilometers.");
            }
        }
        if (limit != null && (limit < 1 || limit > 100)) {
            throw new BadRequestException("Limit must be between 1 and 100.");
        }
        double safeRadiusKm = radiusKm != null ? radiusKm : 2.0;
        int safeLimit = limit != null ? limit : 20;

        double dLat = safeRadiusKm / 111.0;
        double dLng = safeRadiusKm / (111.0 * Math.max(0.1, Math.cos(Math.toRadians(lat.doubleValue()))));

        BigDecimal minLat = lat.subtract(BigDecimal.valueOf(dLat));
        BigDecimal maxLat = lat.add(BigDecimal.valueOf(dLat));
        BigDecimal minLng = lng.subtract(BigDecimal.valueOf(dLng));
        BigDecimal maxLng = lng.add(BigDecimal.valueOf(dLng));

        org.springframework.data.domain.PageRequest pageable =
                org.springframework.data.domain.PageRequest.of(0, Math.min(safeLimit * 4, 400));
        List<Complaint> candidates;
        if (status != null) {
            candidates = complaintRepository.findPublicMapBySpatialBoundsAndStatus(minLat, maxLat, minLng, maxLng, status, pageable);
        } else {
            candidates = complaintRepository.findPublicMapBySpatialBounds(minLat, maxLat, minLng, maxLng, pageable);
        }

        double maxDistanceMeters = safeRadiusKm * 1000.0;
        double queryLat = lat.doubleValue();
        double queryLng = lng.doubleValue();

        List<NearbyComplaintResponse> results = new ArrayList<>();
        Map<String, List<NearbyComplaintResponse>> clusterMap = new LinkedHashMap<>();

        for (Complaint c : candidates) {
            if (category != null && !category.isBlank()) {
                if (c.getCategory() == null || !c.getCategory().equalsIgnoreCase(category.trim())) {
                    continue;
                }
            }
            if (c.getLatitude() != null && c.getLongitude() != null) {
                double dist = GeoUtils.haversineDistance(queryLat, queryLng, c.getLatitude().doubleValue(), c.getLongitude().doubleValue());
                if (dist <= maxDistanceMeters) {
                    NearbyComplaintResponse resp = NearbyComplaintResponse.fromEntity(c, Math.round(dist * 10.0) / 10.0);
                    results.add(resp);
                    String clusterKey = getSpatialAggregationKey(c);
                    clusterMap.computeIfAbsent(clusterKey, k -> new ArrayList<>()).add(resp);
                }
            }
        }

        // K-Anonymity privacy enforcement for public nearby discovery:
        // Enforce k >= 5 separately for every actual spatial cluster.
        // Suppress coordinates for clusters containing fewer than 5 complaints.
        // Do not rely on the aggregate search-radius count.
        int threshold = this.publicMapMinimumGroupSize > 0 ? this.publicMapMinimumGroupSize : 5;
        for (Map.Entry<String, List<NearbyComplaintResponse>> entry : clusterMap.entrySet()) {
            boolean suppressForCluster = entry.getValue().size() < threshold;
            for (NearbyComplaintResponse item : entry.getValue()) {
                item.sanitizeForPublic(suppressForCluster);
            }
        }

        results.sort(Comparator.comparingDouble(NearbyComplaintResponse::getDistanceMeters));
        if (results.size() > safeLimit) {
            results = new ArrayList<>(results.subList(0, safeLimit));
        }

        return results;
    }

    private BigDecimal getStableGroupAnchorLat(String groupKey, List<Complaint> group) {
        if (groupKey.startsWith("GRID_")) {
            try {
                String[] parts = groupKey.split("_");
                int latIndex = Integer.parseInt(parts[1]);
                return BigDecimal.valueOf((latIndex + 0.5) * 0.05).setScale(3, RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }
        BigDecimal sumLat = BigDecimal.ZERO;
        int count = 0;
        for (Complaint c : group) {
            if (c.getLatitude() != null) {
                sumLat = sumLat.add(c.getLatitude());
                count++;
            }
        }
        return count > 0 ? sumLat.divide(BigDecimal.valueOf(count), 3, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal getStableGroupAnchorLng(String groupKey, List<Complaint> group) {
        if (groupKey.startsWith("GRID_")) {
            try {
                String[] parts = groupKey.split("_");
                int lngIndex = Integer.parseInt(parts[2]);
                return BigDecimal.valueOf((lngIndex + 0.5) * 0.05).setScale(3, RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }
        BigDecimal sumLng = BigDecimal.ZERO;
        int count = 0;
        for (Complaint c : group) {
            if (c.getLongitude() != null) {
                sumLng = sumLng.add(c.getLongitude());
                count++;
            }
        }
        return count > 0 ? sumLng.divide(BigDecimal.valueOf(count), 3, RoundingMode.HALF_UP) : null;
    }

    private String getSpatialAggregationKey(Complaint c) {
        if (c.getWard() != null && !c.getWard().trim().isEmpty()) {
            return "WARD_" + c.getWard().trim().toUpperCase(Locale.ROOT);
        }
        if (c.getLatitude() != null && c.getLongitude() != null) {
            int latIndex = (int) Math.floor(c.getLatitude().doubleValue() / 0.05);
            int lngIndex = (int) Math.floor(c.getLongitude().doubleValue() / 0.05);
            return String.format(Locale.ROOT, "GRID_%d_%d", latIndex, lngIndex);
        }
        return "UNKNOWN_ZONE";
    }

    public void setPublicMapMinimumGroupSize(int size) {
        this.publicMapMinimumGroupSize = size;
    }

    public int getPublicMapMinimumGroupSize() {
        return this.publicMapMinimumGroupSize;
    }

    public void setPdfReceiptService(PdfReceiptService pdfReceiptService) {
        this.pdfReceiptService = pdfReceiptService;
    }

    @Transactional(readOnly = true)
    public boolean verifyReceiptSignature(String idOrRef, String signature) {
        Complaint complaint = resolveComplaint(idOrRef);
        if (this.pdfReceiptService == null) {
            this.pdfReceiptService = new PdfReceiptService();
        }
        return this.pdfReceiptService.verifyReceiptSignature(complaint, signature);
    }

    @Transactional(readOnly = true)
    public byte[] generateComplaintReceiptPdf(String idOrRef, UserPrincipal principal) {
        Complaint complaint = resolveComplaint(idOrRef);
        enforceReadPermission(complaint, principal);
        if (this.pdfReceiptService == null) {
            this.pdfReceiptService = new PdfReceiptService();
        }
        return this.pdfReceiptService.generateReceiptPdf(complaint);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getComplaintById(String idOrRef, UserPrincipal principal) {
        Complaint complaint = resolveComplaint(idOrRef);
        enforceReadPermission(complaint, principal);
        return toResponse(complaint, principal);
    }

    @Transactional(readOnly = true)
    public List<ComplaintHistoryResponse> getComplaintHistory(String idOrRef, UserPrincipal principal) {
        Complaint complaint = resolveComplaint(idOrRef);
        enforceReadPermission(complaint, principal);

        List<ComplaintStatusHistory> history = historyRepository.findByComplaintIdOrderByCreatedAtAsc(complaint.getId());
        return history.stream()
                .map(ComplaintHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ComplaintResponse assignWorker(String idOrRef, AssignComplaintRequest req, UserPrincipal principal, String ipAddress) {
        boolean canAssign = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!canAssign) {
            throw new ForbiddenException("Only municipal officers and administrators can dispatch and assign repair orders.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        enforceOfficerWardAccess(complaint, principal);
        User officer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Officer account not found."));

        User worker = userRepository.findById(req.getWorkerId())
                .orElseThrow(() -> new ResourceNotFoundException("Target field worker not found."));

        if (!worker.hasRole(RoleName.WORKER)) {
            throw new BadRequestException("Assigned user must hold the WORKER persona.");
        }

        if (!"ACTIVE".equalsIgnoreCase(worker.getAccountStatus())) {
            throw new BadRequestException("Cannot assign complaint to field technician in '" + worker.getAccountStatus() + "' status. Account must be ACTIVE.");
        }

        complaint.setAssignedOfficer(officer);
        complaint.setAssignedWorker(worker);

        if (req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another transaction or user. Please refresh the record and try again.");
        }

        ComplaintStatus oldStatus = complaint.getStatus();
        if (oldStatus == ComplaintStatus.SUBMITTED || oldStatus == ComplaintStatus.UNDER_REVIEW || oldStatus == ComplaintStatus.REOPENED) {
            complaint.setStatus(ComplaintStatus.ASSIGNED);
            complaint.addStatusHistory(oldStatus, ComplaintStatus.ASSIGNED, officer,
                    req.getNotes() != null ? req.getNotes() : "Dispatched to technician " + worker.getFullName());
        }

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                officer.getId(),
                officer.getFullName(),
                "OFFICER",
                "ASSIGN_WORKER",
                "COMPLAINT",
                saved.getId().toString(),
                "Assigned worker " + worker.getFullName() + " to complaint #" + saved.getReferenceId(),
                ipAddress,
                "SUCCESS"
        );

        publishEvent(new ComplaintEvent(
                ComplaintEventType.WORKER_ASSIGNED,
                saved,
                officer,
                oldStatus,
                saved.getStatus(),
                req.getNotes() != null ? req.getNotes() : "Dispatched to technician " + worker.getFullName()
        ));

        return toResponse(saved, principal);
    }

    @Transactional
    public BulkAssignResponse bulkAssignWorkers(BulkAssignRequest req, UserPrincipal principal, String ipAddress) {
        boolean canAssign = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!canAssign) {
            throw new ForbiddenException("Only municipal officers and administrators can dispatch and assign repair orders.");
        }

        if (req == null || req.getComplaintIds() == null || req.getComplaintIds().isEmpty()) {
            throw new BadRequestException("At least one complaint ID is required for bulk assignment.");
        }
        if (req.getWorkerId() == null) {
            throw new BadRequestException("Target field worker ID is required.");
        }

        User officer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Officer account not found."));

        User worker = userRepository.findById(req.getWorkerId())
                .orElseThrow(() -> new ResourceNotFoundException("Target field worker not found."));

        if (!worker.hasRole(RoleName.WORKER)) {
            throw new BadRequestException("Assigned user must hold the WORKER persona.");
        }

        if (!"ACTIVE".equalsIgnoreCase(worker.getAccountStatus())) {
            throw new BadRequestException("Cannot assign complaint to field technician in '" + worker.getAccountStatus() + "' status. Account must be ACTIVE.");
        }
        if (!"ACTIVE".equalsIgnoreCase(worker.getAccountStatus())) {
            throw new BadRequestException("Selected field worker account is not active.");
        }

        List<String> uniqueIds = req.getComplaintIds().stream().distinct().toList();
        List<BulkAssignResponse.BulkItemResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (String idOrRef : uniqueIds) {
            try {
                Complaint complaint = resolveComplaint(idOrRef);

                enforceOfficerWardAccess(complaint, principal);

                ComplaintStatus currentStatus = complaint.getStatus();
                if (currentStatus.isTerminal()) {
                    results.add(new BulkAssignResponse.BulkItemResult(
                            complaint.getId().toString(),
                            complaint.getReferenceId(),
                            false,
                            "Cannot assign worker to terminal complaint (status: " + currentStatus + ")."
                    ));
                    failureCount++;
                    continue;
                }

                if (currentStatus == ComplaintStatus.RESOLUTION_SUBMITTED || currentStatus == ComplaintStatus.PENDING_CITIZEN_CONFIRMATION) {
                    results.add(new BulkAssignResponse.BulkItemResult(
                            complaint.getId().toString(),
                            complaint.getReferenceId(),
                            false,
                            "Complaint is pending resolution verification or citizen confirmation."
                    ));
                    failureCount++;
                    continue;
                }

                complaint.setAssignedOfficer(officer);
                complaint.setAssignedWorker(worker);

                ComplaintStatus previousStatus = complaint.getStatus();
                if (previousStatus == ComplaintStatus.SUBMITTED || previousStatus == ComplaintStatus.UNDER_REVIEW || previousStatus == ComplaintStatus.REOPENED) {
                    complaint.setStatus(ComplaintStatus.ASSIGNED);
                    complaint.addStatusHistory(previousStatus, ComplaintStatus.ASSIGNED, officer,
                            req.getNotes() != null && !req.getNotes().isBlank()
                                    ? req.getNotes().trim()
                                    : "Batch dispatched to technician " + worker.getFullName());
                }

                Complaint saved = complaintRepository.save(complaint);
                complaintRepository.flush();

                auditService.logAction(
                        officer.getId(),
                        officer.getFullName(),
                        "OFFICER",
                        "ASSIGN_WORKER",
                        "COMPLAINT",
                        saved.getId().toString(),
                        "Batch assigned worker " + worker.getFullName() + " to complaint #" + saved.getReferenceId(),
                        ipAddress,
                        "SUCCESS"
                );

                publishEvent(new ComplaintEvent(
                        ComplaintEventType.WORKER_ASSIGNED,
                        saved,
                        officer,
                        previousStatus,
                        saved.getStatus(),
                        req.getNotes() != null && !req.getNotes().isBlank() ? req.getNotes().trim() : "Batch dispatched to technician " + worker.getFullName()
                ));

                results.add(new BulkAssignResponse.BulkItemResult(
                        saved.getId().toString(),
                        saved.getReferenceId(),
                        true,
                        "Successfully dispatched to " + worker.getFullName()
                ));
                successCount++;
            } catch (ForbiddenException fe) {
                results.add(new BulkAssignResponse.BulkItemResult(
                        idOrRef,
                        null,
                        false,
                        fe.getMessage()
                ));
                failureCount++;
            } catch (ResourceNotFoundException rnfe) {
                results.add(new BulkAssignResponse.BulkItemResult(
                        idOrRef,
                        null,
                        false,
                        "Complaint not found: " + idOrRef
                ));
                failureCount++;
            } catch (Exception ex) {
                log.error("Error bulk-assigning complaint {}: {}", idOrRef, ex.getMessage(), ex);
                results.add(new BulkAssignResponse.BulkItemResult(
                        idOrRef,
                        null,
                        false,
                        "Failed to assign: " + ex.getMessage()
                ));
                failureCount++;
            }
        }

        return new BulkAssignResponse(uniqueIds.size(), successCount, failureCount, results);
    }

    @Transactional
    public ComplaintResponse transitionStatus(String idOrRef, UpdateStatusRequest req, UserPrincipal principal, String ipAddress) {
        Complaint complaint = resolveComplaint(idOrRef);
        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isWorker = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WORKER"));
        boolean isCitizen = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CITIZEN"));

        if (isOfficer) {
            enforceOfficerWardAccess(complaint, principal);
        }

        if (req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another transaction or user. Please refresh the record and try again.");
        }

        ComplaintStatus currentStatus = complaint.getStatus();
        ComplaintStatus newStatus = req.getStatus();

        if (currentStatus == newStatus) {
            throw new BadRequestException("Complaint is already in status: " + newStatus);
        }

        // State Machine Governance Rules (Blueprint Rule #9)
        switch (newStatus) {
            case PENDING_CITIZEN_CONFIRMATION:
                boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
                if (!isOfficer && !isAdmin && !isSupervisor) {
                    throw new ForbiddenException("Only municipal officers and administrators can verify resolution evidence.");
                }
                if (isSupervisor) {
                    enforceSupervisorDepartmentAccess(complaint, principal);
                }
                if (currentStatus != ComplaintStatus.RESOLUTION_SUBMITTED) {
                    throw new BadRequestException("Task cannot be transitioned to PENDING_CITIZEN_CONFIRMATION from status: " + currentStatus + ". Allowed previous status: RESOLUTION_SUBMITTED.");
                }

                // Verify evidence location check: if not WITHIN_RANGE, require non-empty acknowledgement (10-500 chars)
                List<ResolutionEvidence> evList = complaint.getEvidenceList();
                if (evList == null || evList.isEmpty()) {
                    evList = evidenceRepository.findByComplaintIdOrderByCreatedAtDesc(complaint.getId());
                }
                if (evList != null && !evList.isEmpty()) {
                    ResolutionEvidence latestEv = evList.stream()
                            .max(Comparator.comparing(ResolutionEvidence::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                            .orElse(evList.get(0));
                    if (latestEv.getLocationCheck() != null && latestEv.getLocationCheck() != LocationCheck.WITHIN_RANGE) {
                        String ack = req.getLocationAcknowledgement() != null && !req.getLocationAcknowledgement().trim().isEmpty()
                                ? req.getLocationAcknowledgement().trim()
                                : (req.getNotes() != null && !req.getNotes().trim().isEmpty() ? req.getNotes().trim() : null);

                        if (ack == null || ack.length() < 10 || ack.length() > 500) {
                            throw new BadRequestException("Resolution evidence location check is " + latestEv.getLocationCheck() +
                                    ". An approval acknowledgement between 10 and 500 characters is required.");
                        }
                    }
                }

                complaint.setPendingConfirmationAt(OffsetDateTime.now());
                break;

            case RESOLVED:
                throw new ForbiddenException("Complaints cannot be directly marked RESOLVED. Citizen confirmation or auto-confirmation is required.");

            case REWORK_REQUESTED:
                boolean isReworkSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
                if (!isOfficer && !isAdmin && !isReworkSupervisor) {
                    throw new ForbiddenException("Only municipal officers and administrators can request rework on resolution evidence.");
                }
                if (isReworkSupervisor) {
                    enforceSupervisorDepartmentAccess(complaint, principal);
                }
                if (currentStatus != ComplaintStatus.RESOLUTION_SUBMITTED) {
                    throw new BadRequestException("Task cannot be returned for rework from status: " + currentStatus + ". Allowed previous status: RESOLUTION_SUBMITTED.");
                }
                if (req.getNotes() == null || req.getNotes().trim().isEmpty()) {
                    throw new BadRequestException("A meaningful reason is required when requesting rework.");
                }
                break;

            case REJECTED:
                if (!isOfficer && !isAdmin) {
                    throw new ForbiddenException("Only municipal officers and administrators can reject intake complaints.");
                }
                if (currentStatus == ComplaintStatus.PENDING_CITIZEN_CONFIRMATION) {
                    throw new BadRequestException("Complaints awaiting citizen confirmation cannot be rejected by officers.");
                }
                break;

            case IN_PROGRESS:
                boolean isAssignedWorker = complaint.getAssignedWorker() != null &&
                        complaint.getAssignedWorker().getId().equals(principal.getId());
                if (!isAssignedWorker && !isOfficer && !isAdmin) {
                    throw new ForbiddenException("Only the assigned field worker or an officer can mark tasks IN_PROGRESS.");
                }
                if (currentStatus != ComplaintStatus.ASSIGNED && currentStatus != ComplaintStatus.REWORK_REQUESTED) {
                    throw new BadRequestException("Task cannot be transitioned to IN_PROGRESS from status: " + currentStatus + ". Allowed previous statuses: ASSIGNED, REWORK_REQUESTED.");
                }
                break;

            case RESOLUTION_SUBMITTED:
                boolean workerAuthorized = complaint.getAssignedWorker() != null &&
                        complaint.getAssignedWorker().getId().equals(principal.getId());
                if (!workerAuthorized && !isOfficer && !isAdmin) {
                    throw new ForbiddenException("Only the assigned technician can submit resolution evidence.");
                }
                if (currentStatus != ComplaintStatus.IN_PROGRESS) {
                    throw new BadRequestException("Task cannot be transitioned to RESOLUTION_SUBMITTED from status: " + currentStatus + ". Allowed previous status: IN_PROGRESS.");
                }
                // Save evidence metadata if provided
                if (req.getEvidencePhotoUrl() != null || req.getNotes() != null) {
                    ResolutionEvidence evidence = new ResolutionEvidence();
                    evidence.setComplaint(complaint);
                    evidence.setWorker(actor);
                    evidence.setEvidenceNotes(req.getNotes());
                    evidence.setPhotoUrl(req.getEvidencePhotoUrl());
                    evidence.setLatitude(req.getLatitude());
                    evidence.setLongitude(req.getLongitude());
                    evidenceRepository.save(evidence);
                }
                break;

            case REOPENED:
                boolean isOwner = complaint.getCitizen() != null && complaint.getCitizen().getId().equals(principal.getId());
                if (!isOwner) {
                    throw new ForbiddenException("Only the reporting citizen who filed this complaint can reopen it.");
                }
                if (currentStatus != ComplaintStatus.RESOLVED) {
                    throw new BadRequestException("Only RESOLVED complaints can be reopened for dispute.");
                }
                if (req.getNotes() == null || req.getNotes().trim().length() < 10 || req.getNotes().trim().length() > 500) {
                    throw new BadRequestException("A valid dispute reason between 10 and 500 characters is required to reopen a resolved complaint.");
                }
                OffsetDateTime resolvedAt = complaint.getConfirmedAt() != null ? complaint.getConfirmedAt() : complaint.getUpdatedAt();
                int disputeWindow = reopenWindowDays > 0 ? reopenWindowDays : 30;
                if (resolvedAt != null && OffsetDateTime.now().isAfter(resolvedAt.plusDays(disputeWindow))) {
                    throw new BadRequestException("The dispute window of " + disputeWindow + " days for reopening this complaint has expired.");
                }
                complaint.setReopenCount(complaint.getReopenCount() + 1);
                if (slaService != null && complaint.getPriority() != null) {
                    complaint.setSlaDueAt(slaService.computeSlaDueAt(complaint.getCategory(), complaint.getPriority(), OffsetDateTime.now()));
                }
                complaint.setEscalatedAt(OffsetDateTime.now());
                break;

            default:
                if (isCitizen && !isAdmin && !isOfficer) {
                    throw new ForbiddenException("Citizens cannot arbitrarily advance ticket workflow states.");
                }
                break;
        }

        String effectiveNotes = req.getNotes();
        if (req.getLocationAcknowledgement() != null && !req.getLocationAcknowledgement().trim().isEmpty()) {
            effectiveNotes = (effectiveNotes != null && !effectiveNotes.trim().isEmpty() ? effectiveNotes + " | " : "") +
                    "[Location Acknowledged: " + req.getLocationAcknowledgement().trim() + "]";
        }

        complaint.setStatus(newStatus);
        complaint.addStatusHistory(currentStatus, newStatus, actor, effectiveNotes);

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        String action = "TRANSITION_STATUS";
        if (newStatus == ComplaintStatus.PENDING_CITIZEN_CONFIRMATION) {
            action = "APPROVE_RESOLUTION";
        } else if (newStatus == ComplaintStatus.RESOLVED) {
            action = "CONFIRM_RESOLUTION";
        } else if (newStatus == ComplaintStatus.REWORK_REQUESTED) {
            action = "REQUEST_REWORK";
        } else if (newStatus == ComplaintStatus.REOPENED) {
            action = "REOPEN_COMPLAINT";
        }

        String auditDetails = "Transitioned status from " + currentStatus + " to " + newStatus + " on #" + saved.getReferenceId();
        if (req.getLocationAcknowledgement() != null && !req.getLocationAcknowledgement().trim().isEmpty()) {
            auditDetails += " [Location Acknowledged: " + req.getLocationAcknowledgement().trim() + "]";
        }

        auditService.logAction(
                actor.getId(),
                actor.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                action,
                "COMPLAINT",
                saved.getId().toString(),
                auditDetails,
                ipAddress,
                "SUCCESS"
        );

        ComplaintEventType eventType = null;
        if (newStatus == ComplaintStatus.IN_PROGRESS) {
            eventType = ComplaintEventType.WORK_STARTED;
        } else if (newStatus == ComplaintStatus.RESOLUTION_SUBMITTED) {
            eventType = ComplaintEventType.RESOLUTION_SUBMITTED;
        } else if (newStatus == ComplaintStatus.PENDING_CITIZEN_CONFIRMATION) {
            eventType = ComplaintEventType.FIX_PENDING_CONFIRMATION;
        } else if (newStatus == ComplaintStatus.RESOLVED) {
            eventType = ComplaintEventType.COMPLAINT_RESOLVED;
        } else if (newStatus == ComplaintStatus.REWORK_REQUESTED) {
            eventType = ComplaintEventType.REWORK_REQUESTED;
        } else if (newStatus == ComplaintStatus.REJECTED) {
            eventType = ComplaintEventType.COMPLAINT_REJECTED;
        } else if (newStatus == ComplaintStatus.REOPENED) {
            eventType = ComplaintEventType.COMPLAINT_REOPENED;
        }

        if (eventType != null) {
            publishEvent(new ComplaintEvent(
                    eventType,
                    saved,
                    actor,
                    currentStatus,
                    newStatus,
                    req.getNotes()
            ));
        }

        return toResponse(saved, principal);
    }

    @Transactional
    public ComplaintResponse addProgressNote(String idOrRef, String note, UserPrincipal principal, String ipAddress) {
        if (note == null || note.trim().isEmpty()) {
            throw new BadRequestException("Progress note cannot be empty.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        boolean isAssignedWorker = complaint.getAssignedWorker() != null &&
                complaint.getAssignedWorker().getId().equals(principal.getId());
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAssignedWorker && !isOfficer && !isAdmin) {
            throw new ForbiddenException("Only the assigned technician or an officer can add operational progress notes.");
        }

        ComplaintStatus status = complaint.getStatus();
        complaint.addStatusHistory(status, status, actor, note.trim());
        complaint.setUpdatedAt(java.time.OffsetDateTime.now());

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                actor.getId(),
                actor.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "PROGRESS_NOTE",
                "COMPLAINT",
                saved.getId().toString(),
                "Added operational note on #" + saved.getReferenceId() + ": " + note.trim(),
                ipAddress,
                "SUCCESS"
        );

        return toResponse(saved, principal);
    }

    @Transactional
    public ComplaintResponse submitEvidence(String idOrRef,
                                            String notes,
                                            List<MultipartFile> files,
                                            BigDecimal latitude,
                                            BigDecimal longitude,
                                            BigDecimal accuracy,
                                            Boolean deviceReported,
                                            UserPrincipal principal,
                                            String ipAddress) {
        return submitEvidence(idOrRef, notes, files, latitude, longitude, accuracy, null, deviceReported, principal, ipAddress);
    }

    @Transactional
    public ComplaintResponse submitEvidence(String idOrRef,
                                            String notes,
                                            List<MultipartFile> files,
                                            BigDecimal latitude,
                                            BigDecimal longitude,
                                            BigDecimal accuracy,
                                            String locationNote,
                                            Boolean deviceReported,
                                            UserPrincipal principal,
                                            String ipAddress) {
        Complaint complaint = resolveComplaint(idOrRef);

        // Ownership enforcement: only assigned field technician or officer/admin
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isAssigned = complaint.getAssignedWorker() != null &&
                complaint.getAssignedWorker().getId().equals(principal.getId());

        if (!isAssigned && !isOfficer && !isAdmin) {
            throw new ForbiddenException("Only the assigned technician can submit repair evidence for this work order.");
        }

        // Strict task lifecycle state enforcement
        if (complaint.getStatus() != ComplaintStatus.IN_PROGRESS) {
            throw new BadRequestException("Task cannot be transitioned to RESOLUTION_SUBMITTED from status: " + complaint.getStatus() + ". Allowed previous status: IN_PROGRESS.");
        }

        // Validate completion narrative notes
        if (notes == null || notes.trim().isEmpty()) {
            throw new BadRequestException("Completion narrative notes are required.");
        }

        // Validate photographs list
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("At least one authentic repair evidence photograph is required.");
        }
        if (files.size() > 5) {
            throw new BadRequestException("A maximum of 5 evidence photographs can be submitted.");
        }

        // Server-authoritative location verification calculation
        LocationCheck locationCheck;
        BigDecimal distanceMeters = null;
        BigDecimal accuracyMeters = null;
        String cleanLocationNote = locationNote != null ? locationNote.trim() : null;

        if (latitude == null && longitude == null) {
            locationCheck = LocationCheck.NOT_PROVIDED;
            if (cleanLocationNote == null || cleanLocationNote.length() < 10) {
                throw new BadRequestException("When GPS coordinates are not provided, an explanation location note of at least 10 characters is required.");
            }
        } else if (latitude == null || longitude == null) {
            throw new BadRequestException("Both latitude and longitude must be provided together.");
        } else {
            double dLat = latitude.doubleValue();
            double dLon = longitude.doubleValue();

            if (Double.isNaN(dLat) || Double.isInfinite(dLat) || dLat < -90.0 || dLat > 90.0) {
                throw new BadRequestException("Invalid latitude: must be between -90 and 90 degrees.");
            }
            if (Double.isNaN(dLon) || Double.isInfinite(dLon) || dLon < -180.0 || dLon > 180.0) {
                throw new BadRequestException("Invalid longitude: must be between -180 and 180 degrees.");
            }
            if (Double.compare(dLat, 0.0) == 0 && Double.compare(dLon, 0.0) == 0) {
                throw new BadRequestException("Coordinates (0, 0) are invalid.");
            }

            if (accuracy != null) {
                double dAcc = accuracy.doubleValue();
                if (Double.isNaN(dAcc) || Double.isInfinite(dAcc) || dAcc <= 0.0 || dAcc >= 100000.0) {
                    throw new BadRequestException("GPS accuracy must be a positive number below 100,000 meters.");
                }
                accuracyMeters = BigDecimal.valueOf(Math.round(dAcc * 10.0) / 10.0);
            }

            if (complaint.getLatitude() == null || complaint.getLongitude() == null) {
                locationCheck = LocationCheck.NOT_CHECKABLE;
            } else {
                double dist = com.civicfix.common.util.GeoUtils.haversineDistance(
                        complaint.getLatitude(), complaint.getLongitude(), latitude, longitude);
                distanceMeters = BigDecimal.valueOf(Math.round(dist * 10.0) / 10.0);

                if (accuracy != null && accuracy.doubleValue() > maxAccuracyMeters) {
                    locationCheck = LocationCheck.LOW_ACCURACY;
                } else if (dist <= maxDistanceMeters) {
                    locationCheck = LocationCheck.WITHIN_RANGE;
                } else {
                    locationCheck = LocationCheck.OUT_OF_RANGE;
                }

                if ((locationCheck == LocationCheck.LOW_ACCURACY || locationCheck == LocationCheck.OUT_OF_RANGE)
                        && (cleanLocationNote == null || cleanLocationNote.length() < 10)) {
                    throw new BadRequestException("Evidence location check is " + locationCheck + " (distance: " + distanceMeters + "m). An explanation location note of at least 10 characters is required.");
                }
            }
        }

        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        // Store evidence files safely with rollback defense
        List<String> storedUrls = new ArrayList<>();
        String subDir = "complaints/" + complaint.getId();
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    throw new BadRequestException("Uploaded evidence photo cannot be empty.");
                }
                String storedUrl = storageService.storeFile(file, subDir);
                storedUrls.add(storedUrl);
                String fname = storedUrl.substring(storedUrl.lastIndexOf('/') + 1);
                if (fileTransactionHelper != null) {
                    fileTransactionHelper.registerRollbackCleanup(fname, subDir);
                }
            }
        } catch (Exception e) {
            // Roll back any physical files created during this partial batch
            for (String url : storedUrls) {
                String fname = url.substring(url.lastIndexOf('/') + 1);
                storageService.deleteFile(fname, subDir);
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Failed to store evidence photos. Upload aborted without modifying complaint status.", e);
        }

        try {
            // Persist ResolutionEvidence metadata for each uploaded photograph
            for (String photoUrl : storedUrls) {
                ResolutionEvidence evidence = new ResolutionEvidence();
                evidence.setComplaint(complaint);
                evidence.setWorker(actor);
                evidence.setPhotoUrl(photoUrl);
                evidence.setLatitude(latitude);
                evidence.setLongitude(longitude);
                evidence.setDistanceMeters(distanceMeters);
                evidence.setAccuracyMeters(accuracyMeters);
                evidence.setLocationCheck(locationCheck);
                evidence.setLocationNote(cleanLocationNote);
                evidence.setEvidenceNotes(notes.trim());
                if (evidence.getCreatedAt() == null) {
                    evidence.setCreatedAt(OffsetDateTime.now());
                }
                evidenceRepository.save(evidence);
                complaint.getEvidenceList().add(evidence);
            }

            // Transition status strictly to RESOLUTION_SUBMITTED (never straight to RESOLVED)
            ComplaintStatus previousStatus = complaint.getStatus();
            complaint.setStatus(ComplaintStatus.RESOLUTION_SUBMITTED);
            Complaint saved = complaintRepository.save(complaint);
            complaintRepository.flush();

            // Record in status history
            String historyNotes = notes.trim();
            if (cleanLocationNote != null && !cleanLocationNote.isEmpty()) {
                historyNotes += " [Location: " + locationCheck + " - " + cleanLocationNote + "]";
            }

            ComplaintStatusHistory history = new ComplaintStatusHistory();
            history.setComplaint(saved);
            history.setPreviousStatus(previousStatus.name());
            history.setNewStatus(ComplaintStatus.RESOLUTION_SUBMITTED.name());
            history.setChangedByUser(actor);
            history.setChangedByName(actor.getFullName());
            history.setNotes(historyNotes);
            historyRepository.save(history);

            // Record in platform audit ledger
            String auditDetail = String.format("Technician %s submitted %d repair evidence photo(s). Location check: %s, Distance: %s, Accuracy: %s.",
                    actor.getFullName(), storedUrls.size(), locationCheck,
                    distanceMeters != null ? distanceMeters + "m" : "N/A",
                    accuracyMeters != null ? accuracyMeters + "m" : "N/A");

            auditService.logAction(
                    actor.getId(),
                    actor.getFullName(),
                    principal.getAuthorities().iterator().next().getAuthority(),
                    "SUBMIT_EVIDENCE",
                    "COMPLAINT",
                    saved.getReferenceId(),
                    auditDetail,
                    ipAddress,
                    "SUCCESS"
            );

            return toResponse(saved, principal);
        } catch (Exception e) {
            for (String url : storedUrls) {
                String fname = url.substring(url.lastIndexOf('/') + 1);
                storageService.deleteFile(fname, "complaints/" + complaint.getId());
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ResolutionEvidenceResponse> getComplaintEvidence(String idOrRef, UserPrincipal principal) {
        Complaint complaint = resolveComplaint(idOrRef);
        enforceReadPermission(complaint, principal);
        return evidenceRepository.findByComplaintIdOrderByCreatedAtDesc(complaint.getId())
                .stream()
                .map(ResolutionEvidenceResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource loadEvidenceFile(String filename, UserPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required to access repair evidence files.");
        }
        if (filename == null || filename.trim().isEmpty()) {
            throw new BadRequestException("Filename cannot be empty.");
        }
        String cleanName = org.springframework.util.StringUtils.cleanPath(filename.trim());
        if (cleanName.contains("..") || cleanName.contains("/") || cleanName.contains("\\")) {
            throw new BadRequestException("Invalid filename path sequence.");
        }

        // 1. Every served file MUST be linked to an existing ResolutionEvidence or ComplaintPhoto record in the database
        // Matches exact trailing filename segment without loose substring ambiguity or database schema migration
        Complaint complaint = null;
        Optional<ResolutionEvidence> evidenceOpt = evidenceRepository.findFirstByPhotoUrlEndingWith(cleanName);
        if (evidenceOpt.isEmpty()) {
            evidenceOpt = evidenceRepository.findFirstByPhotoUrlContaining(cleanName);
        }
        if (evidenceOpt.isPresent()) {
            complaint = evidenceOpt.get().getComplaint();
        } else {
            Optional<ComplaintPhoto> photoOpt = complaintPhotoRepository.findFirstByPhotoUrlEndingWith(cleanName);
            if (photoOpt.isEmpty()) {
                photoOpt = complaintPhotoRepository.findFirstByPhotoUrlContaining(cleanName);
            }
            if (photoOpt.isPresent()) {
                complaint = photoOpt.get().getComplaint();
            }
        }

        if (complaint == null) {
            throw new ResourceNotFoundException("Evidence photo not found: " + cleanName);
        }

        // 2. Enforce authorization rules (Admin/Officer can access all; Worker can access if assigned; Citizen can access if complaint owner)
        enforceReadPermission(complaint, principal);

        // 3. Resolve file from the complaint-specific directory: "complaints/" + complaintId
        String subDirectory = "complaints/" + complaint.getId();
        try {
            return storageService.loadFileAsResource(cleanName, subDirectory);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Evidence photo not found or not readable: " + cleanName);
        }
    }

    @Transactional
    public ComplaintResponse adjudicateTriage(String idOrRef, AdjudicateTriageRequest req, UserPrincipal principal, String ipAddress) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOfficer && !isAdmin) {
            throw new ForbiddenException("Only municipal officers and administrators can adjudicate AI triage recommendations.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        enforceOfficerWardAccess(complaint, principal);
        User officer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Officer account not found."));

        if (req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another transaction or user. Please refresh the record and try again.");
        }

        String action = req.getAction().trim().toUpperCase();
        String auditDetails;
        String historyNote;

        switch (action) {
            case "ACCEPT":
                if ((complaint.getAiDetectedCategory() == null || complaint.getAiDetectedCategory().isBlank()) &&
                        complaint.getAiRecommendedPriority() == null) {
                    throw new BadRequestException("No pending AI recommendation available to accept on complaint #" + complaint.getReferenceId());
                }
                if (complaint.getAiDetectedCategory() != null && !complaint.getAiDetectedCategory().isBlank()) {
                    complaint.setCategory(complaint.getAiDetectedCategory());
                }
                if (complaint.getAiRecommendedPriority() != null) {
                    complaint.setPriority(complaint.getAiRecommendedPriority());
                }
                complaint.setAiAdjudicationStatus("ACCEPTED");
                complaint.setFinalPrioritySource("AI_ADJUDICATED");
                historyNote = "AI Triage Accepted: Adopted category [" + complaint.getCategory() +
                        "] and priority [" + complaint.getPriority() + "]. " +
                        (req.getNotes() != null && !req.getNotes().isBlank() ? "Officer notes: " + req.getNotes().trim() : "");
                auditDetails = String.format("Officer %s accepted AI triage on complaint #%s (Category: %s, Priority: %s). %s",
                        officer.getFullName(), complaint.getReferenceId(), complaint.getCategory(), complaint.getPriority(),
                        req.getNotes() != null ? "Notes: " + req.getNotes().trim() : "");
                break;

            case "OVERRIDE":
                if ((req.getCategory() == null || req.getCategory().isBlank()) && req.getPriority() == null) {
                    throw new BadRequestException("Override action requires either an updated category or priority.");
                }
                if (req.getCategory() != null && !req.getCategory().isBlank()) {
                    complaint.setCategory(req.getCategory().trim());
                }
                if (req.getPriority() != null) {
                    complaint.setPriority(req.getPriority());
                }
                complaint.setAiAdjudicationStatus("OVERRIDDEN");
                complaint.setFinalPrioritySource("OFFICER_OVERRIDE");
                historyNote = "AI Triage Overridden: Updated category to [" + complaint.getCategory() +
                        "] and priority to [" + complaint.getPriority() + "]. " +
                        (req.getNotes() != null && !req.getNotes().isBlank() ? "Reason: " + req.getNotes().trim() : "");
                auditDetails = String.format("Officer %s overrode AI triage on complaint #%s (New Category: %s, New Priority: %s). %s",
                        officer.getFullName(), complaint.getReferenceId(), complaint.getCategory(), complaint.getPriority(),
                        req.getNotes() != null ? "Reason: " + req.getNotes().trim() : "");
                break;

            case "DISMISS":
                complaint.setAiAdjudicationStatus("DISMISSED");
                historyNote = "AI Triage Dismissed: Preserved existing category [" + complaint.getCategory() +
                        "] and priority [" + complaint.getPriority() + "]. " +
                        (req.getNotes() != null && !req.getNotes().isBlank() ? "Reason: " + req.getNotes().trim() : "");
                auditDetails = String.format("Officer %s dismissed AI triage advice on complaint #%s. %s",
                        officer.getFullName(), complaint.getReferenceId(),
                        req.getNotes() != null ? "Reason: " + req.getNotes().trim() : "");
                break;

            default:
                throw new BadRequestException("Invalid adjudication action: " + action + ". Allowed values: ACCEPT, OVERRIDE, DISMISS.");
        }

        if (slaService != null && complaint.getPriority() != null && ("ACCEPT".equals(action) || "OVERRIDE".equals(action))) {
            complaint.setSlaDueAt(slaService.computeSlaDueAt(complaint.getCategory(), complaint.getPriority(), complaint.getCreatedAt()));
        }

        complaint.setUpdatedAt(OffsetDateTime.now());
        complaint.addStatusHistory(complaint.getStatus(), complaint.getStatus(), officer, historyNote.trim());
        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                officer.getId(),
                officer.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "ADJUDICATE_AI_TRIAGE",
                "COMPLAINT",
                saved.getId().toString(),
                auditDetails,
                ipAddress,
                "SUCCESS"
        );

        return toResponse(saved, principal);
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidate> getDuplicateCandidates(String idOrRef, UserPrincipal principal) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOfficer && !isAdmin) {
            throw new ForbiddenException("Only municipal officers and administrators can inspect duplicate candidate recommendations.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        if (aiTriageService == null) {
            return Collections.emptyList();
        }

        return aiTriageService.findDuplicateCandidates(complaint);
    }

    @Transactional(readOnly = true)
    public ComplaintSummaryResponse getComplaintSummary(String idOrRef, UserPrincipal principal) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        if (!isOfficer && !isAdmin && !isSupervisor) {
            throw new ForbiddenException("Only municipal officers, supervisors and administrators can inspect AI complaint summaries.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        enforceOfficerWardAccess(complaint, principal);

        if (aiTriageService != null) {
            return aiTriageService.generateSummary(complaint, principal);
        }
        return new ComplaintSummaryService().generateSummary(complaint, principal);
    }

    @Transactional(readOnly = true)
    public PotholeAnalysisResponse getPotholeAnalysis(String idOrRef, UserPrincipal principal) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        if (!isOfficer && !isAdmin && !isSupervisor) {
            throw new ForbiddenException("Only municipal officers, supervisors and administrators can inspect pothole analysis.");
        }
        Complaint complaint = resolveComplaint(idOrRef);
        enforceOfficerWardAccess(complaint, principal);
        if (potholeDetectionService == null) {
            throw new BadRequestException("Pothole detection service is not configured.");
        }
        return potholeDetectionService.getExistingAnalysis(complaint.getId())
                .orElseGet(() -> potholeDetectionService.analyzePotholes(complaint.getId(), false));
    }

    @Transactional
    public PotholeAnalysisResponse runPotholeAnalysis(String idOrRef, UserPrincipal principal) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        if (!isOfficer && !isAdmin && !isSupervisor) {
            throw new ForbiddenException("Only municipal officers, supervisors and administrators can execute pothole analysis.");
        }
        Complaint complaint = resolveComplaint(idOrRef);
        enforceOfficerWardAccess(complaint, principal);
        if (potholeDetectionService == null) {
            throw new BadRequestException("Pothole detection service is not configured.");
        }
        return potholeDetectionService.analyzePotholes(complaint.getId(), true);
    }

    @Transactional
    public PotholeAnalysisResponse calibratePotholeAnalysis(String idOrRef, CalibrationRequest request, UserPrincipal principal) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        if (!isOfficer && !isAdmin && !isSupervisor) {
            throw new ForbiddenException("Only municipal officers, supervisors and administrators can calibrate pothole measurements.");
        }
        Complaint complaint = resolveComplaint(idOrRef);
        enforceOfficerWardAccess(complaint, principal);
        if (potholeDetectionService == null) {
            throw new BadRequestException("Pothole detection service is not configured.");
        }
        return potholeDetectionService.calibrateAnalysis(complaint.getId(), request);
    }

    @Transactional
    public ComplaintResponse linkDuplicate(String idOrRef, LinkDuplicateRequest req, UserPrincipal principal, String ipAddress) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOfficer && !isAdmin) {
            throw new ForbiddenException("Only municipal officers and administrators can link duplicate complaints.");
        }

        if (req.getParentComplaintId() == null || req.getParentComplaintId().isBlank()) {
            throw new BadRequestException("Parent complaint identifier cannot be empty.");
        }

        Complaint child = resolveComplaint(idOrRef);
        Complaint parent = resolveComplaint(req.getParentComplaintId());
        enforceOfficerWardAccess(child, principal);
        enforceOfficerWardAccess(parent, principal);
        User officer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Officer account not found."));

        // 1. Validate both IDs and prevent self-linking
        if (child.getId().equals(parent.getId())) {
            throw new BadRequestException("A complaint cannot be linked as a duplicate of itself.");
        }

        // 2. Prevent invalid duplicate chains:
        // Reject linking if parent is already a duplicate of another complaint
        if (parent.getDuplicateOf() != null) {
            throw new BadRequestException(String.format(
                    "Invalid duplicate chain: Complaint #%s is already marked as a duplicate of #%s. Chained duplicates are prohibited; please link directly to root complaint #%s.",
                    parent.getReferenceId(), parent.getDuplicateOf().getReferenceId(), parent.getDuplicateOf().getReferenceId()));
        }

        // Reject linking if child complaint already has duplicate complaints pointing to it
        long existingChildren = complaintRepository.countByDuplicateOfId(child.getId());
        if (existingChildren > 0) {
            throw new BadRequestException(String.format(
                    "Invalid duplicate chain: Complaint #%s already has %d duplicate complaints linked to it. Resolve child links before re-linking this record.",
                    child.getReferenceId(), existingChildren));
        }

        // 3. Prevent circular relationships: trace parent hierarchy
        Complaint cursor = parent;
        Set<UUID> ancestors = new HashSet<>();
        ancestors.add(child.getId());
        while (cursor != null) {
            if (ancestors.contains(cursor.getId())) {
                throw new BadRequestException(String.format(
                        "Circular duplicate relationship detected between complaint #%s and #%s.",
                        child.getReferenceId(), parent.getReferenceId()));
            }
            cursor = cursor.getDuplicateOf();
        }

        // 4. Optimistic locking
        if (req.getVersion() != null && !req.getVersion().equals(child.getVersion())) {
            throw new ConflictException("Complaint has been modified by another transaction or user. Please refresh the record and try again.");
        }

        // 5. Non-destructive linking:
        // Keep citizen, status, description, address, coordinates, and photos completely intact.
        child.setDuplicateOf(parent);
        child.setUpdatedAt(OffsetDateTime.now());

        String reasonText = req.getReason() != null && !req.getReason().isBlank() ? req.getReason().trim() : "Linked by municipal officer review";
        String historyNote = String.format("Linked as duplicate of master complaint #%s. Reason: %s", parent.getReferenceId(), reasonText);
        child.addStatusHistory(child.getStatus(), child.getStatus(), officer, historyNote);

        Complaint saved = complaintRepository.save(child);
        complaintRepository.flush();

        // 6. Audit log
        auditService.logAction(
                officer.getId(),
                officer.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "LINK_DUPLICATE",
                "COMPLAINT",
                saved.getId().toString(),
                String.format("Officer %s linked complaint #%s as duplicate of #%s. Reason: %s",
                        officer.getFullName(), saved.getReferenceId(), parent.getReferenceId(), reasonText),
                ipAddress,
                "SUCCESS"
        );

        publishEvent(new ComplaintEvent(
                ComplaintEventType.DUPLICATE_LINKED,
                saved,
                officer,
                saved.getStatus(),
                saved.getStatus(),
                reasonText
        ));

        return toResponse(saved, principal, true);
    }

    private Complaint resolveComplaint(String idOrRef) {
        if (idOrRef == null || idOrRef.trim().isEmpty()) {
            throw new BadRequestException("Complaint ID or Reference cannot be empty.");
        }
        String trimmed = idOrRef.trim();

        // Check if valid UUID
        try {
            UUID uuid = UUID.fromString(trimmed);
            return complaintRepository.findById(uuid)
                    .or(() -> complaintRepository.findByReferenceIdIgnoreCase(trimmed))
                    .orElseThrow(() -> new ResourceNotFoundException("Complaint #" + idOrRef + " not found."));
        } catch (IllegalArgumentException e) {
            // Not a UUID: lookup by human-readable reference ID
            return complaintRepository.findByReferenceIdIgnoreCase(trimmed)
                    .orElseThrow(() -> new ResourceNotFoundException("Complaint #" + idOrRef + " not found."));
        }
    }

    @Transactional
    public ComplaintResponse escalateComplaint(String idOrRef, EscalateComplaintRequest req, UserPrincipal principal, String ipAddress) {
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isOfficer && !isSupervisor && !isAdmin) {
            throw new ForbiddenException("Only municipal officers, supervisors, and administrators can escalate complaints.");
        }

        if (req == null || req.getReason() == null || req.getReason().trim().length() < 10 || req.getReason().trim().length() > 500) {
            throw new BadRequestException("A valid escalation reason between 10 and 500 characters is required.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        if (complaint.getStatus().isTerminal()) {
            throw new BadRequestException("Cannot escalate a terminal complaint (status: " + complaint.getStatus() + ").");
        }
        if (isOfficer) {
            enforceOfficerWardAccess(complaint, principal);
        }
        if (isSupervisor) {
            enforceSupervisorDepartmentAccess(complaint, principal);
        }

        if (complaint.getEscalatedAt() != null) {
            throw new ConflictException("Complaint #" + complaint.getReferenceId() + " has already been escalated.");
        }

        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        boolean success = slaEscalationService.escalateComplaint(complaint.getId(), req.getReason().trim(), actor, ipAddress);
        if (!success) {
            throw new ConflictException("Complaint #" + complaint.getReferenceId() + " has already been escalated.");
        }

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElse(complaint);
        return toResponse(reloaded, principal);
    }

    @Transactional
    public ComplaintResponse confirmResolution(String idOrRef, ConfirmResolutionRequest req, UserPrincipal principal, String ipAddress) {
        Complaint complaint = resolveComplaint(idOrRef);
        enforceOwningCitizen(complaint, principal);

        if (complaint.getStatus() != ComplaintStatus.PENDING_CITIZEN_CONFIRMATION) {
            throw new ConflictException("Complaint #" + complaint.getReferenceId() + " is currently in status " +
                    complaint.getStatus() + ". Only complaints in PENDING_CITIZEN_CONFIRMATION can be confirmed.");
        }

        if (req != null && req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another process. Please refresh and try again.");
        }

        User citizen = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Citizen account not found."));

        OffsetDateTime now = OffsetDateTime.now();
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setConfirmedAt(now);
        complaint.setConfirmationSource("CITIZEN");
        complaint.setUpdatedAt(now);

        String notes = (req != null && req.getNotes() != null && !req.getNotes().isBlank())
                ? req.getNotes().trim()
                : "Citizen confirmed defect has been resolved.";
        complaint.addStatusHistory(ComplaintStatus.PENDING_CITIZEN_CONFIRMATION, ComplaintStatus.RESOLVED, citizen, notes);

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                citizen.getId(),
                citizen.getFullName(),
                "CITIZEN",
                "CONFIRM_RESOLUTION",
                "COMPLAINT",
                saved.getId().toString(),
                "Citizen confirmed resolution on complaint #" + saved.getReferenceId(),
                ipAddress,
                "SUCCESS"
        );

        publishEvent(new ComplaintEvent(
                ComplaintEventType.RESOLUTION_CONFIRMED,
                saved,
                citizen,
                ComplaintStatus.PENDING_CITIZEN_CONFIRMATION,
                ComplaintStatus.RESOLVED,
                notes
        ));

        return toResponse(saved, principal);
    }

    @Transactional
    public ComplaintResponse rejectResolution(String idOrRef, RejectResolutionRequest req, MultipartFile photo, UserPrincipal principal, String ipAddress) {
        Complaint complaint = resolveComplaint(idOrRef);
        enforceOwningCitizen(complaint, principal);

        if (complaint.getStatus() != ComplaintStatus.PENDING_CITIZEN_CONFIRMATION) {
            throw new ConflictException("Complaint #" + complaint.getReferenceId() + " is currently in status " +
                    complaint.getStatus() + ". Only complaints in PENDING_CITIZEN_CONFIRMATION can be rejected.");
        }

        if (req == null || req.getReason() == null || req.getReason().trim().length() < 10 || req.getReason().trim().length() > 500) {
            throw new BadRequestException("A valid rejection reason between 10 and 500 characters is required.");
        }

        if (req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another process. Please refresh and try again.");
        }

        User citizen = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Citizen account not found."));

        OffsetDateTime now = OffsetDateTime.now();
        complaint.setStatus(ComplaintStatus.REOPENED);
        complaint.setReopenCount(complaint.getReopenCount() + 1);

        // Reset SLA due date from policy starting now
        if (slaService != null && complaint.getPriority() != null) {
            complaint.setSlaDueAt(slaService.computeSlaDueAt(complaint.getCategory(), complaint.getPriority(), now));
        }

        // Escalate complaint with now timestamp, overriding any earlier escalation
        complaint.setEscalatedAt(now);
        complaint.setUpdatedAt(now);

        // Process optional citizen rejection photograph using existing validated StorageService
        if (photo != null && !photo.isEmpty()) {
            try {
                String subDir = "complaints/" + complaint.getId();
                String storedUrl = storageService.storeFile(photo, subDir);
                String fname = storedUrl.substring(storedUrl.lastIndexOf('/') + 1);
                if (fileTransactionHelper != null) {
                    fileTransactionHelper.registerRollbackCleanup(fname, subDir);
                }
                ComplaintPhoto cp = new ComplaintPhoto(complaint, storedUrl);
                complaintPhotoRepository.save(cp);
                complaint.getPhotos().add(cp);
            } catch (Exception ex) {
                log.warn("Failed to store citizen rejection photograph: {}", ex.getMessage());
            }
        }

        String note = "Citizen reported issue is not fixed: " + req.getReason().trim();
        complaint.addStatusHistory(ComplaintStatus.PENDING_CITIZEN_CONFIRMATION, ComplaintStatus.REOPENED, citizen, note);

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                citizen.getId(),
                citizen.getFullName(),
                "CITIZEN",
                "REJECT_RESOLUTION",
                "COMPLAINT",
                saved.getId().toString(),
                "Citizen rejected the fix on #" + saved.getReferenceId() + ". Reason: " + req.getReason().trim(),
                ipAddress,
                "SUCCESS"
        );

        publishEvent(new ComplaintEvent(
                ComplaintEventType.RESOLUTION_REJECTED,
                saved,
                citizen,
                ComplaintStatus.PENDING_CITIZEN_CONFIRMATION,
                ComplaintStatus.REOPENED,
                req.getReason().trim()
        ));

        return toResponse(saved, principal);
    }

    private void enforceOwningCitizen(Complaint complaint, UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ForbiddenException("Authentication required.");
        }
        boolean isOwner = complaint.getCitizen() != null && complaint.getCitizen().getId().equals(principal.getId());
        if (!isOwner) {
            throw new ForbiddenException("Only the reporting citizen who filed this complaint can confirm or reject resolution.");
        }
    }

    private void enforceReadPermission(Complaint complaint, UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        if (isAdmin) {
            return;
        }

        if (isOfficer) {
            enforceOfficerWardAccess(complaint, principal);
            return;
        }

        if (isSupervisor) {
            enforceSupervisorDepartmentAccess(complaint, principal);
            return;
        }

        boolean isWorker = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WORKER"));
        if (isWorker) {
            boolean isAssigned = complaint.getAssignedWorker() != null &&
                    complaint.getAssignedWorker().getId().equals(principal.getId());
            if (!isAssigned) {
                throw new ForbiddenException("Workers may only access repair orders assigned to their crew.");
            }
            return;
        }

        // Citizen: Must be the ticket creator
        boolean isOwner = complaint.getCitizen().getId().equals(principal.getId());
        if (!isOwner) {
            throw new ForbiddenException("Private record: In accordance with municipal privacy policies, you may only inspect complaints filed under your account.");
        }
    }

    public void enforceSupervisorDepartmentAccess(Complaint complaint, UserPrincipal principal) {
        if (complaint == null) return;
        if (principal == null) {
            throw new ForbiddenException("Authentication required.");
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin || principal.hasActiveCrossDepartmentAccess()) {
            return;
        }

        String dept = principal.getDepartment();
        if (dept == null || dept.isBlank()) {
            throw new ForbiddenException("Supervisor has no assigned department. Access denied.");
        }

        if (!SupervisorService.isCategoryAllowedForDepartment(complaint.getCategory(), dept, principal)) {
            throw new ForbiddenException("Supervisors cannot access or manage complaints outside their authorized department (" + dept + ").");
        }
    }

    public void enforceOfficerWardAccess(Complaint complaint, UserPrincipal principal) {
        if (complaint == null || principal == null) {
            return;
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        if (isAdmin || !isOfficer) {
            return;
        }

        String officerWard = principal.getWard();
        if (officerWard == null || officerWard.isBlank()) {
            throw new ForbiddenException("Officer has no assigned ward. Access denied.");
        }

        String complaintWard = complaint.getWard();
        if (complaintWard == null || complaintWard.isBlank() || !officerWard.trim().equalsIgnoreCase(complaintWard.trim())) {
            if (!principal.hasActiveCrossWardAccess()) {
                throw new ForbiddenException("Officers cannot access or manage complaints outside their assigned ward (" + officerWard.trim() + ") without authorized cross-ward permission.");
            }
            if (auditService != null) {
                auditService.logAction(
                        principal.getId(),
                        principal.getFullName(),
                        "OFFICER",
                        "OFFICER_CROSS_WARD_ACCESS",
                        "COMPLAINT",
                        complaint.getId().toString(),
                        "Authorized cross-ward access invoked for complaint #" + complaint.getReferenceId() + " in ward: " + (complaintWard != null ? complaintWard.trim() : "UNASSIGNED"),
                        null,
                        "SUCCESS"
                );
            }
        }
    }

    @Transactional
    public void softDeleteComplaint(String idOrRef, String reason, UserPrincipal principal, String ipAddress) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new ForbiddenException("Only municipal administrators have permission to soft-delete complaint records.");
        }

        if (reason == null || reason.trim().length() < 5) {
            throw new BadRequestException("A valid deletion audit reason of at least 5 characters is required.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        User adminUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found."));

        complaint.setDeletedAt(OffsetDateTime.now());
        complaint.setDeletedBy(adminUser);
        complaint.setDeletionReason(reason.trim());
        complaintRepository.save(complaint);

        auditService.logAction(
                principal.getId(),
                principal.getFullName(),
                "ADMIN",
                "COMPLAINT_SOFT_DELETE",
                "COMPLAINT",
                complaint.getId().toString(),
                "Complaint record soft-deleted. Reason: " + reason.trim(),
                ipAddress,
                "SUCCESS"
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdminDeletedComplaintResponse> getSoftDeletedComplaints(Pageable pageable, UserPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required.");
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new ForbiddenException("Only municipal administrators have permission to inspect soft-deleted complaint records.");
        }

        Page<Complaint> page = complaintRepository.findSoftDeletedComplaints(pageable);
        List<AdminDeletedComplaintResponse> content = page.getContent().stream()
                .map(AdminDeletedComplaintResponse::fromEntity)
                .toList();

        return new PagedResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional
    public ComplaintResponse restoreComplaint(String idOrRef, String reason, UserPrincipal principal, String ipAddress) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required.");
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new ForbiddenException("Only municipal administrators have permission to restore soft-deleted complaints.");
        }

        if (reason == null || reason.trim().length() < 5) {
            throw new BadRequestException("A valid restoration reason of at least 5 characters is required.");
        }

        UUID uuid = null;
        try {
            uuid = UUID.fromString(idOrRef);
        } catch (IllegalArgumentException ignored) {}

        Complaint deletedComplaint = complaintRepository.findSoftDeletedByIdOrRefNative(uuid, idOrRef)
                .orElseThrow(() -> new ResourceNotFoundException("Soft-deleted complaint not found with identifier: " + idOrRef));

        String priorReason = deletedComplaint.getDeletionReason();
        complaintRepository.restoreSoftDeletedComplaintNative(deletedComplaint.getId());

        auditService.logAction(
                principal.getId(),
                principal.getFullName(),
                "ADMIN",
                "RESTORE_COMPLAINT",
                "COMPLAINT",
                deletedComplaint.getId().toString(),
                "Restored soft-deleted complaint #" + deletedComplaint.getReferenceId() +
                        ". Prior deletion reason: " + priorReason + ". Restoration reason: " + reason.trim(),
                ipAddress,
                "SUCCESS"
        );

        Complaint restored = complaintRepository.findById(deletedComplaint.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Restored complaint could not be loaded: " + idOrRef));

        return toResponse(restored, principal);
    }
}
