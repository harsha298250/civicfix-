import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

import offlineStorage from '../src/utils/offlineStorage.js';
import { workerService } from '../src/services/workerService.js';
import { apiClient } from '../src/services/apiClient.js';

test('Phase 2.3.3: offlineStorage enqueues, queries, updates, and deletes evidence records', async () => {
  offlineStorage.resetMemoryStore();

  const mockEvidence = {
    taskId: 'cf-task-123',
    notes: 'Pothole asphalt layered and compacted flush.',
    files: ['data:image/jpeg;base64,mockphotodata'],
    latitude: 13.0827,
    longitude: 80.2707,
    accuracy: 12.5,
    locationNote: 'Directly in front of junction'
  };

  // 1. Enqueue
  const queued = await offlineStorage.enqueueEvidence(mockEvidence);
  assert.ok(queued.id, 'Queued record has an ID');
  assert.equal(queued.taskId, 'cf-task-123');
  assert.equal(queued.syncStatus, 'PENDING');
  assert.equal(queued.files.length, 1);

  // 2. Query count and list
  const count = await offlineStorage.getQueuedCount();
  assert.equal(count, 1, 'Outbox count is 1');

  const pending = await offlineStorage.getPendingEvidence();
  assert.equal(pending.length, 1);
  assert.equal(pending[0].id, queued.id);

  // 3. Update status to SYNCING then FAILED
  await offlineStorage.updateEvidenceStatus(queued.id, 'SYNCING');
  await offlineStorage.updateEvidenceStatus(queued.id, 'FAILED', 'Connection timeout');
  
  const updatedList = await offlineStorage.getPendingEvidence();
  assert.equal(updatedList[0].syncStatus, 'FAILED');
  assert.equal(updatedList[0].lastError, 'Connection timeout');
  assert.equal(updatedList[0].attempts, 1);

  // 4. Remove record
  await offlineStorage.removeEvidence(queued.id);
  const remainingCount = await offlineStorage.getQueuedCount();
  assert.equal(remainingCount, 0, 'Outbox count is 0 after removal');
});

test('Phase 2.3.3: workerService.submitEvidence saves to offline outbox when network fails', async () => {
  offlineStorage.resetMemoryStore();

  // Mock apiClient.complaints.submitEvidence to simulate connection failure (status 0)
  const originalSubmitEvidence = apiClient.complaints.submitEvidence;
  apiClient.complaints.submitEvidence = async () => {
    const err = new Error('Cannot connect to CivicFix backend at http://localhost:8080/api. Ensure Spring Boot is running.');
    err.status = 0;
    throw err;
  };

  try {
    const result = await workerService.submitEvidence('cf-task-456', {
      notes: 'Road surface leveled with bitumen mix.',
      files: ['data:image/jpeg;base64,mockphoto'],
      latitude: 13.085,
      longitude: 80.275
    });

    assert.ok(result.offlineQueued, 'Result indicates item was offlineQueued');
    assert.equal(result.taskId, 'cf-task-456');

    const outboxCount = await workerService.getOfflineOutboxCount();
    assert.equal(outboxCount, 1, 'Worker outbox count has 1 pending item');
  } finally {
    apiClient.complaints.submitEvidence = originalSubmitEvidence;
  }
});

test('Phase 2.3.3: workerService.submitEvidence strictly propagates 400 and 403 HTTP errors without masking as offline', async () => {
  offlineStorage.resetMemoryStore();

  const originalSubmitEvidence = apiClient.complaints.submitEvidence;
  apiClient.complaints.submitEvidence = async () => {
    const err = new Error('Work order is not in IN_PROGRESS state.');
    err.status = 400;
    throw err;
  };

  try {
    await assert.rejects(
      async () => {
        await workerService.submitEvidence('cf-task-789', {
          notes: 'Test note',
          files: ['data:image/jpeg;base64,mockphoto']
        });
      },
      (err) => {
        assert.equal(err.status, 400);
        assert.equal(err.message, 'Work order is not in IN_PROGRESS state.');
        return true;
      }
    );

    const outboxCount = await workerService.getOfflineOutboxCount();
    assert.equal(outboxCount, 0, 'Outbox remains empty on HTTP 400 rejection');
  } finally {
    apiClient.complaints.submitEvidence = originalSubmitEvidence;
  }
});

test('Phase 2.3.3: workerService.syncOfflineEvidence replays pending evidence and removes successfully synced items', async () => {
  offlineStorage.resetMemoryStore();

  // Enqueue 2 items directly
  await offlineStorage.enqueueEvidence({
    taskId: 'cf-task-1',
    notes: 'Task 1 repaired',
    files: ['data:image/jpeg;base64,photo1']
  });
  await offlineStorage.enqueueEvidence({
    taskId: 'cf-task-2',
    notes: 'Task 2 repaired',
    files: ['data:image/jpeg;base64,photo2']
  });

  const submittedTasks = [];
  const originalSubmitEvidence = apiClient.complaints.submitEvidence;
  apiClient.complaints.submitEvidence = async (taskId, formData) => {
    submittedTasks.push(taskId);
    return { data: { id: taskId, status: 'RESOLUTION_SUBMITTED' } };
  };

  try {
    const syncResult = await workerService.syncOfflineEvidence();
    assert.equal(syncResult.total, 2);
    assert.equal(syncResult.syncedCount, 2);
    assert.equal(syncResult.failedCount, 0);
    assert.deepEqual(submittedTasks, ['cf-task-1', 'cf-task-2']);

    const remaining = await workerService.getOfflineOutboxCount();
    assert.equal(remaining, 0, 'All successfully synced items removed from outbox');
  } finally {
    apiClient.complaints.submitEvidence = originalSubmitEvidence;
  }
});

test('Phase 2.3.3: English and Tamil localization keys for worker offline outbox are present and symmetric', () => {
  const enPath = path.join(rootDir, 'src', 'locales', 'en.json');
  const taPath = path.join(rootDir, 'src', 'locales', 'ta.json');

  const en = JSON.parse(fs.readFileSync(enPath, 'utf8'));
  const ta = JSON.parse(fs.readFileSync(taPath, 'utf8'));

  assert.ok(en.worker, 'en.json contains worker');
  assert.ok(ta.worker, 'ta.json contains worker');

  const requiredKeys = [
    'offlinePendingTitle',
    'offlinePendingDesc',
    'offlineSyncNow',
    'offlineSyncing',
    'offlineSyncSuccess',
    'offlineSyncNone',
    'offlineSyncPartial',
    'offlineSavedOffline'
  ];

  for (const k of requiredKeys) {
    assert.ok(en.worker[k], `en.json has worker.${k}`);
    assert.ok(ta.worker[k], `ta.json has worker.${k}`);
    // Check authentic Tamil unicode
    assert.match(ta.worker[k], /[\u0B80-\u0BFF]/, `ta.json worker.${k} contains authentic Tamil Unicode`);
  }
});
