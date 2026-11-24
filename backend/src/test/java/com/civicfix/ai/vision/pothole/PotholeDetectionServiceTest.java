package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.VisionTriageProperties;
import com.civicfix.ai.vision.pothole.dto.CalibrationRequest;
import com.civicfix.ai.vision.pothole.dto.PotholeAnalysisResponse;
import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPhoto;
import com.civicfix.complaint.ComplaintPhotoRepository;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: Pothole Detection Service Unit Tests")
class PotholeDetectionServiceTest {

    private ComplaintRepository complaintRepository;
    private ComplaintPhotoRepository photoRepository;
    private PotholeDetectionRepository detectionRepository;
    private StorageService storageService;
    private PotholeDetectionValidator validator;
    private PotholeMeasurementEngine measurementEngine;
    private PotholeSeverityCalculator severityCalculator;
    private VisionTriageProperties properties;

    private PotholeDetectionService service;
    private UUID complaintId;
    private Complaint complaint;
    private List<ComplaintPhoto> photosList;
    private final List<PotholeDetection> savedDetections = new ArrayList<>();

    @BeforeEach
    void setUp() {
        complaintId = UUID.randomUUID();
        complaint = new Complaint();
        complaint.setId(complaintId);
        photosList = new ArrayList<>();
        savedDetections.clear();

        complaintRepository = (ComplaintRepository) Proxy.newProxyInstance(
                ComplaintRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.of(complaint);
                    }
                    return null;
                }
        );

        photoRepository = (ComplaintPhotoRepository) Proxy.newProxyInstance(
                ComplaintPhotoRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintPhotoRepository.class},
                (proxy, method, args) -> {
                    if ("findByComplaintIdOrderByCreatedAtAsc".equals(method.getName())) {
                        return photosList;
                    }
                    return Collections.emptyList();
                }
        );

        detectionRepository = (PotholeDetectionRepository) Proxy.newProxyInstance(
                PotholeDetectionRepository.class.getClassLoader(),
                new Class<?>[]{PotholeDetectionRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        PotholeDetection d = (PotholeDetection) args[0];
                        if (d.getId() == null) d.setId(UUID.randomUUID());
                        savedDetections.add(d);
                        return d;
                    }
                    if ("findFirstByComplaintIdOrderByCreatedAtDesc".equals(name)) {
                        return savedDetections.isEmpty() ? Optional.empty() : Optional.of(savedDetections.get(savedDetections.size() - 1));
                    }
                    if ("findFirstByComplaintIdAndPhotoUrl".equals(name)) {
                        return savedDetections.isEmpty() ? Optional.empty() : Optional.of(savedDetections.get(0));
                    }
                    return null;
                }
        );

        storageService = (StorageService) Proxy.newProxyInstance(
                StorageService.class.getClassLoader(),
                new Class<?>[]{StorageService.class},
                (proxy, method, args) -> {
                    if ("loadFileAsResource".equals(method.getName())) {
                        byte[] fakePhotoBytes = new byte[25000];
                        Arrays.fill(fakePhotoBytes, (byte) 120);
                        return new ByteArrayResource(fakePhotoBytes);
                    }
                    return null;
                }
        );

        validator = new PotholeDetectionValidator();
        measurementEngine = new PotholeMeasurementEngine();
        severityCalculator = new PotholeSeverityCalculator();
        properties = new VisionTriageProperties();
        properties.setEnabled(false); // local disabled mode

        service = new PotholeDetectionService(
                complaintRepository, photoRepository, detectionRepository,
                storageService, validator, measurementEngine, severityCalculator,
                properties
        );
    }

    @Test
    @DisplayName("analyzePotholes returns NO_POTHOLE_DETECTED when complaint has no photos")
    void testAnalyzePotholesWithNoPhotos() {
        PotholeAnalysisResponse resp = service.analyzePotholes(complaintId, false);

        assertNotNull(resp);
        assertEquals("NO_POTHOLE_DETECTED", resp.getStatus());
        assertEquals(0, resp.getPotholeCount());
        assertTrue(resp.isAdvisory());
    }

    @Test
    @DisplayName("analyzePotholes returns UNAVAILABLE with zero fabricated boxes when provider is disabled")
    void testAnalyzePotholesWithPhoto() {
        ComplaintPhoto photo = new ComplaintPhoto(complaint, "/api/complaints/evidence/files/pothole-test.jpg");
        photo.setId(UUID.randomUUID());
        photosList.add(photo);

        PotholeAnalysisResponse resp = service.analyzePotholes(complaintId, true);

        assertNotNull(resp);
        assertEquals("UNAVAILABLE", resp.getStatus(), "Must report UNAVAILABLE when provider is disabled; no fallback fabrication allowed");
        assertEquals(0, resp.getPotholeCount(), "Zero boxes must be returned when provider is disabled");
        assertTrue(resp.getBoxes().isEmpty());
        assertNull(resp.getModelName(), "Model name must be null when no actual model executed");
        assertNotNull(resp.getMeasurement());
        assertFalse(resp.getMeasurement().isCalibrated(), "Must not claim calibration on uncalibrated image");
        assertNotNull(resp.getSeverity());
        assertTrue(resp.isAdvisory(), "Detection must strictly remain advisory");
    }

    @Test
    @DisplayName("calibrateAnalysis rejects zero or negative dimensions")
    void testCalibrateRejectsInvalidDimension() {
        CalibrationRequest invalid = new CalibrationRequest("MARKER", BigDecimal.ZERO, new PotholeBox());
        assertThrows(BadRequestException.class, () ->
                service.calibrateAnalysis(complaintId, invalid)
        );
    }

    @Test
    @DisplayName("calibrateAnalysis applies valid coplanar reference target and derives metric area")
    void testCalibrateValidTarget() {
        ComplaintPhoto photo = new ComplaintPhoto(complaint, "/api/complaints/evidence/files/pothole-test.jpg");
        photosList.add(photo);

        PotholeDetection existing = new PotholeDetection();
        existing.setId(UUID.randomUUID());
        existing.setComplaint(complaint);
        existing.setPhoto(photo);
        existing.setPhotoUrl(photo.getPhotoUrl());
        existing.setStatus("COMPLETED");
        existing.setModelName("gemini-3.8-flash");
        existing.setPixelWidth(1000);
        existing.setPixelHeight(1000);
        existing.setDetectionsJson("[{\"id\":\"pothole-1\",\"ymin\":0.40,\"xmin\":0.30,\"ymax\":0.60,\"xmax\":0.70,\"confidence\":0.92}]");
        savedDetections.add(existing);

        // Coplanar reference target adjacent to pothole (deltaY = |0.55 - 0.50| = 0.05 <= 0.25)
        PotholeBox refBox = new PotholeBox("ref-a4", 0.45, 0.10, 0.65, 0.15, BigDecimal.valueOf(0.99));
        CalibrationRequest valid = new CalibrationRequest("A4_SHEET", BigDecimal.valueOf(29.7), refBox);

        PotholeAnalysisResponse calibrated = service.calibrateAnalysis(complaintId, valid);

        assertNotNull(calibrated);
        assertTrue(calibrated.getMeasurement().isCalibrated());
        assertTrue(calibrated.getMeasurement().isProvisional(), "Measurement must be flagged as provisional");
        assertEquals("A4_SHEET", calibrated.getMeasurement().getCalibrationMethod());
        assertNotNull(calibrated.getMeasurement().getMetricWidthCm());
        assertNotNull(calibrated.getMeasurement().getMetricLengthCm());
    }

    @Test
    @DisplayName("calibrateAnalysis rejects reference target on different perspective depth plane")
    void testCalibrateRejectsNonCoplanarPerspective() {
        ComplaintPhoto photo = new ComplaintPhoto(complaint, "/api/complaints/evidence/files/pothole-test.jpg");
        photosList.add(photo);

        PotholeDetection existing = new PotholeDetection();
        existing.setId(UUID.randomUUID());
        existing.setComplaint(complaint);
        existing.setPhoto(photo);
        existing.setPhotoUrl(photo.getPhotoUrl());
        existing.setStatus("COMPLETED");
        existing.setModelName("gemini-3.8-flash");
        existing.setPixelWidth(1000);
        existing.setPixelHeight(1000);
        existing.setDetectionsJson("[{\"id\":\"pothole-1\",\"ymin\":0.15,\"xmin\":0.30,\"ymax\":0.35,\"xmax\":0.70,\"confidence\":0.92}]");
        savedDetections.add(existing);

        // Distant foreground reference (deltaY = |0.80 - 0.25| = 0.55 > 0.25)
        PotholeBox refBox = new PotholeBox("ref-card", 0.70, 0.10, 0.90, 0.20, BigDecimal.valueOf(0.99));
        CalibrationRequest request = new CalibrationRequest("CALIBRATION_CARD", BigDecimal.valueOf(20.0), refBox);

        PotholeAnalysisResponse calibrated = service.calibrateAnalysis(complaintId, request);

        assertNotNull(calibrated);
        assertFalse(calibrated.getMeasurement().isCalibrated(), "Must reject calibration when reference is on different perspective plane");
        assertTrue(calibrated.getMeasurement().getStatusMessage().contains("different perspective depth planes"));
    }
}
