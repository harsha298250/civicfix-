import { describe, it } from 'node:test';
import assert from 'node:assert';
import { clusterComplaints, latLngToPixel, createClusterMarkerIcon } from '../src/utils/gisClustering.js';
import { computeDensityHotspots } from '../src/utils/gisHeatmap.js';
import { TAMIL_NADU_DISTRICTS, findDistrictByName, MAJOR_TN_DISTRICTS, TN_BOUNDS, TN_CENTER } from '../src/constants/tamilNaduDistricts.js';
import { apiClient } from '../src/services/apiClient.js';

describe('Phase 2 GIS Enhancements & Tamil Nadu Intelligence Tests', () => {

  it('Tamil Nadu Districts dataset contains all 38 official districts with valid bounds', () => {
    assert.strictEqual(TAMIL_NADU_DISTRICTS.length, 38, 'Must contain exactly 38 districts');
    assert.ok(MAJOR_TN_DISTRICTS.length >= 5, 'Must contain major regional hubs');

    // Bounds verification
    assert.ok(TN_BOUNDS.minLat >= 8.0 && TN_BOUNDS.maxLat <= 14.0);
    assert.ok(TN_BOUNDS.minLng >= 76.0 && TN_BOUNDS.maxLng <= 81.0);
    assert.ok(TN_CENTER.lat > 10.0 && TN_CENTER.lng > 78.0);

    // District lookup
    const chennai = findDistrictByName('Chennai');
    assert.ok(chennai);
    assert.strictEqual(chennai.region, 'North');
    assert.strictEqual(chennai.isMajor, true);

    const coimbatore = findDistrictByName('coimbatore');
    assert.ok(coimbatore);
    assert.strictEqual(coimbatore.name, 'Coimbatore');

    const madurai = findDistrictByName('MADURAI');
    assert.ok(madurai);
    assert.strictEqual(madurai.name, 'Madurai');

    assert.strictEqual(findDistrictByName('NonExistent'), null);
  });

  it('GIS clustering clusters nearby complaint points at zoom 10', () => {
    const complaints = [
      {
        id: '1',
        priority: 'CRITICAL',
        location: { coordinates: { lat: 13.0827, lng: 80.2707 } }
      },
      {
        id: '2',
        priority: 'MEDIUM',
        location: { coordinates: { lat: 13.0830, lng: 80.2710 } }
      },
      {
        id: '3',
        priority: 'LOW',
        location: { coordinates: { lat: 13.0825, lng: 80.2705 } }
      },
      {
        // Far away: Coimbatore (~500km away)
        id: '4',
        priority: 'HIGH',
        location: { coordinates: { lat: 11.0168, lng: 76.9558 } }
      }
    ];

    const clusters = clusterComplaints(complaints, 10, 50);
    assert.strictEqual(clusters.length, 2, 'Should create 2 clusters: 1 for Chennai group, 1 for Coimbatore');

    const chennaiCluster = clusters.find(c => c.count === 3);
    assert.ok(chennaiCluster);
    assert.strictEqual(chennaiCluster.isCluster, true);
    assert.strictEqual(chennaiCluster.highestPriority, 'CRITICAL');

    const coimbatoreCluster = clusters.find(c => c.count === 1);
    assert.ok(coimbatoreCluster);
    assert.strictEqual(coimbatoreCluster.isCluster, false);
    assert.strictEqual(coimbatoreCluster.highestPriority, 'HIGH');
  });

  it('GIS clustering decomposes into individual markers at high zoom (zoom 16)', () => {
    const complaints = [
      {
        id: '1',
        priority: 'CRITICAL',
        location: { coordinates: { lat: 13.0827, lng: 80.2707 } }
      },
      {
        id: '2',
        priority: 'MEDIUM',
        location: { coordinates: { lat: 13.0830, lng: 80.2710 } }
      }
    ];

    const unclustered = clusterComplaints(complaints, 16, 50);
    assert.strictEqual(unclustered.length, 2);
    assert.strictEqual(unclustered[0].isCluster, false);
    assert.strictEqual(unclustered[1].isCluster, false);
  });

  it('Density hotspots are computed with severity weighting', () => {
    const complaints = [
      {
        id: '1',
        priority: 'CRITICAL',
        ward: 'Ward 100',
        location: { coordinates: { lat: 13.0827, lng: 80.2707 } }
      },
      {
        id: '2',
        priority: 'CRITICAL',
        ward: 'Ward 100',
        location: { coordinates: { lat: 13.0829, lng: 80.2709 } }
      },
      {
        id: '3',
        priority: 'HIGH',
        ward: 'Ward 100',
        location: { coordinates: { lat: 13.0825, lng: 80.2705 } }
      }
    ];

    const hotspots = computeDensityHotspots(complaints, 500);
    assert.strictEqual(hotspots.length, 1);
    assert.strictEqual(hotspots[0].count, 3);
    assert.strictEqual(hotspots[0].criticalCount, 2);
    assert.strictEqual(hotspots[0].highCount, 1);
    assert.ok(hotspots[0].weightedScore >= 7.0);
  });

  it('apiClient.complaints.getNearby formats query parameters correctly', async () => {
    let capturedUrl = null;
    const origFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ success: true, data: [] })
      };
    };

    try {
      await apiClient.complaints.getNearby({
        lat: 13.0827,
        lng: 80.2707,
        radiusKm: 3.5,
        category: 'ROADS',
        status: 'SUBMITTED',
        limit: 15
      });

      assert.ok(capturedUrl.includes('/api/complaints/nearby'));
      assert.ok(capturedUrl.includes('lat=13.0827'));
      assert.ok(capturedUrl.includes('lng=80.2707'));
      assert.ok(capturedUrl.includes('radiusKm=3.5'));
      assert.ok(capturedUrl.includes('category=ROADS'));
      assert.ok(capturedUrl.includes('status=SUBMITTED'));
      assert.ok(capturedUrl.includes('limit=15'));
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('apiClient.complaints.getPublicNearby formats public nearby query parameters', async () => {
    let capturedUrl = null;
    const origFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ success: true, data: [] })
      };
    };

    try {
      await apiClient.complaints.getPublicNearby({
        lat: 9.9252,
        lng: 78.1198,
        radiusKm: 5.0
      });

      assert.ok(capturedUrl.includes('/api/complaints/public/nearby'));
      assert.ok(capturedUrl.includes('lat=9.9252'));
      assert.ok(capturedUrl.includes('lng=78.1198'));
      assert.ok(capturedUrl.includes('radiusKm=5'));
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('apiClient.complaints.getPublicNearby handles k-anonymity suppressed location responses cleanly', async () => {
    const origFetch = globalThis.fetch;
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: async () => ({
        success: true,
        data: [
          {
            referenceId: 'CF-SEC-01',
            title: 'Water pipe leak',
            category: 'DRAINAGE',
            status: 'SUBMITTED',
            priority: 'HIGH',
            address: null,
            latitude: null,
            longitude: null,
            locationSuppressed: true,
            privacySanitized: true,
            distanceMeters: 300.0
          }
        ]
      })
    });

    try {
      const res = await apiClient.complaints.getPublicNearby({ lat: 13.0827, lng: 80.2707, radiusKm: 2.0 });
      assert.ok(res.success);
      assert.strictEqual(res.data.length, 1);
      assert.strictEqual(res.data[0].locationSuppressed, true);
      assert.strictEqual(res.data[0].address, null);
      assert.strictEqual(res.data[0].latitude, null);
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('apiClient.complaints.getPublicNearby handles heterogeneous clusters (dense vs suppressed) in single payload', async () => {
    const origFetch = globalThis.fetch;
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: async () => ({
        success: true,
        data: [
          // Item in dense cluster (published)
          {
            referenceId: 'CF-DENSE-1',
            locationSuppressed: false,
            latitude: 13.085,
            longitude: 80.211,
            address: null,
            privacySanitized: true
          },
          // Item in sparse cluster (suppressed)
          {
            referenceId: 'CF-SPARSE-1',
            locationSuppressed: true,
            latitude: null,
            longitude: null,
            address: null,
            privacySanitized: true
          }
        ]
      })
    });

    try {
      const res = await apiClient.complaints.getPublicNearby({ lat: 13.085, lng: 80.21, radiusKm: 5.0 });
      assert.ok(res.success);
      assert.strictEqual(res.data.length, 2);
      const dense = res.data.find(d => d.referenceId === 'CF-DENSE-1');
      const sparse = res.data.find(d => d.referenceId === 'CF-SPARSE-1');
      assert.strictEqual(dense.locationSuppressed, false);
      assert.strictEqual(dense.latitude, 13.085);
      assert.strictEqual(sparse.locationSuppressed, true);
      assert.strictEqual(sparse.latitude, null);
    } finally {
      globalThis.fetch = origFetch;
    }
  });
});
