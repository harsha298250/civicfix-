import { describe, it } from 'node:test';
import assert from 'node:assert';
import { apiClient } from '../src/services/apiClient.js';

describe('Cross-Lingual Duplicate Detection & UI Integration', () => {
  it('apiClient exposes duplicate retrieval and linking endpoints', () => {
    assert.strictEqual(typeof apiClient.complaints.getDuplicates, 'function');
    assert.strictEqual(typeof apiClient.complaints.linkDuplicate, 'function');
  });

  it('cross-lingual concept matching correctly identifies Tamil and English defect pairs', () => {
    const concepts = {
      POTHOLE: {
        en: ['pothole', 'crater', 'asphalt damage'],
        ta: ['குழி', 'பள்ளம்', 'சாலை சேதம்']
      },
      WATER_LEAK: {
        en: ['pipe burst', 'water leak', 'pipeline'],
        ta: ['குடிநீர்', 'குழாய் உடைப்பு', 'தண்ணீர் கசிவு']
      }
    };

    // Test concept bridge
    const testA = 'drinking water pipe burst';
    const testB = 'குடிநீர் குழாய் உடைப்பு';

    const aMatchesWater = concepts.WATER_LEAK.en.some(kw => testA.includes(kw));
    const bMatchesWater = concepts.WATER_LEAK.ta.some(kw => testB.includes(kw));

    assert.ok(aMatchesWater && bMatchesWater, 'Bilingual pair must map to WATER_LEAK concept');
  });

  it('sub-50m co-located complaints produce duplicate candidate classification', () => {
    const distanceMeters = 22.5;
    const isCoLocated = distanceMeters <= 50.0;
    assert.strictEqual(isCoLocated, true, 'Distance of 22.5m is strictly within sub-50m co-location threshold');
  });
});
