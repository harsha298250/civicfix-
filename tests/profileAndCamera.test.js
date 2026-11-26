import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import esbuild from 'esbuild';
import { resolveMediaUrl } from '../src/services/complaintService.js';

describe('Profile Location Hierarchy, Media Resolution, Camera & Tamil Parity Tests', () => {
  const rootDir = process.cwd();

  it('1. TAMIL_NADU_DISTRICTS_BILINGUAL has all 38 districts and CHENNAI_GCC_ZONES has all 15 GCC zones including Royapuram', async () => {
    const outFile = path.join(rootDir, 'node_modules', '.cache', 'tn-locations-test.mjs');
    fs.mkdirSync(path.dirname(outFile), { recursive: true });
    await esbuild.build({
      entryPoints: [path.join(rootDir, 'src', 'data', 'tamilNaduLocations.js')],
      outfile: outFile,
      bundle: true,
      format: 'esm',
      platform: 'node'
    });
    const {
      TAMIL_NADU_DISTRICTS_BILINGUAL,
      CHENNAI_GCC_ZONES,
      getDistrictDisplayLabel,
      getZoneDisplayLabel
    } = await import(`${pathToFileURL(outFile).href}?t=${Date.now()}`);

    assert.equal(TAMIL_NADU_DISTRICTS_BILINGUAL.length, 38);
    assert.equal(CHENNAI_GCC_ZONES.length, 15);
    const royapuram = CHENNAI_GCC_ZONES.find((z) => z.zoneName === 'Royapuram');
    assert.ok(royapuram);
    assert.equal(royapuram.value, 'Zone 05, Royapuram, Chennai');
    assert.equal(royapuram.coveredWards.length, 15);
    assert.ok(royapuram.coveredWards.includes('Ward 49'));
    assert.ok(royapuram.coveredWards.includes('Ward 63'));
    assert.ok(getDistrictDisplayLabel('Chennai', true).includes('சென்னை'));
    assert.ok(getZoneDisplayLabel('Zone 05, Royapuram, Chennai', true).includes('ராயபுரம்'));
  });

  it('2. resolveMediaUrl resolves /api/... paths against backend API origin while preserving presigned/blob URLs', () => {
    assert.equal(resolveMediaUrl('https://bucket.s3.amazonaws.com/img.jpg?sig=1'), 'https://bucket.s3.amazonaws.com/img.jpg?sig=1');
    assert.equal(resolveMediaUrl('blob:https://civicfix-frontend-qkgw.onrender.com/123'), 'blob:https://civicfix-frontend-qkgw.onrender.com/123');
    const resolved = resolveMediaUrl('/api/complaints/photos/files/abc.jpg');
    assert.ok(resolved.endsWith('/api/complaints/photos/files/abc.jpg'));
  });

  it('3. ImageUploadManager.jsx gates Live Viewfinder, disables capture until cameraReady, and handles permission denial with Retry', () => {
    const src = fs.readFileSync(path.join(rootDir, 'src', 'components', 'common', 'ImageUploadManager.jsx'), 'utf-8');
    assert.ok(src.includes('cameraStream && cameraReady && !cameraError'), 'Must only show Live Viewfinder when stream is active and ready');
    assert.ok(src.includes('disabled={!cameraStream || !cameraReady || Boolean(cameraError)}'), 'Must disable capture when camera is not ready');
    assert.ok(src.includes('Retry Camera'), 'Must provide Retry Camera button on permission denial');
    assert.ok(!src.includes('Evidence Tag: Authenticated In-Browser Capture'), 'Must not fabricate placeholder snapshot when camera is inactive');
  });

  it('4. en.json and ta.json have 100% key parity for profile and common namespaces', () => {
    const en = JSON.parse(fs.readFileSync(path.join(rootDir, 'src', 'locales', 'en.json'), 'utf-8'));
    const ta = JSON.parse(fs.readFileSync(path.join(rootDir, 'src', 'locales', 'ta.json'), 'utf-8'));
    assert.deepEqual(Object.keys(en.profile).sort(), Object.keys(ta.profile).sort());
    for (const k of Object.keys(en.common)) {
      assert.ok(k in ta.common, `Missing common.${k} in ta.json`);
    }
  });
});
