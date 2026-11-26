import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  ALLOWED_DEMO_STAFF_EMAILS,
  EXPECTED_FIREBASE_PROJECT_ID,
  loadServiceAccountCredentials,
  lookupFirebaseUserByEmail,
  maskUid,
  parseArgs,
  runDemoStaffActivation
} from '../scripts/activate-demo-staff-firebase.mjs';

describe('Demo Staff Firebase Activation Script', () => {
  it('1. Restricts allowlist strictly to the 4 exact @civicfix.gov demo staff accounts', () => {
    assert.deepEqual(ALLOWED_DEMO_STAFF_EMAILS, [
      'admin@civicfix.gov',
      'supervisor@civicfix.gov',
      'officer@civicfix.gov',
      'worker@civicfix.gov'
    ]);
  });

  it('2. Defaults to dry-run mode and only enables apply when --apply is passed', () => {
    assert.equal(parseArgs([]).apply, false);
    assert.equal(parseArgs([]).dryRun, true);
    assert.equal(parseArgs(['--dry-run']).apply, false);
    assert.equal(parseArgs(['--apply']).apply, true);
  });

  it('3. Refuses credentials located inside the Git repository', () => {
    const insidePath = path.join(process.cwd(), 'fake-sa.json');
    assert.throws(
      () => loadServiceAccountCredentials(insidePath, process.cwd()),
      /inside the Git repository/
    );
  });

  it('4. Rejects non-allowlisted emails and masks UIDs', async () => {
    assert.equal(maskUid('abc123456789xyz'), 'abc1...9xyz');
    await assert.rejects(
      () => lookupFirebaseUserByEmail(EXPECTED_FIREBASE_PROJECT_ID, 'tok', 'citizen@gmail.com', async () => ({})),
      /not in the authorized demo staff allowlist/
    );
  });

  it('5. Dry-run never calls accounts:update; apply only sets emailVerified=true for unverified existing users', async () => {
    const updateCalls = [];
    const state = {
      'admin@civicfix.gov': { localId: 'uidAdmin0001', emailVerified: true, disabled: false },
      'supervisor@civicfix.gov': { localId: 'uidSuper0002', emailVerified: false, disabled: false },
      'officer@civicfix.gov': { localId: 'uidOffic0003', emailVerified: false, disabled: false },
      'worker@civicfix.gov': { localId: 'uidWorke0004', emailVerified: false, disabled: true }
    };

    const mockFetch = async (url, opts) => {
      const body = JSON.parse(opts.body);
      if (url.endsWith('accounts:lookup')) {
        const email = body.email[0];
        return {
          ok: true,
          json: async () => ({ users: state[email] ? [state[email]] : [] })
        };
      }
      if (url.endsWith('accounts:update')) {
        updateCalls.push(body);
        const match = Object.values(state).find((u) => u.localId === body.localId);
        if (match) match.emailVerified = Boolean(body.emailVerified);
        return {
          ok: true,
          json: async () => match
        };
      }
      throw new Error(`Unexpected URL: ${url}`);
    };

    const dryResults = await runDemoStaffActivation({
      apply: false,
      projectId: EXPECTED_FIREBASE_PROJECT_ID,
      accessToken: 'mock-token',
      fetchImpl: mockFetch
    });
    assert.equal(updateCalls.length, 0);
    assert.equal(dryResults[0].action, 'ALREADY_VERIFIED');
    assert.equal(dryResults[2].action, 'DRY_RUN_WOULD_VERIFY');

    const applyResults = await runDemoStaffActivation({
      apply: true,
      projectId: EXPECTED_FIREBASE_PROJECT_ID,
      accessToken: 'mock-token',
      fetchImpl: mockFetch
    });
    assert.equal(updateCalls.length, 3);
    for (const call of updateCalls) {
      assert.deepEqual(Object.keys(call).sort(), ['emailVerified', 'localId']);
      assert.equal(call.emailVerified, true);
    }
    assert.equal(applyResults[1].action, 'VERIFIED');
    assert.equal(applyResults[2].emailVerifiedAfter, true);
    assert.equal(applyResults[3].disabled, true);
  });

  it('6. Validates external service-account JSON project_id without printing secrets', () => {
    const tmpFile = path.join(os.tmpdir(), `civicfix-sa-test-${Date.now()}.json`);
    fs.writeFileSync(
      tmpFile,
      JSON.stringify({
        type: 'service_account',
        project_id: 'wrong-project',
        client_email: 'test@wrong.iam.gserviceaccount.com',
        private_key: '-----BEGIN PRIVATE KEY-----\nTEST\n-----END PRIVATE KEY-----\n'
      })
    );
    try {
      assert.throws(
        () => loadServiceAccountCredentials(tmpFile, process.cwd()),
        /project_id mismatch/
      );
    } finally {
      fs.unlinkSync(tmpFile);
    }
  });
});
