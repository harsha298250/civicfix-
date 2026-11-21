import { describe, it } from 'node:test';
import assert from 'node:assert';
import stateGeoJson from '../src/data/gis/tamil_nadu_state.json' with { type: 'json' };
import zonesGeoJson from '../src/data/gis/chennai_gcc_zones.json' with { type: 'json' };
import { apiClient } from '../src/services/apiClient.js';

describe('GIS Intelligence & Boundary Datasets', () => {
  it('Tamil Nadu state GeoJSON is valid with Survey of India metadata', () => {
    assert.strictEqual(stateGeoJson.type, 'FeatureCollection');
    assert.ok(stateGeoJson.metadata);
    assert.ok(stateGeoJson.metadata.source.includes('Survey of India'));
    assert.strictEqual(stateGeoJson.metadata.crs, 'urn:ogc:def:crs:OGC:1.3:CRS84 (EPSG:4326)');

    assert.ok(Array.isArray(stateGeoJson.features));
    assert.strictEqual(stateGeoJson.features.length, 1);

    const tnFeature = stateGeoJson.features[0];
    assert.strictEqual(tnFeature.properties.stateCode, 'TN');
    assert.strictEqual(tnFeature.properties.stateName, 'Tamil Nadu');
    assert.strictEqual(tnFeature.geometry.type, 'Polygon');
    assert.ok(tnFeature.geometry.coordinates[0].length >= 30, 'Should have detailed polygon ring');
  });

  it('Chennai GCC Zones GeoJSON includes all 15 official zones with valid geometry', () => {
    assert.strictEqual(zonesGeoJson.type, 'FeatureCollection');
    assert.ok(zonesGeoJson.metadata);
    assert.ok(zonesGeoJson.metadata.source.includes('Greater Chennai Corporation'));
    assert.strictEqual(zonesGeoJson.features.length, 15, 'Must contain all 15 GCC zones');

    const expectedZones = [
      'Thiruvottiyur', 'Manali', 'Madhavaram', 'Tondiarpet', 'Royapuram',
      'Thiru. Vi. Ka. Nagar', 'Ambattur', 'Anna Nagar', 'Teynampet', 'Kodambakkam',
      'Valasaravakkam', 'Alandur', 'Adyar', 'Perungudi', 'Sholinganallur'
    ];

    expectedZones.forEach((expectedName) => {
      const match = zonesGeoJson.features.find(f => f.properties.zoneName === expectedName);
      assert.ok(match, `Zone ${expectedName} should be present`);
      assert.strictEqual(match.geometry.type, 'Polygon');
      assert.ok(match.geometry.coordinates[0].length >= 4, 'Coordinates ring must close');
      assert.ok(Array.isArray(match.properties.coveredWards));
      assert.ok(match.properties.coveredWards.length > 0, 'Should list covered wards');
    });
  });

  it('apiClient.gis exposes state, zones and validation endpoints', () => {
    assert.strictEqual(typeof apiClient.gis.reverseGeocode, 'function');
    assert.strictEqual(typeof apiClient.gis.getBoundariesState, 'function');
    assert.strictEqual(typeof apiClient.gis.getBoundariesZones, 'function');
    assert.strictEqual(typeof apiClient.gis.validateLocation, 'function');
  });

  it('Anna Nagar coordinate (13.085, 80.210) falls strictly within Zone VIII bounding box', () => {
    const annaNagar = zonesGeoJson.features.find(f => f.properties.zoneName === 'Anna Nagar');
    assert.ok(annaNagar);
    const ring = annaNagar.geometry.coordinates[0];

    const lons = ring.map(pt => pt[0]);
    const lats = ring.map(pt => pt[1]);
    const minLon = Math.min(...lons);
    const maxLon = Math.max(...lons);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);

    const ptLat = 13.085;
    const ptLon = 80.210;

    assert.ok(ptLon >= minLon && ptLon <= maxLon, `Lon ${ptLon} should be in [${minLon}, ${maxLon}]`);
    assert.ok(ptLat >= minLat && ptLat <= maxLat, `Lat ${ptLat} should be in [${minLat}, ${maxLat}]`);
  });

  it('Gazette No. 408 & 2026 Disaster Plan ward reconciliation: Zone XIII (168-180) and Zone XIV (181-191)', () => {
    const z13 = zonesGeoJson.features.find(f => f.properties.zoneNumber === 'XIII');
    const z14 = zonesGeoJson.features.find(f => f.properties.zoneNumber === 'XIV');

    assert.ok(z13);
    assert.ok(z14);

    const w13 = z13.properties.coveredWards;
    const w14 = z14.properties.coveredWards;

    assert.strictEqual(w13.length, 13, 'Zone XIII must have 13 wards (168-180)');
    assert.strictEqual(w13[0], 'Ward 168');
    assert.strictEqual(w13[w13.length - 1], 'Ward 180');
    assert.ok(!w13.includes('Ward 181'), 'Ward 181 must not belong to Zone XIII');

    assert.strictEqual(w14.length, 11, 'Zone XIV must have 11 wards (181-191)');
    assert.strictEqual(w14[0], 'Ward 181');
    assert.strictEqual(w14[w14.length - 1], 'Ward 191');

    // Verify all 200 wards
    const allWards = [];
    zonesGeoJson.features.forEach(f => allWards.push(...f.properties.coveredWards));
    assert.strictEqual(allWards.length, 200, 'Must have exactly 200 wards across GCC');
    const uniqueWards = new Set(allWards);
    assert.strictEqual(uniqueWards.size, 200, 'All 200 wards must be distinct');
    for (let i = 1; i <= 200; i++) {
      assert.ok(uniqueWards.has('Ward ' + i), `Ward ${i} must exist`);
    }
  });
});
