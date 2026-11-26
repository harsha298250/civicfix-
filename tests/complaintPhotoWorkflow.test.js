import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

describe('Complaint Photo Storage, Authenticated Viewing, Lightbox & Download Tests', () => {
  const rootDir = process.cwd();

  it('1. AuthenticatedImage handles 401/403, 404, general errors, full-size lightbox, and authenticated download', () => {
    const src = fs.readFileSync(
      path.join(rootDir, 'src', 'components', 'common', 'AuthenticatedImage.jsx'),
      'utf-8'
    );
    assert.ok(src.includes("'/api/complaints/photos/files/'"));
    assert.ok(src.includes("credentials: 'include'"));
    assert.ok(src.includes("UNAUTHORIZED: 'Access Restricted'"));
    assert.ok(src.includes("NOT_FOUND: 'Image Not Found'"));
    assert.ok(src.includes('enableLightbox'));
    assert.ok(src.includes('role="dialog"'));
    assert.ok(src.includes('a.download = inferredName'));
  });

  it('2. Citizen, Officer, and Worker complaint/task viewers use AuthenticatedImage with lightbox instead of unauthenticated <img>', () => {
    const pages = [
      path.join(rootDir, 'src', 'pages', 'citizen', 'ComplaintDetailsPage.jsx'),
      path.join(rootDir, 'src', 'pages', 'officer', 'ComplaintReviewPage.jsx'),
      path.join(rootDir, 'src', 'pages', 'worker', 'TaskDetailsPage.jsx'),
      path.join(rootDir, 'src', 'pages', 'officer', 'ResolutionVerificationPage.jsx')
    ];

    for (const p of pages) {
      const content = fs.readFileSync(p, 'utf-8');
      assert.ok(content.includes('AuthenticatedImage'), `${path.basename(p)} must use AuthenticatedImage`);
      assert.ok(content.includes('enableLightbox'), `${path.basename(p)} must enable full-size lightbox preview`);
    }
  });
});
