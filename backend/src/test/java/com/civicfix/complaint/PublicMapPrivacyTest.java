package com.civicfix.complaint;

import com.civicfix.complaint.dto.PublicMapComplaintResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PublicMapPrivacyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ComplaintRepository complaintRepository;
    private ComplaintService complaintService;

    @BeforeEach
    void setUp() {
        complaintRepository = Mockito.mock(ComplaintRepository.class);
        complaintService = new ComplaintService(
                complaintRepository,
                Mockito.mock(ComplaintStatusHistoryRepository.class),
                Mockito.mock(ResolutionEvidenceRepository.class),
                Mockito.mock(ComplaintPhotoRepository.class),
                Mockito.mock(com.civicfix.user.UserRepository.class),
                Mockito.mock(com.civicfix.audit.AuditService.class),
                Mockito.mock(com.civicfix.storage.StorageService.class),
                Mockito.mock(com.civicfix.ai.AiTriageService.class),
                Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),
                Mockito.mock(com.civicfix.complaint.sla.SlaService.class),
                Mockito.mock(com.civicfix.complaint.sla.SlaEscalationService.class),
                Mockito.mock(com.civicfix.ai.vision.VisionTriageProperties.class)
        );
        // Explicitly set the default privacy threshold k=5
        complaintService.setPublicMapMinimumGroupSize(5);
    }

    private Complaint createTestComplaint(String ward, String category, double lat, double lng) {
        Complaint c = new Complaint();
        c.setId(UUID.randomUUID());
        c.setReferenceId("CF-2026-" + new Random().nextInt(90000));
        c.setTitle("Pothole near street corner");
        c.setDescription("Resident personal note with phone +91 98401 23456");
        c.setAddress("42 Resident Street, Anna Nagar");
        c.setCategory(category);
        c.setStatus(ComplaintStatus.SUBMITTED);
        c.setPriority(ComplaintPriority.MEDIUM);
        c.setWard(ward);
        c.setLatitude(BigDecimal.valueOf(lat));
        c.setLongitude(BigDecimal.valueOf(lng));
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }

    @Test
    @DisplayName("Group size below five (< 5): Location coordinates are suppressed for resident privacy")
    void groupSizeBelowFiveIsSuppressed() {
        List<Complaint> mockList = new ArrayList<>();
        // Create 3 complaints in Ward 10 (< 5 threshold)
        for (int i = 0; i < 3; i++) {
            mockList.add(createTestComplaint("Ward 10", "Roads & Potholes", 13.0827 + (i * 0.001), 80.2707));
        }
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(mockList);

        List<PublicMapComplaintResponse> responses = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);

        assertEquals(3, responses.size());
        for (PublicMapComplaintResponse resp : responses) {
            assertTrue(resp.getLocationSuppressed(), "Low density (< 5) group MUST have locationSuppressed=true");
            assertNull(resp.getLatitude(), "Suppressed group MUST have null latitude");
            assertNull(resp.getLongitude(), "Suppressed group MUST have null longitude");
            assertNotNull(resp.getSuppressionReason(), "Suppressed group must carry explanation");
            assertTrue(resp.getSuppressionReason().contains("Low complaint density"));
            // Basic sanitized metadata is retained
            assertEquals("Ward 10", resp.getWard());
            assertEquals("Roads & Potholes", resp.getCategory());
        }
    }

    @Test
    @DisplayName("Group size equal to five (= 5): Eligible for aggregation, safe coordinates displayed")
    void groupSizeEqualToFiveIsEligibleForAggregation() {
        List<Complaint> mockList = new ArrayList<>();
        // Create exactly 5 complaints in Ward 12 (= 5 threshold)
        for (int i = 0; i < 5; i++) {
            mockList.add(createTestComplaint("Ward 12", "Water Supply", 13.0500 + (i * 0.001), 80.2100));
        }
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(mockList);

        List<PublicMapComplaintResponse> responses = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);

        assertEquals(5, responses.size());
        for (PublicMapComplaintResponse resp : responses) {
            assertFalse(resp.getLocationSuppressed(), "Group size 5 must NOT be suppressed");
            assertNotNull(resp.getLatitude(), "Group size 5 must have latitude");
            assertNotNull(resp.getLongitude(), "Group size 5 must have longitude");
            assertEquals(5, resp.getCount(), "Aggregation count must equal group size (5)");
            assertTrue(resp.getAggregated(), "Must be marked as aggregated");
            // Check coordinate rounding (~110m resolution)
            assertTrue(resp.getLatitude().scale() <= 3);
            assertTrue(resp.getLongitude().scale() <= 3);
        }
    }

    @Test
    @DisplayName("Boundary verification (k=4 vs k=5): k=4 is suppressed and k=5 is aggregated")
    void boundaryVerificationK4SuppressedAndK5Aggregated() {
        // Test k=4: exactly 4 complaints -> MUST be suppressed under threshold 5
        List<Complaint> list4 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            list4.add(createTestComplaint("Ward 07", "Roads", 13.080 + (i * 0.001), 80.270));
        }
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(list4);

        List<PublicMapComplaintResponse> res4 = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);
        assertEquals(4, res4.size());
        for (PublicMapComplaintResponse r : res4) {
            assertTrue(r.getLocationSuppressed(), "Group size 4 (< 5) MUST be suppressed");
            assertNull(r.getLatitude(), "k=4 latitude must be null");
            assertNull(r.getLongitude(), "k=4 longitude must be null");
        }

        // Test k=5: exactly 5 complaints -> MUST NOT be suppressed
        List<Complaint> list5 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list5.add(createTestComplaint("Ward 07", "Roads", 13.080 + (i * 0.001), 80.270));
        }
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(list5);

        List<PublicMapComplaintResponse> res5 = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);
        assertEquals(5, res5.size());
        for (PublicMapComplaintResponse r : res5) {
            assertFalse(r.getLocationSuppressed(), "Group size 5 (>= 5) MUST NOT be suppressed");
            assertNotNull(r.getLatitude(), "k=5 latitude must exist");
            assertNotNull(r.getLongitude(), "k=5 longitude must exist");
            assertEquals(5, r.getCount());
        }
    }

    @Test
    @DisplayName("Aggregated Coordinates: Cluster centroid is assigned across all cluster items preventing individual location recovery")
    void aggregatedClusterAssignsCentroidAcrossAllItems() {
        List<Complaint> mockList = new ArrayList<>();
        // Distinct coordinates: 13.010, 13.020, 13.030, 13.040, 13.050 -> Average = 13.030
        for (int i = 0; i < 5; i++) {
            mockList.add(createTestComplaint("Ward 09", "Water Supply", 13.010 + (i * 0.010), 80.200));
        }
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(mockList);

        List<PublicMapComplaintResponse> responses = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);

        assertEquals(5, responses.size());
        BigDecimal expectedCentroidLat = responses.get(0).getLatitude();
        BigDecimal expectedCentroidLng = responses.get(0).getLongitude();

        // All 5 items must share the exact same centroid coordinates
        for (PublicMapComplaintResponse r : responses) {
            assertEquals(expectedCentroidLat, r.getLatitude(), "All cluster members must share centroid latitude");
            assertEquals(expectedCentroidLng, r.getLongitude(), "All cluster members must share centroid longitude");
        }
    }

    @Test
    @DisplayName("Differencing Attack Prevention: Category filtering that reduces group size below k causes suppression")
    void filteringDownToSmallGroupCausesSuppressionToPreventDifferencingAttacks() {
        List<Complaint> mockList = new ArrayList<>();
        // Ward 20 has 6 total complaints: 2 Roads & Potholes, 4 Streetlights
        mockList.add(createTestComplaint("Ward 20", "Roads & Potholes", 13.060, 80.250));
        mockList.add(createTestComplaint("Ward 20", "Roads & Potholes", 13.061, 80.251));
        mockList.add(createTestComplaint("Ward 20", "Streetlights", 13.062, 80.252));
        mockList.add(createTestComplaint("Ward 20", "Streetlights", 13.063, 80.253));
        mockList.add(createTestComplaint("Ward 20", "Streetlights", 13.064, 80.254));
        mockList.add(createTestComplaint("Ward 20", "Streetlights", 13.065, 80.255));

        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(mockList);

        // When filtering specifically for "Roads & Potholes", only 2 records match in Ward 20 (< 5)
        List<PublicMapComplaintResponse> filteredRoads = complaintService.getPublicMapComplaints(
                null, null, null, null, null, "Roads & Potholes", 250);

        assertEquals(2, filteredRoads.size());
        for (PublicMapComplaintResponse resp : filteredRoads) {
            assertTrue(resp.getLocationSuppressed(),
                    "Filtered subset has only 2 complaints (< 5), so location MUST be suppressed to prevent differencing attack");
            assertNull(resp.getLatitude());
            assertNull(resp.getLongitude());
        }
    }

    @Test
    @DisplayName("Public map response strictly excludes sensitive personal and internal fields in JSON")
    void sensitiveFieldsNeverAppearInPublicResponseJson() throws Exception {
        Complaint complaint = createTestComplaint("Ward 05", "Electrical", 13.0827194, 80.2707321);
        PublicMapComplaintResponse resp = PublicMapComplaintResponse.fromEntity(complaint, 1);

        String json = objectMapper.writeValueAsString(resp);
        JsonNode node = objectMapper.readTree(json);

        assertFalse(node.has("title"), "Public map response must NEVER contain complaint title");
        assertFalse(node.has("referenceId"), "Public map response must NEVER contain reference ID");
        assertFalse(node.has("description"), "Public map response must NEVER contain description");
        assertFalse(node.has("address"), "Public map response must NEVER contain address");
        assertFalse(node.has("citizen"), "Public map response must NEVER contain citizen PII");
        assertFalse(node.has("citizenId"), "Public map response must NEVER contain citizen ID");
        assertFalse(node.has("citizenPhone"), "Public map response must NEVER contain phone");
        assertFalse(node.has("citizenEmail"), "Public map response must NEVER contain email");
        assertFalse(node.has("internalNotes"), "Public map response must NEVER contain internal notes");
        assertFalse(node.has("assignedOfficer"), "Public map response must NEVER contain assigned officer");
        assertFalse(node.has("assignedWorker"), "Public map response must NEVER contain assigned worker");
        assertFalse(node.has("evidence"), "Public map response must NEVER contain evidence URLs");

        // Public-safe fields must exist
        assertTrue(node.has("id"));
        assertTrue(node.has("category"));
        assertTrue(node.has("status"));
        assertTrue(node.has("priority"));
        assertTrue(node.has("ward"));
        assertTrue(node.has("latitude"));
        assertTrue(node.has("longitude"));
        assertTrue(node.has("locationSuppressed"));
        assertTrue(node.has("aggregated"));
        assertTrue(node.has("count"));
    }

    @Test
    @DisplayName("Custom configured threshold is respected, and invalid config (< 1) safely falls back to default 5")
    void configurableThresholdAndSafeFallback() {
        List<Complaint> mockList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mockList.add(createTestComplaint("Ward 01", "Roads", 13.01, 80.21));
        }
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(mockList);

        // With k=3, 3 complaints are eligible
        complaintService.setPublicMapMinimumGroupSize(3);
        List<PublicMapComplaintResponse> resK3 = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);
        assertFalse(resK3.get(0).getLocationSuppressed(), "Group size 3 with k=3 must NOT be suppressed");

        // With invalid k=0 or k=-1, safe fallback to default 5 kicks in -> suppressed!
        complaintService.setPublicMapMinimumGroupSize(0);
        List<PublicMapComplaintResponse> resFallback = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);
        assertTrue(resFallback.get(0).getLocationSuppressed(), "Invalid k=0 must safely fallback to k=5 suppression");
    }

    @Test
    @DisplayName("Differencing Protection: Arbitrary client bounding boxes cannot slice ward clusters or alter centroids")
    void arbitraryBoundingBoxCannotSliceWardOrAlterCentroid() {
        List<Complaint> mockList = new ArrayList<>();
        // 6 complaints in Ward 15 across different coordinates
        for (int i = 0; i < 6; i++) {
            mockList.add(createTestComplaint("Ward 15", "Roads", 13.080 + (i * 0.005), 80.200 + (i * 0.005)));
        }
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(mockList);

        // Query 1: Full citywide viewport
        List<PublicMapComplaintResponse> fullView = complaintService.getPublicMapComplaints(
                null, null, null, null, null, null, 250);
        assertEquals(6, fullView.size());
        BigDecimal centroidLatFull = fullView.get(0).getLatitude();
        BigDecimal centroidLngFull = fullView.get(0).getLongitude();

        // Query 2: Client provides a narrow bounding box covering only 2 of the 6 complaints
        // Under remediation, ward cluster is evaluated as a whole. Its anchor either falls in or out,
        // but its count and coordinates are NEVER recalculated on a partial slice.
        BigDecimal minLat = BigDecimal.valueOf(13.070);
        BigDecimal maxLat = BigDecimal.valueOf(13.090);
        BigDecimal minLng = BigDecimal.valueOf(13.190);
        BigDecimal maxLng = BigDecimal.valueOf(13.220);

        List<PublicMapComplaintResponse> narrowView = complaintService.getPublicMapComplaints(
                minLat, maxLat, minLng, maxLng, null, null, 250);

        // If visible, the cluster centroid must strictly match the full-ward anchor, not a sliced centroid
        if (!narrowView.isEmpty()) {
            assertEquals(6, narrowView.get(0).getCount(), "Cluster count must remain 6 (full ward), not sliced");
            assertEquals(centroidLatFull, narrowView.get(0).getLatitude(), "Centroid must remain full ward centroid");
            assertEquals(centroidLngFull, narrowView.get(0).getLongitude(), "Centroid must remain full ward centroid");
        }
    }

    @Test
    @DisplayName("Combining Status and Category Filters below threshold always suppresses location")
    void combiningFiltersBelowThresholdAlwaysSuppresses() {
        List<Complaint> mockList = new ArrayList<>();
        // Ward 30 has 5 complaints: 3 RESOLVED Roads, 2 SUBMITTED Water
        Complaint c1 = createTestComplaint("Ward 30", "Roads", 13.01, 80.20);
        c1.setStatus(ComplaintStatus.RESOLVED);
        Complaint c2 = createTestComplaint("Ward 30", "Roads", 13.02, 80.21);
        c2.setStatus(ComplaintStatus.RESOLVED);
        Complaint c3 = createTestComplaint("Ward 30", "Roads", 13.03, 80.22);
        c3.setStatus(ComplaintStatus.RESOLVED);
        Complaint c4 = createTestComplaint("Ward 30", "Water Supply", 13.04, 80.23);
        c4.setStatus(ComplaintStatus.SUBMITTED);
        Complaint c5 = createTestComplaint("Ward 30", "Water Supply", 13.05, 80.24);
        c5.setStatus(ComplaintStatus.SUBMITTED);

        mockList.addAll(List.of(c1, c2, c3, c4, c5));
        when(complaintRepository.findAllWithCoordinates(any(Pageable.class))).thenReturn(mockList);

        // Query filtering for "Water Supply": only 2 complaints match (< 5)
        List<PublicMapComplaintResponse> waterResponses = complaintService.getPublicMapComplaints(
                null, null, null, null, null, "Water Supply", 250);
        assertEquals(2, waterResponses.size());
        for (PublicMapComplaintResponse r : waterResponses) {
            assertTrue(r.getLocationSuppressed(), "Filtered subset with 2 items must have location suppressed");
            assertNull(r.getLatitude());
            assertNull(r.getLongitude());
        }
    }
}
