import fs from 'node:fs';
import path from 'node:path';
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import en from '../src/locales/en.json' with { type: 'json' };
import ta from '../src/locales/ta.json' with { type: 'json' };
import {
  translateCategory,
  translateErrorMessage,
  translateNotificationItem,
} from '../src/utils/i18nHelpers.js';

function flattenKeys(obj, prefix = '') {
  const keys = {};
  for (const [k, v] of Object.entries(obj)) {
    const full = prefix ? `${prefix}.${k}` : k;
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      Object.assign(keys, flattenKeys(v, full));
    } else {
      keys[full] = v;
    }
  }
  return keys;
}

function getPlaceholders(str) {
  if (typeof str !== 'string') return [];
  const matches = str.match(/\{\{([^}]+)\}\}/g) || [];
  return [...new Set(matches)].sort();
}

function collectSourceFiles(dir) {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...collectSourceFiles(full));
    } else if (entry.isFile() && /\.(js|jsx)$/.test(entry.name)) {
      results.push(full);
    }
  }
  return results;
}

describe('CivicFix Complete Tamil & English Localization Suite', () => {
  const enFlat = flattenKeys(en);
  const taFlat = flattenKeys(ta);

  it('maintains 100% key parity between en.json and ta.json with non-empty values', () => {
    const enKeys = Object.keys(enFlat).sort();
    const taKeys = Object.keys(taFlat).sort();

    const missingInTa = enKeys.filter((k) => !(k in taFlat));
    const missingInEn = taKeys.filter((k) => !(k in enFlat));

    assert.deepStrictEqual(missingInTa, []);
    assert.deepStrictEqual(missingInEn, []);
    assert.ok(enKeys.length > 800, `Expected >800 keys, got ${enKeys.length}`);

    const emptyEn = enKeys.filter((k) => typeof enFlat[k] === 'string' && enFlat[k].trim() === '');
    const emptyTa = taKeys.filter((k) => typeof taFlat[k] === 'string' && taFlat[k].trim() === '');
    assert.deepStrictEqual(emptyEn, []);
    assert.deepStrictEqual(emptyTa, []);
  });

  it('preserves matching {{param}} interpolation placeholders across English and Tamil', () => {
    const mismatched = [];
    for (const key of Object.keys(enFlat)) {
      const enVars = getPlaceholders(enFlat[key]);
      const taVars = getPlaceholders(taFlat[key]);
      if (JSON.stringify(enVars) !== JSON.stringify(taVars)) {
        mismatched.push({ key, enVars, taVars });
      }
    }
    assert.deepStrictEqual(mismatched, []);
  });

  it('resolves every static t("...") key used across src/ in both en.json and ta.json', () => {
    const srcFiles = collectSourceFiles(path.resolve(process.cwd(), 'src'));
    const staticKeyRegex = /\bt\(\s*['"]([a-zA-Z0-9_.-]+)['"]/g;
    const missing = [];

    for (const filePath of srcFiles) {
      const content = fs.readFileSync(filePath, 'utf8');
      let match;
      while ((match = staticKeyRegex.exec(content)) !== null) {
        const key = match[1];
        if (!key.includes('.')) continue;
        if (!(key in enFlat) || !(key in taFlat)) {
          missing.push(`${path.relative(process.cwd(), filePath)}: ${key}`);
        }
      }
    }

    assert.deepStrictEqual(missing, []);
  });

  it('configures ta-IN and en-IN BCP-47 locales in LanguageContext and Tamil font stack in CSS', () => {
    const langCtx = fs.readFileSync(path.resolve(process.cwd(), 'src/context/LanguageContext.jsx'), 'utf8');
    assert.ok(langCtx.includes("'ta-IN'"), 'LanguageContext should set ta-IN');
    assert.ok(langCtx.includes("'en-IN'"), 'LanguageContext should set en-IN');
    assert.ok(langCtx.includes('civicfix_language'), 'LanguageContext should persist civicfix_language');

    const indexCss = fs.readFileSync(path.resolve(process.cwd(), 'src/index.css'), 'utf8');
    assert.ok(indexCss.includes('Noto Sans Tamil'), 'index.css should include Noto Sans Tamil font fallback');
  });

  it('translates dynamic categories, backend error messages, and notifications into Tamil', () => {
    const tTa = (key, params = {}) => {
      const val = taFlat[key];
      if (!val) return key;
      return val.replace(/\{\{(\w+)\}\}/g, (_, k) => String(params[k] ?? ''));
    };

    assert.equal(translateCategory('Pothole & Road Hazard', tTa), ta.categories.roads);
    assert.equal(translateCategory('Drainage & Sewage', tTa), ta.categories.drainage);

    assert.equal(translateErrorMessage({ status: 403, message: 'Forbidden' }, tTa), ta.errors.forbidden);
    assert.equal(
      translateErrorMessage({ status: 500, message: 'Internal Server Error' }, tTa),
      ta.errors.internalServer
    );

    const translatedNotif = translateNotificationItem(
      {
        type: 'WORKER_ASSIGNED',
        referenceId: 'CF-2026-001',
        title: 'Complaint Assigned',
        message: 'Complaint #CF-2026-001 has been assigned to field crew.',
      },
      tTa
    );
    assert.ok(translatedNotif.title.includes('#CF-2026-001'));
    assert.equal(translatedNotif.message, ta.notifications.dynamic.workerAssignedDesc);
  });

  it('wires localization across all screenshot pages, ErrorBoundary, and role profiles without hardcoded seed route bugs', () => {
    const requiredLocalizedFiles = [
      'src/pages/supervisor/SupervisorEscalationsPage.jsx',
      'src/pages/officer/ComplaintReviewPage.jsx',
      'src/pages/officer/AssignmentsPage.jsx',
      'src/pages/officer/VerificationQueuePage.jsx',
      'src/pages/officer/CivicIssueMapPage.jsx',
      'src/pages/officer/OfficerAnalyticsPage.jsx',
      'src/pages/officer/OfficerNotificationsPage.jsx',
      'src/pages/worker/WorkerNotificationsPage.jsx',
      'src/pages/admin/AdminNotificationsPage.jsx',
      'src/pages/worker/WorkerDashboard.jsx',
      'src/pages/citizen/CitizenIssueMapPage.jsx',
      'src/pages/officer/OfficerProfilePage.jsx',
      'src/pages/worker/WorkerProfilePage.jsx',
      'src/pages/admin/AdminProfilePage.jsx',
      'src/pages/public/StaffActivationPage.jsx',
      'src/components/common/AuthenticatedImage.jsx',
    ];

    for (const rel of requiredLocalizedFiles) {
      const content = fs.readFileSync(path.resolve(process.cwd(), rel), 'utf8');
      assert.ok(content.includes('useTranslation'), `${rel} must use useTranslation()`);
    }

    const errorBoundary = fs.readFileSync(
      path.resolve(process.cwd(), 'src/components/common/ErrorBoundary.jsx'),
      'utf8'
    );
    assert.ok(errorBoundary.includes('taTranslations'), 'ErrorBoundary must support Tamil fallback');

    const officerLayout = fs.readFileSync(
      path.resolve(process.cwd(), 'src/components/layout/OfficerLayout.jsx'),
      'utf8'
    );
    assert.ok(
      !officerLayout.includes('/officer/complaints/CF-2026-00124/review'),
      'OfficerLayout must not hardcode CF-2026-00124'
    );
  });
});
