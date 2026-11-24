package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.pothole.dto.CalibrationRequest;
import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import com.civicfix.ai.vision.pothole.dto.PotholeMeasurement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pothole size and measurement engine.
 * Strictly enforces that real-world dimensions (cm / m²) are provided ONLY when
 * a validated calibration reference exists, rejecting uncalibrated extrapolation.
 */
@Component
public class PotholeMeasurementEngine {

    public static final String UNCALIBRATED_MESSAGE =
            "Real-world dimensions unavailable — valid image calibration is required.";
    public static final String DEPTH_UNSUPPORTED_MESSAGE =
            "Monocular RGB depth estimation unsupported without 3D stereoscopic reference.";
    public static final double MAX_COPLANAR_DELTA_Y = 0.25;

    /**
     * Compute uncalibrated image-space metrics.
     */
    public PotholeMeasurement computeImageSpaceMetrics(List<PotholeBox> boxes, int imageWidth, int imageHeight) {
        PotholeMeasurement m = new PotholeMeasurement();
        m.setImageWidth(imageWidth);
        m.setImageHeight(imageHeight);
        m.setCalibrated(false);
        m.setProvisional(false);
        m.setCalibrationMethod(null);
        m.setMetricWidthCm(null);
        m.setMetricLengthCm(null);
        m.setMetricAreaSqm(null);
        m.setEstimatedDepthCm(null);
        m.setStatusMessage(UNCALIBRATED_MESSAGE);

        if (boxes == null || boxes.isEmpty()) {
            m.setTotalPotholeAreaPixels(0L);
            m.setImageCoveragePercent(0.0);
            return m;
        }

        long totalPixelArea = 0L;
        for (PotholeBox box : boxes) {
            if (box.getPixelArea() != null) {
                totalPixelArea += box.getPixelArea();
            }
        }

        long totalImagePixels = (long) imageWidth * imageHeight;
        double coverage = totalImagePixels > 0
                ? ((double) totalPixelArea / totalImagePixels) * 100.0
                : 0.0;

        m.setTotalPotholeAreaPixels(totalPixelArea);
        m.setImageCoveragePercent(Math.round(coverage * 10.0) / 10.0);

        return m;
    }

    /**
     * Compute calibrated real-world metrics from a validated in-frame reference target.
     * Enforces planar coplanarity and spatial proximity checks to reject perspective scaling errors.
     */
    public PotholeMeasurement applyCalibration(
            List<PotholeBox> boxes,
            int imageWidth,
            int imageHeight,
            CalibrationRequest calibration) {

        PotholeMeasurement m = computeImageSpaceMetrics(boxes, imageWidth, imageHeight);

        if (calibration == null || calibration.getKnownDimensionCm() == null || calibration.getReferenceBox() == null) {
            m.setStatusMessage("Real-world dimensions unavailable — valid image calibration is required. Missing required reference dimension or bounding box.");
            return m;
        }

        if (boxes == null || boxes.isEmpty()) {
            m.setStatusMessage("Real-world dimensions unavailable — valid image calibration is required. No detected potholes available to calibrate.");
            return m;
        }

        PotholeBox refBox = calibration.getReferenceBox();
        double refXmin = refBox.getXmin();
        double refYmin = refBox.getYmin();
        double refXmax = refBox.getXmax();
        double refYmax = refBox.getYmax();

        // Validate reference box geometry
        if (refXmin < 0.0 || refYmin < 0.0 || refXmax > 1.0 || refYmax > 1.0 || refXmin >= refXmax || refYmin >= refYmax) {
            m.setStatusMessage("Real-world dimensions unavailable — valid image calibration is required. Reference bounding box has invalid or out-of-bounds coordinates.");
            return m;
        }

        double refWidthPixels = (refXmax - refXmin) * imageWidth;
        double refHeightPixels = (refYmax - refYmin) * imageHeight;
        double maxRefDimensionPixels = Math.max(refWidthPixels, refHeightPixels);

        if (maxRefDimensionPixels < 8.0) {
            m.setStatusMessage("Real-world dimensions unavailable — valid image calibration is required. Reference object too small in frame (<8 pixels) for accurate scaling.");
            return m;
        }

        // Identify primary (largest pixel area) pothole to verify perspective coplanarity
        PotholeBox primaryPothole = null;
        long maxPotholeArea = -1;
        int maxPotholeWidthPx = 0;
        int maxPotholeHeightPx = 0;
        long totalAreaPx = 0;

        for (PotholeBox box : boxes) {
            int w = box.getPixelWidth() != null ? box.getPixelWidth() : (int) Math.round((box.getXmax() - box.getXmin()) * imageWidth);
            int h = box.getPixelHeight() != null ? box.getPixelHeight() : (int) Math.round((box.getYmax() - box.getYmin()) * imageHeight);
            long area = (long) w * h;
            if (area > maxPotholeArea) {
                maxPotholeArea = area;
                primaryPothole = box;
            }
            if (w > maxPotholeWidthPx) maxPotholeWidthPx = w;
            if (h > maxPotholeHeightPx) maxPotholeHeightPx = h;
            totalAreaPx += area;
        }

        // Calculate vertical perspective distance (Δy) between reference target and primary pothole
        if (primaryPothole != null) {
            double refCenterY = (refYmin + refYmax) / 2.0;
            double potholeCenterY = (primaryPothole.getYmin() + primaryPothole.getYmax()) / 2.0;
            double deltaY = Math.abs(refCenterY - potholeCenterY);

            if (deltaY > MAX_COPLANAR_DELTA_Y) {
                m.setCalibrated(false);
                m.setProvisional(false);
                m.setStatusMessage(String.format(
                        "Real-world dimensions unavailable — valid image calibration is required. Reference target and pothole appear on different perspective depth planes (vertical difference Δy = %.2f > %.2f). Target must be placed adjacent to the defect on the same road plane.",
                        deltaY, MAX_COPLANAR_DELTA_Y));
                return m;
            }
        }

        // Scale factor: centimeters per pixel
        BigDecimal knownCm = calibration.getKnownDimensionCm();
        BigDecimal refPixelsBd = BigDecimal.valueOf(maxRefDimensionPixels);
        BigDecimal cmPerPixel = knownCm.divide(refPixelsBd, 6, RoundingMode.HALF_UP);

        BigDecimal metricWidth = BigDecimal.valueOf(maxPotholeWidthPx).multiply(cmPerPixel).setScale(2, RoundingMode.HALF_UP);
        BigDecimal metricLength = BigDecimal.valueOf(maxPotholeHeightPx).multiply(cmPerPixel).setScale(2, RoundingMode.HALF_UP);

        // Approximate area in square meters: totalAreaPx * (cmPerPixel)^2 / 10000
        BigDecimal cm2PerPixel2 = cmPerPixel.multiply(cmPerPixel);
        BigDecimal areaCm2 = BigDecimal.valueOf(totalAreaPx).multiply(cm2PerPixel2);
        BigDecimal areaM2 = areaCm2.divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);

        m.setCalibrated(true);
        m.setProvisional(true);
        m.setCalibrationMethod(calibration.getReferenceType());
        m.setMetricWidthCm(metricWidth);
        m.setMetricLengthCm(metricLength);
        m.setMetricAreaSqm(areaM2);
        m.setEstimatedDepthCm(null); // Strictly null for monocular RGB
        m.setStatusMessage(String.format("Provisional measurement — calibration and perspective limitations may affect accuracy. Calibrated via %s (ratio: %.4f cm/px). %s",
                calibration.getReferenceType(), cmPerPixel.doubleValue(), DEPTH_UNSUPPORTED_MESSAGE));

        return m;
    }
}
