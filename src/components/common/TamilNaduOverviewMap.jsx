import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import L from 'leaflet';
import { hasValidCoordinates } from '../../services/complaintService';
import tamilNaduStateGeoJson from '../../data/gis/tamil_nadu_state.json';
import chennaiGccZonesGeoJson from '../../data/gis/chennai_gcc_zones.json';

const escapeHtml = (str) => {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
};

const getPriorityColor = (priority) => {
  switch (priority) {
    case 'CRITICAL':
      return '#C63F48';
    case 'HIGH':
      return '#D97706';
    case 'MEDIUM':
      return '#2563EB';
    case 'LOW':
    default:
      return '#137A70';
  }
};

const createComplaintMarkerIcon = (priority = 'HIGH', isSelected = false) => {
  const color = getPriorityColor(priority);
  const size = isSelected ? 40 : 32;
  const ring = isSelected ? 'ring-4 ring-purple-400' : '';

  return L.divIcon({
    className: 'custom-complaint-marker',
    html: `
      <div style="
        background-color: ${color};
        width: ${size}px;
        height: ${size}px;
        border-radius: 50% 50% 50% 0;
        transform: rotate(-45deg);
        display: flex;
        align-items: center;
        justify-content: center;
        border: 2px solid white;
        box-shadow: 0 4px 12px rgba(0,0,0,0.35);
        transition: transform 0.2s;
      ">
        <span class="material-symbols-outlined" style="
          transform: rotate(45deg);
          color: white;
          font-size: ${isSelected ? 20 : 16}px;
          line-height: 1;
        ">${priority === 'CRITICAL' ? 'priority_high' : 'location_on'}</span>
      </div>
    `,
    iconSize: [size, size],
    iconAnchor: [size / 2, size],
    popupAnchor: [0, -size]
  });
};

