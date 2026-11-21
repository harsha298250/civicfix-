package com.civicfix.ai.duplicate;

import com.civicfix.ai.dto.DuplicateCandidate;
import com.civicfix.ai.triage.TextSimilarityUtils;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.complaint.ComplaintStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cross-Lingual Duplicate Detection & Co-Location Tests")
class CrossLingualDuplicateDetectionTest {

    private ComplaintRepository complaintRepository;
    private DuplicateDetectionEngine duplicateEngine;
    private final AtomicReference<List<Complaint>> spatialReturn = new AtomicReference<>(Collections.emptyList());

    @BeforeEach
    void setUp() {
        spatialReturn.set(Collections.emptyList());

        complaintRepository = (ComplaintRepository) Proxy.newProxyInstance(
                ComplaintRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintRepository.class},
                (proxy, method, args) -> {
                    if ("findActiveComplaintsInSpatialBounds".equals(method.getName())) {
                        return spatialReturn.get();
                    }
                    return null;
                }
        );

        duplicateEngine = new DuplicateDetectionEngine(complaintRepository);
    }

    @Test
    @DisplayName("TextSimilarityUtils identifies shared concept between English water pipe burst and Tamil equivalent")
    void testSharedConceptWaterPipeBurst() {
        String englishText = "Drinking water pipe burst leak near school junction";
        String tamilText = "பள்ளிக்கூடம் அருகே குடிநீர் குழாய் உடைந்து தண்ணீர் வீணாகிறது";

        assertTrue(TextSimilarityUtils.hasSharedConcept(englishText, tamilText),
                "Should recognize cross-lingual concept match for water pipe burst");
    }

    @Test
    @DisplayName("TextSimilarityUtils identifies shared concept for road pothole between English and Tamil")
    void testSharedConceptRoadPothole() {
        String englishText = "Dangerous pothole crater on main avenue";
        String tamilText = "சாலையில் பெரிய குழி மற்றும் பள்ளம் உள்ளது";

        assertTrue(TextSimilarityUtils.hasSharedConcept(englishText, tamilText),
                "Should recognize cross-lingual concept match for road pothole");
    }

    @Test
    @DisplayName("Completely unrelated defects yield no shared concept")
    void testNoSharedConceptForDifferentCategories() {
        String textA = "Broken streetlight at corner";
        String textB = "குப்பை தேக்கம் துர்நாற்றம்"; // Garbage dump

        assertFalse(TextSimilarityUtils.hasSharedConcept(textA, textB));
    }

    @Test
    @DisplayName("Sub-50m co-located complaint in Tamil detected as duplicate of English complaint")
    void testCoLocatedBilingualDuplicateWithin50m() {
        UUID existingId = UUID.randomUUID();
        Complaint existingTamilComplaint = new Complaint();
        existingTamilComplaint.setId(existingId);
        existingTamilComplaint.setReferenceId("CF-2026-99001");
        existingTamilComplaint.setTitle("குடிநீர் குழாய் உடைப்பு");
        existingTamilComplaint.setDescription("தண்ணீர் சாலையில் பெருக்கெடுத்து ஓடுகிறது.");
        existingTamilComplaint.setCategory("WATER_SEWERAGE");
        existingTamilComplaint.setStatus(ComplaintStatus.SUBMITTED);
        // Location: Anna Nagar (13.08500, 80.21000)
        existingTamilComplaint.setLatitude(BigDecimal.valueOf(13.08500));
        existingTamilComplaint.setLongitude(BigDecimal.valueOf(80.21000));

        spatialReturn.set(List.of(existingTamilComplaint));

        // Query complaint in English: ~22 meters away (13.08520, 80.21000)
        List<DuplicateCandidate> candidates = duplicateEngine.findCandidates(
                UUID.randomUUID(),
                BigDecimal.valueOf(13.08520),
                BigDecimal.valueOf(80.21000),
                "Zone 08, Anna Nagar",
                "WATER_SEWERAGE",
                "Pipe burst water leak",
                "Fresh drinking water leaking all over roadway"
        );

        assertFalse(candidates.isEmpty(), "Should detect co-located Tamil complaint as duplicate candidate");
        DuplicateCandidate match = candidates.get(0);
        assertEquals("CF-2026-99001", match.getReferenceId());
        assertTrue(match.getDistanceMeters() <= 30.0, "Distance should be ~22m");
        assertTrue(match.getSimilarityScore() >= 0.70, "Cross-lingual concept match should yield >= 70% score");
        assertTrue(match.getExplanation().contains("cross-lingual concept match"));
    }
}
