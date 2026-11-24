package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Geometric validator and sanitization engine for candidate pothole bounding boxes.
 */
@Component
public class PotholeDetectionValidator {

    private static final Logger log = LoggerFactory.getLogger(PotholeDetectionValidator.class);
    private static final double MIN_BOX_SPAN = 0.01; // Minimum 1% of frame dimension
    private static final double IOU_DEDUPLICATION_THRESHOLD = 0.65;

    /**
     * Sanitizes, clamps, filters degenerate boxes, and performs IoU deduplication.
     */
    public List<PotholeBox> validateAndDeduplicate(List<PotholeBox> rawBoxes, int imageWidth, int imageHeight) {
        if (rawBoxes == null || rawBoxes.isEmpty()) {
            return new ArrayList<>();
        }

        List<PotholeBox> validBoxes = new ArrayList<>();
        int boxSeq = 1;

        for (PotholeBox raw : rawBoxes) {
            if (raw == null) continue;

            double ymin = raw.getYmin();
            double xmin = raw.getXmin();
            double ymax = raw.getYmax();
            double xmax = raw.getXmax();

            // Handle models that return coordinates on a 0-1000 integer scale (e.g. 350, 720)
            if (ymin > 2.0 || xmin > 2.0 || ymax > 2.0 || xmax > 2.0) {
                ymin = ymin / 1000.0;
                xmin = xmin / 1000.0;
                ymax = ymax / 1000.0;
                xmax = xmax / 1000.0;
            }

            // Invert if coordinates are swapped
            if (ymin > ymax) {
                double temp = ymin;
                ymin = ymax;
                ymax = temp;
            }
            if (xmin > xmax) {
                double temp = xmin;
                xmin = xmax;
                xmax = temp;
            }

            // Clamp into [0.0, 1.0]
            ymin = Math.max(0.0, Math.min(1.0, ymin));
            xmin = Math.max(0.0, Math.min(1.0, xmin));
            ymax = Math.max(0.0, Math.min(1.0, ymax));
            xmax = Math.max(0.0, Math.min(1.0, xmax));

            double height = ymax - ymin;
            double width = xmax - xmin;

            // Reject degenerate or excessively microscopic bounding boxes
            if (height < MIN_BOX_SPAN || width < MIN_BOX_SPAN) {
                log.debug("Discarding degenerate pothole candidate box: span [{}, {}]", width, height);
                continue;
            }

            // Clamp confidence into [0.0, 1.0]
            BigDecimal conf = raw.getConfidence();
            if (conf == null) {
                conf = BigDecimal.valueOf(0.50);
            } else {
                double cVal = Math.max(0.0, Math.min(1.0, conf.doubleValue()));
                conf = BigDecimal.valueOf(cVal).setScale(2, RoundingMode.HALF_UP);
            }

            // Compute image-space pixel metrics
            int pWidth = (int) Math.round(width * imageWidth);
            int pHeight = (int) Math.round(height * imageHeight);
            int pArea = pWidth * pHeight;
            double relArea = (width * height) * 100.0;

            PotholeBox sanitized = new PotholeBox(
                    "pothole-" + (boxSeq++),
                    ymin, xmin, ymax, xmax, conf
            );
            sanitized.setPixelWidth(pWidth);
            sanitized.setPixelHeight(pHeight);
            sanitized.setPixelArea(pArea);
            sanitized.setRelativeAreaPercent(Math.round(relArea * 10.0) / 10.0);

            validBoxes.add(sanitized);
        }

        // Apply Non-Maximum Suppression (IoU deduplication)
        return applyNms(validBoxes, IOU_DEDUPLICATION_THRESHOLD);
    }

    private List<PotholeBox> applyNms(List<PotholeBox> boxes, double iouThreshold) {
        if (boxes.size() <= 1) {
            return boxes;
        }

        // Sort descending by confidence
        boxes.sort(Comparator.comparing(PotholeBox::getConfidence).reversed());

        List<PotholeBox> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[boxes.size()];

        for (int i = 0; i < boxes.size(); i++) {
            if (suppressed[i]) continue;
            PotholeBox current = boxes.get(i);
            kept.add(current);

            for (int j = i + 1; j < boxes.size(); j++) {
                if (suppressed[j]) continue;
                if (computeIoU(current, boxes.get(j)) > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }

        return kept;
    }

    public double computeIoU(PotholeBox a, PotholeBox b) {
        double interYmin = Math.max(a.getYmin(), b.getYmin());
        double interXmin = Math.max(a.getXmin(), b.getXmin());
        double interYmax = Math.min(a.getYmax(), b.getYmax());
        double interXmax = Math.min(a.getXmax(), b.getXmax());

        double interWidth = Math.max(0.0, interXmax - interXmin);
        double interHeight = Math.max(0.0, interYmax - interYmin);
        double interArea = interWidth * interHeight;

        double areaA = (a.getYmax() - a.getYmin()) * (a.getXmax() - a.getXmin());
        double areaB = (b.getYmax() - b.getYmin()) * (b.getXmax() - b.getXmin());

        double unionArea = areaA + areaB - interArea;
        if (unionArea <= 0.0) return 0.0;

        return interArea / unionArea;
    }
}
