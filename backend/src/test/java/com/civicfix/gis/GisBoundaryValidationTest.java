package com.civicfix.gis;

import com.civicfix.common.util.GeoUtils;
import com.civicfix.gis.dto.WardValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GIS Spatial Boundary & Ward Containment Tests")
class GisBoundaryValidationTest {

    private GisService gisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Fast deterministic unit test initialization (rate limiter disabled, loads GeoJSON boundaries from classpath)
        gisService = new GisService(objectMapper, false, 0, 0, 0);
    }

    @Test
    @DisplayName("Load GeoJSON boundaries successfully from classpath")
    void testBoundaryLoading() {
        assertNotNull(gisService.getStateBoundaryGeoJson(), "State boundary GeoJSON should be loaded");
        assertNotNull(gisService.getZonesBoundaryGeoJson(), "GCC zones GeoJSON should be loaded");
        assertFalse(gisService.getZoneBoundaries().isEmpty(), "Zone boundaries list should not be empty");
        assertEquals(15, gisService.getZoneBoundaries().size(), "All 15 GCC Zones should be parsed");
    }

    @Test
    @DisplayName("Ray-Casting Point-in-Polygon handles simple square correctly")
    void testRayCastingSimplePolygon() {
        List<List<Double>> square = List.of(
                List.of(10.0, 10.0),
                List.of(20.0, 10.0),
                List.of(20.0, 20.0),
                List.of(10.0, 20.0),
                List.of(10.0, 10.0)
        );

        // Point inside
        assertTrue(GeoUtils.isPointInPolygon(15.0, 15.0, square));
        // Points outside
        assertFalse(GeoUtils.isPointInPolygon(5.0, 15.0, square));
        assertFalse(GeoUtils.isPointInPolygon(25.0, 15.0, square));
        assertFalse(GeoUtils.isPointInPolygon(15.0, 25.0, square));
    }

    @Test
    @DisplayName("Anna Nagar coordinates validated successfully against Anna Nagar ward")
    void testValidLocationInAnnaNagar() {
        // Center of Anna Nagar: lat ~ 13.085, lon ~ 80.210
        BigDecimal lat = BigDecimal.valueOf(13.0850);
        BigDecimal lng = BigDecimal.valueOf(80.2100);
        String ward = "Ward 94, Anna Nagar";

        WardValidationResult result = gisService.validateWardContainment(lat, lng, ward);

        assertEquals("VALID", result.getStatus());
        assertTrue(result.getInsideState());
        assertTrue(result.getInsideZone());
        assertNotNull(result.getDetectedWard());
        assertTrue(result.getDetectedWard().contains("Anna Nagar"));
        assertEquals(0.0, result.getDistanceMeters());
    }

    @Test
    @DisplayName("Ward mismatch detected when coordinates are in Anna Nagar but Adyar ward is selected")
    void testMismatchBetweenAnnaNagarAndAdyar() {
        // Anna Nagar coordinates
        BigDecimal lat = BigDecimal.valueOf(13.0850);
        BigDecimal lng = BigDecimal.valueOf(80.2100);
        String selectedWard = "Ward 13, Adyar";

        WardValidationResult result = gisService.validateWardContainment(lat, lng, selectedWard);

        assertEquals("MISMATCH", result.getStatus());
        assertTrue(result.getInsideState());
        assertFalse(result.getInsideZone());
        assertEquals(selectedWard, result.getSelectedWard());
        assertTrue(result.getDetectedWard().contains("Anna Nagar"));
        assertTrue(result.getDistanceMeters() > 1000.0, "Distance to Adyar should be greater than 1km");
    }

    @Test
    @DisplayName("Adyar coordinates validated successfully against Adyar ward")
    void testValidLocationInAdyar() {
        // Center of Adyar: lat ~ 13.005, lon ~ 80.250
        BigDecimal lat = BigDecimal.valueOf(13.0050);
        BigDecimal lng = BigDecimal.valueOf(80.2500);
        String ward = "Zone XIII - Adyar";

        WardValidationResult result = gisService.validateWardContainment(lat, lng, ward);

        assertEquals("VALID", result.getStatus());
        assertTrue(result.getInsideState());
        assertTrue(result.getInsideZone());
        assertTrue(result.getDetectedWard().contains("Adyar"));
    }

    @Test
    @DisplayName("Coordinates outside Tamil Nadu state boundary yield OUTSIDE_BOUNDS")
    void testOutsideTamilNaduState() {
        // Bangalore / Bengaluru, Karnataka: 12.9716, 77.5946
        BigDecimal lat = BigDecimal.valueOf(12.9716);
        BigDecimal lng = BigDecimal.valueOf(77.5946);

        WardValidationResult result = gisService.validateWardContainment(lat, lng, "Ward 94, Anna Nagar");

        assertEquals("OUTSIDE_BOUNDS", result.getStatus());
        assertFalse(result.getInsideState());
        assertFalse(result.getInsideZone());
        assertTrue(result.getDistanceMeters() > 10000.0, "Distance to TN border should be > 10km");
        assertEquals("Survey of India / Bharat Maps", result.getBoundarySource());
    }

    @Test
    @DisplayName("Null and invalid coordinates yield UNAVAILABLE status")
    void testInvalidCoordinates() {
        WardValidationResult r1 = gisService.validateWardContainment(null, null, "Ward 94");
        assertEquals("UNAVAILABLE", r1.getStatus());

        WardValidationResult r2 = gisService.validateWardContainment(BigDecimal.ZERO, BigDecimal.ZERO, "Ward 94");
        assertEquals("UNAVAILABLE", r2.getStatus());
    }

    @Test
    @DisplayName("Tamil Nadu interior location outside Chennai is marked OUTSIDE_GCC with pending cadastral note")
    void testTamilNaduInteriorNonChennaiLocation() {
        // Madurai center: 9.9252, 78.1198 (inside TN state boundary, outside Chennai GCC)
        BigDecimal lat = BigDecimal.valueOf(9.9252);
        BigDecimal lng = BigDecimal.valueOf(78.1198);

        WardValidationResult result = gisService.validateWardContainment(lat, lng, "Madurai Central Ward");

        assertEquals("OUTSIDE_GCC", result.getStatus(), "Madurai location must return OUTSIDE_GCC, not VALID");
        assertTrue(result.getInsideState());
        assertFalse(result.getInsideZone());
        assertTrue(result.getDistanceMeters() > 100000.0, "Distance from Madurai to GCC boundary should exceed 100 km");
        assertTrue(result.getMessage().contains("outside Greater Chennai Corporation"));
        assertTrue(result.getApproximate());
    }

    @Test
    @DisplayName("Disputed wards 168, 169, 180 correctly allocated to Zone XIII (Adyar) per Gazette 408 & 2026 Plan")
    void testDisputedWardsZone13AdyarAllocation() {
        BigDecimal lat = BigDecimal.valueOf(13.0050);
        BigDecimal lng = BigDecimal.valueOf(80.2500);

        WardValidationResult r168 = gisService.validateWardContainment(lat, lng, "Ward 168");
        assertEquals("VALID", r168.getStatus());
        assertTrue(r168.getDetectedWard().contains("Adyar"));

        WardValidationResult r169 = gisService.validateWardContainment(lat, lng, "Ward 169");
        assertEquals("VALID", r169.getStatus());
        assertTrue(r169.getDetectedWard().contains("Adyar"));

        WardValidationResult r180 = gisService.validateWardContainment(lat, lng, "Ward 180");
        assertEquals("VALID", r180.getStatus());
        assertTrue(r180.getDetectedWard().contains("Adyar"));
    }

    @Test
    @DisplayName("Disputed wards 181 and 182 correctly allocated to Zone XIV (Perungudi) per Gazette 408 & 2026 Plan")
    void testDisputedWardsZone14PerungudiAllocation() {
        // Perungudi coordinates: ~12.9600, 80.2400
        BigDecimal lat = BigDecimal.valueOf(12.9600);
        BigDecimal lng = BigDecimal.valueOf(80.2400);

        WardValidationResult r181 = gisService.validateWardContainment(lat, lng, "Ward 181");
        assertEquals("VALID", r181.getStatus());
        assertTrue(r181.getDetectedWard().contains("Perungudi"));

        WardValidationResult r182 = gisService.validateWardContainment(lat, lng, "Ward 182");
        assertEquals("VALID", r182.getStatus());
        assertTrue(r182.getDetectedWard().contains("Perungudi"));

        // If coordinates are in Adyar (13.005, 80.250), claiming Ward 181 (Perungudi) should be a MISMATCH
        WardValidationResult mismatchResult = gisService.validateWardContainment(BigDecimal.valueOf(13.0050), BigDecimal.valueOf(80.2500), "Ward 181");
        assertEquals("MISMATCH", mismatchResult.getStatus());
        assertTrue(mismatchResult.getDistanceMeters() > 0.0);
    }

    @Test
    @DisplayName("Interior Tamil Nadu location (Coimbatore) outside GCC yields OUTSIDE_GCC with geodesic distance")
    void testCoimbatoreInteriorOutsideGcc() {
        // Coimbatore: 11.0168, 76.9558
        BigDecimal lat = BigDecimal.valueOf(11.0168);
        BigDecimal lng = BigDecimal.valueOf(76.9558);

        WardValidationResult result = gisService.validateWardContainment(lat, lng, "Coimbatore South");

        assertEquals("OUTSIDE_GCC", result.getStatus());
        assertTrue(result.getInsideState());
        assertFalse(result.getInsideZone());
        assertTrue(result.getDistanceMeters() > 300000.0, "Coimbatore distance to GCC should exceed 300km");
        assertTrue(result.getApproximate());
    }

    @Test
    @DisplayName("Stage 2: Ward 1 never matches Ward 10, 11, 100 or 180")
    void testWard1DoesNotMatchWard10Or11Or100Or180() {
        assertEquals("I", gisService.findClaimedZone("Ward 1").getZoneNumber());
        assertEquals("I", gisService.findClaimedZone("Ward 10").getZoneNumber());
        assertEquals("I", gisService.findClaimedZone("Ward 11").getZoneNumber());
        assertEquals("VIII", gisService.findClaimedZone("Ward 100").getZoneNumber());
        assertEquals("XIII", gisService.findClaimedZone("Ward 180").getZoneNumber());

        // Adyar coordinates (Zone XIII)
        BigDecimal latAdyar = BigDecimal.valueOf(13.0050);
        BigDecimal lngAdyar = BigDecimal.valueOf(80.2500);

        // Ward 180 is valid in Adyar
        WardValidationResult r180 = gisService.validateWardContainment(latAdyar, lngAdyar, "Ward 180");
        assertEquals("VALID", r180.getStatus());

        // Ward 1, 10, 11, 100 must NEVER match Adyar coordinates
        WardValidationResult r1 = gisService.validateWardContainment(latAdyar, lngAdyar, "Ward 1");
        assertEquals("MISMATCH", r1.getStatus());
        assertTrue(r1.getDistanceMeters() > 5000.0);

        WardValidationResult r10 = gisService.validateWardContainment(latAdyar, lngAdyar, "Ward 10");
        assertEquals("MISMATCH", r10.getStatus());
        assertTrue(r10.getDistanceMeters() > 5000.0);

        WardValidationResult r11 = gisService.validateWardContainment(latAdyar, lngAdyar, "Ward 11");
        assertEquals("MISMATCH", r11.getStatus());
        assertTrue(r11.getDistanceMeters() > 5000.0);

        WardValidationResult r100 = gisService.validateWardContainment(latAdyar, lngAdyar, "Ward 100");
        assertEquals("MISMATCH", r100.getStatus());
        assertTrue(r100.getDistanceMeters() > 3000.0);
    }

    @Test
    @DisplayName("Stage 2: Ward 18 never matches Ward 180")
    void testWard18DoesNotMatchWard180() {
        // Ward 18 is in Zone II (Manali), Ward 180 is in Zone XIII (Adyar)
        assertEquals("II", gisService.findClaimedZone("Ward 18").getZoneNumber());
        assertEquals("XIII", gisService.findClaimedZone("Ward 180").getZoneNumber());

        // Coordinates in Adyar (Zone XIII)
        BigDecimal latAdyar = BigDecimal.valueOf(13.0050);
        BigDecimal lngAdyar = BigDecimal.valueOf(80.2500);

        // Ward 180 in Adyar is valid
        assertEquals("VALID", gisService.validateWardContainment(latAdyar, lngAdyar, "Ward 180").getStatus());

        // Ward 18 in Adyar MUST be MISMATCH with distance to Zone II
        WardValidationResult r18 = gisService.validateWardContainment(latAdyar, lngAdyar, "Ward 18");
        assertEquals("MISMATCH", r18.getStatus());
        assertTrue(r18.getDistanceMeters() > 10000.0, "Distance from Adyar to Manali (Zone II) must be > 10km");
    }

    @Test
    @DisplayName("Stage 2: Ward 100 never matches Ward 10")
    void testWard100DoesNotMatchWard10() {
        // Ward 100 is in Zone VIII (Anna Nagar), Ward 10 is in Zone I (Thiruvottiyur)
        assertEquals("VIII", gisService.findClaimedZone("Ward 100").getZoneNumber());
        assertEquals("I", gisService.findClaimedZone("Ward 10").getZoneNumber());

        // Coordinates in Anna Nagar (Zone VIII)
        BigDecimal latAnna = BigDecimal.valueOf(13.0850);
        BigDecimal lngAnna = BigDecimal.valueOf(80.2100);

        // Ward 100 in Anna Nagar is valid
        assertEquals("VALID", gisService.validateWardContainment(latAnna, lngAnna, "Ward 100").getStatus());

        // Ward 10 in Anna Nagar MUST be a MISMATCH
        WardValidationResult r10 = gisService.validateWardContainment(latAnna, lngAnna, "Ward 10");
        assertEquals("MISMATCH", r10.getStatus());
        assertTrue(r10.getDistanceMeters() > 3000.0, "Distance from Anna Nagar to Zone I must be > 3km");
    }

    @Test
    @DisplayName("Stage 2: Explicit ward label parsing supports various formats safely")
    void testExplicitWardLabelParsingFormats() {
        assertEquals(1, GisService.extractSingleWardNumber("Ward 1"));
        assertEquals(1, GisService.extractSingleWardNumber("ward 01"));
        assertEquals(1, GisService.extractSingleWardNumber("W-1"));
        assertEquals(1, GisService.extractSingleWardNumber("W.1"));
        assertEquals(1, GisService.extractSingleWardNumber("Wrd 1"));
        assertEquals(1, GisService.extractSingleWardNumber("Ward #1"));
        assertEquals(94, GisService.extractSingleWardNumber("Ward 94, Anna Nagar"));
        assertEquals(180, GisService.extractSingleWardNumber("Ward-180"));
        assertEquals(200, GisService.extractSingleWardNumber("Ward 200, Sholinganallur"));

        // Out of GCC ward bounds (> 200 or < 1)
        assertNull(GisService.extractSingleWardNumber("Ward 201"));
        assertNull(GisService.extractSingleWardNumber("Ward 999"));
        assertNull(GisService.extractSingleWardNumber("Ward 0"));
    }

    @Test
    @DisplayName("Stage 2: Zone numbers, postal codes and unrelated numbers are not parsed as ward numbers")
    void testZoneNumberAndPostalCodeNotParsedAsWardNumber() {
        // Zone 1 is a zone claim, not a ward number
        assertNull(GisService.extractSingleWardNumber("Zone 1"));
        assertNull(GisService.extractSingleWardNumber("Zone 13"));
        assertNull(GisService.extractSingleWardNumber("Zone XIII"));

        // Postal codes / generic numbers
        assertNull(GisService.extractSingleWardNumber("PIN 600028"));
        assertNull(GisService.extractSingleWardNumber("600028"));
        assertNull(GisService.extractSingleWardNumber("12345"));
        assertNull(GisService.extractSingleWardNumber("West Mambalam"));

        // findClaimedZone resolves Zone 1 to Zone I, Zone 10 to Zone X
        assertEquals("I", gisService.findClaimedZone("Zone 1").getZoneNumber());
        assertEquals("X", gisService.findClaimedZone("Zone 10").getZoneNumber());
        assertEquals("XIII", gisService.findClaimedZone("Zone XIII").getZoneNumber());

        // findClaimedZone on postal code or generic text returns null
        assertNull(gisService.findClaimedZone("PIN 600028"));
        assertNull(gisService.findClaimedZone("random text without ward"));
    }

    @Test
    @DisplayName("Stage 2: Multiple conflicting ward claims in input are rejected as ambiguous")
    void testAmbiguousConflictingWardClaimsRejected() {
        // Input with conflicting ward numbers must return null / rejected
        assertNull(GisService.extractSingleWardNumber("Ward 10 and Ward 180"));
        assertNull(GisService.extractSingleWardNumber("Ward 1, Ward 2"));
        assertNull(gisService.findClaimedZone("Ward 10 and Ward 180"));
        assertNull(gisService.findClaimedZone("Ward 1, Ward 2"));

        // Validation against Anna Nagar coordinates yields MISMATCH when claims are conflicting
        BigDecimal latAnna = BigDecimal.valueOf(13.0850);
        BigDecimal lngAnna = BigDecimal.valueOf(80.2100);
        WardValidationResult rAmbiguous = gisService.validateWardContainment(latAnna, lngAnna, "Ward 10 and Ward 180");
        assertEquals("MISMATCH", rAmbiguous.getStatus());
        assertEquals(0.0, rAmbiguous.getDistanceMeters());
    }

    @Test
    @DisplayName("Stage 2: Null, blank, malformed input is safely handled without exceptions")
    void testNullBlankMalformedInputHandledSafely() {
        assertNull(GisService.extractSingleWardNumber(null));
        assertNull(GisService.extractSingleWardNumber(""));
        assertNull(GisService.extractSingleWardNumber("   "));
        assertNull(GisService.extractSingleWardNumber("!@#$%^&*()"));

        assertNull(gisService.findClaimedZone(null));
        assertNull(gisService.findClaimedZone(""));
        assertNull(gisService.findClaimedZone("   "));
        assertNull(gisService.findClaimedZone("!@#$%^&*()"));

        // In validateWardContainment, null or blank wardName treats the location as VALID inside the zone
        BigDecimal latAnna = BigDecimal.valueOf(13.0850);
        BigDecimal lngAnna = BigDecimal.valueOf(80.2100);
        WardValidationResult rNull = gisService.validateWardContainment(latAnna, lngAnna, null);
        assertEquals("VALID", rNull.getStatus());

        WardValidationResult rBlank = gisService.validateWardContainment(latAnna, lngAnna, "   ");
        assertEquals("VALID", rBlank.getStatus());
    }
}
