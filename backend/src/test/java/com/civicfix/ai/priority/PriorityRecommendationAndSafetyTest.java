package com.civicfix.ai.priority;

import com.civicfix.ai.dto.AiTriageResult;
import com.civicfix.ai.triage.DeterministicTriageEngine;
import com.civicfix.complaint.ComplaintPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AI Priority Recommendation & Hazard Safety Tests")
class PriorityRecommendationAndSafetyTest {

    private DeterministicTriageEngine triageEngine;

    @BeforeEach
    void setUp() {
        triageEngine = new DeterministicTriageEngine();
    }

    @Test
    @DisplayName("Exposed live wire sparking triggers CRITICAL priority")
    void testCriticalHazardLiveWire() {
        AiTriageResult result = triageEngine.evaluate(
                "Sparking power cable on street",
                "There is an exposed wire sparking near rainwater puddle after storm.",
                null,
                null
        );

        assertEquals(ComplaintPriority.CRITICAL, result.getRecommendedPriority());
        assertTrue(result.getHazardScore().contains("Critical Hazard"));
        assertTrue(result.getExplanation().contains("CRITICAL"));
    }

    @Test
    @DisplayName("Negated hazard clause does not falsely elevate to CRITICAL")
    void testNegatedLiveWireDoesNotTriggerCritical() {
        AiTriageResult result = triageEngine.evaluate(
                "Inspection of street cable",
                "Electrician inspected the site and confirmed there is no live wire on the road.",
                null,
                null
        );

        assertNotEquals(ComplaintPriority.CRITICAL, result.getRecommendedPriority(),
                "Negated 'no live wire' must not trigger CRITICAL priority");
    }

    @Test
    @DisplayName("Negated open manhole does not falsely trigger CRITICAL")
    void testNegatedOpenManhole() {
        AiTriageResult result = triageEngine.evaluate(
                "Drain inspection report",
                "Citizen verified this is not an open manhole, just a minor curb crack.",
                null,
                null
        );

        assertNotEquals(ComplaintPriority.CRITICAL, result.getRecommendedPriority(),
                "Negated 'not an open manhole' must not trigger CRITICAL priority");
    }

    @Test
    @DisplayName("Arterial water main burst triggers HIGH priority")
    void testHighHazardWaterMainBurst() {
        AiTriageResult result = triageEngine.evaluate(
                "Arterial pipeline failure",
                "Severe water main burst flooding transit bus lane.",
                null,
                null
        );

        assertEquals(ComplaintPriority.HIGH, result.getRecommendedPriority());
        assertTrue(result.getExplanation().contains("HIGH"));
    }

    @Test
    @DisplayName("Tamil fallen tree trigger recommends HIGH priority")
    void testTamilHighHazardFallenTree() {
        AiTriageResult result = triageEngine.evaluate(
                "சாலையில் மரம் விழுந்தது",
                "பெரிய மரக்கிளை விழுந்தது போக்குவரத்து முற்றிலும் பாதிக்கப்பட்டுள்ளது.",
                null,
                null
        );

        assertEquals(ComplaintPriority.HIGH, result.getRecommendedPriority());
    }

    @Test
    @DisplayName("Tamil severed power wire recommends CRITICAL priority")
    void testTamilCriticalHazardSnappedWire() {
        AiTriageResult result = triageEngine.evaluate(
                "அறுந்த மின்கம்பி ஆபத்து",
                "மின்கசிவு ஏற்பட்டு தீப்பொறி பறக்கிறது பொதுமக்கள் நடமாட முடியவில்லை.",
                null,
                null
        );

        assertEquals(ComplaintPriority.CRITICAL, result.getRecommendedPriority());
    }
}
