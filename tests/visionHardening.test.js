import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { compressAndNormalizeImage } from '../src/utils/imageUtils.js';

describe('Phase 2.4.4: AI Image Analysis Hardening & Privacy Tests', () => {

  test('compressAndNormalizeImage normalizes raw filename and image format to JPEG', async () => {
    const mockFile = {
      name: 'broken_pothole_site_photo.png',
      size: 4500000,
      type: 'image/png'
    };

    const result = await compressAndNormalizeImage(mockFile, 1600, 0.82);

    assert.ok(result, 'Result should be generated');
    assert.strictEqual(result.type, 'image/jpeg', 'Output mime type must be image/jpeg');
    assert.ok(result.name.endsWith('.jpg'), 'Normalized file extension must end in .jpg');
    assert.ok(result.sizeFormatted.includes('KB') || result.sizeFormatted.includes('MB'));
  });

  test('compressAndNormalizeImage handles null or empty inputs gracefully', async () => {
    const result = await compressAndNormalizeImage(null);
    assert.strictEqual(result, null);
  });

  test('WebP format converted and sanitized name', async () => {
    const mockWebpFile = {
      name: 'chennai_t_nagar_water_leak.webp',
      size: 1200000,
      type: 'image/webp'
    };

    const result = await compressAndNormalizeImage(mockWebpFile);
    assert.strictEqual(result.name, 'chennai_t_nagar_water_leak.jpg');
    assert.strictEqual(result.type, 'image/jpeg');
  });

});
