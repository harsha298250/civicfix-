import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import esbuild from 'esbuild';
import React from 'react';
import { renderToString } from 'react-dom/server';

const storage = new Map();
const mockStorage = {
  getItem: (key) => (storage.has(key) ? storage.get(key) : null),
  setItem: (key, val) => storage.set(key, String(val)),
  removeItem: (key) => storage.delete(key),
  clear: () => storage.clear()
};
globalThis.localStorage = mockStorage;
globalThis.window = { localStorage: mockStorage };

describe('CivicFix Theme System — Light, Dark, System & Persistence Tests', () => {
  const rootDir = process.cwd();
  const cacheDir = path.join(rootDir, 'node_modules', '.cache');
  let themeMod;

  it('1. Compiles ThemeContext and ThemeToggle cleanly', async () => {
    fs.mkdirSync(cacheDir, { recursive: true });
    const ctxOut = path.join(cacheDir, 'theme-context-test.mjs');
    await esbuild.build({
      entryPoints: [path.join(rootDir, 'src', 'context', 'ThemeContext.jsx')],
      outfile: ctxOut,
      bundle: true,
      format: 'esm',
      platform: 'node',
      external: ['react'],
      loader: { '.jsx': 'jsx', '.js': 'js' }
    });
    themeMod = await import(`${pathToFileURL(ctxOut).href}?t=${Date.now()}`);
    assert.equal(typeof themeMod.resolveTheme, 'function');
    assert.equal(typeof themeMod.ThemeProvider, 'function');
    assert.equal(typeof themeMod.useTheme, 'function');
  });

  it('2. Resolves light, dark, and system themes accurately', () => {
    assert.equal(themeMod.resolveTheme('light', true), 'light');
    assert.equal(themeMod.resolveTheme('light', false), 'light');
    assert.equal(themeMod.resolveTheme('dark', false), 'dark');
    assert.equal(themeMod.resolveTheme('dark', true), 'dark');
    assert.equal(themeMod.resolveTheme('system', true), 'dark');
    assert.equal(themeMod.resolveTheme('system', false), 'light');
  });

  it('3. Persists and restores theme preference from localStorage across refreshes', () => {
    storage.clear();
    assert.equal(themeMod.readStoredTheme(), 'system');

    localStorage.setItem(themeMod.THEME_STORAGE_KEY, 'dark');
    assert.equal(themeMod.readStoredTheme(), 'dark');

    localStorage.setItem(themeMod.THEME_STORAGE_KEY, 'light');
    assert.equal(themeMod.readStoredTheme(), 'light');

    localStorage.setItem(themeMod.THEME_STORAGE_KEY, 'invalid-value');
    assert.equal(themeMod.readStoredTheme(), 'system');
  });

  it('4. Renders ThemeToggle with accessible ARIA label and mounts in all required layouts', async () => {
    const toggleOut = path.join(cacheDir, 'theme-toggle-test.mjs');
    await esbuild.build({
      entryPoints: [path.join(rootDir, 'src', 'components', 'common', 'ThemeToggle.jsx')],
      outfile: toggleOut,
      bundle: true,
      format: 'esm',
      platform: 'node',
      external: ['react'],
      loader: { '.jsx': 'jsx', '.js': 'js' }
    });
    const { default: ThemeToggle } = await import(`${pathToFileURL(toggleOut).href}?t=${Date.now()}`);

    const buttonHtml = renderToString(React.createElement(ThemeToggle));
    assert.ok(buttonHtml.includes('aria-label="Theme:'), 'Button variant must have accessible aria-label');
    assert.ok(buttonHtml.includes('data-testid="theme-toggle-button"'));

    const selectorHtml = renderToString(React.createElement(ThemeToggle, { variant: 'selector' }));
    assert.ok(selectorHtml.includes('role="radiogroup"'));
    assert.ok(selectorHtml.includes('Light') && selectorHtml.includes('Dark') && selectorHtml.includes('System'));

    const requiredFiles = [
      'src/components/layout/PublicLayout.jsx',
      'src/components/layout/CitizenLayout.jsx',
      'src/components/layout/OfficerLayout.jsx',
      'src/components/layout/SupervisorLayout.jsx',
      'src/components/layout/WorkerLayout.jsx',
      'src/components/layout/AdminLayout.jsx',
      'src/pages/public/LoginPage.jsx',
      'src/pages/public/RegisterPage.jsx',
      'src/pages/citizen/CitizenProfilePage.jsx'
    ];
    for (const rel of requiredFiles) {
      const src = fs.readFileSync(path.join(rootDir, rel), 'utf-8');
      assert.ok(src.includes('<ThemeToggle'), `${rel} must mount <ThemeToggle />`);
    }

    const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf-8');
    assert.ok(indexHtml.includes('civicfix_theme'), 'index.html must include pre-render theme script to prevent FOUC');
  });
});
