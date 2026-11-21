import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

test('Phase 2.3.1: Citizen account management API client endpoints exist', async () => {
  const apiClientPath = path.join(rootDir, 'src', 'services', 'apiClient.js');
  const content = fs.readFileSync(apiClientPath, 'utf8');

  assert.match(content, /citizen:\s*\{/, 'apiClient should contain citizen namespace');
  assert.match(content, /exportAccountData:\s*\(\)\s*=>\s*apiRequest\('\/citizen\/account\/export'\)/, 'exportAccountData endpoint is defined');
  assert.match(content, /deleteAccount:\s*\(password\)\s*=>\s*apiRequest\('\/citizen\/account\/delete'/, 'deleteAccount endpoint is defined');
});

test('Phase 2.3.1: Citizen profile page integrates export and deletion with ModalDialog', () => {
  const profilePagePath = path.join(rootDir, 'src', 'pages', 'citizen', 'CitizenProfilePage.jsx');
  const content = fs.readFileSync(profilePagePath, 'utf8');

  assert.match(content, /ModalDialog/, 'CitizenProfilePage should import and use ModalDialog');
  assert.match(content, /apiClient\.citizen\.exportAccountData/, 'CitizenProfilePage calls exportAccountData');
  assert.match(content, /apiClient\.citizen\.deleteAccount/, 'CitizenProfilePage calls deleteAccount');
  assert.match(content, /logout\(\)/, 'CitizenProfilePage logs user out on successful deletion');
  assert.match(content, /navigate\('\/'\)/, 'CitizenProfilePage navigates to home after account deletion');
  assert.match(content, /type="password"/, 'CitizenProfilePage requires password confirmation for deletion');
  assert.match(content, /aria-label=\{t\('citizen\.account\.exportData'\)\}/, 'Accessible aria-label on export button');
  assert.match(content, /aria-label=\{t\('citizen\.account\.deleteAccount'\)\}/, 'Accessible aria-label on delete button');
});

test('Phase 2.3.1: English and Tamil localization keys for citizen account are present and symmetric', () => {
  const enPath = path.join(rootDir, 'src', 'locales', 'en.json');
  const taPath = path.join(rootDir, 'src', 'locales', 'ta.json');

  const en = JSON.parse(fs.readFileSync(enPath, 'utf8'));
  const ta = JSON.parse(fs.readFileSync(taPath, 'utf8'));

  assert.ok(en.citizen?.account, 'en.json contains citizen.account');
  assert.ok(ta.citizen?.account, 'ta.json contains citizen.account');

  const requiredKeys = [
    'privacyTitle',
    'privacyDesc',
    'exportData',
    'exporting',
    'exportSuccess',
    'exportFailed',
    'deleteAccount',
    'deleteModalTitle',
    'deleteWarning',
    'passwordPrompt',
    'passwordPlaceholder',
    'passwordRequired',
    'confirmDelete',
    'deleting',
    'deleteSuccess',
    'deleteFailed'
  ];

  for (const k of requiredKeys) {
    assert.ok(en.citizen.account[k], `en.json has citizen.account.${k}`);
    assert.ok(ta.citizen.account[k], `ta.json has citizen.account.${k}`);
    // Check authentic Tamil unicode
    assert.match(ta.citizen.account[k], /[\u0B80-\u0BFF]/, `ta.json citizen.account.${k} contains authentic Tamil Unicode`);
  }
});
