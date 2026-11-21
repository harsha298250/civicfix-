import test from 'node:test';
import assert from 'node:assert/strict';
import { apiClient } from '../src/services/apiClient.js';
import { officerService } from '../src/services/officerService.js';

test.describe('Officer Bulk Assignment Tests', () => {

  test.it('apiClient.complaints.bulkAssign sends POST /api/complaints/bulk-assign with payload', async () => {
    let capturedUrl = null;
    let capturedOptions = null;

    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url, options) => {
      capturedUrl = url;
      capturedOptions = options;
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          success: true,
          message: 'Batch assignment completed: 2 succeeded, 0 failed.',
          data: {
            totalRequested: 2,
            successCount: 2,
            failureCount: 0,
            results: [
              { complaintId: 'comp-1', referenceId: 'CF-2026-00001', success: true, message: 'Dispatched' },
              { complaintId: 'comp-2', referenceId: 'CF-2026-00002', success: true, message: 'Dispatched' }
            ]
          }
        })
      };
    };

    try {
      const res = await apiClient.complaints.bulkAssign({
        complaintIds: ['comp-1', 'comp-2'],
        workerId: '00000000-0000-0000-0000-000000000003',
        notes: 'Priority road repair crew deployment'
      });

      assert.ok(capturedUrl.includes('/api/complaints/bulk-assign'));
      assert.equal(capturedOptions.method, 'POST');
      const body = JSON.parse(capturedOptions.body);
      assert.deepEqual(body.complaintIds, ['comp-1', 'comp-2']);
      assert.equal(body.workerId, '00000000-0000-0000-0000-000000000003');
      assert.equal(res.data.successCount, 2);
      assert.equal(res.data.failureCount, 0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test.it('officerService.bulkAssignWorkers validates complaint IDs and delegates to apiClient', async () => {
    let calledPayload = null;

    const originalBulkAssign = apiClient.complaints.bulkAssign;
    apiClient.complaints.bulkAssign = async (payload) => {
      calledPayload = payload;
      return {
        success: true,
        data: {
          totalRequested: 1,
          successCount: 1,
          failureCount: 0,
          results: [{ complaintId: 'comp-10', referenceId: 'CF-2026-00010', success: true, message: 'Assigned' }]
        }
      };
    };

    try {
      const result = await officerService.bulkAssignWorkers(
        ['comp-10'],
        'marcus.chen',
        'Emergency response'
      );

      assert.equal(calledPayload.complaintIds[0], 'comp-10');
      assert.equal(calledPayload.workerId, '00000000-0000-0000-0000-000000000003');
      assert.equal(calledPayload.notes, 'Emergency response');
      assert.equal(result.successCount, 1);
    } finally {
      apiClient.complaints.bulkAssign = originalBulkAssign;
    }
  });

  test.it('officerService.bulkAssignWorkers rejects empty complaint list with clear error', async () => {
    await assert.rejects(
      () => officerService.bulkAssignWorkers([], '00000000-0000-0000-0000-000000000003'),
      /At least one complaint ID is required/
    );
  });
});
