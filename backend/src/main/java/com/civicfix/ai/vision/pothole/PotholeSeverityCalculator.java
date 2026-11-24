package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import com.civicfix.ai.vision.pothole.dto.PotholeMeasurement;
import com.civicfix.ai.vision.pothole.dto.PotholeSeverityResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes an explainable visual severity assessment for detected road potholes.
 * Adheres strictly to the advisory principle: outputs guidance for human officers
 * without ever altering official complaint priority or workflow status.
 */
@Component
public class PotholeSeverityCalculator {

    public PotholeSeverityResult calculateSeverity(List<PotholeBox> boxes, PotholeMeasurement measurement) {
        if (boxes == null || boxes.isEmpty()) {
            return new PotholeSeverityResult(
                    PotholeSeverity.INCONCLUSIVE,
                    BigDecimal.valueOf(0.00),
                    List.of("No pothole bounding boxes detected in uploaded photograph"),
                    "Visual inspection did not identify high-confidence pothole contours."
            );
        }

        int count = boxes.size();
        double coverage = measurement != null && measurement.getImageCoveragePercent() != null
                ? measurement.getImageCoveragePercent()
                : 0.0;

        // Average confidence
        double sumConf = 0.0;
        for (PotholeBox b : boxes) {
            sumConf += (b.getConfidence() != null ? b.getConfidence().doubleValue() : 0.50);
        }
        double avgConf = sumConf / count;
        BigDecimal finalConf = BigDecimal.valueOf(avgConf).setScale(2, RoundingMode.HALF_UP);

        List<String> factors = new ArrayList<>();
        factors.add(String.format("Detected %d candidate pothole region(s)", count));
        factors.add(String.format("Pothole visual frame coverage: %.1f%% of image area", coverage));
        factors.add(String.format("Mean detection confidence: %.2f", avgConf));
        factors.add("Visual frame coverage is camera-distance dependent and requires on-site verification");

        PotholeSeverity severity;
        String baseAssessment;

        if (coverage >= 20.0 || (count >= 4 && coverage >= 12.0)) {
            severity = PotholeSeverity.CRITICAL;
            baseAssessment = "Extensive multi-point road surface collapse detected across vehicle travel lane; immediate safety intervention advisory.";
            factors.add("Cluster density indicates widespread pavement failure");
        } else if (coverage >= 8.0 || count >= 3) {
            severity = PotholeSeverity.HIGH;
            baseAssessment = "Substantial asphalt cavitation observed; high probability of vehicular tire/axle impact hazard.";
            factors.add("Significant visual dimension creates immediate road hazard");
        } else if (coverage >= 2.5 || count >= 2) {
            severity = PotholeSeverity.MODERATE;
            baseAssessment = "Moderate surface pothole identified; scheduled patching required to prevent rapid seasonal degradation.";
            factors.add("Moderate pavement surface opening");
        } else if (coverage > 0.0) {
            severity = PotholeSeverity.LOW;
            baseAssessment = "Early-stage localized surface depression detected; standard routine maintenance advisory.";
            factors.add("Minor localized surface wear");
        } else {
            severity = PotholeSeverity.INCONCLUSIVE;
            baseAssessment = "Inconclusive visual evidence; officer manual review advised.";
        }

        String explanation = baseAssessment + " Visual severity is advisory only. Image coverage depends on camera distance, angle, and perspective. Physical road dimensions and actual hazard severity require field verification.";

        return new PotholeSeverityResult(severity, finalConf, factors, explanation);
    }
}
