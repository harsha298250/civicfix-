# CivicFix — Web Push & Notification Architecture
## Progressive Enhancement, VAPID & Visibility-Aware Delivery

**Document Version:** 1.0  
**Phase:** 2.3.5 — Notification Improvements  
**Target Roles:** Citizens, Municipal Officers, Field Technicians, Supervisors  
**Date:** 2026-11-20  

---

### 1. Architectural Overview

CivicFix employs a multi-tiered, progressive enhancement strategy for delivering real-time civic alerts:

1. **Tier 1 (Base Layer) — Visibility-Aware Background Polling:**
   - Always available in any modern browser without requiring user permission grants.
   - Polite 60-second polling interval for unread counter updates (`GET /api/notifications/unread-count`).
   - Automatically pauses when tab is hidden (`document.visibilityState === 'hidden'`) using the HTML5 Page Visibility API to save battery and conserve municipal bandwidth.
   - Instantly checks and resumes polling when the resident switches back to the tab.

2. **Tier 2 (Progressive Enhancement) — Web Push with VAPID (RFC 8291, RFC 8292):**
   - For users granting push notification permissions in browsers supporting Service Workers.
   - Enables background delivery when the browser tab is closed or minimized.
   - End-to-end encrypted payload delivery directly to the registered Push Service endpoint.

3. **Tier 3 (SMS & Email Fallback):**
   - For critical urgent events (e.g. disaster triage, hazard escalation, technician dispatch) configured through Resident Profile Alert Preferences.

---

### 2. Web Push Protocol & VAPID Sequence

```
+---------------+              +------------------+              +----------------------+
| Resident      |              | CivicFix Backend |              | Browser Push Service |
| ServiceWorker |              | (Spring Boot)    |              | (FCM, Mozilla, Apple)|
+---------------+              +------------------+              +----------------------+
        |                               |                                   |
        | 1. Request Push Subscription  |                                   |
        |------------------------------------------------------------------>|
        | 2. Returns PushSubscription (endpoint, p256dh, auth)              |
        |<------------------------------------------------------------------|
        |                               |                                   |
        | 3. Register Subscription      |                                   |
        |    POST /api/notifications/push-subscription                     |
        |------------------------------>|                                   |
        |                               | (Stores subscription against user)|
        |                               |                                   |
        |                               | 4. Work order updated / dispatched|
        |                               |    Sign JWT with VAPID Private Key|
        |                               |    Encrypt payload with p256dh/auth|
        |                               | 5. POST to Push Service Endpoint  |
        |                               |---------------------------------->|
        | 6. ServiceWorker 'push' event |                                   |
        |<------------------------------------------------------------------|
        | 7. self.registration.showNotification(...)                        |
        |                               |                                   |
        | 8. Resident clicks notif      |                                   |
        |    'notificationclick' event  |                                   |
        |    clients.openWindow('/complaints/:id')                          |
```

---

### 3. VAPID Key Management & Security Specifications

1. **RFC 8292 Compliance:**
   - Voluntary Application Server Identification (VAPID) allows application servers to identify themselves to push services using an ECDSA key pair on the `prime256v1` (NIST P-256) curve.
2. **Key Storage:**
   - VAPID Private Key MUST never be exposed to the client or committed to source control.
   - Configured via environment variable `CIVICFIX_VAPID_PRIVATE_KEY` on production containers.
   - Public Key exposed via `GET /api/notifications/vapid-public-key` for client-side subscription initialization.
3. **Payload Encryption (RFC 8291):**
   - Payload is encrypted with `aes128gcm` using the client's public P-256 key (`p256dh`) and shared authentication secret (`auth`).

---

### 4. Service Worker Event Handlers

In `public/service-worker.js`:

```javascript
self.addEventListener('push', (event) => {
  if (!event.data) return;

  const data = event.data.json();
  const options = {
    body: data.body || 'New civic update regarding your reported issue.',
    icon: '/icon-192.png',
    badge: '/badge-72.png',
    data: {
      url: data.url || '/citizen/dashboard',
      complaintId: data.complaintId
    },
    actions: [
      { action: 'view', title: 'View Ticket' },
      { action: 'close', title: 'Dismiss' }
    ]
  };

  event.waitUntil(
    self.registration.showNotification(data.title || 'CivicFix Municipal Update', options)
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || '/';

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if (client.url.includes(targetUrl) && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});
```

---

### 5. Preference Governance

Residents retain complete control over alert channels via **Resident Profile & Preferences**:
- **Email Alerts:** Subscribed by default for work-order status transitions and officer resolution confirmations.
- **SMS Alerts:** Subscribed by default for field crew dispatches and immediate public hazard alerts.
- **Web Push:** Opt-in banner presented to resident upon their first submitted complaint. Preferences are persisted across sessions in browser storage and synchronized with municipal accounts.
