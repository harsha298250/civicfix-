import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

test('Phase 2.3.5: AppContext contains visibility-aware notification polling', () => {
  const appContextPath = path.join(rootDir, 'src', 'context', 'AppContext.jsx');
  const content = fs.readFileSync(appContextPath, 'utf8');

  assert.match(content, /NOTIFICATION_POLL_INTERVAL_MS\s*=\s*60000/, 'Defines 60s polite polling interval');
  assert.match(content, /document\.visibilityState\s*===\s*'hidden'/, 'Checks document.visibilityState to pause polling when tab is hidden');
  assert.match(content, /document\.addEventListener\('visibilitychange'/, 'Listens to visibilitychange event');
  assert.match(content, /document\.removeEventListener\('visibilitychange'/, 'Cleans up visibilitychange listener on unmount');
  assert.match(content, /clearInterval\(pollTimer\)/, 'Clears polling timer when tab is hidden or component unmounts');
});

test('Phase 2.3.5: CitizenProfilePage persists resident notification alert preferences across sessions', () => {
  const profilePagePath = path.join(rootDir, 'src', 'pages', 'citizen', 'CitizenProfilePage.jsx');
  const content = fs.readFileSync(profilePagePath, 'utf8');

  assert.match(content, /localStorage\.getItem\(`civicfix_notif_pref_/, 'Retrieves persisted notification preferences from localStorage');
  assert.match(content, /localStorage\.setItem\(`civicfix_notif_pref_/, 'Saves resident alert preferences to localStorage on save');
  assert.match(content, /emailAlerts/, 'Includes emailAlerts preference');
  assert.match(content, /smsAlerts/, 'Includes smsAlerts preference');
});

test('Phase 2.3.5: Web Push & VAPID progressive enhancement architecture documentation is comprehensive', () => {
  const docPath = path.join(rootDir, 'docs', 'WEB_PUSH_ARCHITECTURE.md');
  assert.ok(fs.existsSync(docPath), 'WEB_PUSH_ARCHITECTURE.md exists');
  const content = fs.readFileSync(docPath, 'utf8');

  assert.match(content, /VAPID/, 'Documents VAPID specifications');
  assert.match(content, /RFC 8291/, 'Documents RFC 8291 payload encryption');
  assert.match(content, /RFC 8292/, 'Documents RFC 8292 application server identification');
  assert.match(content, /ServiceWorker/, 'Documents ServiceWorker push handlers');
  assert.match(content, /showNotification/, 'Documents showNotification implementation');
});
