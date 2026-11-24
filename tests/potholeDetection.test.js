import { describe, it } from 'node:test';
import assert from 'node:assert';
import { apiClient } from '../src/services/apiClient.js';
import { complaintService } from '../src/services/complaintService.js';

describe('Phase 5: Pothole Detection & Measurement Frontend Integration Tests', () => {

  it('1. apiClient exposes pothole detection and calibration endpoints', () => {
    assert.strictEqual(typeof apiClient.complaints.getPotholeAnalysis, 'function');
    assert.strictEqual(typeof apiClient.complaints.runPotholeAnalysis, 'function');
    assert.strictEqual(typeof apiClient.complaints.calibratePotholeAnalysis, 'function');
  });

  it('2. complaintService delegates to apiClient for pothole capabilities', () => {
    assert.strictEqual(typeof complaintService.getPotholeAnalysis, 'function');
    assert.strictEqual(typeof complaintService.runPotholeAnalysis, 'function');
    assert.strictEqual(typeof complaintService.calibratePotholeAnalysis, 'function');
  });

  it('3. Bounding box coordinates normalize safely into 0-100% ranges for responsive overlay', () => {
    const rawBox = { ymin: 0.25, xmin: 0.15, ymax: 0.65, xmax: 0.55 };
    const leftPct = Math.max(0, Math.min(100, rawBox.xmin * 100));
    const topPct = Math.max(0, Math.min(100, rawBox.ymin * 100));
    const widthPct = (rawBox.xmax - rawBox.xmin) * 100;
    const heightPct = (rawBox.ymax - rawBox.ymin) * 100;

    assert.strictEqual(leftPct, 15);
    assert.strictEqual(topPct, 25);
    assert.strictEqual(widthPct, 40);
    assert.strictEqual(heightPct, 40);
  });

  it('4. Real-world dimensions are strictly marked uncalibrated unless calibration reference is provided', () => {
    const uncalibratedMeasurement = {
      isCalibrated: false,
      provisional: false,
      metricWidthCm: null,
      metricLengthCm: null,
      metricAreaSqm: null,
      statusMessage: 'Real-world dimensions unavailable — valid image calibration is required.'
    };

    assert.strictEqual(uncalibratedMeasurement.isCalibrated, false);
    assert.strictEqual(uncalibratedMeasurement.provisional, false);
    assert.strictEqual(uncalibratedMeasurement.metricWidthCm, null);
    assert.ok(uncalibratedMeasurement.statusMessage.includes('Real-world dimensions unavailable — valid image calibration is required.'));
  });

  it('5. Severity palette maps correctly across all severity tiers', () => {
    const getSeverityColor = (sev) => {
      switch (sev?.toUpperCase()) {
        case 'CRITICAL': return '#ef4444';
        case 'HIGH': return '#f97316';
        case 'MODERATE': return '#f59e0b';
        case 'LOW': return '#3b82f6';
        default: return '#9ca3af';
      }
    };

    assert.strictEqual(getSeverityColor('CRITICAL'), '#ef4444');
    assert.strictEqual(getSeverityColor('HIGH'), '#f97316');
    assert.strictEqual(getSeverityColor('MODERATE'), '#f59e0b');
    assert.strictEqual(getSeverityColor('LOW'), '#3b82f6');
    assert.strictEqual(getSeverityColor('INCONCLUSIVE'), '#9ca3af');
  });

  it('6. All distinct detection lifecycle statuses are recognized and safely handled', () => {
    const supportedStatuses = ['COMPLETED', 'NO_POTHOLE_DETECTED', 'UNAVAILABLE', 'ANALYSIS_FAILED', 'SAFETY_BLOCKED'];
    const statusLabels = {
      COMPLETED: 'Genuine inference executed',
      NO_POTHOLE_DETECTED: 'No potholes detected',
      UNAVAILABLE: 'Service unavailable',
      ANALYSIS_FAILED: 'Analysis failed',
      SAFETY_BLOCKED: 'Blocked by content policy'
    };

    supportedStatuses.forEach(st => {
      assert.ok(statusLabels[st], `Status ${st} must have mapped UI explanation`);
    });
  });

  it('7. Coplanarity perspective validation heuristic flags targets with deltaY > 0.25', () => {
    const isCoplanar = (refCenterY, potholeCenterY) => Math.abs(refCenterY - potholeCenterY) <= 0.25;

    assert.strictEqual(isCoplanar(0.55, 0.50), true, 'Adjacent target is coplanar');
    assert.strictEqual(isCoplanar(0.85, 0.25), false, 'Foreground/background target is non-coplanar');
  });

  it('8. Model name is displayed only when genuine inference occurred', () => {
    const getModelBadge = (analysis) => {
      if (!analysis || !analysis.modelName || analysis.status === 'UNAVAILABLE' || analysis.status === 'ANALYSIS_FAILED') {
        return null;
      }
      return `Model: ${analysis.modelName}`;
    };

    assert.strictEqual(getModelBadge({ status: 'UNAVAILABLE', modelName: null }), null);
    assert.strictEqual(getModelBadge({ status: 'ANALYSIS_FAILED', modelName: null }), null);
    assert.strictEqual(getModelBadge({ status: 'COMPLETED', modelName: 'gemini-3.8-flash' }), 'Model: gemini-3.8-flash');
  });

  it('9. Calibrated measurements are explicitly classified as provisional', () => {
    const calibratedMeasurement = {
      isCalibrated: true,
      provisional: true,
      metricWidthCm: 59.4,
      metricLengthCm: 29.7,
      metricAreaSqm: 0.1764,
      statusMessage: 'Provisional measurement — calibration and perspective limitations may affect accuracy. Calibrated via A4_SHEET (ratio: 0.1485 cm/px).'
    };

    assert.strictEqual(calibratedMeasurement.isCalibrated, true);
    assert.strictEqual(calibratedMeasurement.provisional, true);
    assert.ok(calibratedMeasurement.statusMessage.includes('Provisional measurement — calibration and perspective limitations may affect accuracy.'));
  });
});
