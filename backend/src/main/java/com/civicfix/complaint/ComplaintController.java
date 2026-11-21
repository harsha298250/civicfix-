package com.civicfix.complaint;

import com.civicfix.ai.AiTriageService;
import com.civicfix.ai.dto.DuplicateCandidate;
import com.civicfix.ai.dto.SuggestCategoryRequest;
import com.civicfix.ai.dto.SuggestCategoryResponse;
import com.civicfix.common.ApiResponse;
import com.civicfix.common.PagedResponse;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.complaint.dto.*;
import com.civicfix.officer.OfficerAnalyticsService;
import com.civicfix.officer.dto.PublicTrustAnalyticsResponse;
import com.civicfix.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final ComplaintService complaintService;
    private final OfficerAnalyticsService officerAnalyticsService;
    private final AiTriageService aiTriageService;

    @org.springframework.beans.factory.annotation.Autowired
    public ComplaintController(ComplaintService complaintService,
                               OfficerAnalyticsService officerAnalyticsService,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) AiTriageService aiTriageService) {
        this.complaintService = complaintService;
        this.officerAnalyticsService = officerAnalyticsService;
        this.aiTriageService = aiTriageService;
    }

    public ComplaintController(ComplaintService complaintService, OfficerAnalyticsService officerAnalyticsService) {
        this(complaintService, officerAnalyticsService, null);
    }

    @PostMapping("/suggest-category")
    public ResponseEntity<ApiResponse<SuggestCategoryResponse>> suggestCategory(
            @RequestBody SuggestCategoryRequest request) {
        if (aiTriageService == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    new SuggestCategoryResponse("ROADS", 50.0, "AI triage service not configured.", List.of())
            ));
        }
        String text = request != null ? request.getText() : "";
        String lang = request != null ? request.getLanguage() : "en";
        SuggestCategoryResponse response = aiTriageService.suggestCategory(text, lang);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/public/trust")
    public ResponseEntity<ApiResponse<PublicTrustAnalyticsResponse>> getPublicTrustScores(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        PublicTrustAnalyticsResponse res = officerAnalyticsService.getPublicWardTrustScores(from, to);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> createComplaint(
            @Valid @RequestBody CreateComplaintRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.createComplaint(request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Complaint filed successfully.", response));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> createComplaintMultipart(
            @ModelAttribute @Valid CreateComplaintRequest request,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.createComplaint(request, files, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Complaint filed successfully.", response));
    }

    @GetMapping("/public/map")
    public ResponseEntity<ApiResponse<List<PublicMapComplaintResponse>>> getPublicMapComplaints(
            @RequestParam(required = false) BigDecimal minLat,
            @RequestParam(required = false) BigDecimal maxLat,
            @RequestParam(required = false) BigDecimal minLng,
            @RequestParam(required = false) BigDecimal maxLng,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "250") int limit) {
        List<PublicMapComplaintResponse> markers = complaintService.getPublicMapComplaints(
                minLat, maxLat, minLng, maxLng, status, category, limit);
        return ResponseEntity.ok(ApiResponse.success(markers));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagedResponse<ComplaintResponse>>> getComplaints(
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) BigDecimal minLat,
            @RequestParam(required = false) BigDecimal maxLat,
            @RequestParam(required = false) BigDecimal minLng,
            @RequestParam(required = false) BigDecimal maxLng,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        PagedResponse<ComplaintResponse> response = complaintService.getComplaints(
                principal, status, category, ward, minLat, maxLat, minLng, maxLng, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> getComplaintById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ComplaintResponse response = complaintService.getComplaintById(id, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/{idOrRef}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadComplaintReceipt(
            @PathVariable String idOrRef,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (idOrRef == null || !SAFE_IDENTIFIER_PATTERN.matcher(idOrRef.trim()).matches()) {
            throw new BadRequestException("Invalid complaint identifier format. Alphanumeric, hyphen, and underscore characters only (max 64).");
        }
        String cleanIdOrRef = idOrRef.trim();
        byte[] pdfBytes = complaintService.generateComplaintReceiptPdf(cleanIdOrRef, principal);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename("complaint-receipt-" + cleanIdOrRef + ".pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .headers(headers -> headers.setContentDisposition(contentDisposition))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/{idOrRef}/receipt/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyComplaintReceipt(
            @PathVariable String idOrRef,
            @RequestParam String signature) {
        if (idOrRef == null || !SAFE_IDENTIFIER_PATTERN.matcher(idOrRef.trim()).matches()) {
            throw new BadRequestException("Invalid complaint identifier format.");
        }
        boolean isValid = complaintService.verifyReceiptSignature(idOrRef.trim(), signature);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "identifier", idOrRef.trim(),
                "verified", isValid
        )));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ComplaintHistoryResponse>>> getComplaintHistory(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ComplaintHistoryResponse> history = complaintService.getComplaintHistory(id, principal);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> assignWorker(
            @PathVariable String id,
            @Valid @RequestBody AssignComplaintRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.assignWorker(id, request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Work order dispatched to technician.", response));
    }

    @PostMapping("/bulk-assign")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BulkAssignResponse>> bulkAssignWorkers(
            @Valid @RequestBody BulkAssignRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        BulkAssignResponse response = complaintService.bulkAssignWorkers(request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(
                "Batch assignment completed: " + response.getSuccessCount() + " succeeded, " + response.getFailureCount() + " failed.",
                response
        ));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.transitionStatus(id, request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Complaint status updated successfully.", response));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> addNote(
            @PathVariable String id,
            @Valid @RequestBody AddNoteRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.addProgressNote(id, request.getNote(), principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Operational progress note recorded successfully.", response));
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> submitEvidence(
            @PathVariable String id,
            @RequestParam("notes") String notes,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "latitude", required = false) BigDecimal latitude,
            @RequestParam(value = "longitude", required = false) BigDecimal longitude,
            @RequestParam(value = "accuracy", required = false) BigDecimal accuracy,
            @RequestParam(value = "locationNote", required = false) String locationNote,
            @RequestParam(value = "deviceReported", required = false, defaultValue = "false") Boolean deviceReported,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.submitEvidence(
                id, notes, files, latitude, longitude, accuracy, locationNote, deviceReported, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Repair evidence submitted. Work order queued for municipal officer verification.", response));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ResolutionEvidenceResponse>>> getComplaintEvidence(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ResolutionEvidenceResponse> evidence = complaintService.getComplaintEvidence(id, principal);
        return ResponseEntity.ok(ApiResponse.success(evidence));
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> uploadComplaintPhotos(
            @PathVariable String id,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.addComplaintPhotos(id, files, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Photographs uploaded successfully.", response));
    }

    @GetMapping({"/evidence/files/{filename:.+}", "/photos/files/{filename:.+}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getEvidenceFile(
            @PathVariable String filename,
            @AuthenticationPrincipal UserPrincipal principal) {
        Resource fileResource = complaintService.loadEvidenceFile(filename, principal);

        String contentType = "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (lower.endsWith(".png")) {
            contentType = "image/png";
        } else if (lower.endsWith(".webp")) {
            contentType = "image/webp";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(fileResource);
    }

    @PostMapping("/{id}/ai/adjudicate")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> adjudicateAiTriage(
            @PathVariable String id,
            @Valid @RequestBody AdjudicateTriageRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.adjudicateTriage(id, request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("AI triage recommendation adjudicated successfully.", response));
    }

    @GetMapping("/{id}/duplicates")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DuplicateCandidate>>> getDuplicateCandidates(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<DuplicateCandidate> candidates = complaintService.getDuplicateCandidates(id, principal);
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    @PostMapping("/{id}/duplicates/link")
    @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> linkDuplicate(
            @PathVariable String id,
            @Valid @RequestBody LinkDuplicateRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.linkDuplicate(id, request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Duplicate complaint linked successfully.", response));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('OFFICER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> escalateComplaint(
            @PathVariable String id,
            @Valid @RequestBody EscalateComplaintRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.escalateComplaint(id, request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Complaint escalated successfully.", response));
    }

    @PostMapping("/{id}/confirm-resolution")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> confirmResolution(
            @PathVariable String id,
            @RequestBody(required = false) ConfirmResolutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.confirmResolution(id, request, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Resolution confirmed successfully.", response));
    }

    @PostMapping(value = "/{id}/reject-resolution", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> rejectResolutionJson(
            @PathVariable String id,
            @Valid @RequestBody RejectResolutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        ComplaintResponse response = complaintService.rejectResolution(id, request, null, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Resolution rejected. Complaint has been reopened and escalated.", response));
    }

    @PostMapping(value = "/{id}/reject-resolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ComplaintResponse>> rejectResolutionMultipart(
            @PathVariable String id,
            @RequestParam("reason") String reason,
            @RequestParam(value = "version", required = false) Long version,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        RejectResolutionRequest request = new RejectResolutionRequest(reason, version);
        ComplaintResponse response = complaintService.rejectResolution(id, request, photo, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Resolution rejected. Complaint has been reopened and escalated.", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> softDeleteComplaint(
            @PathVariable String id,
            @RequestParam(value = "reason", defaultValue = "Administrative archival") String reason,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        complaintService.softDeleteComplaint(id, reason, principal, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Complaint successfully soft-deleted and removed from active municipal processing.", null));
    }
}
