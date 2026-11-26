import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import esbuild from 'esbuild';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { mapFirebaseAuthError } from '../src/services/firebaseAuth.js';
import { apiRequest } from '../src/services/apiClient.js';
import { authService } from '../src/services/authService.js';

describe('Staff Auth, Session Restoration & Officer Dashboard Hotfix Tests', () => {
  const rootDir = process.cwd();

  it('1. OfficerLayout & OfficerDashboard render safely with empty or missing notifications/complaints (no unreadOfficerNotifs ReferenceError)', async () => {
    const layoutPath = path.join(rootDir, 'src', 'components', 'layout', 'OfficerLayout.jsx');
    const layoutSrc = fs.readFileSync(layoutPath, 'utf-8');
    assert.ok(!layoutSrc.includes('unreadOfficerNotifs'), 'Must not reference undeclared unreadOfficerNotifs');

    const outFile = path.join(rootDir, 'node_modules', '.cache', 'officer-layout-test.mjs');
    fs.mkdirSync(path.dirname(outFile), { recursive: true });

    const stubPlugin = {
      name: 'stub-officer-layout',
      setup(build) {
        build.onResolve({ filter: /context\/AppContext$/ }, () => ({
          path: 'app-ctx-stub',
          namespace: 'stub'
        }));
        build.onLoad({ filter: /^app-ctx-stub$/, namespace: 'stub' }, () => ({
          contents: `export const useApp = () => ({ currentUser: { id: '1', name: 'Officer', role: 'OFFICER' }, complaints: undefined, unreadCount: undefined, toast: null, logout: async () => {} });`,
          loader: 'js'
        }));
      }
    };

    await esbuild.build({
      entryPoints: [layoutPath],
      outfile: outFile,
      bundle: true,
      format: 'esm',
      platform: 'node',
      external: ['react', 'react-dom', 'react-router-dom'],
      plugins: [stubPlugin],
      loader: { '.jsx': 'jsx', '.js': 'js' }
    });

    const { OfficerLayout } = await import(`${pathToFileURL(outFile).href}?t=${Date.now()}`);
    const html = renderToString(
      React.createElement(
        MemoryRouter,
        { initialEntries: ['/officer/dashboard'] },
        React.createElement(OfficerLayout)
      )
    );
    assert.ok(html.includes('Municipal Ops'), 'OfficerLayout must render cleanly even when notifications/complaints are undefined');
  });

  it('2. OfficerDashboard renders cleanly with empty notifications and no summary/queue data', async () => {
    const dashPath = path.join(rootDir, 'src', 'pages', 'officer', 'OfficerDashboard.jsx');
    const outFile = path.join(rootDir, 'node_modules', '.cache', 'officer-dash-test.mjs');
    fs.mkdirSync(path.dirname(outFile), { recursive: true });

    const stubPlugin = {
      name: 'stub-officer-dash',
      setup(build) {
        build.onResolve({ filter: /context\/AppContext$/ }, () => ({
          path: 'app-ctx-stub',
          namespace: 'stub'
        }));
        build.onLoad({ filter: /^app-ctx-stub$/, namespace: 'stub' }, () => ({
          contents: `export const useApp = () => ({ currentUser: { id: '1', name: 'Officer', role: 'OFFICER' } });`,
          loader: 'js'
        }));
      }
    };

    await esbuild.build({
      entryPoints: [dashPath],
      outfile: outFile,
      bundle: true,
      format: 'esm',
      platform: 'node',
      external: ['react', 'react-dom', 'react-router-dom'],
      plugins: [stubPlugin],
      loader: { '.jsx': 'jsx', '.js': 'js' }
    });

    const { OfficerDashboard } = await import(`${pathToFileURL(outFile).href}?t=${Date.now()}`);
    const html = renderToString(
      React.createElement(
        MemoryRouter,
        { initialEntries: ['/officer/dashboard'] },
        React.createElement(OfficerDashboard)
      )
    );
    assert.ok(html.length > 0, 'OfficerDashboard must render initial loading/empty state cleanly');
  });

  it('3. Firebase invalid-password and 400 error codes map to safe, user-friendly messages', () => {
    assert.match(mapFirebaseAuthError({ code: 'auth/wrong-password' }), /Incorrect password/i);
    assert.match(mapFirebaseAuthError({ code: 'auth/invalid-credential' }), /Invalid email address or password/i);
    assert.match(mapFirebaseAuthError({ code: 'auth/invalid-login-credentials' }), /Invalid email address or password/i);
    assert.match(mapFirebaseAuthError({ code: 'auth/missing-password' }), /enter your account password/i);
    assert.match(mapFirebaseAuthError({ code: 'auth/user-disabled' }), /disabled/i);
  });

  it('4. AppContext does not fetch /api/complaints before authentication completes', () => {
    const appCtxSrc = fs.readFileSync(path.join(rootDir, 'src', 'context', 'AppContext.jsx'), 'utf-8');
    const bootstrapEffectStart = appCtxSrc.indexOf('useEffect(() => {');
    const bootstrapEffectEnd = appCtxSrc.indexOf('authService.checkSession()');
    const bootstrapHeader = appCtxSrc.slice(bootstrapEffectStart, bootstrapEffectEnd);
    assert.ok(
      !bootstrapHeader.includes('refreshComplaints()'),
      'AppContext must NOT call refreshComplaints() unconditionally before checkSession() resolves'
    );
  });

  it('5. Session exchange failure rejects cleanly without fabricating a user', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => ({
      ok: false,
      status: 401,
      headers: { get: () => 'application/json' },
      json: async () => ({ message: 'Firebase token rejected' })
    });

    try {
      await assert.rejects(
        () => apiRequest('/auth/firebase/session', { method: 'POST', body: { idToken: 'bad-token' } }),
        (err) => err.status === 401 && err.message.includes('Firebase token rejected')
      );
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('6. Session restoration after page refresh restores staff role and authenticated complaints request sends credentials', async () => {
    const originalFetch = globalThis.fetch;
    const calls = [];

    globalThis.fetch = async (url, config) => {
      calls.push({ url, config });
      if (url.includes('/auth/me')) {
        return {
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: async () => ({
            data: {
              id: '10000000-0000-0000-0000-000000000002',
              name: 'Elena Vance',
              email: 'officer@civicfix.gov',
              role: 'OFFICER',
              roles: ['OFFICER'],
              accountStatus: 'ACTIVE'
            }
          })
        };
      }
      if (url.includes('/complaints')) {
        return {
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: async () => ({ data: [] })
        };
      }
      return {
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({})
      };
    };

    try {
      const restored = await authService.checkSession();
      assert.equal(restored.role, 'OFFICER');
      assert.equal(restored.email, 'officer@civicfix.gov');

      await apiRequest('/complaints');
      const complaintCall = calls.find((c) => c.url.includes('/complaints'));
      assert.ok(complaintCall, 'Expected GET /complaints call');
      assert.equal(complaintCall.config.credentials, 'include');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('7. LoginPage and RoleRoute route all four staff roles using backend user.role, not hardcoded emails', () => {
    const loginSrc = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'public', 'LoginPage.jsx'), 'utf-8');
    const roleRouteSrc = fs.readFileSync(path.join(rootDir, 'src', 'components', 'common', 'RoleRoute.jsx'), 'utf-8');

    assert.ok(loginSrc.includes("if (user.role === 'OFFICER') navigate('/officer/dashboard')"));
    assert.ok(loginSrc.includes("else if (user.role === 'SUPERVISOR') navigate('/supervisor/escalations')"));
    assert.ok(loginSrc.includes("else if (user.role === 'WORKER') navigate('/worker/dashboard')"));
    assert.ok(loginSrc.includes("else if (user.role === 'ADMIN') navigate('/admin/dashboard')"));

    assert.ok(roleRouteSrc.includes("case 'ADMIN':"));
    assert.ok(roleRouteSrc.includes("case 'SUPERVISOR':"));
    assert.ok(roleRouteSrc.includes("case 'OFFICER':"));
    assert.ok(roleRouteSrc.includes("case 'WORKER':"));
  });
});
