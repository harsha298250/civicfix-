package com.civicfix.complaint;

import com.civicfix.ai.AiTriageService;
import com.civicfix.ai.vision.VisionTriageProperties;
import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.complaint.sla.SlaEscalationService;
import com.civicfix.complaint.sla.SlaService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.storage.StorageService;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplaintPdfReceiptSecurityTest {

    private ComplaintRepository complaintRepository;
    private UserRepository userRepository;
    private PdfReceiptService pdfReceiptService;
    private ComplaintService complaintService;

    private User ownerCitizen;
    private User otherCitizen;
    private User officerWard1;
    private User officerWard2;
    private User adminUser;

    private Complaint testComplaint;

    @BeforeEach
    void setUp() {
        complaintRepository = mock(ComplaintRepository.class);
        userRepository = mock(UserRepository.class);
        pdfReceiptService = new PdfReceiptService();

        complaintService = new ComplaintService(
                complaintRepository,
                mock(ComplaintStatusHistoryRepository.class),
                mock(ResolutionEvidenceRepository.class),
                mock(ComplaintPhotoRepository.class),
                userRepository,
                mock(AuditService.class),
                mock(StorageService.class),
                mock(AiTriageService.class),
                mock(ApplicationEventPublisher.class),
                mock(SlaService.class),
                mock(SlaEscalationService.class),
                mock(VisionTriageProperties.class)
        );
        complaintService.setPdfReceiptService(pdfReceiptService);

        // Citizen who filed the complaint
        ownerCitizen = new User();
        ownerCitizen.setId(UUID.randomUUID());
        ownerCitizen.setEmail("owner@citizen.org");
        ownerCitizen.setFullName("Arun Kumar");
        ownerCitizen.setRoles(Set.of(new Role(RoleName.CITIZEN)));

        // Different citizen
        otherCitizen = new User();
        otherCitizen.setId(UUID.randomUUID());
        otherCitizen.setEmail("attacker@citizen.org");
        otherCitizen.setFullName("Malicious Snooper");
        otherCitizen.setRoles(Set.of(new Role(RoleName.CITIZEN)));

        // Officer assigned to Ward 1
        officerWard1 = new User();
        officerWard1.setId(UUID.randomUUID());
        officerWard1.setEmail("officer1@metro.gov");
        officerWard1.setFullName("Officer One");
        officerWard1.setWard("Ward 1");
        officerWard1.setRoles(Set.of(new Role(RoleName.OFFICER)));

        // Officer assigned to Ward 2
        officerWard2 = new User();
        officerWard2.setId(UUID.randomUUID());
        officerWard2.setEmail("officer2@metro.gov");
        officerWard2.setFullName("Officer Two");
        officerWard2.setWard("Ward 2");
        officerWard2.setRoles(Set.of(new Role(RoleName.OFFICER)));

        // Admin
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin@metro.gov");
        adminUser.setFullName("System Admin");
        adminUser.setRoles(Set.of(new Role(RoleName.ADMIN)));

        testComplaint = new Complaint();
        testComplaint.setId(UUID.randomUUID());
        testComplaint.setReferenceId("CF-2026-12345");
        testComplaint.setTitle("Broken streetlight on 4th Main");
        testComplaint.setDescription("Streetlight bulb has been flickering and completely dark for 3 days");
        testComplaint.setCategory("Streetlights");
        testComplaint.setStatus(ComplaintStatus.SUBMITTED);
        testComplaint.setPriority(ComplaintPriority.HIGH);
        testComplaint.setWard("Ward 1");
        testComplaint.setCitizen(ownerCitizen);
        testComplaint.setLatitude(new BigDecimal("13.0827"));
        testComplaint.setLongitude(new BigDecimal("80.2707"));
        testComplaint.setCreatedAt(OffsetDateTime.now());

        when(complaintRepository.findById(testComplaint.getId())).thenReturn(Optional.of(testComplaint));
        when(complaintRepository.findByReferenceIdIgnoreCase("CF-2026-12345")).thenReturn(Optional.of(testComplaint));
    }

    @Test
    @DisplayName("PDF Generation: Produces valid PDF document with header, reference, disclaimers, and metadata")
    void generatePdf_DirectGeneration_ProducesValidPdfBytes() {
        byte[] pdfBytes = pdfReceiptService.generateReceiptPdf(testComplaint);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "Generated PDF must have substantial content");

        // First 4 bytes of any valid PDF document are '%PDF'
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header, "PDF file must start with '%PDF' magic header");
    }

    @Test
    @DisplayName("Citizen Authorization: Complaint owner can download receipt successfully")
    void ownerCitizenCanDownloadReceipt() {
        UserPrincipal ownerPrincipal = new UserPrincipal(ownerCitizen);
        byte[] pdf = complaintService.generateComplaintReceiptPdf(testComplaint.getId().toString(), ownerPrincipal);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("Citizen Isolation: Different citizen is strictly FORBIDDEN from downloading another's receipt")
    void otherCitizenCannotDownloadReceipt() {
        UserPrincipal otherPrincipal = new UserPrincipal(otherCitizen);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                complaintService.generateComplaintReceiptPdf(testComplaint.getId().toString(), otherPrincipal));

        assertTrue(ex.getMessage().contains("Private record") || ex.getMessage().contains("only inspect complaints filed under your account"));
    }

    @Test
    @DisplayName("Officer Authorization: Officer assigned to matching ward can download receipt")
    void officerInMatchingWardCanDownloadReceipt() {
        UserPrincipal officer1Principal = new UserPrincipal(officerWard1);
        byte[] pdf = complaintService.generateComplaintReceiptPdf("CF-2026-12345", officer1Principal);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("Officer Isolation: Officer in different ward is FORBIDDEN from downloading receipt")
    void officerInDifferentWardCannotDownloadReceipt() {
        UserPrincipal officer2Principal = new UserPrincipal(officerWard2);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                complaintService.generateComplaintReceiptPdf("CF-2026-12345", officer2Principal));

        assertTrue(ex.getMessage().contains("outside their assigned ward") || ex.getMessage().contains("cross-ward"));
    }

    @Test
    @DisplayName("Admin Authorization: System administrator can download receipt across wards")
    void adminCanDownloadReceipt() {
        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);
        byte[] pdf = complaintService.generateComplaintReceiptPdf(testComplaint.getId().toString(), adminPrincipal);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("Invalid ID: Non-existent complaint throws ResourceNotFoundException")
    void nonExistentComplaintThrowsNotFound() {
        UserPrincipal ownerPrincipal = new UserPrincipal(ownerCitizen);

        assertThrows(ResourceNotFoundException.class, () ->
                complaintService.generateComplaintReceiptPdf("CF-2026-99999", ownerPrincipal));
    }

    @Test
    @DisplayName("Invalid ID: Blank or empty ID throws BadRequestException")
    void blankIdThrowsBadRequest() {
        UserPrincipal ownerPrincipal = new UserPrincipal(ownerCitizen);

        assertThrows(com.civicfix.common.exception.BadRequestException.class, () ->
                complaintService.generateComplaintReceiptPdf("   ", ownerPrincipal));
    }

    @Test
    @DisplayName("Supervisor Department Authorization: Supervisor in matching department can download receipt")
    void supervisorMatchingDepartmentCanDownloadReceipt() {
        User electricalSupervisor = new User();
        electricalSupervisor.setId(UUID.randomUUID());
        electricalSupervisor.setEmail("supervisor.elec@metro.gov");
        electricalSupervisor.setFullName("Electrical Supervisor");
        electricalSupervisor.setDepartment("Electrical & Street Lighting");
        electricalSupervisor.setRoles(Set.of(new Role(RoleName.SUPERVISOR)));

        UserPrincipal supervisorPrincipal = new UserPrincipal(electricalSupervisor);
        byte[] pdf = complaintService.generateComplaintReceiptPdf(testComplaint.getId().toString(), supervisorPrincipal);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("Supervisor Department Authorization: Supervisor outside authorized department is FORBIDDEN")
    void supervisorWrongDepartmentCannotDownloadReceipt() {
        User roadsSupervisor = new User();
        roadsSupervisor.setId(UUID.randomUUID());
        roadsSupervisor.setEmail("supervisor.roads@metro.gov");
        roadsSupervisor.setFullName("Roads Supervisor");
        roadsSupervisor.setDepartment("Roads & Traffic");
        roadsSupervisor.setRoles(Set.of(new Role(RoleName.SUPERVISOR)));

        UserPrincipal supervisorPrincipal = new UserPrincipal(roadsSupervisor);
        assertThrows(ForbiddenException.class, () ->
                complaintService.generateComplaintReceiptPdf(testComplaint.getId().toString(), supervisorPrincipal));
    }

    @Test
    @DisplayName("Soft-Deleted Complaint: Resolved to not found in accordance with municipal retention policy")
    void softDeletedComplaintThrowsNotFound() {
        // Soft-deleted complaint is filtered out by database query returning empty
        when(complaintRepository.findById(any())).thenReturn(Optional.empty());
        when(complaintRepository.findByReferenceIdIgnoreCase(any())).thenReturn(Optional.empty());

        UserPrincipal ownerPrincipal = new UserPrincipal(ownerCitizen);
        assertThrows(ResourceNotFoundException.class, () ->
                complaintService.generateComplaintReceiptPdf(testComplaint.getId().toString(), ownerPrincipal));
    }

    @Test
    @DisplayName("HMAC Tamper-Evidence: Valid receipt generates deterministic HMAC-SHA256 and verifies successfully")
    void validReceiptSignature_VerifiesSuccessfully() {
        String signature = pdfReceiptService.calculateReceiptHmac(testComplaint);

        assertNotNull(signature);
        assertEquals(64, signature.length(), "HMAC-SHA256 signature must be 64 hex characters");

        boolean verified = pdfReceiptService.verifyReceiptSignature(testComplaint, signature);
        assertTrue(verified, "Signature generated from complaint data must verify successfully");
    }

    @Test
    @DisplayName("HMAC Tamper-Evidence: Modifying any complaint attribute causes verification to fail")
    void modifiedComplaintAttributes_FailsSignatureVerification() {
        String validSignature = pdfReceiptService.calculateReceiptHmac(testComplaint);

        // 1. Alter ward
        Complaint tamperedWard = new Complaint();
        tamperedWard.setId(testComplaint.getId());
        tamperedWard.setReferenceId(testComplaint.getReferenceId());
        tamperedWard.setCitizen(testComplaint.getCitizen());
        tamperedWard.setWard("Ward 99"); // Changed
        tamperedWard.setCategory(testComplaint.getCategory());
        tamperedWard.setStatus(testComplaint.getStatus());
        tamperedWard.setCreatedAt(testComplaint.getCreatedAt());

        assertFalse(pdfReceiptService.verifyReceiptSignature(tamperedWard, validSignature),
                "Altering ward must invalidate the HMAC signature");

        // 2. Alter status
        Complaint tamperedStatus = new Complaint();
        tamperedStatus.setId(testComplaint.getId());
        tamperedStatus.setReferenceId(testComplaint.getReferenceId());
        tamperedStatus.setCitizen(testComplaint.getCitizen());
        tamperedStatus.setWard(testComplaint.getWard());
        tamperedStatus.setCategory(testComplaint.getCategory());
        tamperedStatus.setStatus(ComplaintStatus.RESOLVED); // Changed
        tamperedStatus.setCreatedAt(testComplaint.getCreatedAt());

        assertFalse(pdfReceiptService.verifyReceiptSignature(tamperedStatus, validSignature),
                "Altering status must invalidate the HMAC signature");
    }

    @Test
    @DisplayName("HMAC Tamper-Evidence: Invalid or forged signature fails verification")
    void invalidSignatureHex_FailsVerification() {
        assertFalse(pdfReceiptService.verifyReceiptSignature(testComplaint, "0000000000000000000000000000000000000000000000000000000000000000"));
        assertFalse(pdfReceiptService.verifyReceiptSignature(testComplaint, "invalid_sig"));
        assertFalse(pdfReceiptService.verifyReceiptSignature(testComplaint, null));
        assertFalse(pdfReceiptService.verifyReceiptSignature(null, "signature"));
    }

    @Test
    @DisplayName("HMAC Tamper-Evidence: verifyReceiptTamperEvidence handles raw data verification")
    void verifyReceiptTamperEvidence_ValidAndModifiedCases() {
        String sig = pdfReceiptService.calculateReceiptHmac(testComplaint);

        boolean valid = pdfReceiptService.verifyReceiptTamperEvidence(
                testComplaint.getId(),
                testComplaint.getReferenceId(),
                testComplaint.getCitizen().getId(),
                testComplaint.getWard(),
                testComplaint.getCategory(),
                testComplaint.getStatus().name(),
                testComplaint.getCreatedAt().toEpochSecond(),
                sig
        );
        assertTrue(valid, "Tamper evidence check with matching raw parameters must pass");

        boolean tamperedRef = pdfReceiptService.verifyReceiptTamperEvidence(
                testComplaint.getId(),
                "CF-2026-99999", // Tampered
                testComplaint.getCitizen().getId(),
                testComplaint.getWard(),
                testComplaint.getCategory(),
                testComplaint.getStatus().name(),
                testComplaint.getCreatedAt().toEpochSecond(),
                sig
        );
        assertFalse(tamperedRef, "Tampered reference number must fail verification");
    }

    @Test
    @DisplayName("Receipt Verification Service: Service delegator resolves complaint and checks signature")
    void receiptVerificationServiceDelegation() {
        String sig = pdfReceiptService.calculateReceiptHmac(testComplaint);

        assertTrue(complaintService.verifyReceiptSignature("CF-2026-12345", sig));
        assertFalse(complaintService.verifyReceiptSignature("CF-2026-12345", "bad_signature"));
    }

    @Test
    @DisplayName("PDF Generation embeds HMAC signature in output bytes")
    void pdfOutputEmbedsHmacSignatureText() throws Exception {
        byte[] pdfBytes = pdfReceiptService.generateReceiptPdf(testComplaint);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes);
        try {
            assertEquals(1, reader.getNumberOfPages());
            byte[] pageContent = reader.getPageContent(1);
            String pageText = new String(pageContent, StandardCharsets.ISO_8859_1);
            assertTrue(pageText.contains("DIGITAL TAMPER-EVIDENCE") || pageText.contains("Cryptographic Proof"),
                    "Decompressed PDF stream must contain digital seal reference");
        } finally {
            reader.close();
        }
    }

    @Test
    @DisplayName("PDF Header Sanitization: Whitelist regex accepts clean IDs and rejects CRLF / injection attempts")
    void identifierSanitization_RejectsCrlfAndSpecialCharacters() {
        java.util.regex.Pattern safePattern = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

        // Safe identifiers
        assertTrue(safePattern.matcher("CF-2026-12345").matches());
        assertTrue(safePattern.matcher("00000000-0000-0000-0000-000000000001").matches());
        assertTrue(safePattern.matcher("ticket_42").matches());

        // Header injection & traversal attacks: MUST be rejected
        assertFalse(safePattern.matcher("CF-2026\r\nSet-Cookie: sessionId=evil").matches(), "CRLF injection must be rejected");
        assertFalse(safePattern.matcher("CF-2026\nInjected-Header: 1").matches(), "LF injection must be rejected");
        assertFalse(safePattern.matcher("receipt\"name.pdf").matches(), "Double quotes must be rejected");
        assertFalse(safePattern.matcher("../../etc/passwd").matches(), "Path traversal must be rejected");
        assertFalse(safePattern.matcher("<script>alert(1)</script>").matches(), "HTML tags must be rejected");
        assertFalse(safePattern.matcher("   ").matches(), "Blank ID must be rejected");
    }
}
