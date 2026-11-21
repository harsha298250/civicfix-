import { describe, it } from 'node:test';
import assert from 'node:assert';
import { suggestCategoryFromText, mapBackendCategoryToFrontendId } from '../src/utils/triageUtils.js';
import { apiClient } from '../src/services/apiClient.js';

describe('AI Complaint Classification & Lexicon Matching', () => {
  it('identifies roads category from English keywords', () => {
    const result = suggestCategoryFromText('Deep pothole and cracked asphalt near junction');
    assert.strictEqual(result, 'roads');
  });

  it('identifies roads category with typo tolerance on pothol', () => {
    const result = suggestCategoryFromText('Large pothol causing vehicle damage');
    assert.strictEqual(result, 'roads');
  });

  it('identifies water category from authentic Tamil script', () => {
    const result = suggestCategoryFromText('குடிநீர் குழாய் உடைந்து தண்ணீர் வீணாகிறது');
    assert.strictEqual(result, 'water');
  });

  it('identifies lighting category from Tamil street light text', () => {
    const result = suggestCategoryFromText('தெருவிளக்கு எரியவில்லை இருட்டாக உள்ளது');
    assert.strictEqual(result, 'lighting');
  });

  it('identifies waste category from Tamil garbage phrase', () => {
    const result = suggestCategoryFromText('தெருவில் குப்பை அள்ளப்படவில்லை துர்நாற்றம் வீசுகிறது');
    assert.strictEqual(result, 'waste');
  });

  it('mapBackendCategoryToFrontendId maps all backend categories correctly', () => {
    assert.strictEqual(mapBackendCategoryToFrontendId('ROADS'), 'roads');
    assert.strictEqual(mapBackendCategoryToFrontendId('WATER_SEWERAGE'), 'water');
    assert.strictEqual(mapBackendCategoryToFrontendId('STREET_LIGHT'), 'lighting');
    assert.strictEqual(mapBackendCategoryToFrontendId('GARBAGE'), 'waste');
    assert.strictEqual(mapBackendCategoryToFrontendId('DRAINAGE'), 'water');
    assert.strictEqual(mapBackendCategoryToFrontendId('PUBLIC_TRANSPORT'), 'traffic');
    assert.strictEqual(mapBackendCategoryToFrontendId('PARKS'), 'parks');
  });

  it('apiClient.complaints.suggestCategory function is exposed', () => {
    assert.strictEqual(typeof apiClient.complaints.suggestCategory, 'function');
  });
});
