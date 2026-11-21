package com.civicfix.ai.vision;

import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPhoto;
import com.civicfix.complaint.ComplaintPhotoRepository;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.security.ratelimit.RateLimiterService;
import com.civicfix.storage.StorageService;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VisionTriageAsyncService {

    private static final Logger log = LoggerFactory.getLogger(VisionTriageAsyncService.class);
    private static final long MAX_PHOTO_BYTES = 4 * 1024 * 1024; // 4 MB

    private final ComplaintRepository complaintRepository;
    private final ComplaintPhotoRepository complaintPhotoRepository;
    private final StorageService storageService;
    private final RateLimiterService rateLimiterService;
    private final VisionTriageProperties properties;
    private final GeminiVisionTriageProvider geminiProvider;
    private final XaiVisionTriageProvider xaiProvider;
    private final TaskExecutor taskExecutor;

    public VisionTriageAsyncService(
            ComplaintRepository complaintRepository,
            ComplaintPhotoRepository complaintPhotoRepository,
            StorageService storageService,
            RateLimiterService rateLimiterService,
            VisionTriageProperties properties,
            GeminiVisionTriageProvider geminiProvider,
            XaiVisionTriageProvider xaiProvider,
            @Qualifier("visionTaskExecutor") TaskExecutor taskExecutor) {
        this.complaintRepository = complaintRepository;
        this.complaintPhotoRepository = complaintPhotoRepository;
        this.storageService = storageService;
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.geminiProvider = geminiProvider;
        this.xaiProvider = xaiProvider;
        this.taskExecutor = taskExecutor;
    }

    public void submitVisionTriage(UUID complaintId, UUID citizenId) {
        taskExecutor.execute(new RejectableVisionTask(complaintId, citizenId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processVisionTriage(UUID complaintId, UUID citizenId) {
        if (!properties.isEnabled()) {
            return;
        }

        // 1. Claim the row atomically
        int claimed = complaintRepository.claimForVisionTriage(complaintId, OffsetDateTime.now());
        if (claimed == 0) {
            log.debug("Row for complaint ID [{}] not in NOT_RUN state; claim skipped.", complaintId);
            return;
        }

        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            return;
        }

        if (!complaint.isVisionConsent()) {
            log.debug("Complaint ID [{}] does not have vision consent; skipping vision triage.", complaintId);
            return;
        }

        // 2. Verify and filter valid photos (up to 3 non-WebP candidates)
        List<ComplaintPhoto> photos = complaintPhotoRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId);
        if (photos == null || photos.isEmpty()) {
            complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "No photo attached", OffsetDateTime.now());
            log.info("Vision triage status: SKIPPED (reason: No photo attached) for complaint ID [{}]", complaintId);
            return;
        }

        List<ComplaintPhoto> candidatePhotos = new ArrayList<>();
        boolean encounteredWebp = false;
        boolean encounteredOversize = false;
        boolean encounteredUnsupported = false;

        for (ComplaintPhoto p : photos) {
            String pName = p.getFileName() != null ? p.getFileName().toLowerCase() : "";
            String pUrl = p.getPhotoUrl() != null ? p.getPhotoUrl().toLowerCase() : "";
            String pType = p.getContentType() != null ? p.getContentType().toLowerCase() : "";

            boolean isWebp = pName.endsWith(".webp") || pUrl.endsWith(".webp") || "image/webp".equals(pType);
            if (isWebp) {
                encounteredWebp = true;
                continue;
            }

            boolean isJpegOrPng = pName.endsWith(".jpg") || pName.endsWith(".jpeg") || pName.endsWith(".png") ||
                                  pUrl.endsWith(".jpg") || pUrl.endsWith(".jpeg") || pUrl.endsWith(".png") ||
                                  "image/jpeg".equals(pType) || "image/jpg".equals(pType) || "image/png".equals(pType);
            if (!isJpegOrPng) {
                encounteredUnsupported = true;
                continue;
            }

            if (p.getFileSize() != null && p.getFileSize() > MAX_PHOTO_BYTES) {
                encounteredOversize = true;
                continue;
            }

            candidatePhotos.add(p);
            if (candidatePhotos.size() == 3) {
                break;
            }
        }

        if (candidatePhotos.isEmpty()) {
            if (encounteredWebp) {
                complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "WebP image format skipped to prevent unstripped EXIF metadata leakage", OffsetDateTime.now());
                log.info("Vision triage status: SKIPPED (reason: WebP image format) for complaint ID [{}]", complaintId);
            } else if (encounteredOversize) {
                complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "Photo exceeds maximum 4 MB size limit for vision triage", OffsetDateTime.now());
                log.info("Vision triage status: SKIPPED (reason: Photo exceeds 4 MB) for complaint ID [{}]", complaintId);
            } else if (encounteredUnsupported) {
                complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "Unsupported image format for vision triage", OffsetDateTime.now());
                log.info("Vision triage status: SKIPPED (reason: Unsupported image format) for complaint ID [{}]", complaintId);
            } else {
                complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "No eligible photos found for vision triage", OffsetDateTime.now());
                log.info("Vision triage status: SKIPPED (reason: No eligible photos) for complaint ID [{}]", complaintId);
            }
            return;
        }

        // 3. Global Daily Cap from DB
        OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        long dailyCount = complaintRepository.countDailyVisionInvocations(startOfDay);
        if (dailyCount >= properties.getDailyCap()) {
            complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "Daily vision triage cap of " + properties.getDailyCap() + " reached", OffsetDateTime.now());
            log.info("Vision triage status: SKIPPED (reason: Daily cap reached) for complaint ID [{}]", complaintId);
            return;
        }

        // 4. Per-User Rate Limit
        if (citizenId != null) {
            ConsumptionProbe probe = rateLimiterService.tryConsume(
                    "ai_vision:user:" + citizenId,
                    properties.getUserMinuteCapacity(),
                    properties.getUserMinuteDuration()
            );
            if (!probe.isConsumed()) {
                complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "User per-minute vision rate limit exceeded", OffsetDateTime.now());
                log.info("Vision triage status: SKIPPED (reason: User rate limit exceeded) for complaint ID [{}]", complaintId);
                return;
            }
        }

        // 5. Read image bytes and analyze up to 3 candidate photos
        VisionTriageProvider provider = selectProvider();
        List<VisionTriageOutput> outputs = new ArrayList<>();
        String lastErrorReason = "Could not process photo";

        for (ComplaintPhoto photo : candidatePhotos) {
            String fileName = photo.getFileName() != null ? photo.getFileName().toLowerCase() : "";
            String photoUrl = photo.getPhotoUrl() != null ? photo.getPhotoUrl().toLowerCase() : "";
            String contentType = photo.getContentType() != null ? photo.getContentType().toLowerCase() : "";

            byte[] imageBytes;
            try {
                String storedFilename = extractFilename(photo.getPhotoUrl());
                Resource resource = storageService.loadFileAsResource(storedFilename, "complaints/" + complaintId);
                if (!resource.exists() || !resource.isReadable()) {
                    log.warn("Photo resource missing or unreadable for complaint ID [{}]: {}", complaintId, storedFilename);
                    lastErrorReason = "Could not read stored photo from disk";
                    continue;
                }
                imageBytes = resource.getInputStream().readAllBytes();
                if (imageBytes.length > MAX_PHOTO_BYTES) {
                    log.info("Photo bytes exceed 4 MB for photo [{}] of complaint [{}]", storedFilename, complaintId);
                    lastErrorReason = "Photo bytes exceed 4 MB";
                    continue;
                }
            } catch (Exception e) {
                lastErrorReason = "Error loading photo file: " + e.getMessage();
                log.warn("Error reading photo file for complaint ID [{}]: {}", complaintId, e.getMessage());
                continue;
            }

            String mimeType = (fileName.endsWith(".png") || photoUrl.endsWith(".png") || "image/png".equals(contentType))
                    ? "image/png"
                    : "image/jpeg";

            try {
                VisionTriageOutput out = provider.analyze(imageBytes, mimeType);
                if (out != null) {
                    outputs.add(out);
                }
            } catch (Exception e) {
                String err = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
                if (err.length() > 300) {
                    err = err.substring(0, 300);
                }
                lastErrorReason = err;
                log.warn("Vision triage photo analysis failed: {} for complaint ID [{}]", err, complaintId);
            }
        }

        if (outputs.isEmpty()) {
            if (lastErrorReason.length() > 300) {
                lastErrorReason = lastErrorReason.substring(0, 300);
            }
            complaintRepository.updateVisionStatus(complaintId, "FAILED", lastErrorReason, OffsetDateTime.now());
            log.warn("Vision triage status: FAILED (reason: {}) for complaint ID [{}]", lastErrorReason, complaintId);
            return;
        }

        // 6. Aggregate multi-photo consensus
        String visionCat;
        int maxSeverity;
        BigDecimal avgConfidence;
        String compositeReason;

        if (outputs.size() == 1) {
            VisionTriageOutput single = outputs.get(0);
            visionCat = single.getCategory();
            maxSeverity = single.getSeverity();
            avgConfidence = single.getConfidence();
            compositeReason = single.getReason();
        } else {
            // Find plurality category among non-UNKNOWN
            java.util.Map<String, Long> categoryCounts = new java.util.HashMap<>();
            java.util.Map<String, BigDecimal> categoryMaxConf = new java.util.HashMap<>();
            for (VisionTriageOutput out : outputs) {
                String cat = out.getCategory();
                if (cat != null && !cat.isBlank() && !cat.equalsIgnoreCase("UNKNOWN")) {
                    categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0L) + 1L);
                    BigDecimal currentMax = categoryMaxConf.get(cat);
                    if (currentMax == null || (out.getConfidence() != null && out.getConfidence().compareTo(currentMax) > 0)) {
                        categoryMaxConf.put(cat, out.getConfidence() != null ? out.getConfidence() : BigDecimal.ZERO);
                    }
                }
            }

            if (categoryCounts.isEmpty()) {
                visionCat = "UNKNOWN";
            } else {
                visionCat = categoryCounts.entrySet().stream()
                        .max((e1, e2) -> {
                            int countCmp = e1.getValue().compareTo(e2.getValue());
                            if (countCmp != 0) return countCmp;
                            BigDecimal c1 = categoryMaxConf.getOrDefault(e1.getKey(), BigDecimal.ZERO);
                            BigDecimal c2 = categoryMaxConf.getOrDefault(e2.getKey(), BigDecimal.ZERO);
                            return c1.compareTo(c2);
                        })
                        .map(java.util.Map.Entry::getKey)
                        .orElse("UNKNOWN");
            }

            // Severity: maximum severity observed across all photos (safety elevation)
            maxSeverity = outputs.stream().mapToInt(VisionTriageOutput::getSeverity).max().orElse(1);

            // Confidence: average confidence across valid outputs
            BigDecimal sumConf = BigDecimal.ZERO;
            int confCount = 0;
            for (VisionTriageOutput out : outputs) {
                if (out.getConfidence() != null) {
                    sumConf = sumConf.add(out.getConfidence());
                    confCount++;
                }
            }
            avgConfidence = confCount > 0
                    ? sumConf.divide(BigDecimal.valueOf(confCount), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(0.50);

            // Reason: multi-photo consensus summary
            StringBuilder sb = new StringBuilder();
            sb.append("Multi-photo consensus (").append(outputs.size()).append(" photos): ");
            for (int i = 0; i < outputs.size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append("[P").append(i + 1).append(": ").append(outputs.get(i).getReason()).append("]");
            }
            compositeReason = sb.toString();
            if (compositeReason.length() > 500) {
                compositeReason = compositeReason.substring(0, 497) + "...";
            }
        }

        // 7. Calculate disagreement
        boolean disagreement = false;
        if (visionCat != null && !visionCat.equalsIgnoreCase("UNKNOWN") && complaint.getAiDetectedCategory() != null) {
            if (!visionCat.equalsIgnoreCase(complaint.getAiDetectedCategory())) {
                disagreement = true;
            }
        }

        // 8. Calculate priority elevation (severity 5 elevates by at most one level, never lowers)
        ComplaintPriority elevatedPriority = complaint.getAiRecommendedPriority();
        String prioritySource = "RULE_ENGINE";
        if (maxSeverity == 5 && complaint.getAiRecommendedPriority() != null) {
            switch (complaint.getAiRecommendedPriority()) {
                case LOW -> elevatedPriority = ComplaintPriority.MEDIUM;
                case MEDIUM -> elevatedPriority = ComplaintPriority.HIGH;
                case HIGH -> elevatedPriority = ComplaintPriority.CRITICAL;
                case CRITICAL -> elevatedPriority = ComplaintPriority.CRITICAL;
            }
            if (elevatedPriority != complaint.getAiRecommendedPriority()) {
                prioritySource = "VISION_SEVERITY_5";
            }
        }

        // 9. Targeted update
        complaintRepository.updateVisionSuccess(
                complaintId,
                visionCat,
                maxSeverity,
                avgConfidence,
                compositeReason,
                provider.getModelName(),
                "OK",
                com.civicfix.ai.prompt.PromptRegistry.DEFAULT_VERSION,
                OffsetDateTime.now(),
                disagreement,
                prioritySource,
                elevatedPriority
        );

        log.info("Vision triage status: OK for complaint ID [{}] (provider: {}, model: {}, photosAnalyzed: {}, disagreement: {})",
                complaintId, provider.getProviderName(), provider.getModelName(), outputs.size(), disagreement);
    }

    private VisionTriageProvider selectProvider() {
        String p = properties.getProvider() != null ? properties.getProvider().trim().toLowerCase() : "gemini";
        if ("xai".equals(p)) {
            return xaiProvider;
        }
        return geminiProvider;
    }

    private String extractFilename(String photoUrl) {
        if (photoUrl == null) return "";
        int idx = photoUrl.lastIndexOf('/');
        if (idx >= 0 && idx < photoUrl.length() - 1) {
            return photoUrl.substring(idx + 1);
        }
        return photoUrl;
    }

    public class RejectableVisionTask implements Runnable {
        private final UUID complaintId;
        private final UUID citizenId;

        public RejectableVisionTask(UUID complaintId, UUID citizenId) {
            this.complaintId = complaintId;
            this.citizenId = citizenId;
        }

        @Override
        public void run() {
            try {
                processVisionTriage(complaintId, citizenId);
            } catch (Exception e) {
                log.error("Unhandled error during async vision triage for complaint ID [{}]", complaintId, e);
                try {
                    complaintRepository.updateVisionStatus(complaintId, "FAILED", "Unexpected async triage failure", OffsetDateTime.now());
                } catch (Exception ignored) {}
            }
        }

        public void onRejected() {
            try {
                complaintRepository.updateVisionStatus(complaintId, "SKIPPED", "Vision triage executor queue full", OffsetDateTime.now());
                log.warn("Marked complaint ID [{}] as SKIPPED due to executor queue rejection.", complaintId);
            } catch (Exception e) {
                log.error("Could not record rejection status for complaint ID [{}]", complaintId, e);
            }
        }
    }
}

