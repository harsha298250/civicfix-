import test, { describe, it } from 'node:test';
import assert from 'node:assert';
import enTranslations from '../src/locales/en.json' with { type: 'json' };
import taTranslations from '../src/locales/ta.json' with { type: 'json' };

describe('Phase 2.2 — Staff & Cross-Role Tamil Localization Tests', () => {

  const staffNamespaces = ['officer', 'worker', 'supervisor', 'admin', 'publicMap', 'accessibility'];

  it('1. All Phase 2.2 required namespaces exist in both en.json and ta.json', () => {
    for (const ns of staffNamespaces) {
      assert.ok(enTranslations[ns], `en.json must contain namespace: ${ns}`);
      assert.ok(taTranslations[ns], `ta.json must contain namespace: ${ns}`);
      assert.strictEqual(typeof enTranslations[ns], 'object', `${ns} in en.json must be an object`);
      assert.strictEqual(typeof taTranslations[ns], 'object', `${ns} in ta.json must be an object`);
    }
  });

  it('2. 100% key parity between English and Tamil for all staff namespaces', () => {
    for (const ns of staffNamespaces) {
      const enKeys = Object.keys(enTranslations[ns]).sort();
      const taKeys = Object.keys(taTranslations[ns]).sort();

      assert.deepStrictEqual(
        taKeys,
        enKeys,
        `Namespace "${ns}" must have identical keys in en.json and ta.json`
      );
    }
  });

  it('3. Tamil translations contain valid Tamil text and are distinct from English', () => {
    // Regex matching Tamil Unicode character range: \u0B80-\u0BFF
    const tamilUnicodeRegex = /[\u0B80-\u0BFF]/;

    for (const ns of staffNamespaces) {
      for (const [key, taVal] of Object.entries(taTranslations[ns])) {
        const enVal = enTranslations[ns][key];

        assert.ok(taVal && typeof taVal === 'string', `${ns}.${key} in Tamil must be a non-empty string`);
        assert.ok(
          tamilUnicodeRegex.test(taVal),
          `${ns}.${key} in ta.json must contain authentic Tamil script characters (Found: "${taVal}")`
        );
        assert.notStrictEqual(
          taVal,
          enVal,
          `${ns}.${key} in Tamil must not be an untranslated English placeholder`
        );
      }
    }
  });

  it('4. Navigation keys for all five roles are defined in en.json and ta.json', () => {
    const requiredNavKeys = [
      'overview', 'complaintQueue', 'reviewAssignment', 'workerAssignments',
      'resolutionVerification', 'liveIssueMap', 'operationalAnalytics', 'profileSettings',
      'workerDashboard', 'assignedTasks', 'taskDetails', 'updateProgress',
      'submitEvidence', 'myProfileEquipment', 'escalationsQueue', 'userDirectory',
      'rolesMatrix', 'platformAnalytics', 'auditLedger', 'systemSettings'
    ];

    for (const k of requiredNavKeys) {
      assert.ok(enTranslations.nav[k], `en.json must define nav.${k}`);
      assert.ok(taTranslations.nav[k], `ta.json must define nav.${k}`);
      assert.notStrictEqual(enTranslations.nav[k], taTranslations.nav[k], `nav.${k} should differ in Tamil`);
    }
  });

  it('5. Interpolation placeholders match exactly between English and Tamil strings', () => {
    const extractPlaceholders = (str) => {
      const matches = str.match(/\{\{\s*(\w+)\s*\}\}/g);
      return matches ? matches.map(m => m.replace(/[{}]/g, '').trim()).sort() : [];
    };

    for (const ns of staffNamespaces) {
      for (const [key, enVal] of Object.entries(enTranslations[ns])) {
        const taVal = taTranslations[ns][key];
        const enPlaceholders = extractPlaceholders(enVal);
        const taPlaceholders = extractPlaceholders(taVal);

        assert.deepStrictEqual(
          taPlaceholders,
          enPlaceholders,
          `Placeholders in ${ns}.${key} must match between EN and TA`
        );
      }
    }
  });

  it('6. Fallback mechanism preserves English text when a Tamil key is absent', () => {
    function lookup(dict, key) {
      const parts = key.split('.');
      let cur = dict;
      for (const p of parts) {
        if (!cur || typeof cur !== 'object') return undefined;
        cur = cur[p];
      }
      return cur;
    }

    function translateWithFallback(key, lang, dicts) {
      const val = lookup(dicts[lang], key);
      if (val !== undefined) return val;
      const fallback = lookup(dicts.en, key);
      return fallback !== undefined ? fallback : key;
    }

    const dicts = {
      en: enTranslations,
      ta: { ...taTranslations, officer: { ...taTranslations.officer } }
    };
    // Artificially delete a key in Tamil
    delete dicts.ta.officer.queueTitle;

    const result = translateWithFallback('officer.queueTitle', 'ta', dicts);
    assert.strictEqual(result, 'Municipal Complaint Queue', 'Should fall back to English queueTitle');
  });

});
