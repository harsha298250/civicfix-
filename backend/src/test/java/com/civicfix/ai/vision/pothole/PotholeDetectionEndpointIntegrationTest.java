package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.pothole.dto.CalibrationRequest;
import com.civicfix.ai.vision.pothole.dto.PotholeAnalysisResponse;
import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import com.civicfix.common.ApiResponse;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintController;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.complaint.ComplaintService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: Pothole Detection Controller & RBAC Authorization Tests")
class PotholeDetectionEndpointIntegrationTest {

    private UserPrincipal officerPrincipal;
    private UserPrincipal supervisorPrincipal;
    private UserPrincipal adminPrincipal;
    private UserPrincipal citizenPrincipal;
    private UserPrincipal workerPrincipal;

    private UUID complaintId;

    @BeforeEach
    void setUp() {
        complaintId = UUID.randomUUID();

        User officer = new User();
        officer.setId(UUID.randomUUID());
        officer.setEmail("elena.vance@metro.gov");
        officer.setRoles(Set.of(new Role(RoleName.OFFICER)));
        officerPrincipal = new UserPrincipal(officer);

        User supervisor = new User();
        supervisor.setId(UUID.randomUUID());
        supervisor.setEmail("supervisor@metro.gov");
        supervisor.setRoles(Set.of(new Role(RoleName.SUPERVISOR)));
        supervisorPrincipal = new UserPrincipal(supervisor);

        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@civicfix.org");
        admin.setRoles(Set.of(new Role(RoleName.ADMIN)));
        adminPrincipal = new UserPrincipal(admin);

        User citizen = new User();
        citizen.setId(UUID.randomUUID());
        citizen.setEmail("alex.m@citizen.org");
        citizen.setRoles(Set.of(new Role(RoleName.CITIZEN)));
        citizenPrincipal = new UserPrincipal(citizen);

        User worker = new User();
        worker.setId(UUID.randomUUID());
        worker.setEmail("marcus.chen@fieldops.gov");
        worker.setRoles(Set.of(new Role(RoleName.WORKER)));
        workerPrincipal = new UserPrincipal(worker);
    }

    @Test
    @DisplayName("Non-staff citizen calling getPotholeAnalysis is rejected with 403 Forbidden")
    void testCitizenRejectedWithForbidden() {
        ComplaintService service = new ComplaintService(null, null, null, null, null, null, null);
        assertThrows(ForbiddenException.class, () ->
                service.getPotholeAnalysis(complaintId.toString(), citizenPrincipal)
        );
    }

    @Test
    @DisplayName("Non-staff worker calling getPotholeAnalysis is rejected with 403 Forbidden")
    void testWorkerRejectedWithForbidden() {
        ComplaintService service = new ComplaintService(null, null, null, null, null, null, null);
        assertThrows(ForbiddenException.class, () ->
                service.getPotholeAnalysis(complaintId.toString(), workerPrincipal)
        );
    }

    @Test
    @DisplayName("Worker calling runPotholeAnalysis is rejected with 403 Forbidden")
    void testWorkerRunRejectedWithForbidden() {
        ComplaintService service = new ComplaintService(null, null, null, null, null, null, null);
        assertThrows(ForbiddenException.class, () ->
                service.runPotholeAnalysis(complaintId.toString(), workerPrincipal)
        );
    }

    @Test
    @DisplayName("Worker calling calibratePotholeAnalysis is rejected with 403 Forbidden")
    void testWorkerCalibrateRejectedWithForbidden() {
        ComplaintService service = new ComplaintService(null, null, null, null, null, null, null);
        CalibrationRequest req = new CalibrationRequest("A4_SHEET", BigDecimal.valueOf(29.7), new PotholeBox());
        assertThrows(ForbiddenException.class, () ->
                service.calibratePotholeAnalysis(complaintId.toString(), req, workerPrincipal)
        );
    }

    @Test
    @DisplayName("Controller returns structured ApiResponse wrapping PotholeAnalysisResponse")
    void testControllerWrapsResponseSuccessfully() {
        PotholeAnalysisResponse expected = new PotholeAnalysisResponse();
        expected.setComplaintId(complaintId);
        expected.setStatus("COMPLETED");
        expected.setPotholeCount(3);

        // Subclass test stub
        ComplaintService stubService = new ComplaintService(null, null, null, null, null, null, null) {
            @Override
            public PotholeAnalysisResponse getPotholeAnalysis(String idOrRef, UserPrincipal principal) {
                return expected;
            }

            @Override
            public PotholeAnalysisResponse runPotholeAnalysis(String idOrRef, UserPrincipal principal) {
                return expected;
            }

            @Override
            public PotholeAnalysisResponse calibratePotholeAnalysis(String idOrRef, CalibrationRequest req, UserPrincipal principal) {
                return expected;
            }
        };

        ComplaintController controller = new ComplaintController(stubService, null);

        ResponseEntity<ApiResponse<PotholeAnalysisResponse>> getResp =
                controller.getPotholeAnalysis(complaintId.toString(), officerPrincipal);
        assertEquals(200, getResp.getStatusCode().value());
        assertTrue(getResp.getBody().isSuccess());
        assertEquals(3, getResp.getBody().getData().getPotholeCount());

        ResponseEntity<ApiResponse<PotholeAnalysisResponse>> postResp =
                controller.runPotholeAnalysis(complaintId.toString(), supervisorPrincipal);
        assertEquals(200, postResp.getStatusCode().value());
        assertTrue(postResp.getBody().isSuccess());

        CalibrationRequest req = new CalibrationRequest("A4_SHEET", BigDecimal.valueOf(29.7), new PotholeBox());
        ResponseEntity<ApiResponse<PotholeAnalysisResponse>> calResp =
                controller.calibratePotholeAnalysis(complaintId.toString(), req, adminPrincipal);
        assertEquals(200, calResp.getStatusCode().value());
        assertTrue(calResp.getBody().isSuccess());
        assertEquals("Pothole dimensions calibrated successfully.", calResp.getBody().getMessage());
    }
}
