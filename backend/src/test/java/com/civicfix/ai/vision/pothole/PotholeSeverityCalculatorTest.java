package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import com.civicfix.ai.vision.pothole.dto.PotholeMeasurement;
import com.civicfix.ai.vision.pothole.dto.PotholeSeverityResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: Pothole Severity Calculator Tests")
class PotholeSeverityCalculatorTest {

    private final PotholeSeverityCalculator calculator = new PotholeSeverityCalculator();

    @Test
    @DisplayName("Empty detections yield INCONCLUSIVE severity with zero confidence")
    void testEmptyDetectionsYieldsInconclusive() {
        PotholeSeverityResult res = calculator.calculateSeverity(List.of(), new PotholeMeasurement());
        assertEquals(PotholeSeverity.INCONCLUSIVE, res.getSeverity());
        assertEquals(0, res.getConfidence().compareTo(BigDecimal.ZERO));
        assertTrue(res.isAdvisory());
    }

    @Test
    @DisplayName("Massive road collapse (>20% image coverage) yields CRITICAL severity")
    void testCriticalSeverityOnMajorPavementFailure() {
        PotholeBox box = new PotholeBox("b1", 0.20, 0.20, 0.80, 0.70, BigDecimal.valueOf(0.95));
        PotholeMeasurement m = new PotholeMeasurement();
        m.setImageCoveragePercent(22.5);

        PotholeSeverityResult res = calculator.calculateSeverity(List.of(box), m);
        assertEquals(PotholeSeverity.CRITICAL, res.getSeverity());
        assertTrue(res.getExplanation().contains("immediate safety intervention"));
        assertTrue(res.getExplanation().contains("Visual severity is advisory only. Image coverage depends on camera distance"));
        assertTrue(res.isAdvisory());
    }

    @Test
    @DisplayName("Substantial pothole (>=8% coverage) yields HIGH severity")
    void testHighSeverityOnSubstantialPothole() {
        PotholeBox box = new PotholeBox("b1", 0.40, 0.30, 0.70, 0.60, BigDecimal.valueOf(0.85));
        PotholeMeasurement m = new PotholeMeasurement();
        m.setImageCoveragePercent(9.2);

        PotholeSeverityResult res = calculator.calculateSeverity(List.of(box), m);
        assertEquals(PotholeSeverity.HIGH, res.getSeverity());
        assertTrue(res.isAdvisory());
    }

    @Test
    @DisplayName("Moderate surface pothole yields MODERATE severity")
    void testModerateSeverity() {
        PotholeBox box = new PotholeBox("b1", 0.45, 0.40, 0.65, 0.60, BigDecimal.valueOf(0.80));
        PotholeMeasurement m = new PotholeMeasurement();
        m.setImageCoveragePercent(4.0);

        PotholeSeverityResult res = calculator.calculateSeverity(List.of(box), m);
        assertEquals(PotholeSeverity.MODERATE, res.getSeverity());
        assertTrue(res.isAdvisory());
    }

    @Test
    @DisplayName("Minor localized surface depression yields LOW severity")
    void testLowSeverity() {
        PotholeBox box = new PotholeBox("b1", 0.48, 0.48, 0.54, 0.54, BigDecimal.valueOf(0.70));
        PotholeMeasurement m = new PotholeMeasurement();
        m.setImageCoveragePercent(1.2);

        PotholeSeverityResult res = calculator.calculateSeverity(List.of(box), m);
        assertEquals(PotholeSeverity.LOW, res.getSeverity());
        assertTrue(res.isAdvisory());
    }
}
