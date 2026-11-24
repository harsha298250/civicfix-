package com.civicfix.gis;

import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.complaint.*;
import com.civicfix.complaint.dto.NearbyComplaintResponse;
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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GIS Intelligence: Nearby Complaint Discovery & Spatial Radius Tests")
class NearbyComplaintTest {

    private ComplaintRepository complaintRepository;
    private ComplaintService complaintService;

    @BeforeEach
    void setUp() {
        complaintRepository = mock(ComplaintRepository.class);
        ComplaintStatusHistoryRepository historyRepository = mock(ComplaintStatusHistoryRepository.class);
        ResolutionEvidenceRepository evidenceRepository = mock(ResolutionEvidenceRepository.class);
        ComplaintPhotoRepository photoRepository = mock(ComplaintPhotoRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditService auditService = mock(AuditService.class);
        StorageService storageService = mock(StorageService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        complaintService = new ComplaintService(
                complaintRepository,
                historyRepository,
                evidenceRepository,
                photoRepository,
                userRepository,
                auditService,
                storageService,
                null, // aiTriageService
                eventPublisher
        );
    }

    private Complaint createTestComplaint(UUID id, String ref, String title, String category,
                                          ComplaintStatus status, ComplaintPriority priority,
                                          BigDecimal lat, BigDecimal lng) {
        Complaint c = new Complaint();
        c.setId(id);
        c.setReferenceId(ref);
        c.setTitle(title);
        c.setCategory(category);
        c.setStatus(status);
        c.setPriority(priority);
        c.setLatitude(lat);
        c.setLongitude(lng);
        c.setAddress("Test Road, Chennai");
        c.setWard("Ward 100");
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }

    @Test
    @DisplayName("Nearby 1: Discovers complaints within specified radius and sorts by distance ascending")
    void discoversComplaintsWithinRadiusAndSortsByDistance() {
        // Query coordinate: Anna Nagar Tower Park (13.0850, 80.2100)
        BigDecimal queryLat = BigDecimal.valueOf(13.0850);
        BigDecimal queryLng = BigDecimal.valueOf(80.2100);

        // Point A: ~200 meters away (13.0865, 80.2105)
        Complaint cA = createTestComplaint(UUID.randomUUID(), "CF-A", "Pothole near park", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0865), BigDecimal.valueOf(80.2105));

        // Point B: ~700 meters away (13.0910, 80.2120)
        Complaint cB = createTestComplaint(UUID.randomUUID(), "CF-B", "Streetlight broken", "STREETLIGHTS",
                ComplaintStatus.IN_PROGRESS, ComplaintPriority.MEDIUM,
                BigDecimal.valueOf(13.0910), BigDecimal.valueOf(80.2120));

        // Point C: ~3.5 km away (outside 1.5 km radius) (13.1150, 80.2150)
        Complaint cC = createTestComplaint(UUID.randomUUID(), "CF-C", "Distant garbage dump", "GARBAGE",
                ComplaintStatus.SUBMITTED, ComplaintPriority.LOW,
                BigDecimal.valueOf(13.1150), BigDecimal.valueOf(80.2150));

        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(cB, cA, cC)); // Returned in arbitrary DB order

        List<NearbyComplaintResponse> results = complaintService.getNearbyComplaints(
                queryLat, queryLng, 1.5, null, null, 10
        );

        // Must include Point A and Point B, exclude Point C
        assertEquals(2, results.size());

        // Point A must be first because it is closest
        assertEquals("CF-A", results.get(0).getReferenceId());
        assertEquals("CF-B", results.get(1).getReferenceId());

        assertTrue(results.get(0).getDistanceMeters() < results.get(1).getDistanceMeters());
        assertTrue(results.get(0).getDistanceMeters() < 300.0);
        assertTrue(results.get(1).getDistanceMeters() < 1000.0);
    }

    @Test
    @DisplayName("Nearby 2: Category and status filters are strictly applied")
    void categoryAndStatusFiltersApplied() {
        BigDecimal queryLat = BigDecimal.valueOf(13.0850);
        BigDecimal queryLng = BigDecimal.valueOf(80.2100);

        Complaint roadSubmitted = createTestComplaint(UUID.randomUUID(), "CF-1", "Road Issue", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0855), BigDecimal.valueOf(80.2105));

