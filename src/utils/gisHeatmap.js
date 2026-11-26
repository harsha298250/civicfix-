import { hasValidCoordinates } from '../services/complaintService.js';

/**
 * Spatial Heatmap and Density Aggregation Engine.
 * Aggregates point coordinates into spatial density cells and generates
 * high-performance SVG/Canvas density circles with priority weighting.
 */

/**
 * Compute spatial density buckets from a list of complaints.
 *
 * @param {Array} complaints - List of complaint entities
 * @param {number} cellRadiusMeters - Density sampling radius in meters (default 400m)
 * @returns {Array} List of density hotspots
 */
export function computeDensityHotspots(complaints, cellRadiusMeters = 400) {
  if (!Array.isArray(complaints) || complaints.length === 0) {
    return [];
  }

  const valid = complaints.filter(
    (c) => !c.locationSuppressed && hasValidCoordinates(c.location?.coordinates)
  );

  const hotspots = [];
  const degreeThreshold = cellRadiusMeters / 111000.0; // approximate degree distance

  for (const c of valid) {
    const lat = Number(c.location?.coordinates?.lat);
    const lng = Number(c.location?.coordinates?.lng);

    if (isNaN(lat) || isNaN(lng)) continue;

    let matchedHotspot = null;

    for (const h of hotspots) {
      const dLat = h.lat - lat;
      const dLng = h.lng - lng;
      const distSq = dLat * dLat + dLng * dLng;

      if (distSq <= degreeThreshold * degreeThreshold) {
        matchedHotspot = h;
        break;
      }
    }

    const isCritical = c.priority === 'CRITICAL';
    const isHigh = c.priority === 'HIGH';
    const weight = isCritical ? 3.0 : (isHigh ? 2.0 : 1.0);

    if (matchedHotspot) {
      matchedHotspot.count++;
      matchedHotspot.weightedScore += weight;
      matchedHotspot.items.push(c);
      if (isCritical) matchedHotspot.criticalCount++;
      if (isHigh) matchedHotspot.highCount++;

      // Centroid update
      const total = matchedHotspot.items.length;
      matchedHotspot.lat = (matchedHotspot.lat * (total - 1) + lat) / total;
      matchedHotspot.lng = (matchedHotspot.lng * (total - 1) + lng) / total;
    } else {
      hotspots.push({
        lat,
        lng,
        count: 1,
        weightedScore: weight,
        criticalCount: isCritical ? 1 : 0,
        highCount: isHigh ? 1 : 0,
        ward: c.ward || c.location?.ward || 'Tamil Nadu',
        items: [c]
      });
    }
  }

  return hotspots;
}

/**
 * Create a Leaflet LayerGroup containing visual density hotspot circles.
 *
 * @param {Array} hotspots - Output of computeDensityHotspots
 * @param {Object} [L] - Leaflet instance
 * @returns {Object} Leaflet layer group
 */
export function createHotspotLayerGroup(hotspots, L = (typeof window !== 'undefined' ? window.L : globalThis.L)) {
  if (!L || typeof L.layerGroup !== 'function') {
    return null;
  }
  const layerGroup = L.layerGroup();

  for (const h of hotspots) {
    let color = '#0D9488';     // Teal: Low density
    let fillColor = '#14B8A6';
    let fillOpacity = 0.25;
    let radius = 250;

    if (h.criticalCount > 0 || h.weightedScore >= 12 || h.count >= 8) {
      color = '#DC2626';       // Red: Critical density hotspot
      fillColor = '#EF4444';
      fillOpacity = 0.45;
      radius = Math.min(350 + h.count * 25, 800);
    } else if (h.highCount > 0 || h.weightedScore >= 6 || h.count >= 4) {
      color = '#D97706';       // Amber: Medium density hotspot
      fillColor = '#F59E0B';
      fillOpacity = 0.35;
      radius = Math.min(280 + h.count * 20, 600);
    }

    const circle = L.circle([h.lat, h.lng], {
      radius,
      color,
      weight: 2,
      fillColor,
      fillOpacity
    });

    const tooltipContent = `
      <div style="font-family: inherit; font-size: 11px; padding: 2px;">
        <div style="font-weight: 700; color: ${color}; text-transform: uppercase; margin-bottom: 2px;">
          Civic Issue Hotspot
        </div>
        <div style="color: #18232F; margin-bottom: 2px;">
          <b>${h.count}</b> Total Issues (${h.criticalCount} Critical, ${h.highCount} High)
        </div>
        <div style="color: #64748B; font-size: 10px;">
          Area: ${String(h.ward || '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]))}
        </div>
      </div>
    `;

    circle.bindTooltip(tooltipContent, { sticky: true });
    layerGroup.addLayer(circle);
  }

  return layerGroup;
}
