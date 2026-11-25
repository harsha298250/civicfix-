import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import esbuild from 'esbuild';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';

describe('ReportComplaintPage — Regression Tests (i18n & Mount Integrity)', () => {
  const rootDir = process.cwd();
  const pagePath = path.join(rootDir, 'src', 'pages', 'citizen', 'ReportComplaintPage.jsx');

  it('1. ReportComplaintPage.jsx does not reference undeclared i18n and binds language from useTranslation()', () => {
    const content = fs.readFileSync(pagePath, 'utf-8');
    assert.ok(!/\bi18n\b/.test(content), 'Must not reference undeclared i18n identifier');
    assert.ok(content.includes('const { t, language } = useTranslation();'), 'Must destructure language from useTranslation()');
    assert.ok(content.includes('[formData.title, formData.description, language]'), 'Category suggestion effect must depend on language');
  });

  it('2. ReportComplaintPage mounts and renders in React without throwing ReferenceError', async () => {
    const outFile = path.join(rootDir, 'node_modules', '.cache', 'report-complaint-page-test.mjs');
    fs.mkdirSync(path.dirname(outFile), { recursive: true });

    const stubAppContextPlugin = {
      name: 'stub-dependencies',
      setup(build) {
        build.onResolve({ filter: /^leaflet$/ }, () => ({
          path: 'leaflet-stub',
          namespace: 'stub'
        }));
        build.onResolve({ filter: /context\/AppContext$/ }, () => ({
          path: 'app-context-stub',
          namespace: 'stub'
        }));
        build.onLoad({ filter: /^leaflet-stub$/, namespace: 'stub' }, () => ({
          contents: `export default { divIcon: () => ({}), map: () => ({ setView: () => {}, on: () => {}, invalidateSize: () => {}, remove: () => {} }), tileLayer: () => ({ on: () => {}, addTo: () => {} }), marker: () => ({ on: () => {}, addTo: () => {}, setLatLng: () => {} }), circle: () => ({ addTo: () => {}, setLatLng: () => {}, setRadius: () => {} }) };`,
          loader: 'js'
        }));
        build.onLoad({ filter: /^app-context-stub$/, namespace: 'stub' }, () => ({
          contents: `export const useApp = () => ({ currentUser: { id: 'usr-test', name: 'Test Resident', ward: 'Zone 05, Royapuram, Chennai' }, refreshComplaints: async () => {}, showToast: () => {} });`,
          loader: 'js'
        }));
      }
    };

    await esbuild.build({
      entryPoints: [pagePath],
      outfile: outFile,
      bundle: true,
      format: 'esm',
      platform: 'node',
      external: ['react', 'react-dom', 'react-router-dom'],
      plugins: [stubAppContextPlugin],
      loader: { '.jsx': 'jsx', '.js': 'js' }
    });

    const mod = await import(`${pathToFileURL(outFile).href}?t=${Date.now()}`);
    const { ReportComplaintPage } = mod;

    // Render directly with MemoryRouter + ReportComplaintPage (useTranslation provides LanguageContext fallback)
    const html = renderToString(
      React.createElement(
        MemoryRouter,
        { initialEntries: ['/citizen/complaints/new'] },
        React.createElement(ReportComplaintPage)
      )
    );

    assert.ok(typeof html === 'string' && html.length > 0, 'ReportComplaintPage must render non-empty HTML');
    assert.ok(html.includes('Report') || html.includes('Issue') || html.includes('Step'), 'Rendered page must contain complaint wizard markup');
  });
});
