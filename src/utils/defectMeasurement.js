/**
 * Calibration-Assisted Defect Measurement Utility
 *
 * Enforces the engineering rule that a single monocular RGB photograph cannot
 * yield reliable real-world centimetre dimensions without a known-scale
 * coplanar reference marker. When no valid reference is provided, returns
 * calibrated=false and never invents centimetre values.
 */

export const CALIBRATION_MARKERS = [
  { id: 'NONE', label: 'No reference marker (visual analysis only)', knownLengthCm: null },
  { id: 'A4_SHEET', label: 'A4 Paper Sheet (29.7 cm)', knownLengthCm: 29.7 },
  { id: 'STANDARD_BRICK', label: 'Standard Clay Brick (21.5 cm)', knownLengthCm: 21.5 },
  { id: 'TRAFFIC_CONE_BASE', label: 'Traffic Cone Base (28.0 cm)', knownLengthCm: 28.0 },
  { id: 'CUSTOM_MARKER', label: 'Custom Known Object (cm)', knownLengthCm: null }
];

export function computeCalibratedMeasurement({
  markerType = 'NONE',
  customMarkerCm = null,
  markerPixelSpan = null,
  defectLengthPx = null,
  defectWidthPx = null,
  method = 'Coplanar Reference Scale'
}) {
  if (!markerType || markerType === 'NONE') {
    return {
      calibrated: false,
      provisional: false,
      method: null,
      estimatedLengthCm: null,
      estimatedWidthCm: null,
      estimatedAreaSqm: null,
      confidence: null,
      unit: null,
      limitationNote: 'No valid scale reference object selected. Real-world centimetre dimensions are not estimated from an uncalibrated single photo.'
    };
  }

  const preset = CALIBRATION_MARKERS.find((m) => m.id === markerType);
  const knownCm = markerType === 'CUSTOM_MARKER'
    ? Number(customMarkerCm)
    : Number(preset?.knownLengthCm);

  const refPx = Number(markerPixelSpan);
  const lenPx = Number(defectLengthPx);
  const widPx = Number(defectWidthPx);

  if (!Number.isFinite(knownCm) || knownCm <= 0 || !Number.isFinite(refPx) || refPx < 10) {
    return {
      calibrated: false,
      provisional: false,
      method: null,
      estimatedLengthCm: null,
      estimatedWidthCm: null,
      estimatedAreaSqm: null,
      confidence: null,
      unit: null,
      limitationNote: 'Valid reference object size (> 0 cm) and marker pixel span (>= 10 px) are required to compute scale.'
    };
  }

  if (!Number.isFinite(lenPx) || lenPx <= 0 || !Number.isFinite(widPx) || widPx <= 0) {
    return {
      calibrated: false,
      provisional: false,
      method: null,
      estimatedLengthCm: null,
      estimatedWidthCm: null,
      estimatedAreaSqm: null,
      confidence: null,
      unit: null,
      limitationNote: 'Mark or enter positive defect pixel dimensions to calculate approximate size.'
    };
  }

  const cmPerPixel = knownCm / refPx;
  const estimatedLengthCm = Math.round(lenPx * cmPerPixel * 10) / 10;
  const estimatedWidthCm = Math.round(widPx * cmPerPixel * 10) / 10;
  const estimatedAreaSqm = Math.round(((estimatedLengthCm * estimatedWidthCm) / 10000) * 1000) / 1000;

  // Honest heuristic confidence based on marker pixel span (never presented as scientific accuracy)
  const confidence = Math.min(85, Math.max(40, Math.round(45 + Math.min(refPx, 200) * 0.2)));

  return {
    calibrated: true,
    provisional: true,
    method,
    markerType,
    knownMarkerCm: knownCm,
    estimatedLengthCm,
    estimatedWidthCm,
    estimatedAreaSqm,
    confidence,
    unit: 'cm',
    limitationNote: 'Provisional planar estimate assuming the reference marker lies on the same surface plane as the defect. Depth cannot be measured from a single RGB photo.'
  };
}

/**
 * Pure pixel-buffer analyzer for client-side canvas auto-detection.
 * Scans RGBA pixel data for:
 * 1) Bright neutral rectangular reference marker (e.g., white A4 sheet)
 * 2) Dark/high-contrast road defect region outside the reference marker
 */
