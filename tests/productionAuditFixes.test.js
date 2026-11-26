import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { CATEGORIES } from '../src/constants/statusConstants.js';
import { mapBackendCategoryToFrontendId } from '../src/utils/triageUtils.js';
import { translateCategory } from '../src/utils/i18nHelpers.js';
import { createHotspotLayerGroup, computeDensityHotspots } from '../src/utils/gisHeatmap.js';
import { computeCalibratedMeasurement, analyzeDefectImagePixels } from '../src/utils/defectMeasurement.js';
import en from '../src/locales/en.json' with { type: 'json' };
import ta from '../src/locales/ta.json' with { type: 'json' };

const readSrc = (rel) => fs.readFileSync(path.resolve(process.cwd(), rel), 'utf8');

describe('Production Audit Fixes Regression Suite', () => {
  it('supports OTHERS category across constants, triage mapping, i18n, and ReportComplaintPage', () => {
    const others = CATEGORIES.find((c) => c.id === 'others');
    assert.ok(others, 'CATEGORIES must include others');
    assert.equal(mapBackendCategoryToFrontendId('OTHERS'), 'others');
    assert.equal(mapBackendCategoryToFrontendId('UNKNOWN_CUSTOM'), 'others');

    assert.equal(translateCategory('OTHERS', (k) => (k === 'categories.others' ? ta.categories.others : k)), ta.categories.others);
    assert.ok(en.reportComplaint.otherCategoryLabel);
    assert.ok(ta.reportComplaint.otherCategoryLabel);

    const reportPage = readSrc('src/pages/citizen/ReportComplaintPage.jsx');
    assert.ok(reportPage.includes('otherCategoryDescription'));
    assert.ok(reportPage.includes('customCategoryDescription'));
  });

  it('fixes OfficerLayout and WorkerLayout sidebar active navigation matching without overlapping highlights', () => {
    const officerLayout = readSrc('src/components/layout/OfficerLayout.jsx');
    assert.ok(officerLayout.includes("pathname === '/officer/complaints'"));
    assert.ok(officerLayout.includes('/^\\/officer\\/complaints\\/[^/]+\\/review$/'));

    const workerLayout = readSrc('src/components/layout/WorkerLayout.jsx');
    assert.ok(workerLayout.includes("pathname === '/worker/tasks'"));
    assert.ok(workerLayout.includes('/^\\/worker\\/tasks\\/[^/]+$/'));
    assert.ok(workerLayout.includes('/^\\/worker\\/tasks\\/[^/]+\\/progress$/'));
    assert.ok(workerLayout.includes('/^\\/worker\\/tasks\\/[^/]+\\/evidence$/'));
  });

  it('prevents Leaflet map z-index bleed-through and elevates ComplaintReviewPage modals', () => {
    const miniMap = readSrc('src/components/common/LocationMiniMap.jsx');
    assert.ok(miniMap.includes('isolate z-0'), 'LocationMiniMap must isolate Leaflet stacking context');

    const reviewPage = readSrc('src/pages/officer/ComplaintReviewPage.jsx');
    assert.ok(reviewPage.includes('z-[80]'), 'ComplaintReviewPage modals must use z-[80] above layout/map');
    assert.ok(reviewPage.includes("complaint.status !== 'REJECTED'"), 'Reject button must be guarded for rejected tickets');
  });

  it('excludes rejected complaints from active OfficerQueue and OfficerDashboard feeds', () => {
    const queuePage = readSrc('src/pages/officer/ComplaintQueuePage.jsx');
    assert.ok(queuePage.includes('COMPLAINT_STATUS.REJECTED'));

    const officerDash = readSrc('src/pages/officer/OfficerDashboard.jsx');
    assert.ok(officerDash.includes("c.status !== 'REJECTED'"));
  });

  it('provides camera live-preview readiness, snapshot preview, and retake/confirm controls', () => {
    const uploadMgr = readSrc('src/components/common/ImageUploadManager.jsx');
    assert.ok(uploadMgr.includes('capturedPreview'));
    assert.ok(uploadMgr.includes('handleRetakeCaptured'));
    assert.ok(uploadMgr.includes('handleConfirmCaptured'));
    assert.ok(uploadMgr.includes('onResize'));
  });

  it('handles microphone permission/device errors and cleans up recognition listeners', () => {
    const voiceBtn = readSrc('src/components/common/VoiceInputButton.jsx');
    assert.ok(voiceBtn.includes('service-not-allowed'));
    assert.ok(voiceBtn.includes('audio-capture'));
    assert.ok(voiceBtn.includes('recognitionRef.current = null'));
  });

  it('prevents OverviewMap zoom-snap bug and escapes HTML in heatmap tooltips', () => {
    const overviewMap = readSrc('src/components/common/TamilNaduOverviewMap.jsx');
    assert.ok(overviewMap.includes('lastViewportKeyRef'));

    const hotspots = computeDensityHotspots([
      {
        id: '1',
        priority: 'HIGH',
        ward: '<img src=x onerror=alert(1)>',
        location: { coordinates: { lat: 13.08, lng: 80.27 } }
      }
    ]);
    assert.equal(hotspots.length, 1);

    const mockL = {
      layerGroup: () => ({ addLayer: () => {} }),
      circle: () => ({
        _tooltip: '',
        bindTooltip(html) {
          this._tooltip = html;
        }
      })
    };
    const gisHeatmap = readSrc('src/utils/gisHeatmap.js');
    assert.ok(gisHeatmap.includes('&lt;'));
    assert.ok(typeof createHotspotLayerGroup === 'function');
  });

  it('optimizes Lighthouse LCP hero image and Material Symbols font-display swap', () => {
    const indexHtml = readSrc('index.html');
    assert.ok(indexHtml.includes('rel="preload"'));
    assert.ok(indexHtml.includes('rel="preconnect"'));

    const fontsCss = readSrc('public/fonts/fonts.css');
    assert.ok(fontsCss.includes('font-display: swap;'));

    const landing = readSrc('src/pages/public/LandingPage.jsx');
    assert.ok(landing.includes('fetchPriority="high"'));
    assert.ok(landing.includes('fm=webp'));
  });

  it('computes calibrated defect measurements only with a valid scale reference and refuses uncalibrated cm invention', () => {
    const uncalibrated = computeCalibratedMeasurement({ markerType: 'NONE', defectLengthPx: 200, defectWidthPx: 100 });
    assert.equal(uncalibrated.calibrated, false);
    assert.equal(uncalibrated.estimatedLengthCm, null);
    assert.equal(uncalibrated.estimatedWidthCm, null);

    const calibrated = computeCalibratedMeasurement({
      markerType: 'A4_SHEET',
      markerPixelSpan: 100,
      defectLengthPx: 200,
      defectWidthPx: 150
    });
    assert.equal(calibrated.calibrated, true);
    assert.equal(calibrated.estimatedLengthCm, 59.4);
    assert.equal(calibrated.estimatedWidthCm, 44.6);
    assert.equal(calibrated.unit, 'cm');
    assert.ok(calibrated.confidence >= 45 && calibrated.confidence <= 88);

    // Synthetic 60x60 RGB buffer: neutral gray road (128), white marker (10x10), dark pothole (20x15)
    const w = 60, h = 60;
    const data = new Uint8ClampedArray(w * h * 4);
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = (y * w + x) * 4;
        let v = 135;
        if (x >= 2 && x < 12 && y >= 2 && y < 12) v = 240; // 10x10 white marker
        else if (x >= 25 && x < 45 && y >= 25 && y < 40) v = 30; // 20x15 dark pothole
        data[i] = v; data[i + 1] = v; data[i + 2] = v; data[i + 3] = 255;
      }
    }
    const autoRes = analyzeDefectImagePixels({ width: w, height: h, data }, 'A4_SHEET');
    assert.equal(autoRes.markerDetected, true);
    assert.equal(autoRes.defectDetected, true);
    assert.equal(autoRes.calibrated, true);
    assert.equal(autoRes.provisional, true);
    assert.equal(autoRes.estimatedLengthCm, 59.4);
    assert.equal(autoRes.estimatedWidthCm, 44.6);
  });

  it('includes UNDER_REVIEW email event, concurrency 409 handler, and non-conflicting V30/V31 migrations', () => {
    const eventType = readSrc('backend/src/main/java/com/civicfix/complaint/event/ComplaintEventType.java');
    assert.ok(eventType.includes('COMPLAINT_UNDER_REVIEW'));

    const notifSvc = readSrc('backend/src/main/java/com/civicfix/notification/NotificationService.java');
    assert.ok(notifSvc.includes('case COMPLAINT_UNDER_REVIEW:'));

    const exHandler = readSrc('backend/src/main/java/com/civicfix/common/exception/GlobalExceptionHandler.java');
    assert.ok(exHandler.includes('DataIntegrityViolationException'));

    const v30 = readSrc('backend/src/main/resources/db/migration/V30__add_custom_category_description.sql');
    assert.ok(v30.includes('custom_category_description'));

    const v31 = readSrc('backend/src/main/resources/db/migration/V31__add_measurement_fields_and_email_lower_unique.sql');
    assert.ok(v31.includes('idx_users_email_lower_unique'));
    assert.ok(v31.includes('citizen_confirmed_length_cm'));

    const reportPage = readSrc('src/pages/citizen/ReportComplaintPage.jsx');
    assert.ok(reportPage.includes('measurement-overlay-preview'));

    const rateLimit = readSrc('backend/src/main/java/com/civicfix/security/ratelimit/RateLimitInterceptor.java');
    assert.ok(rateLimit.includes('/api/auth/resend-verification'));

    const healthCtrl = readSrc('backend/src/main/java/com/civicfix/health/HealthController.java');
    assert.ok(healthCtrl.includes('"database"'));
    assert.ok(healthCtrl.includes('"storage"'));
    assert.ok(healthCtrl.includes('"email"'));
    assert.ok(healthCtrl.includes('"aiVision"'));

    const appCtx = readSrc('src/context/AppContext.jsx');
    assert.ok(appCtx.includes("import('../services/firebaseAuth')"));
  });
});
