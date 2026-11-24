package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.pothole.dto.CalibrationRequest;
import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import com.civicfix.ai.vision.pothole.dto.PotholeMeasurement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5: Pothole Measurement & Calibration Engine Tests")
class PotholeMeasurementEngineTest {

    private final PotholeMeasurementEngine engine = new PotholeMeasurementEngine();

    @Test
    @DisplayName("Computes uncalibrated image-space metrics without fabricating real-world dimensions")
    void testUncalibratedMetricsSafety() {
        PotholeBox box = new PotholeBox("box-1", 0.20, 0.20, 0.40, 0.50, BigDecimal.valueOf(0.85));
        box.setPixelWidth(300);
        box.setPixelHeight(200);
        box.setPixelArea(60000);

        PotholeMeasurement m = engine.computeImageSpaceMetrics(List.of(box), 1000, 1000);

        assertNotNull(m);
        assertEquals(60000L, m.getTotalPotholeAreaPixels());
        assertEquals(6.0, m.getImageCoveragePercent(), 0.01);
        assertFalse(m.isCalibrated());
        assertNull(m.getMetricWidthCm(), "Real-world width must be null when uncalibrated");
        assertNull(m.getMetricLengthCm(), "Real-world length must be null when uncalibrated");
        assertNull(m.getMetricAreaSqm(), "Real-world area must be null when uncalibrated");
        assertNull(m.getEstimatedDepthCm(), "Depth must be null for monocular RGB image");
        assertEquals(PotholeMeasurementEngine.UNCALIBRATED_MESSAGE, m.getStatusMessage());
    }

    @Test
    @DisplayName("Applies valid in-frame reference target calibration and derives metric dimensions")
    void testValidReferenceTargetCalibration() {
        PotholeBox pothole = new PotholeBox("pothole-1", 0.40, 0.30, 0.60, 0.70, BigDecimal.valueOf(0.90));
        pothole.setPixelWidth(400); // 400 px
        pothole.setPixelHeight(200); // 200 px
        pothole.setPixelArea(80000);

        // Reference: A4 sheet (29.7 cm long side) placed adjacent to pothole on same road plane
        // ymin: 0.45, xmin: 0.10, ymax: 0.65, xmax: 0.15 -> height = 200px
        PotholeBox refBox = new PotholeBox("ref-a4", 0.45, 0.10, 0.65, 0.15, BigDecimal.valueOf(0.99));
        // height in pixels = (0.65 - 0.45) * 1000 = 200 px
        CalibrationRequest request = new CalibrationRequest(
                "A4_SHEET",
                BigDecimal.valueOf(29.7),
                refBox
        );

        PotholeMeasurement calibrated = engine.applyCalibration(List.of(pothole), 1000, 1000, request);

        assertTrue(calibrated.isCalibrated());
        assertTrue(calibrated.isProvisional(), "Calibrated measurement must be explicitly flagged as provisional");
        assertTrue(calibrated.getStatusMessage().contains("Provisional measurement"), "Status message must indicate provisional nature");
        assertEquals("A4_SHEET", calibrated.getCalibrationMethod());
        assertNotNull(calibrated.getMetricWidthCm());
        assertNotNull(calibrated.getMetricLengthCm());
        assertNotNull(calibrated.getMetricAreaSqm());
        assertNull(calibrated.getEstimatedDepthCm(), "Depth must remain null even when 2D calibrated");

        // 29.7 cm / 200 px = 0.1485 cm/px
        // Width: 400 * 0.1485 = ~59.40 cm
        assertEquals(BigDecimal.valueOf(59.40).setScale(2), calibrated.getMetricWidthCm());
    }

    @Test
    @DisplayName("Rejects reference targets on different perspective depth planes (deltaY > 0.25)")
    void testRejectsNonCoplanarPerspectiveTarget() {
        // Pothole in distant background (y in [0.15, 0.35], center = 0.25)
        PotholeBox pothole = new PotholeBox("pothole-1", 0.15, 0.30, 0.35, 0.70, BigDecimal.valueOf(0.90));
        pothole.setPixelWidth(400);
        pothole.setPixelHeight(200);
        pothole.setPixelArea(80000);

        // Reference card placed in foreground near camera (y in [0.70, 0.90], center = 0.80)
        // deltaY = 0.80 - 0.25 = 0.55 > 0.25
        PotholeBox refBox = new PotholeBox("ref-card", 0.70, 0.10, 0.90, 0.20, BigDecimal.valueOf(0.99));
        CalibrationRequest request = new CalibrationRequest("CALIBRATION_CARD", BigDecimal.valueOf(20.0), refBox);

        PotholeMeasurement result = engine.applyCalibration(List.of(pothole), 1000, 1000, request);

        assertFalse(result.isCalibrated());
        assertNull(result.getMetricWidthCm());
        assertNull(result.getMetricLengthCm());
        assertNull(result.getMetricAreaSqm());
        assertTrue(result.getStatusMessage().contains("different perspective depth planes"),
                "Must reject non-coplanar calibration due to perspective scaling error");
    }

    @Test
    @DisplayName("Rejects microscopic reference targets (<8 pixels) to prevent astronomical measurement error")
    void testRejectsMicroscopicCalibrationTarget() {
        PotholeBox pothole = new PotholeBox("pothole-1", 0.30, 0.30, 0.60, 0.70, BigDecimal.valueOf(0.90));
        // Reference target too tiny: span only 2 pixels in 1000px image
        PotholeBox tinyRef = new PotholeBox("tiny-ref", 0.40, 0.10, 0.402, 0.102, BigDecimal.valueOf(0.99));
        CalibrationRequest request = new CalibrationRequest("COIN", BigDecimal.valueOf(2.5), tinyRef);

        PotholeMeasurement result = engine.applyCalibration(List.of(pothole), 1000, 1000, request);

        assertFalse(result.isCalibrated());
        assertTrue(result.getStatusMessage().toLowerCase().contains("too small in frame"));
    }
}