export function analyzeDefectImagePixels({ width, height, data }, markerType = 'A4_SHEET', customMarkerCm = null) {
  if (!width || !height || !data || data.length < width * height * 4) {
    return {
      markerDetected: false,
      defectDetected: false,
      markerBox: null,
      defectBox: null,
      ...computeCalibratedMeasurement({ markerType: 'NONE' })
    };
  }

  const totalPixels = width * height;
  const lumas = new Float32Array(totalPixels);
  let lumaSum = 0;

  // Detect bright low-saturation reference marker bounding box
  let mMinX = width, mMinY = height, mMaxX = -1, mMaxY = -1, markerCount = 0;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const idx = (y * width + x) * 4;
      const r = data[idx];
      const g = data[idx + 1];
      const b = data[idx + 2];
      const luma = 0.299 * r + 0.587 * g + 0.114 * b;
      lumas[y * width + x] = luma;
      lumaSum += luma;

      const maxC = Math.max(r, g, b);
      const minC = Math.min(r, g, b);
      if (r > 205 && g > 205 && b > 205 && (maxC - minC) < 35) {
        markerCount++;
        if (x < mMinX) mMinX = x;
        if (y < mMinY) mMinY = y;
        if (x > mMaxX) mMaxX = x;
        if (y > mMaxY) mMaxY = y;
      }
    }
  }

  const meanLuma = lumaSum / totalPixels;
  const markerAreaRatio = markerCount / totalPixels;
  const markerDetected =
    markerCount >= 40 &&
    markerAreaRatio >= 0.004 &&
    markerAreaRatio <= 0.35 &&
    mMaxX > mMinX &&
    mMaxY > mMinY;

  const markerBox = markerDetected
    ? { x: mMinX, y: mMinY, width: mMaxX - mMinX + 1, height: mMaxY - mMinY + 1 }
    : null;

  // Detect dark/shadowed defect region outside marker box
  const darkThreshold = Math.max(25, meanLuma * 0.58);
  let dMinX = width, dMinY = height, dMaxX = -1, dMaxY = -1, defectCount = 0;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (
        markerBox &&
        x >= markerBox.x &&
        x <= markerBox.x + markerBox.width &&
        y >= markerBox.y &&
        y <= markerBox.y + markerBox.height
      ) {
        continue;
      }
      const luma = lumas[y * width + x];
      if (luma < darkThreshold) {
        defectCount++;
        if (x < dMinX) dMinX = x;
        if (y < dMinY) dMinY = y;
        if (x > dMaxX) dMaxX = x;
        if (y > dMaxY) dMaxY = y;
      }
    }
  }

  const defectAreaRatio = defectCount / totalPixels;
  const defectDetected =
    defectCount >= 50 &&
    defectAreaRatio >= 0.005 &&
    defectAreaRatio <= 0.65 &&
    dMaxX > dMinX &&
    dMaxY > dMinY;

  const defectBox = defectDetected
    ? { x: dMinX, y: dMinY, width: dMaxX - dMinX + 1, height: dMaxY - dMinY + 1 }
    : null;

  if (!markerDetected || !defectDetected) {
    return {
      markerDetected,
      defectDetected,
      analysisWidth: width,
      analysisHeight: height,
      markerBox,
      defectBox,
      markerPixelSpan: markerBox ? Math.max(markerBox.width, markerBox.height) : null,
      defectLengthPx: defectBox ? Math.max(defectBox.width, defectBox.height) : null,
      defectWidthPx: defectBox ? Math.min(defectBox.width, defectBox.height) : null,
      ...computeCalibratedMeasurement({ markerType: 'NONE' }),
      limitationNote: !markerDetected
        ? 'No clear calibration reference marker detected automatically. Select a marker and enter pixel spans or confirm manually.'
        : 'Reference marker detected, but defect boundary contrast was insufficient. Please enter or adjust defect dimensions manually.'
    };
  }

  const markerPixelSpan = Math.max(markerBox.width, markerBox.height);
  const defectLengthPx = Math.max(defectBox.width, defectBox.height);
  const defectWidthPx = Math.min(defectBox.width, defectBox.height);
  const effectiveMarker = markerType && markerType !== 'NONE' ? markerType : 'A4_SHEET';

  return {
    markerDetected: true,
    defectDetected: true,
    analysisWidth: width,
    analysisHeight: height,
    markerBox,
    defectBox,
    markerPixelSpan,
    defectLengthPx,
    defectWidthPx,
    ...computeCalibratedMeasurement({
      markerType: effectiveMarker,
      customMarkerCm,
      markerPixelSpan,
      defectLengthPx,
      defectWidthPx,
      method: 'Canvas Auto-Boundary & Coplanar Reference (Provisional)'
    })
  };
}

/**
 * Loads an image URL/Blob URL onto an offscreen canvas and runs pixel analysis.
 */
export function runCanvasAutoMeasurement(imageUrl, markerType = 'A4_SHEET', customMarkerCm = null) {
  return new Promise((resolve, reject) => {
    if (typeof Image === 'undefined' || typeof document === 'undefined') {
      reject(new Error('Canvas image analysis is only available in browser environments.'));
      return;
    }
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const maxDim = 400;
        const scale = Math.min(1, maxDim / Math.max(img.naturalWidth || img.width || 1, img.naturalHeight || img.height || 1));
        const w = Math.max(32, Math.round((img.naturalWidth || img.width) * scale));
        const h = Math.max(32, Math.round((img.naturalHeight || img.height) * scale));
        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, w, h);
        const imgData = ctx.getImageData(0, 0, w, h);
        resolve(analyzeDefectImagePixels({ width: w, height: h, data: imgData.data }, markerType, customMarkerCm));
      } catch (err) {
        reject(err);
      }
    };
    img.onerror = () => reject(new Error('Unable to decode image for client-side canvas analysis.'));
    img.src = imageUrl;
  });
}
