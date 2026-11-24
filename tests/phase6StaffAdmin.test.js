import test from 'node:test';
import assert from 'node:assert/strict';
import { apiClient } from '../src/services/apiClient.js';

test.describe('Phase 6 Staff Management, Workload & Analytics Tests', () => {

  test.it('apiClient.auth.verifyStaffActivation queries verification endpoint via POST body to avoid URL token exposure', async () => {
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
          data: {
            email: 'worker@metro.gov',
            fullName: 'Ravi Tech',
            role: 'WORKER',
            department: 'Roads & Infrastructure'
          }
        })
      };
    };

    try {
      const res = await apiClient.auth.verifyStaffActivation('token_xyz_123');
      assert.ok(capturedUrl.includes('/auth/staff-activation/verify'));
      assert.equal(capturedOptions?.method, 'POST');
      const body = JSON.parse(capturedOptions.body);
      assert.equal(body.token, 'token_xyz_123');
      assert.equal(res.data.email, 'worker@metro.gov');
      assert.equal(res.data.role, 'WORKER');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test.it('apiClient.auth.activateStaff sends token and password payload', async () => {
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
          message: 'Staff account activated successfully.'
        })
      };
    };

    try {
      const res = await apiClient.auth.activateStaff({
        token: 'valid_token_abc',
        password: 'PermanentSecurePassword#2026'
      });
      assert.ok(capturedUrl.includes('/auth/staff-activation/activate'));
      assert.equal(capturedOptions.method, 'POST');
      const body = JSON.parse(capturedOptions.body);
      assert.equal(body.token, 'valid_token_abc');
      assert.equal(body.password, 'PermanentSecurePassword#2026');
      assert.equal(res.success, true);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test.it('apiClient.supervisor.getWorkers returns live workload properties', async () => {
    let capturedUrl = null;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          success: true,
          data: [
            {
              id: '00000000-0000-0000-0000-000000000003',
              fullName: 'Marcus Chen',
              department: 'SDOT Rapid Asphalt Maintenance',
              activeOrdersCount: 2,
              pendingOrdersCount: 1,
              inProgressOrdersCount: 1,
              completedOrdersCount: 5,
              overdueOrdersCount: 0
            }
          ]
        })
      };
    };

    try {
      const res = await apiClient.supervisor.getWorkers();
      assert.ok(capturedUrl.includes('/supervisor/workers'));
      assert.equal(res.data.length, 1);
      const worker = res.data[0];
      assert.equal(worker.fullName, 'Marcus Chen');
      assert.equal(worker.activeOrdersCount, 2);
      assert.equal(worker.pendingOrdersCount, 1);
      assert.equal(worker.completedOrdersCount, 5);
      assert.equal(worker.overdueOrdersCount, 0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test.it('apiClient.admin.getStaffPerformance serializes department filter', async () => {
    let capturedUrl = null;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          success: true,
          data: [
            {
              staffId: '111',
              fullName: 'Elena Vance',
              role: 'OFFICER',
              department: 'Roads',
              assignedCount: 10,
              completedCount: 8,
              pendingCount: 2,
              overdueCount: 0,
              reworkCount: 1,
              avgResolutionHours: 14.5
            }
          ]
        })
      };
    };

    try {
      const res = await apiClient.admin.getStaffPerformance({ department: 'Roads' });
      assert.ok(capturedUrl.includes('/admin/analytics/staff-performance?department=Roads'));
      assert.equal(res.data[0].fullName, 'Elena Vance');
      assert.equal(res.data[0].avgResolutionHours, 14.5);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test.it('apiClient.admin.getAreaPerformance queries area analytics endpoint', async () => {
    let capturedUrl = null;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          success: true,
          data: [
            {
              district: 'Greater Chennai',
              ward: 'Ward 08',
              totalReceived: 45,
              resolvedCount: 38,
              pendingCount: 7,
              overdueCount: 2,
              resolutionRatePercent: 84.4
            }
          ]
        })
      };
    };

    try {
      const res = await apiClient.admin.getAreaPerformance({ ward: 'Ward 08' });
      assert.ok(capturedUrl.includes('/admin/analytics/area-performance?ward=Ward+08'));
      assert.equal(res.data[0].resolutionRatePercent, 84.4);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test.it('apiClient.admin.getMonthlyAnalytics passes year parameter', async () => {
    let capturedUrl = null;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          success: true,
          data: [
            {
              yearMonth: '2026-05',
              receivedCount: 20,
              resolvedCount: 18,
              pendingCount: 2,
              overdueCount: 1,
              resolutionRatePercent: 90.0,
              slaCompliancePercent: 95.0
            }
          ]
        })
      };
    };

    try {
      const res = await apiClient.admin.getMonthlyAnalytics({ year: 2026 });
      assert.ok(capturedUrl.includes('/admin/analytics/monthly?year=2026'));
      assert.equal(res.data[0].slaCompliancePercent, 95.0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
