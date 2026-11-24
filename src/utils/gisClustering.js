/**
 * Convert latitude and longitude to Web Mercator pixel coordinates at a given zoom level.
 */
export function latLngToPixel(lat, lng, zoom) {
  const sinY = Math.sin((lat * Math.PI) / 180);
  const clampedSinY = Math.min(Math.max(sinY, -0.9999), 0.9999);
  const scale = 256 * Math.pow(2, zoom);

  const x = scale * ((lng + 180) / 360);
  const y = scale * (0.5 - Math.log((1 + clampedSinY) / (1 - clampedSinY)) / (4 * Math.PI));

  return { x, y };
}

/**
 * High-performance spatial distance clustering in pixel space at current zoom level.
 *
 * @param {Array} complaints - List of complaint objects with location.coordinates
 * @param {number} zoom - Current Leaflet zoom level (e.g., 7 to 18)
 * @param {number} pixelDistance - Proximity threshold in pixels to merge into a cluster
 * @returns {Array} List of cluster objects and individual complaint markers
 */
export function clusterComplaints(complaints, zoom, pixelDistance = 50) {
  if (!Array.isArray(complaints) || complaints.length === 0) {
    return [];
  }

  // At high zoom (15+), show individual markers with their full precision
  if (zoom >= 15) {
    return complaints.map((c) => ({
      isCluster: false,
      count: 1,
      lat: Number(c.location?.coordinates?.lat),
      lng: Number(c.location?.coordinates?.lng),
      priority: c.priority || 'MEDIUM',
      complaint: c,
      items: [c]
    }));
  }

  const clusters = [];

  for (const c of complaints) {
    const lat = Number(c.location?.coordinates?.lat);
    const lng = Number(c.location?.coordinates?.lng);

    if (isNaN(lat) || isNaN(lng)) continue;

    const pixel = latLngToPixel(lat, lng, zoom);
    let matchedCluster = null;

    for (const cluster of clusters) {
      const dx = cluster.pixel.x - pixel.x;
      const dy = cluster.pixel.y - pixel.y;
      const distSq = dx * dx + dy * dy;

      if (distSq <= pixelDistance * pixelDistance) {
        matchedCluster = cluster;
        break;
      }
    }

    if (matchedCluster) {
      matchedCluster.items.push(c);
      matchedCluster.count++;

      // Recompute centroid
      const total = matchedCluster.items.length;
      let sumLat = 0;
      let sumLng = 0;
      let highestPriority = matchedCluster.highestPriority;

      for (const item of matchedCluster.items) {
        sumLat += Number(item.location?.coordinates?.lat);
        sumLng += Number(item.location?.coordinates?.lng);
        if (item.priority === 'CRITICAL') highestPriority = 'CRITICAL';
        else if (item.priority === 'HIGH' && highestPriority !== 'CRITICAL') highestPriority = 'HIGH';
      }

      matchedCluster.lat = sumLat / total;
      matchedCluster.lng = sumLng / total;
      matchedCluster.highestPriority = highestPriority;
      matchedCluster.isCluster = true;
      matchedCluster.pixel = latLngToPixel(matchedCluster.lat, matchedCluster.lng, zoom);
    } else {
      clusters.push({
        isCluster: false,
        count: 1,
        lat,
        lng,
        pixel,
        highestPriority: c.priority || 'MEDIUM',
        complaint: c,
        items: [c]
      });
    }
  }

  return clusters;
}

/**
 * Generate cluster icon for Leaflet map based on count and severity.
 */
export function createClusterMarkerIcon(cluster, isSelected = false, L = (typeof window !== 'undefined' ? window.L : globalThis.L)) {
  if (!L || typeof L.divIcon !== 'function') {
    return null;
  }
  const count = cluster.count;
  const priority = cluster.highestPriority || 'MEDIUM';

  let bg = '#137A70'; // Teal default
  let ringColor = 'rgba(19, 122, 112, 0.25)';

  if (priority === 'CRITICAL') {
    bg = '#C63F48';
    ringColor = 'rgba(198, 63, 72, 0.35)';
  } else if (priority === 'HIGH') {
    bg = '#D97706';
    ringColor = 'rgba(217, 119, 6, 0.3)';
  } else if (priority === 'MEDIUM') {
    bg = '#2563EB';
    ringColor = 'rgba(37, 99, 235, 0.25)';
  }

  const size = count > 50 ? 46 : (count > 10 ? 40 : 34);

  return L.divIcon({
    className: 'custom-cluster-marker',
    html: `
      <div style="
        position: relative;
        width: ${size}px;
        height: ${size}px;
        display: flex;
        align-items: center;
        justify-content: center;
      ">
        <div style="
          position: absolute;
          inset: -4px;
          border-radius: 50%;
          background: ${ringColor};
          animation: pulse 2s infinite ease-in-out;
        "></div>
        <div style="
          width: 100%;
          height: 100%;
          border-radius: 50%;
          background-color: ${bg};
          border: 2px solid white;
          color: white;
          font-weight: 800;
          font-size: ${size > 40 ? 14 : 12}px;
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: 0 4px 10px rgba(0,0,0,0.3);
          font-family: sans-serif;
          z-index: 2;
          ${isSelected ? 'outline: 3px solid #8b5cf6;' : ''}
        ">
          ${count}
        </div>
      </div>
    `,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2]
  });
}
