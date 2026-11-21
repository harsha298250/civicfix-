package com.civicfix.ai;

import com.civicfix.ai.dto.AiTriageResult;
import com.civicfix.ai.dto.SuggestCategoryResponse;
import com.civicfix.ai.duplicate.DuplicateDetectionEngine;
import com.civicfix.ai.triage.CivicLexicon;
import com.civicfix.ai.triage.DeterministicTriageEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AI Complaint Classification & Typo Tolerance Tests")
class ClassificationAndTypoToleranceTest {

    private DeterministicTriageEngine triageEngine;
    private AiTriageService triageService;

    @BeforeEach
    void setUp() {
        triageEngine = new DeterministicTriageEngine();
        triageService = new AiTriageService(triageEngine, new DuplicateDetectionEngine(null));
    }

    @Test
    @DisplayName("English complaint with exact road keywords classifies as ROADS")
    void testExactEnglishRoadClassification() {
        AiTriageResult result = triageEngine.evaluate(
                "Large pothole near signal",
                "Asphalt damaged with deep crater on primary lane.",
                null,
                null
        );

        assertEquals(CivicLexicon.CAT_ROADS, result.getDetectedCategory());
        assertTrue(result.getConfidence().doubleValue() >= 65.0);
        assertTrue(result.getExplanation().contains("ROADS"));
    }

    @Test
    @DisplayName("Tamil complaint with authentic Unicode classifies as ROADS")
    void testAuthenticTamilRoadClassification() {
        AiTriageResult result = triageEngine.evaluate(
                "சாலையில் பெரிய பள்ளம்",
                "வாகனங்கள் செல்ல முடியாத அளவுக்கு தார் சாலை சேதமடைந்துள்ளது.",
                null,
                null
        );

        assertEquals(CivicLexicon.CAT_ROADS, result.getDetectedCategory());
        assertTrue(result.getExplanation().contains("ROADS"));
    }

    @Test
    @DisplayName("Typo tolerance: 'pothol' matches 'pothole' within edit distance 1")
    void testTypoTolerancePothole() {
        AiTriageResult result = triageEngine.evaluate(
                "Dangerous pothol on road",
                "Deep crater causing traffic slowdown.",
                null,
                null
        );

        assertEquals(CivicLexicon.CAT_ROADS, result.getDetectedCategory());
        assertTrue(result.getExplanation().contains("ROADS"));
    }

    @Test
    @DisplayName("Typo tolerance: 'garbag' matches 'garbage' within edit distance 1")
    void testTypoToleranceGarbage() {
        AiTriageResult result = triageEngine.evaluate(
                "Uncollected garbag dump",
                "Severe foul stench from overflowing dustbin.",
                null,
                null
        );

        assertEquals(CivicLexicon.CAT_GARBAGE, result.getDetectedCategory());
    }

    @Test
    @DisplayName("Tamil complaint for drinking water pipe classifies as WATER_SEWERAGE")
    void testTamilWaterSewerageClassification() {
        AiTriageResult result = triageEngine.evaluate(
                "குடிநீர் குழாய் உடைப்பு",
                "குழாயிலிருந்து தண்ணீர் அதிக அளவில் வெளியேறி வீணாகிறது.",
                null,
                null
        );

        assertEquals(CivicLexicon.CAT_WATER, result.getDetectedCategory());
    }

    @Test
    @DisplayName("Tamil complaint for broken street light classifies as STREET_LIGHT")
    void testTamilStreetLightClassification() {
        AiTriageResult result = triageEngine.evaluate(
                "தெருவிளக்கு பழுது",
                "இரவில் தெரு விளக்கு எரியவில்லை, பகுதி முழுவதும் இருட்டாக உள்ளது.",
                null,
                null
        );

        assertEquals(CivicLexicon.CAT_STREET_LIGHT, result.getDetectedCategory());
    }

    @Test
    @DisplayName("AiTriageService.suggestCategory provides fast advisory classification")
    void testLiveSuggestCategoryEndpoint() {
        SuggestCategoryResponse resp = triageService.suggestCategory("Clogged monsoon drain overflowing onto road", "en");

        assertNotNull(resp);
        assertEquals(CivicLexicon.CAT_DRAINAGE, resp.getCategory());
        assertTrue(resp.getConfidence() >= 60.0);
        assertNotNull(resp.getExplanation());
    }
}
