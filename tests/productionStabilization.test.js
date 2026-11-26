import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

describe('Production Stabilization Regression Tests', () => {
  const rootDir = process.cwd();

  it('1. ComplaintDetailsPage declares all React hooks before conditional early returns', () => {
    const src = fs.readFileSync(
      path.join(rootDir, 'src', 'pages', 'citizen', 'ComplaintDetailsPage.jsx'),
      'utf-8'
    );
    const firstEarlyReturnIdx = src.indexOf('if (loading) {');
    const downloadingPdfHookIdx = src.indexOf('const [downloadingPdf, setDownloadingPdf] = useState(false);');
    assert.ok(firstEarlyReturnIdx > 0, 'Expected loading early return');
    assert.ok(downloadingPdfHookIdx > 0, 'Expected downloadingPdf useState hook');
    assert.ok(
      downloadingPdfHookIdx < firstEarlyReturnIdx,
      'useState(downloadingPdf) must appear before conditional early returns to prevent ErrorBoundary hook-order crash'
    );
  });

  it('2. Staff UI, locales, and login flow use @civicfix.gov consistently without @metro.gov mismatches', () => {
    const filesToCheck = [
      path.join(rootDir, 'src', 'locales', 'en.json'),
      path.join(rootDir, 'src', 'locales', 'ta.json'),
      path.join(rootDir, 'src', 'pages', 'admin', 'AdminProfilePage.jsx'),
      path.join(rootDir, 'src', 'pages', 'admin', 'CreateStaffPage.jsx'),
      path.join(rootDir, 'src', 'pages', 'officer', 'OfficerProfilePage.jsx'),
      path.join(rootDir, 'src', 'pages', 'public', 'LoginPage.jsx')
    ];
    for (const filePath of filesToCheck) {
      const content = fs.readFileSync(filePath, 'utf-8');
      assert.ok(!content.includes('@metro.gov'), `${path.basename(filePath)} must not reference @metro.gov`);
    }

    const loginSrc = fs.readFileSync(
      path.join(rootDir, 'src', 'pages', 'public', 'LoginPage.jsx'),
      'utf-8'
    );
    assert.ok(loginSrc.includes("@civicfix.gov"), 'LoginPage must handle @civicfix.gov staff verification guidance');
    assert.ok(loginSrc.includes('firebaseAuthService.resendVerificationEmail'), 'LoginPage must support Firebase verification email resend');
  });

  it('3. ReportComplaintPage does not pre-populate hardcoded Anna Salai address or GPS coordinates', () => {
    const src = fs.readFileSync(
      path.join(rootDir, 'src', 'pages', 'citizen', 'ReportComplaintPage.jsx'),
      'utf-8'
    );
    assert.ok(!src.includes("address: 'Anna Salai, Near Spencer Plaza, Chennai'"));
    assert.ok(!src.includes("address: 'Anna Salai, Chennai'"));
    assert.ok(!src.includes('coordinates: { lat: 13.0604, lng: 80.2642 }'));
    assert.ok(src.includes('coordinates: null'));
    assert.ok(src.includes('locationConfirmed: false'));
  });

  it('4. ImageUploadManager initializes video element muted/playsInline and listens to readiness events', () => {
    const src = fs.readFileSync(
      path.join(rootDir, 'src', 'components', 'common', 'ImageUploadManager.jsx'),
      'utf-8'
    );
    assert.ok(src.includes('videoEl.muted = true;'));
    assert.ok(src.includes('videoEl.playsInline = true;'));
    assert.ok(src.includes('onLoadedData'));
    assert.ok(src.includes('onCanPlay'));
  });

  it('5. en.json and ta.json maintain 100% key parity across all namespaces', () => {
    const en = JSON.parse(fs.readFileSync(path.join(rootDir, 'src', 'locales', 'en.json'), 'utf-8'));
    const ta = JSON.parse(fs.readFileSync(path.join(rootDir, 'src', 'locales', 'ta.json'), 'utf-8'));
    const flatten = (obj, prefix = '') =>
      Object.entries(obj).flatMap(([k, v]) =>
        v && typeof v === 'object' && !Array.isArray(v)
          ? flatten(v, prefix ? `${prefix}.${k}` : k)
          : [prefix ? `${prefix}.${k}` : k]
      );
    const enKeys = new Set(flatten(en));
    const taKeys = new Set(flatten(ta));
    assert.deepEqual([...enKeys].filter((k) => !taKeys.has(k)), []);
    assert.deepEqual([...taKeys].filter((k) => !enKeys.has(k)), []);
  });
});