        Complaint garbageSubmitted = createTestComplaint(UUID.randomUUID(), "CF-2", "Garbage Issue", "GARBAGE",
                ComplaintStatus.SUBMITTED, ComplaintPriority.MEDIUM,
                BigDecimal.valueOf(13.0852), BigDecimal.valueOf(80.2102));

        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(roadSubmitted, garbageSubmitted));

        List<NearbyComplaintResponse> results = complaintService.getNearbyComplaints(
                queryLat, queryLng, 2.0, "ROADS", null, 10
        );

        assertEquals(1, results.size());
        assertEquals("CF-1", results.get(0).getReferenceId());
        assertEquals("ROADS", results.get(0).getCategory());
    }

    @Test
    @DisplayName("Nearby 3: Invalid or missing coordinates throw BadRequestException")
    void invalidCoordinatesThrowBadRequest() {
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(null, BigDecimal.valueOf(80.2), 2.0, null, null, 10)
        );

        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(BigDecimal.valueOf(95.0), BigDecimal.valueOf(80.2), 2.0, null, null, 10)
        );

        // Null Island (0.0, 0.0)
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(BigDecimal.ZERO, BigDecimal.ZERO, 2.0, null, null, 10)
        );
    }

    @Test
    @DisplayName("Nearby 4: Invalid or non-finite radius throws BadRequestException")
    void invalidNumericRadiusThrowsBadRequest() {
        BigDecimal lat = BigDecimal.valueOf(13.0827);
        BigDecimal lng = BigDecimal.valueOf(80.2707);

        // NaN radius
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(lat, lng, Double.NaN, null, null, 10)
        );

        // Positive Infinity
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(lat, lng, Double.POSITIVE_INFINITY, null, null, 10)
        );

        // Negative Infinity
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(lat, lng, Double.NEGATIVE_INFINITY, null, null, 10)
        );

        // Negative radius
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(lat, lng, -5.0, null, null, 10)
        );

        // Zero radius (< 0.1)
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(lat, lng, 0.0, null, null, 10)
        );

        // Radius exceeding max (> 50 km)
        assertThrows(BadRequestException.class, () ->
                complaintService.getNearbyComplaints(lat, lng, 50.1, null, null, 10)
        );
    }

    @Test
    @DisplayName("Nearby 5: Public nearby endpoint enforces k-anonymity (k=5) and omits exact street address")
    void publicNearbyEnforcesKAnonymityAndOmitsAddress() {
        BigDecimal queryLat = BigDecimal.valueOf(13.0850);
        BigDecimal queryLng = BigDecimal.valueOf(80.2100);

        Complaint c1 = createTestComplaint(UUID.randomUUID(), "CF-P1", "Pothole", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0855), BigDecimal.valueOf(80.2105));
        c1.setAddress("123 Residential Lane, Secret Apt");

        Complaint c2 = createTestComplaint(UUID.randomUUID(), "CF-P2", "Garbage", "GARBAGE",
                ComplaintStatus.SUBMITTED, ComplaintPriority.MEDIUM,
                BigDecimal.valueOf(13.0852), BigDecimal.valueOf(80.2102));
        c2.setAddress("456 Citizen House, Private Road");

        // Scenario A: Low density (< 5 complaints) -> suppress coordinates entirely to prevent triangulation
        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(c1, c2));

        List<NearbyComplaintResponse> lowDensity = complaintService.getPublicNearbyComplaints(
                queryLat, queryLng, 2.0, null, null, 10
        );

        assertEquals(2, lowDensity.size());
        for (NearbyComplaintResponse resp : lowDensity) {
            assertNull(resp.getAddress(), "Exact street address must be omitted");
            assertNull(resp.getLatitude(), "Coordinates must be suppressed under k=5");
            assertNull(resp.getLongitude(), "Coordinates must be suppressed under k=5");
            assertTrue(resp.getLocationSuppressed(), "locationSuppressed flag must be true");
            assertTrue(resp.getPrivacySanitized(), "privacySanitized flag must be true");
        }

        // Scenario B: Adequate density (>= 5 complaints) -> omit address, truncate coordinates to 3 decimals
        Complaint c3 = createTestComplaint(UUID.randomUUID(), "CF-P3", "Light", "STREETLIGHTS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.LOW,
                BigDecimal.valueOf(13.0854), BigDecimal.valueOf(80.2103));
        Complaint c4 = createTestComplaint(UUID.randomUUID(), "CF-P4", "Water", "DRAINAGE",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0853), BigDecimal.valueOf(80.2104));
        Complaint c5 = createTestComplaint(UUID.randomUUID(), "CF-P5", "Sign", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.MEDIUM,
                BigDecimal.valueOf(13.0851), BigDecimal.valueOf(80.2101));

        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(c1, c2, c3, c4, c5));

        List<NearbyComplaintResponse> adequateDensity = complaintService.getPublicNearbyComplaints(
                queryLat, queryLng, 2.0, null, null, 10
        );

        assertEquals(5, adequateDensity.size());
        for (NearbyComplaintResponse resp : adequateDensity) {
            assertNull(resp.getAddress(), "Exact street address must be omitted");
            assertNotNull(resp.getLatitude(), "Coordinates present at k>=5");
            assertNotNull(resp.getLongitude(), "Coordinates present at k>=5");
            assertEquals(3, resp.getLatitude().scale(), "Latitude rounded to 3 decimals (~110m)");
            assertEquals(3, resp.getLongitude().scale(), "Longitude rounded to 3 decimals (~110m)");
            assertFalse(resp.getLocationSuppressed(), "locationSuppressed flag must be false");
            assertTrue(resp.getPrivacySanitized(), "privacySanitized flag must be true");
        }
    }

    @Test
    @DisplayName("Nearby 6: Officer is scoped strictly to assigned ward unless cross-ward authorized")
    void roleBasedGeographicAuthorizationForOfficer() {
        BigDecimal queryLat = BigDecimal.valueOf(13.0850);
        BigDecimal queryLng = BigDecimal.valueOf(80.2100);

        Complaint ward100Complaint = createTestComplaint(UUID.randomUUID(), "CF-W100", "Pothole in Ward 100", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0855), BigDecimal.valueOf(80.2105));
        ward100Complaint.setWard("Ward 100");

        Complaint ward200Complaint = createTestComplaint(UUID.randomUUID(), "CF-W200", "Pothole in Ward 200", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0852), BigDecimal.valueOf(80.2102));
        ward200Complaint.setWard("Ward 200");

        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(ward100Complaint, ward200Complaint));

        // Create officer user assigned to Ward 100
        User officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setEmail("officer.ward100@metro.gov");
        officerUser.setFullName("Officer Ward 100");
        officerUser.setWard("Ward 100");
        officerUser.setRoles(Set.of(new Role(RoleName.OFFICER)));
        officerUser.setCrossWardAccess(false);
        UserPrincipal officerPrincipal = new UserPrincipal(officerUser);

        List<NearbyComplaintResponse> results = complaintService.getNearbyComplaints(
                queryLat, queryLng, 2.0, null, null, 10, officerPrincipal
        );

        assertEquals(1, results.size());
        assertEquals("CF-W100", results.get(0).getReferenceId());
        assertEquals("Ward 100", results.get(0).getWard());
    }

    @Test
    @DisplayName("Nearby 7: Worker is scoped strictly to assigned work orders; ward fallback is removed and missing assignment fails closed")
    void roleBasedGeographicAuthorizationForWorker() {
        BigDecimal queryLat = BigDecimal.valueOf(13.0850);
        BigDecimal queryLng = BigDecimal.valueOf(80.2100);

        UUID workerId = UUID.randomUUID();
        User workerUser = new User();
        workerUser.setId(workerId);
        workerUser.setEmail("worker.rajan@metro.gov");
        workerUser.setFullName("Rajan Worker");
        workerUser.setRoles(Set.of(new Role(RoleName.WORKER)));
        workerUser.setWard("Ward 50");
        UserPrincipal workerPrincipal = new UserPrincipal(workerUser);

        // 1. Complaint assigned to this worker (in Ward 99) -> must be returned
        Complaint assignedComplaint = createTestComplaint(UUID.randomUUID(), "CF-ASSIGNED", "My Work Order", "ROADS",
                ComplaintStatus.IN_PROGRESS, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0855), BigDecimal.valueOf(80.2105));
        assignedComplaint.setAssignedWorker(workerUser);
        assignedComplaint.setWard("Ward 99");

        // 2. Complaint assigned to another worker (in Ward 99) -> must be excluded
        User otherWorker = new User();
        otherWorker.setId(UUID.randomUUID());
        Complaint otherComplaint = createTestComplaint(UUID.randomUUID(), "CF-OTHER", "Other Work Order", "ROADS",
                ComplaintStatus.IN_PROGRESS, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0852), BigDecimal.valueOf(80.2102));
        otherComplaint.setAssignedWorker(otherWorker);
        otherComplaint.setWard("Ward 99");

        // 3. Unassigned complaint in worker's OWN ward (Ward 50) -> must be excluded (belonging to ward does not grant access)
        Complaint unassignedWard50Complaint = createTestComplaint(UUID.randomUUID(), "CF-WARD50-UNASSIGNED", "Unassigned in Ward 50", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0854), BigDecimal.valueOf(80.2104));
        unassignedWard50Complaint.setAssignedWorker(null); // Missing assignment fails closed
        unassignedWard50Complaint.setWard("Ward 50");

        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(assignedComplaint, otherComplaint, unassignedWard50Complaint));

        List<NearbyComplaintResponse> results = complaintService.getNearbyComplaints(
                queryLat, queryLng, 2.0, null, null, 10, workerPrincipal
        );

        // Only the complaint directly assigned to this worker must be returned
        assertEquals(1, results.size(), "Worker must only see work orders assigned to their account");
        assertEquals("CF-ASSIGNED", results.get(0).getReferenceId());
    }

    @Test
    @DisplayName("Nearby 8: Citizen sees full details for own complaint but sanitized public view for others")
    void citizenNearbySanitizesOtherCitizensComplaints() {
        BigDecimal queryLat = BigDecimal.valueOf(13.0850);
        BigDecimal queryLng = BigDecimal.valueOf(80.2100);

        UUID citizenId = UUID.randomUUID();
        User citizenUser = new User();
        citizenUser.setId(citizenId);
        citizenUser.setEmail("alex.morgan@citizen.org");
        citizenUser.setFullName("Alex Morgan");
        citizenUser.setRoles(Set.of(new Role(RoleName.CITIZEN)));
        UserPrincipal citizenPrincipal = new UserPrincipal(citizenUser);

        Complaint ownComplaint = createTestComplaint(UUID.randomUUID(), "CF-OWN", "My Pothole", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                new BigDecimal("13.0855432"), new BigDecimal("80.2105432"));
        ownComplaint.setCitizen(citizenUser);
        ownComplaint.setAddress("12 My Private Lane, Chennai");

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        Complaint otherComplaint = createTestComplaint(UUID.randomUUID(), "CF-OTHER", "Neighbor Leak", "DRAINAGE",
                ComplaintStatus.SUBMITTED, ComplaintPriority.MEDIUM,
                new BigDecimal("13.0852111"), new BigDecimal("80.2102111"));
        otherComplaint.setCitizen(otherUser);
        otherComplaint.setAddress("44 Neighbor House, Chennai");

        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(ownComplaint, otherComplaint));

        List<NearbyComplaintResponse> results = complaintService.getNearbyComplaints(
                queryLat, queryLng, 2.0, null, null, 10, citizenPrincipal
        );

        assertEquals(2, results.size());

        // Own complaint: retains exact address and precision
        NearbyComplaintResponse ownResp = results.stream().filter(r -> r.getReferenceId().equals("CF-OWN")).findFirst().orElseThrow();
        assertEquals("12 My Private Lane, Chennai", ownResp.getAddress());
        assertEquals(new BigDecimal("13.0855432"), ownResp.getLatitude());

        // Other complaint: sanitized address omitted, coordinates rounded
        NearbyComplaintResponse otherResp = results.stream().filter(r -> r.getReferenceId().equals("CF-OTHER")).findFirst().orElseThrow();
        assertNull(otherResp.getAddress(), "Other citizen's address must be sanitized/omitted");
        assertEquals(3, otherResp.getLatitude().scale(), "Other citizen's latitude rounded to 3 decimals");
        assertTrue(otherResp.getPrivacySanitized());
    }

    @Test
    @DisplayName("Nearby 9: Cluster-independent k-anonymity suppresses sparse clusters even when search radius total exceeds threshold")
    void publicNearbyClusterIndependentKAnonymity() {
        BigDecimal queryLat = BigDecimal.valueOf(13.0850);
        BigDecimal queryLng = BigDecimal.valueOf(80.2100);

        // Cluster A: 5 complaints in Ward 08 (satisfies k >= 5)
        Complaint a1 = createTestComplaint(UUID.randomUUID(), "CF-A1", "Pothole A1", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0855), BigDecimal.valueOf(80.2105));
        a1.setWard("Ward 08");

        Complaint a2 = createTestComplaint(UUID.randomUUID(), "CF-A2", "Pothole A2", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0854), BigDecimal.valueOf(80.2104));
        a2.setWard("Ward 08");

        Complaint a3 = createTestComplaint(UUID.randomUUID(), "CF-A3", "Pothole A3", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.MEDIUM,
                BigDecimal.valueOf(13.0853), BigDecimal.valueOf(80.2103));
        a3.setWard("Ward 08");

        Complaint a4 = createTestComplaint(UUID.randomUUID(), "CF-A4", "Pothole A4", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.LOW,
                BigDecimal.valueOf(13.0852), BigDecimal.valueOf(80.2102));
        a4.setWard("Ward 08");

        Complaint a5 = createTestComplaint(UUID.randomUUID(), "CF-A5", "Pothole A5", "ROADS",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0851), BigDecimal.valueOf(80.2101));
        a5.setWard("Ward 08");

        // Cluster B: 1 isolated complaint in Ward 99 (fails k >= 5, cluster size is only 1)
        Complaint b1 = createTestComplaint(UUID.randomUUID(), "CF-B1", "Isolated Leak in Ward 99", "DRAINAGE",
                ComplaintStatus.SUBMITTED, ComplaintPriority.HIGH,
                BigDecimal.valueOf(13.0860), BigDecimal.valueOf(80.2110));
        b1.setWard("Ward 99");

        // Total complaints in search radius = 5 + 1 = 6 (exceeds threshold 5 globally)
        when(complaintRepository.findPublicMapBySpatialBounds(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(a1, a2, a3, a4, a5, b1));

        List<NearbyComplaintResponse> results = complaintService.getPublicNearbyComplaints(
                queryLat, queryLng, 2.0, null, null, 10
        );

        assertEquals(6, results.size(), "Total results returned should be 6");

        // Verify Cluster A complaints: dense cluster (k >= 5) -> coordinates present at 3 decimals
        List<NearbyComplaintResponse> clusterAResponses = results.stream()
                .filter(r -> r.getReferenceId().startsWith("CF-A"))
                .toList();
        assertEquals(5, clusterAResponses.size());
        for (NearbyComplaintResponse r : clusterAResponses) {
            assertNull(r.getAddress(), "Exact address must always be omitted publicly");
            assertNotNull(r.getLatitude(), "Dense cluster coordinates must be published");
            assertNotNull(r.getLongitude(), "Dense cluster coordinates must be published");
            assertEquals(3, r.getLatitude().scale(), "Coordinates rounded to 3 decimals");
            assertFalse(r.getLocationSuppressed(), "locationSuppressed must be false for dense cluster");
            assertTrue(r.getPrivacySanitized());
        }

        // Verify Cluster B complaint: sparse cluster (k = 1 < 5) -> coordinates MUST be suppressed!
        NearbyComplaintResponse clusterBResponse = results.stream()
                .filter(r -> r.getReferenceId().equals("CF-B1"))
                .findFirst()
                .orElseThrow();

        assertNull(clusterBResponse.getAddress(), "Exact address must always be omitted publicly");
        assertNull(clusterBResponse.getLatitude(), "Sparse cluster coordinates MUST be suppressed even if search radius total >= 5");
        assertNull(clusterBResponse.getLongitude(), "Sparse cluster coordinates MUST be suppressed even if search radius total >= 5");
        assertTrue(clusterBResponse.getLocationSuppressed(), "locationSuppressed must be true for sparse cluster");
        assertTrue(clusterBResponse.getPrivacySanitized());
    }
}
