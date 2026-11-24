package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: Pothole Detection Validator & IoU Deduplication Tests")
class PotholeDetectionValidatorTest {

    private final PotholeDetectionValidator validator = new PotholeDetectionValidator();

    @Test
    @DisplayName("Normalizes 0-1000 integer coordinates into [0.0, 1.0] and rectifies swapped bounds")
    void testCoordinateNormalizationAndSwappedBounds() {
        PotholeBox raw = new PotholeBox("raw-1", 700.0, 600.0, 300.0, 200.0, BigDecimal.valueOf(0.85));
        List<PotholeBox> result = validator.validateAndDeduplicate(List.of(raw), 1000, 1000);

        assertEquals(1, result.size());
        PotholeBox sanitized = result.get(0);
        assertEquals(0.30, sanitized.getYmin(), 0.001);
        assertEquals(0.20, sanitized.getXmin(), 0.001);
        assertEquals(0.70, sanitized.getYmax(), 0.001);
        assertEquals(0.60, sanitized.getXmax(), 0.001);
        assertEquals(400, sanitized.getPixelWidth());
        assertEquals(400, sanitized.getPixelHeight());
    }

    @Test
    @DisplayName("Clamps out-of-range bounding coordinates into [0.0, 1.0]")
    void testClampingOutOfBounds() {
        PotholeBox raw = new PotholeBox("raw-2", -0.15, -0.05, 1.25, 1.05, BigDecimal.valueOf(1.50));
        List<PotholeBox> result = validator.validateAndDeduplicate(List.of(raw), 800, 600);

        assertEquals(1, result.size());
        PotholeBox clamped = result.get(0);
        assertEquals(0.0, clamped.getYmin(), 0.001);
        assertEquals(0.0, clamped.getXmin(), 0.001);
        assertEquals(1.0, clamped.getYmax(), 0.001);
        assertEquals(1.0, clamped.getXmax(), 0.001);
        assertEquals(0, clamped.getConfidence().compareTo(BigDecimal.valueOf(1.00)));
    }

    @Test
    @DisplayName("Filters degenerate or microscopic boxes with span less than 1%")
    void testFiltersMicroscopicBoxes() {
        PotholeBox microscopic = new PotholeBox("tiny", 0.50, 0.50, 0.505, 0.505, BigDecimal.valueOf(0.90));
        List<PotholeBox> result = validator.validateAndDeduplicate(List.of(microscopic), 1000, 1000);

        assertTrue(result.isEmpty(), "Microscopic candidate box should be discarded");
    }

    @Test
    @DisplayName("Applies Non-Maximum Suppression (IoU) to deduplicate overlapping detections")
    void testNmsDeduplication() {
        // Box A: primary detection with higher confidence
        PotholeBox boxA = new PotholeBox("box-a", 0.30, 0.30, 0.60, 0.60, BigDecimal.valueOf(0.92));
        // Box B: almost identical duplicate with lower confidence
        PotholeBox boxB = new PotholeBox("box-b", 0.32, 0.31, 0.61, 0.59, BigDecimal.valueOf(0.74));
        // Box C: distinct pothole in different location
        PotholeBox boxC = new PotholeBox("box-c", 0.70, 0.10, 0.85, 0.25, BigDecimal.valueOf(0.80));

        List<PotholeBox> result = validator.validateAndDeduplicate(List.of(boxA, boxB, boxC), 1000, 1000);

        assertEquals(2, result.size(), "Should suppress overlapping duplicate boxB");
        assertTrue(result.stream().anyMatch(b -> b.getConfidence().compareTo(BigDecimal.valueOf(0.92)) == 0));
        assertTrue(result.stream().anyMatch(b -> b.getConfidence().compareTo(BigDecimal.valueOf(0.80)) == 0));
    }
}
