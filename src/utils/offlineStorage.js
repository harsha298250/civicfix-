/**
 * CivicFix Offline Evidence Storage & Outbox Manager
 * 
 * Provides resilient, durable client-side storage for field repair evidence
 * using native IndexedDB with automatic fallback for test/unsupported environments.
 */

const DB_NAME = 'CivicFix_Offline_DB';
const DB_VERSION = 1;
const STORE_NAME = 'offline_evidence_queue';

// In-memory fallback for non-browser/test environments
let memoryStore = new Map();

/**
 * Open or create the IndexedDB database
 */
export const openDB = () => {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      resolve(null); // Fallback to memoryStore
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const db = event.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        store.createIndex('syncStatus', 'syncStatus', { unique: false });
        store.createIndex('taskId', 'taskId', { unique: false });
        store.createIndex('queuedAt', 'queuedAt', { unique: false });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
};

/**
 * Convert a File or Blob to Base64 Data URL for persistent serialization
 */
export const fileToDataUrl = (file) => {
  return new Promise((resolve, reject) => {
    if (!file) {
      resolve(null);
      return;
    }
    if (typeof FileReader === 'undefined') {
      // In node/test environment without FileReader
      resolve('data:image/jpeg;base64,synthetic-offline-photo-data');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = (err) => reject(err);
    reader.readAsDataURL(file);
  });
};

/**
 * Convert a Data URL back to a File/Blob for FormData submission
 */
export const dataUrlToFile = (dataUrl, filename = 'repair_evidence.jpg', mimeType = 'image/jpeg') => {
  if (!dataUrl || typeof dataUrl !== 'string') {
    return new Blob(['synthetic-offline-photo-data'], { type: mimeType });
  }
  const arr = dataUrl.split(',');
  const mime = arr[0].match(/:(.*?);/)?.[1] || mimeType;
  const bstr = typeof atob !== 'undefined' ? atob(arr[1] || '') : Buffer.from(arr[1] || '', 'base64').toString('binary');
  let n = bstr.length;
  const u8arr = new Uint8Array(n);
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n);
  }
  if (typeof File !== 'undefined') {
    return new File([u8arr], filename, { type: mime });
  }
  return new Blob([u8arr], { type: mime });
};

/**
 * Enqueue an evidence submission into the outbox
 */
export const enqueueEvidence = async (item) => {
  const recordId = item.id || `offline_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
  
  // Serialize files to dataUrls
  const serializedFiles = [];
  if (Array.isArray(item.files)) {
    for (const f of item.files) {
      if (typeof f === 'string') {
        serializedFiles.push({ name: 'evidence.jpg', type: 'image/jpeg', dataUrl: f });
      } else {
        const dataUrl = await fileToDataUrl(f);
        serializedFiles.push({
          name: f.name || 'evidence.jpg',
          type: f.type || 'image/jpeg',
          size: f.size || 0,
          dataUrl
        });
      }
    }
  }

  const record = {
    id: recordId,
    taskId: String(item.taskId).trim(),
    notes: String(item.notes || '').trim(),
    files: serializedFiles,
    latitude: item.latitude ?? null,
    longitude: item.longitude ?? null,
    accuracy: item.accuracy ?? null,
    locationNote: item.locationNote ?? null,
    deviceReported: !!item.deviceReported,
    queuedAt: new Date().toISOString(),
    syncStatus: 'PENDING', // PENDING, SYNCING, FAILED, SYNCED
    attempts: 0,
    lastError: null
  };

  const db = await openDB();
  if (!db) {
    memoryStore.set(recordId, record);
    return record;
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const req = store.put(record);
    req.onsuccess = () => resolve(record);
    req.onerror = () => reject(req.error);
  });
};

/**
 * Retrieve all pending or failed evidence submissions waiting to sync
 */
export const getPendingEvidence = async () => {
  const db = await openDB();
  if (!db) {
    return Array.from(memoryStore.values()).filter(
      (r) => r.syncStatus === 'PENDING' || r.syncStatus === 'FAILED'
    );
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly');
    const store = tx.objectStore(STORE_NAME);
    const req = store.getAll();
    req.onsuccess = () => {
      const records = req.result || [];
      const pending = records.filter(
        (r) => r.syncStatus === 'PENDING' || r.syncStatus === 'FAILED'
      );
      resolve(pending);
    };
    req.onerror = () => reject(req.error);
  });
};

/**
 * Get count of items awaiting synchronization
 */
export const getQueuedCount = async () => {
  const pending = await getPendingEvidence();
  return pending.length;
};

/**
 * Update sync status and error message for a specific record
 */
export const updateEvidenceStatus = async (id, status, error = null) => {
  const db = await openDB();
  if (!db) {
    const record = memoryStore.get(id);
    if (record) {
      record.syncStatus = status;
      record.attempts = (record.attempts || 0) + (status === 'SYNCING' ? 1 : 0);
      if (error) record.lastError = String(error);
      memoryStore.set(id, record);
    }
    return record;
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const getReq = store.get(id);
    getReq.onsuccess = () => {
      const record = getReq.result;
      if (!record) {
        resolve(null);
        return;
      }
      record.syncStatus = status;
      record.attempts = (record.attempts || 0) + (status === 'SYNCING' ? 1 : 0);
      if (error) record.lastError = String(error);
      const putReq = store.put(record);
      putReq.onsuccess = () => resolve(record);
      putReq.onerror = () => reject(putReq.error);
    };
    getReq.onerror = () => reject(getReq.error);
  });
};

/**
 * Remove record after successful sync to server
 */
export const removeEvidence = async (id) => {
  const db = await openDB();
  if (!db) {
    memoryStore.delete(id);
    return true;
  }

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const req = store.delete(id);
    req.onsuccess = () => resolve(true);
    req.onerror = () => reject(req.error);
  });
};

/**
 * Reset memory store (used in unit tests)
 */
export const resetMemoryStore = () => {
  memoryStore.clear();
};

export default {
  openDB,
  fileToDataUrl,
  dataUrlToFile,
  enqueueEvidence,
  getPendingEvidence,
  getQueuedCount,
  updateEvidenceStatus,
  removeEvidence,
  resetMemoryStore
};
