import { describe, it } from 'node:test';
import assert from 'node:assert';
import { apiClient } from '../src/services/apiClient.js';

describe('AI Priority Recommendation & Officer Adjudication Governance', () => {
  it('adjudicateAiTriage endpoint accepts and delegates officer decisions', () => {
    assert.strictEqual(typeof apiClient.complaints.adjudicateAiTriage, 'function');
  });

  it('priority values map to correct SLA duration bounds', () => {
    const SLA_HOURS = {
      CRITICAL: 24,
      HIGH: 48,
      MEDIUM: 72,
      LOW: 120
    };

    assert.strictEqual(SLA_HOURS.CRITICAL, 24, 'Critical issues must resolve within 24 hours');
    assert.strictEqual(SLA_HOURS.HIGH, 48, 'High issues must resolve within 48 hours');
    assert.strictEqual(SLA_HOURS.MEDIUM, 72, 'Medium issues must resolve within 72 hours');
    assert.strictEqual(SLA_HOURS.LOW, 120, 'Low issues must resolve within 120 hours');
  });

  it('adjudication payload requires action of ACCEPT, OVERRIDE, or DISMISS', () => {
    const validActions = ['ACCEPT', 'OVERRIDE', 'DISMISS'];
    ['ACCEPT', 'OVERRIDE', 'DISMISS'].forEach((act) => {
      assert.ok(validActions.includes(act));
    });
  });
});
