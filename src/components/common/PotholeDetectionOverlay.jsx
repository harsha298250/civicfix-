import React, { useState } from 'react';

/**
 * PotholeDetectionOverlay
 * Responsive SVG/Canvas visual overlay rendering localized 2D bounding boxes
 * over road defect photographs, with severity-coded boundary styling,
 * confidence tags, and toggleable visual aids.
 */
export const PotholeDetectionOverlay = ({
  photoUrl,
  boxes = [],
  severity = 'INCONCLUSIVE',
  imageCoveragePercent = 0,
  isCalibrated = false,
  metricAreaSqm = null,
  onCalibrateClick,
  className = ''
}) => {
  const [showOverlay, setShowOverlay] = useState(true);
  const [hoveredBoxId, setHoveredBoxId] = useState(null);

  const getSeverityColor = (sev) => {
    switch (sev?.toUpperCase()) {
      case 'CRITICAL':
        return { border: '#ef4444', bg: 'rgba(239, 68, 68, 0.25)', text: '#f87171' };
      case 'HIGH':
        return { border: '#f97316', bg: 'rgba(249, 115, 22, 0.25)', text: '#fb923c' };
      case 'MODERATE':
        return { border: '#f59e0b', bg: 'rgba(245, 158, 11, 0.25)', text: '#fbbf24' };
      case 'LOW':
        return { border: '#3b82f6', bg: 'rgba(59, 130, 246, 0.25)', text: '#60a5fa' };
      default:
        return { border: '#9ca3af', bg: 'rgba(156, 163, 175, 0.25)', text: '#d1d5db' };
    }
  };

  const colors = getSeverityColor(severity);

  return (
    <div className={`relative rounded-2xl overflow-hidden bg-black/40 border border-outline-variant/30 select-none ${className}`}>
      {/* Base Photograph */}
      <img
        src={photoUrl}
        alt="Road defect evidence photograph"
        className="w-full h-auto max-h-[480px] object-contain mx-auto block"
        loading="lazy"
      />

      {/* Interactive Detection Overlay */}
      {showOverlay && boxes && boxes.length > 0 && (
        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          className="absolute inset-0 w-full h-full pointer-events-none"
        >
          {boxes.map((box, idx) => {
            const x = Math.max(0, Math.min(100, box.xmin * 100));
            const y = Math.max(0, Math.min(100, box.ymin * 100));
            const width = Math.max(0.5, Math.min(100 - x, (box.xmax - box.xmin) * 100));
            const height = Math.max(0.5, Math.min(100 - y, (box.ymax - box.ymin) * 100));
            const isHovered = hoveredBoxId === (box.id || idx);

            return (
              <g key={box.id || idx}>
                {/* Bounding Rectangle */}
                <rect
                  x={`${x}%`}
                  y={`${y}%`}
                  width={`${width}%`}
                  height={`${height}%`}
                  fill={isHovered ? colors.bg : 'rgba(249, 115, 22, 0.18)'}
                  stroke={isHovered ? '#ffffff' : colors.border}
                  strokeWidth={isHovered ? '2.5' : '1.8'}
                  strokeDasharray={isHovered ? 'none' : '4 2'}
                  rx="1"
                />

                {/* Corner crosshairs for precision targeting */}
                <circle cx={`${x}%`} cy={`${y}%`} r="1" fill={colors.border} />
                <circle cx={`${x + width}%`} cy={`${y}%`} r="1" fill={colors.border} />
                <circle cx={`${x}%`} cy={`${y + height}%`} r="1" fill={colors.border} />
                <circle cx={`${x + width}%`} cy={`${y + height}%`} r="1" fill={colors.border} />
              </g>
            );
          })}
        </svg>
      )}

      {/* HTML Annotation Tags positioned over SVG */}
      {showOverlay && boxes && boxes.length > 0 && (
        <div className="absolute inset-0 pointer-events-none">
          {boxes.map((box, idx) => {
            const left = Math.max(0, Math.min(85, box.xmin * 100));
            const top = Math.max(2, Math.min(92, box.ymin * 100));

            return (
              <div
                key={box.id || idx}
                style={{ left: `${left}%`, top: `${top}%` }}
                className="absolute pointer-events-auto transform -translate-y-full -translate-x-1"
                onMouseEnter={() => setHoveredBoxId(box.id || idx)}
                onMouseLeave={() => setHoveredBoxId(null)}
              >
                <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-md text-[10px] font-mono font-bold bg-surface-container-lowest/90 backdrop-blur-md border border-outline-variant/60 shadow-lg text-on-surface whitespace-nowrap">
                  <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: colors.border }} />
                  <span>#{idx + 1}</span>
                  <span className="text-tertiary">
                    {Math.round((box.confidence || 0.8) * 100)}%
                  </span>
                  {box.relativeAreaPercent && (
                    <span className="text-outline text-[9px]">({box.relativeAreaPercent}%)</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Control Toolbar Bar (Top Right) */}
      <div className="absolute top-3 right-3 flex items-center gap-2">
        <button
          type="button"
          onClick={() => setShowOverlay(!showOverlay)}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold backdrop-blur-md border shadow-md transition-all flex items-center gap-1.5 ${
            showOverlay
              ? 'bg-surface-container-high/90 text-on-surface border-outline-variant/40 hover:bg-surface-container'
              : 'bg-surface-container-lowest/80 text-tertiary border-outline-variant/20 hover:text-on-surface'
          }`}
          title="Toggle computer vision detection boxes"
        >
          <span className="material-symbols-outlined text-sm">
            {showOverlay ? 'visibility' : 'visibility_off'}
          </span>
          <span>{showOverlay ? 'Hide Boxes' : 'Show Boxes'}</span>
        </button>

        {onCalibrateClick && (
          <button
            type="button"
            onClick={onCalibrateClick}
            className="px-3 py-1.5 rounded-xl text-xs font-semibold backdrop-blur-md border border-outline-variant/40 bg-surface-container-high/90 text-on-surface hover:bg-surface-container shadow-md flex items-center gap-1.5"
            title="Calibrate real-world metric dimensions with a reference target"
          >
            <span className="material-symbols-outlined text-sm">straighten</span>
            <span>{isCalibrated ? 'Recalibrate' : 'Calibrate Target'}</span>
          </button>
        )}
      </div>

      {/* Statistics Strip (Bottom) */}
      <div className="absolute bottom-0 inset-x-0 bg-surface-container-lowest/90 backdrop-blur-md px-4 py-2 border-t border-outline-variant/30 flex flex-wrap items-center justify-between text-xs text-on-surface gap-2">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 font-semibold">
            <span className="material-symbols-outlined text-sm text-primary-container">adjust</span>
            <span>{boxes.length} {boxes.length === 1 ? 'Pothole' : 'Potholes'} Localized</span>
          </div>
          <span className="text-outline-variant">|</span>
          <div className="text-tertiary font-mono">
            Coverage: <strong className="text-on-surface">{imageCoveragePercent}%</strong> of frame
          </div>
        </div>

        <div className="flex items-center gap-2 text-xs">
          {isCalibrated ? (
            <span
              className="px-2 py-0.5 rounded-md bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 font-medium flex items-center gap-1"
              title="Provisional measurement — calibration and perspective limitations may affect accuracy."
            >
              <span className="material-symbols-outlined text-[13px]">check_circle</span>
              <span>Provisional Metric: {metricAreaSqm ? `${metricAreaSqm} m²` : 'Calibrated'}</span>
            </span>
          ) : (
            <span className="text-[11px] text-tertiary italic">
              Real-world dimensions unavailable — valid image calibration is required.
            </span>
          )}
        </div>
      </div>
    </div>
  );
};

export default PotholeDetectionOverlay;
