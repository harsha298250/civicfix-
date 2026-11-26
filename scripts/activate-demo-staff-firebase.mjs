#!/usr/bin/env node
/**
 * One-Time Local Firebase Admin Activation Script for Internal CivicFix Demo Staff Accounts
 *
 * Strictly restricted to:
 *   - admin@civicfix.gov
 *   - supervisor@civicfix.gov
 *   - officer@civicfix.gov
 *   - worker@civicfix.gov
 *
 * Safety guarantees:
 *   - Read-only --dry-run by default; requires explicit --apply to set emailVerified=true.
 *   - Never creates, deletes, or disables accounts.
 *   - Never modifies Neon PostgreSQL, roles, or passwords.
 *   - Requires a Firebase service-account JSON file located OUTSIDE the Git repository.
 *   - Never prints credentials, private keys, access tokens, or full UIDs.
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { pathToFileURL } from 'node:url';

export const ALLOWED_DEMO_STAFF_EMAILS = Object.freeze([
  'admin@civicfix.gov',
  'supervisor@civicfix.gov',
  'officer@civicfix.gov',
  'worker@civicfix.gov'
]);

export const EXPECTED_FIREBASE_PROJECT_ID = 'civicfix-15ee3';

export function maskUid(uid) {
  if (!uid || typeof uid !== 'string') return 'N/A';
  const trimmed = uid.trim();
  if (trimmed.length <= 8) return `${trimmed.slice(0, 2)}...${trimmed.slice(-2)}`;
  return `${trimmed.slice(0, 4)}...${trimmed.slice(-4)}`;
}

export function isPathInsideDir(targetPath, parentDir) {
  const resolvedTarget = path.resolve(targetPath);
  const resolvedParent = path.resolve(parentDir);
  const rel = path.relative(resolvedParent, resolvedTarget);
  return rel === '' || (!rel.startsWith('..') && !path.isAbsolute(rel));
}

export function parseArgs(argv) {
  const args = {
    apply: false,
    dryRun: true,
    credentialsPath: process.env.GOOGLE_APPLICATION_CREDENTIALS || ''
  };

  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === '--apply') {
      args.apply = true;
      args.dryRun = false;
    } else if (token === '--dry-run') {
      args.apply = false;
      args.dryRun = true;
    } else if (token === '--credentials' && argv[i + 1]) {
      args.credentialsPath = argv[i + 1];
      i += 1;
    } else if (token.startsWith('--credentials=')) {
      args.credentialsPath = token.slice('--credentials='.length);
    } else if (token === '--help' || token === '-h') {
      args.help = true;
    } else {
      throw new Error(`Unsupported argument: ${token}`);
    }
  }
  return args;
}

export function loadServiceAccountCredentials(credentialsPath, repoRoot = process.cwd()) {
  if (!credentialsPath || !credentialsPath.trim()) {
    throw new Error(
      'Missing credentials path. Set GOOGLE_APPLICATION_CREDENTIALS or pass --credentials <path-outside-git>.'
    );
  }

  const resolvedPath = path.resolve(credentialsPath.trim());
  if (isPathInsideDir(resolvedPath, repoRoot)) {
    throw new Error(
      'Refusing to read Firebase Admin credentials from inside the Git repository. Store the JSON file outside the repo.'
    );
  }

  if (!fs.existsSync(resolvedPath)) {
    throw new Error(`Credentials file not found at: ${resolvedPath}`);
  }

  const raw = fs.readFileSync(resolvedPath, 'utf8');
  const parsed = JSON.parse(raw);

  if (parsed.type !== 'service_account') {
    throw new Error('Invalid credentials file: expected type "service_account".');
  }
  if (parsed.project_id !== EXPECTED_FIREBASE_PROJECT_ID) {
    throw new Error(
      `Credentials project_id mismatch: expected "${EXPECTED_FIREBASE_PROJECT_ID}", got "${parsed.project_id || 'none'}".`
    );
  }
  if (!parsed.client_email || !parsed.private_key) {
    throw new Error('Invalid service account JSON: missing client_email or private_key.');
  }

  return {
    projectId: parsed.project_id,
    clientEmail: parsed.client_email,
    privateKey: parsed.private_key,
    tokenUri: parsed.token_uri || 'https://oauth2.googleapis.com/token'
  };
}

function base64UrlEncode(input) {
  return Buffer.from(input)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

export async function fetchGoogleAccessToken(creds, fetchImpl = fetch) {
  const nowSec = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claimSet = base64UrlEncode(
    JSON.stringify({
      iss: creds.clientEmail,
      scope: 'https://www.googleapis.com/auth/identitytoolkit https://www.googleapis.com/auth/cloud-platform',
      aud: creds.tokenUri,
      iat: nowSec,
      exp: nowSec + 3600
    })
  );

  const signingInput = `${header}.${claimSet}`;
  const signer = crypto.createSign('RSA-SHA256');
  signer.update(signingInput);
  signer.end();
  const signature = signer.sign(creds.privateKey);
  const jwt = `${signingInput}.${base64UrlEncode(signature)}`;

  const res = await fetchImpl(creds.tokenUri, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt
    }).toString()
  });

  if (!res.ok) {
    throw new Error(`Failed to obtain Google OAuth2 access token (HTTP ${res.status}).`);
  }

  const data = await res.json();
  if (!data.access_token) {
    throw new Error('Google OAuth2 response did not include an access_token.');
  }
  return data.access_token;
}

export async function lookupFirebaseUserByEmail(projectId, accessToken, email, fetchImpl = fetch) {
  if (!ALLOWED_DEMO_STAFF_EMAILS.includes(email)) {
    throw new Error(`Email "${email}" is not in the authorized demo staff allowlist.`);
  }

  const url = `https://identitytoolkit.googleapis.com/v1/projects/${projectId}/accounts:lookup`;
  const res = await fetchImpl(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ email: [email] })
  });

  if (!res.ok) {
    throw new Error(`Firebase accounts:lookup failed for ${email} (HTTP ${res.status}).`);
  }

  const data = await res.json();
  const user = Array.isArray(data.users) ? data.users[0] : null;
  return user || null;
}

export async function markFirebaseEmailVerified(projectId, accessToken, localId, email, fetchImpl = fetch) {
  if (!ALLOWED_DEMO_STAFF_EMAILS.includes(email)) {
    throw new Error(`Email "${email}" is not in the authorized demo staff allowlist.`);
  }

  const url = `https://identitytoolkit.googleapis.com/v1/projects/${projectId}/accounts:update`;
  const res = await fetchImpl(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      localId,
      emailVerified: true
    })
  });

  if (!res.ok) {
    throw new Error(`Firebase accounts:update failed for ${email} (HTTP ${res.status}).`);
  }

  return res.json();
}

export async function runDemoStaffActivation({
  apply = false,
  projectId,
  accessToken,
  fetchImpl = fetch
}) {
  const results = [];

  for (const email of ALLOWED_DEMO_STAFF_EMAILS) {
    const user = await lookupFirebaseUserByEmail(projectId, accessToken, email, fetchImpl);
    if (!user) {
      results.push({
        email,
        exists: false,
        uidMasked: 'N/A',
        emailVerifiedBefore: null,
        emailVerifiedAfter: null,
        disabled: null,
        action: 'MISSING_DO_NOT_CREATE'
      });
      continue;
    }

    const emailVerifiedBefore = Boolean(user.emailVerified);
    const disabled = Boolean(user.disabled);
    const uidMasked = maskUid(user.localId);

    if (emailVerifiedBefore) {
      results.push({
        email,
        exists: true,
        uidMasked,
        emailVerifiedBefore: true,
        emailVerifiedAfter: true,
        disabled,
        action: 'ALREADY_VERIFIED'
      });
      continue;
    }

    if (!apply) {
      results.push({
        email,
        exists: true,
        uidMasked,
        emailVerifiedBefore: false,
        emailVerifiedAfter: false,
        disabled,
        action: 'DRY_RUN_WOULD_VERIFY'
      });
      continue;
    }

    await markFirebaseEmailVerified(projectId, accessToken, user.localId, email, fetchImpl);
    const updatedUser = await lookupFirebaseUserByEmail(projectId, accessToken, email, fetchImpl);

    results.push({
      email,
      exists: true,
      uidMasked,
      emailVerifiedBefore: false,
      emailVerifiedAfter: Boolean(updatedUser?.emailVerified),
      disabled: Boolean(updatedUser?.disabled),
      action: updatedUser?.emailVerified ? 'VERIFIED' : 'UPDATE_FAILED'
    });
  }

  return results;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log(
      [
        'Usage: node scripts/activate-demo-staff-firebase.mjs [--dry-run | --apply] [--credentials <path-outside-git>]',
        '',
        'Defaults to --dry-run. Reads GOOGLE_APPLICATION_CREDENTIALS if --credentials is omitted.'
      ].join('\n')
    );
    return;
  }

  const creds = loadServiceAccountCredentials(args.credentialsPath, process.cwd());
  const accessToken = await fetchGoogleAccessToken(creds);
  const results = await runDemoStaffActivation({
    apply: args.apply,
    projectId: creds.projectId,
    accessToken
  });

  console.log(`Mode: ${args.apply ? 'APPLY (emailVerified=true only)' : 'DRY-RUN (read-only)'}`);
  console.log(`Project: ${creds.projectId}`);
  console.table(results);
}

const isDirectExecution =
  process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (isDirectExecution) {
  main().catch((err) => {
    console.error(`Activation script aborted: ${err.message}`);
    process.exitCode = 1;
  });
}