export const TamilNaduOverviewMap = ({
  complaints = [],
  selectedComplaintId = null,
  onSelectComplaint = null,
  searchedLocation = null,
  height = '100%',
  detailRoutePrefix = '/officer/complaints',
  getDetailUrl = null
}) => {
  const navigate = useNavigate();
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersGroupRef = useRef(null);
  const searchMarkerRef = useRef(null);
  const stateLayerRef = useRef(null);
  const zonesLayerRef = useRef(null);

  const [tileError, setTileError] = useState(false);
  const [showStateBoundary, setShowStateBoundary] = useState(false);
  const [showGccZones, setShowGccZones] = useState(true);

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    if (!mapInstanceRef.current) {
      // Centered on Tamil Nadu
      const map = L.map(mapContainerRef.current, {
        center: [11.1271, 78.6569], // Tamil Nadu geographic center
        zoom: 7,
        zoomControl: true,
        attributionControl: true
      });

      // Standard OpenStreetMap Tile Layer
      const tileLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19
      });

      tileLayer.on('tileerror', () => {
        setTileError(true);
      });

      tileLayer.addTo(map);

      const markersGroup = L.layerGroup().addTo(map);
      markersGroupRef.current = markersGroup;
      mapInstanceRef.current = map;
    }

    const map = mapInstanceRef.current;

    // Invalidate size on mount and after layout paints
    const timer1 = setTimeout(() => {
      if (map) map.invalidateSize();
    }, 150);

    const timer2 = setTimeout(() => {
      if (map) map.invalidateSize();
    }, 450);

    // Watch for size changes (split view toggling, window resize)
    let resizeObserver = null;
    if (mapContainerRef.current && window.ResizeObserver) {
      resizeObserver = new ResizeObserver(() => {
        if (mapInstanceRef.current) {
          mapInstanceRef.current.invalidateSize();
        }
      });
      resizeObserver.observe(mapContainerRef.current);
    }

    return () => {
      clearTimeout(timer1);
      clearTimeout(timer2);
      if (resizeObserver) resizeObserver.disconnect();
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, []);

  // Update Markers when complaints or selection changes
  useEffect(() => {
    if (!mapInstanceRef.current || !markersGroupRef.current) return;

    const map = mapInstanceRef.current;
    map.invalidateSize();

    const markersGroup = markersGroupRef.current;
    markersGroup.clearLayers();

    const bounds = [];
    let selectedMarker = null;

    complaints.forEach((c) => {
      // Never render a marker if location is suppressed for resident privacy
      if (c.locationSuppressed) return;

      const lat = Number(c.location?.coordinates?.lat);
      const lng = Number(c.location?.coordinates?.lng);

      if (hasValidCoordinates(c.location?.coordinates)) {
        const isSelected = selectedComplaintId === c.id;
        const marker = L.marker([lat, lng], {
          icon: createComplaintMarkerIcon(c.priority, isSelected),
          zIndexOffset: isSelected ? 1000 : 100
        });

        // Popup HTML: Strictly non-sensitive, shows category, status and ward only
        const safeCategory = escapeHtml(c.category || 'Civic Infrastructure');
        const safeWard = escapeHtml(c.ward || c.location?.ward || 'Tamil Nadu');
        const safeStatus = escapeHtml(c.status || 'SUBMITTED');

        const popupContent = document.createElement('div');
        popupContent.className = 'p-1 text-xs font-sans';
        popupContent.innerHTML = `
          <div style="font-family: inherit; min-width: 170px;">
            <div style="font-size: 11px; color: #137A70; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px;">
              ${safeCategory}
            </div>
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; font-size: 11px;">
              <span style="color: #667384;">Status:</span>
              <span style="font-weight: 600; color: #18232F;">${safeStatus}</span>
            </div>
            <div style="display: flex; align-items: center; justify-content: space-between; font-size: 11px;">
              <span style="color: #667384;">Ward:</span>
              <span style="font-weight: 600; color: #18232F;">${safeWard}</span>
            </div>
          </div>
        `;

        marker.bindPopup(popupContent, { maxWidth: 260 });

        marker.on('click', () => {
          if (onSelectComplaint) {
            onSelectComplaint(c);
          }
        });

        markersGroup.addLayer(marker);
        bounds.push([lat, lng]);

        if (isSelected) {
          selectedMarker = marker;
        }
      }
    });

    // Fly to selected complaint when changed
    if (selectedComplaintId) {
      const selected = complaints.find((c) => c.id === selectedComplaintId);
      const lat = Number(selected?.location?.coordinates?.lat);
      const lng = Number(selected?.location?.coordinates?.lng);
      if (!isNaN(lat) && !isNaN(lng)) {
        map.flyTo([lat, lng], 14, { duration: 1.0 });
        if (selectedMarker) {
          setTimeout(() => {
            selectedMarker.openPopup();
          }, 350);
        }
      }
    } else if (bounds.length > 0) {
      try {
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 13 });
      } catch (e) {
        // ignore bound edge cases
      }
    }
  }, [complaints, selectedComplaintId]);

  // Handle external searched location pin
  useEffect(() => {
    if (!mapInstanceRef.current) return;
    const map = mapInstanceRef.current;

    if (searchMarkerRef.current) {
      searchMarkerRef.current.remove();
      searchMarkerRef.current = null;
    }

    if (searchedLocation?.lat && searchedLocation?.lng) {
      const { lat, lng, label } = searchedLocation;
      const searchIcon = L.divIcon({
        className: 'custom-search-marker',
        html: `
          <div style="
            background-color: #8B5CF6;
            width: 36px;
            height: 36px;
            border-radius: 50% 50% 50% 0;
            transform: rotate(-45deg);
            display: flex;
            align-items: center;
            justify-content: center;
            border: 2px solid white;
            box-shadow: 0 4px 14px rgba(139, 92, 246, 0.5);
          ">
            <span class="material-symbols-outlined" style="
              transform: rotate(45deg);
              color: white;
              font-size: 20px;
            ">search</span>
          </div>
        `,
        iconSize: [36, 36],
        iconAnchor: [18, 36]
      });

      const safeLabel = escapeHtml(label || `${lat.toFixed(4)}, ${lng.toFixed(4)}`);
      const marker = L.marker([lat, lng], { icon: searchIcon, zIndexOffset: 2000 })
        .addTo(map)
        .bindPopup(`<b>Searched Location:</b><br/>${safeLabel}`)
        .openPopup();

      searchMarkerRef.current = marker;
      map.flyTo([lat, lng], 15, { duration: 1.2 });
    }
  }, [searchedLocation]);

  // Manage Administrative Boundary Layers
  useEffect(() => {
    if (!mapInstanceRef.current) return;
    const map = mapInstanceRef.current;

    // 1. Tamil Nadu State Boundary Layer
    if (showStateBoundary) {
      if (!stateLayerRef.current && tamilNaduStateGeoJson) {
        stateLayerRef.current = L.geoJSON(tamilNaduStateGeoJson, {
          style: {
            color: '#2563EB',
            weight: 2,
            dashArray: '6, 6',
            fillColor: '#3B82F6',
            fillOpacity: 0.05
          },
          onEachFeature: (feature, layer) => {
            const name = feature?.properties?.stateName || 'Tamil Nadu';
            layer.bindTooltip(`<b>${escapeHtml(name)} State Boundary</b><br/><span style="font-size:10px;color:#64748B;">Survey of India / Bharat Maps</span>`, { sticky: true });
          }
        });
      }
      if (stateLayerRef.current && !map.hasLayer(stateLayerRef.current)) {
        stateLayerRef.current.addTo(map);
      }
    } else {
      if (stateLayerRef.current && map.hasLayer(stateLayerRef.current)) {
        map.removeLayer(stateLayerRef.current);
      }
    }

    // 2. Chennai GCC Zones Boundary Layer
    if (showGccZones) {
      if (!zonesLayerRef.current && chennaiGccZonesGeoJson) {
        zonesLayerRef.current = L.geoJSON(chennaiGccZonesGeoJson, {
          style: () => ({
            color: '#0D9488',
            weight: 1.5,
            fillColor: '#14B8A6',
            fillOpacity: 0.10
          }),
          onEachFeature: (feature, layer) => {
            const zNum = feature?.properties?.zoneNumber || '';
            const zName = feature?.properties?.zoneName || '';
            const wardCount = feature?.properties?.coveredWards?.length || 0;
            layer.bindTooltip(`<b>Zone ${escapeHtml(zNum)}: ${escapeHtml(zName)}</b><br/><span style="font-size:10px;color:#64748B;">GCC Planning Division (${wardCount} wards)</span>`, { sticky: true });
          }
        });
      }
      if (zonesLayerRef.current && !map.hasLayer(zonesLayerRef.current)) {
        zonesLayerRef.current.addTo(map);
      }
    } else {
      if (zonesLayerRef.current && map.hasLayer(zonesLayerRef.current)) {
        map.removeLayer(zonesLayerRef.current);
      }
    }
  }, [showStateBoundary, showGccZones]);

  return (
    <div
      style={{ height: height || '100%', minHeight: '480px' }}
      className="relative w-full h-full min-h-[480px] flex-1 rounded-2xl overflow-hidden border border-[#E2E7ED] shadow-sm bg-slate-100 flex flex-col"
    >
      <div
        ref={mapContainerRef}
        style={{ height: '100%', width: '100%', minHeight: '480px' }}
        className="w-full h-full min-h-[480px] z-0 flex-1"
      />

      {/* GIS Boundary Controls */}
      <div className="absolute top-3 left-3 bg-white/95 backdrop-blur-xs px-2.5 py-1.5 rounded-xl border border-[#E2E7ED] z-10 shadow-sm flex items-center gap-2 text-xs">
        <span className="font-semibold text-slate-700 flex items-center gap-1">
          <span className="material-symbols-outlined text-[16px] text-teal-600">layers</span>
          <span>Boundaries:</span>
        </span>
        <button
          type="button"
          aria-pressed={showStateBoundary}
          onClick={() => setShowStateBoundary((prev) => !prev)}
          className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors ${
            showStateBoundary
              ? 'bg-blue-100 text-blue-700 border border-blue-300'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
          }`}
        >
          TN State
        </button>
        <button
          type="button"
          aria-pressed={showGccZones}
          onClick={() => setShowGccZones((prev) => !prev)}
          className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors ${
            showGccZones
              ? 'bg-teal-100 text-teal-700 border border-teal-300'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
          }`}
        >
          GCC Zones (15)
        </button>
      </div>

      {tileError && (
        <div className="absolute top-3 right-3 bg-amber-600/90 text-white px-3 py-1.5 rounded-lg text-xs font-semibold z-10 shadow">
          Demonstration Tile Server · High Traffic Mode
        </div>
      )}

      {/* Tamil Nadu Watermark */}
      <div className="absolute bottom-3 left-3 bg-white/95 backdrop-blur-xs px-3 py-1.5 rounded-xl text-xs text-[#18232F] border border-[#E2E7ED] z-10 shadow-sm flex items-center gap-2">
        <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
        <span className="font-semibold">Tamil Nadu Geographic Grid</span>
        <span className="text-[#667384] text-[11px] font-mono">({complaints.length} pinpoints)</span>
      </div>
    </div>
  );
};

export default TamilNaduOverviewMap;
