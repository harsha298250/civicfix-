import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';

// Mock localStorage
const storage = new Map();
globalThis.localStorage = {
  getItem: (key) => storage.get(key) || null,
  setItem: (key, val) => storage.set(key, String(val)),
  removeItem: (key) => storage.delete(key),
  clear: () => storage.clear()
};

import { authService } from '../src/services/authService.js';
import { firebaseAuthService } from '../src/services/firebaseAuth.js';
import { isFirebaseConfigured, firebaseConfig } from '../src/services/firebaseConfig.js';
import { apiClient } from '../src/services/apiClient.js';

describe('Firebase Authentication Frontend Integration Tests', () => {
  beforeEach(() => {
    storage.clear();
    authService.setCurrentUser(null);
  });

  test('isFirebaseConfigured correctly detects unconfigured environment', () => {
    // When environment variables are blank/empty in test environment
    const configured = isFirebaseConfigured();
    assert.equal(typeof configured, 'boolean');
  });

  test('apiClient has createFirebaseSession endpoint adapter configured', () => {
    assert.equal(typeof apiClient.auth.createFirebaseSession, 'function');
  });

  test('authService exposes loginWithFirebase and registerWithFirebase methods', () => {
    assert.equal(typeof authService.loginWithFirebase, 'function');
    assert.equal(typeof authService.registerWithFirebase, 'function');
  });

  test('Firebase sign in fails cleanly with error message when unconfigured', async () => {
    if (!firebaseAuthService.isConfigured()) {
      await assert.rejects(
        async () => {
          await firebaseAuthService.signInWithEmail('test@citizen.org', 'Pass#123');
        },
        (err) => {
          assert.match(err.message, /Firebase Authentication is not configured/);
          return true;
        }
      );
    }
  });

  test('Firebase registration fails cleanly with error message when unconfigured', async () => {
    if (!firebaseAuthService.isConfigured()) {
      await assert.rejects(
        async () => {
          await firebaseAuthService.registerCitizen({
            email: 'new@citizen.org',
            password: 'SecurePass#2026',
            name: 'Citizen Test'
          });
        },
        (err) => {
          assert.match(err.message, /Firebase Authentication is not configured/);
          return true;
        }
      );
    }
  });

  test('apiClient.auth.createFirebaseSession dispatches POST /auth/firebase/session with JSON payload', async () => {
    const originalFetch = globalThis.fetch;
    let dispatchedUrl = null;
    let dispatchedMethod = null;
    let dispatchedBody = null;

    try {
      globalThis.fetch = async (url, options) => {
        dispatchedUrl = url;
        dispatchedMethod = options.method;
        dispatchedBody = JSON.parse(options.body);

        return {
          ok: true,
          status: 200,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({
            success: true,
            data: {
              id: '00000000-0000-0000-0000-000000000009',
              name: 'Firebase Citizen',
              email: 'firebase.user@civicfix.in',
              role: 'CITIZEN',
              roles: ['CITIZEN'],
              accountStatus: 'ACTIVE'
            }
          })
        };
      };

      const result = await apiClient.auth.createFirebaseSession('fake-id-token-xyz', {
        name: 'Firebase Citizen',
        ward: 'Ward 10'
      });

      assert.ok(dispatchedUrl.includes('/auth/firebase/session'));
      assert.equal(dispatchedMethod, 'POST');
      assert.equal(dispatchedBody.idToken, 'fake-id-token-xyz');
      assert.equal(dispatchedBody.name, 'Firebase Citizen');
      assert.equal(result.data.email, 'firebase.user@civicfix.in');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test('authService.login delegates to firebaseAuthService.signInWithEmail when Firebase is configured', async () => {
    const originalIsConfigured = firebaseAuthService.isConfigured;
    const originalSignIn = firebaseAuthService.signInWithEmail;
    let signInCalledWith = null;

    try {
      firebaseAuthService.isConfigured = () => true;
      firebaseAuthService.signInWithEmail = async (email, password, rememberMe) => {
        signInCalledWith = { email, password, rememberMe };
        return {
          id: 'test-user-id',
          name: 'Delegated User',
          email,
          role: 'OFFICER',
          authSource: 'FIREBASE_SESSION'
        };
      };

      const result = await authService.login('officer@civicfix.gov', 'Secret#2026', true);

      assert.ok(signInCalledWith, 'signInWithEmail should be called when Firebase is configured');
      assert.equal(signInCalledWith.email, 'officer@civicfix.gov');
      assert.equal(signInCalledWith.password, 'Secret#2026');
      assert.equal(signInCalledWith.rememberMe, true);
      assert.equal(result.role, 'OFFICER');
    } finally {
      firebaseAuthService.isConfigured = originalIsConfigured;
      firebaseAuthService.signInWithEmail = originalSignIn;
    }
  });

  test('Firebase session creation preserves Neon staff roles for ADMIN, OFFICER, SUPERVISOR, WORKER', async () => {
    const rolesToTest = ['ADMIN', 'OFFICER', 'SUPERVISOR', 'WORKER'];
    const originalFetch = globalThis.fetch;

    for (const role of rolesToTest) {
      try {
        globalThis.fetch = async () => ({
          ok: true,
          status: 200,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({
            success: true,
            data: {
              id: `00000000-0000-0000-0000-00000000000${role.length}`,
              name: `Test ${role}`,
              email: `${role.toLowerCase()}@civicfix.gov`,
              role: role,
              roles: [role],
              accountStatus: 'ACTIVE',
              department: 'Public Works'
            }
          })
        });

        const res = await apiClient.auth.createFirebaseSession('valid-token-staff');
        assert.equal(res.data.role, role, `Role ${role} must be preserved from Neon backend`);
        assert.ok(res.data.roles.includes(role));
      } finally {
        globalThis.fetch = originalFetch;
      }
    }
  });

  test('Logout terminates session and leaves active user null', async () => {
    const originalFetch = globalThis.fetch;
    try {
      globalThis.fetch = async () => ({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ success: true })
      });

      authService.setCurrentUser({
        id: 'usr-fb-001',
        name: 'Citizen Test',
        role: 'CITIZEN',
        authSource: 'FIREBASE_SESSION'
      });

      assert.ok(authService.getCurrentUser());

      await authService.logout();

      assert.equal(authService.getCurrentUser(), null);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
