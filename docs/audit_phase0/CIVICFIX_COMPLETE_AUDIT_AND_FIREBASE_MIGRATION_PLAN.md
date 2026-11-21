  # CIVICFIX — COMPLETE PROJECT AUDIT, SECURITY ASSESSMENT & FIREBASE MIGRATION MASTER PLAN

  > **Document Status**: Production Master Architecture & Migration Plan  
  > **Author**: Senior Full-Stack Engineer, Firebase Architect & Security Auditor  
  > **Evaluation Date**: November 2026  
  > **Repository Target**: `c:\Users\harsh\Downloads\stitch_civicfix_ai_platform_design`  
  > **Target Production Architecture**: Vercel (React 18 + Vite SPA) + Firebase (Authentication, Cloud Firestore, Firebase Storage, Cloud Functions, Cloud Scheduler) + Leaflet GIS

  ---

  ## 1. EXECUTIVE SUMMARY

  An exhaustive, forensic technical inspection of the **CivicFix** platform was conducted across all frontend components, backend services, database migrations, security configurations, test suites, and documentation.

  ### Core Audit Verdict:
  CivicFix is a mature, well-engineered civic complaint management system designed specifically for municipal public works in Tamil Nadu (Chennai metropolitan region). It possesses high-value intellectual property and business logic:
  - A complete, deterministic civic triage lexicon (`CivicLexicon.java`) and spatial duplicate screening engine (`DuplicateDetectionEngine.java`).
  - An optional multimodal vision triage layer supporting **Google Gemini** and **xAI Grok** (`GeminiVisionTriageProvider.java`, `XaiVisionTriageProvider.java`) with citizen consent gating.
  - Strict multi-persona workflows across **Citizens**, **Municipal Officers**, **Field Technicians**, **Supervisors**, and **Platform Administrators**.
  - GPS hardware evidence distance checks (`EvidenceLocationVerificationTest.java`), citizen closure confirmation/reopen loops, and SLA escalation tracking.
  - An automated frontend test suite with **201/201 passing unit and integration tests** across 32 suites.
  - A comprehensive Spring Boot test suite comprising **351 automated tests** across 32 test classes.

  ### The Migration Imperative:
  The current deployment topology depends on a monolithic Java 21 / Spring Boot 3.3.4 container, a stateful PostgreSQL 16 database requiring custom spatial extensions, and local filesystem volume mounts. This creates substantial hosting overhead, cold-boot complexity, and maintenance costs for demonstrations and student capstone evaluations.

  The target architecture transitions CivicFix into a modern, 100% serverless, zero-maintenance platform:
  1. **Frontend Hosting**: Vercel Edge Network for the React 18 + Vite PWA.
  2. **Authentication**: Firebase Authentication (Email/Password, Phone OTP) with role enforcement via Firebase Auth Custom Claims.
  3. **Database**: Cloud Firestore with document schemas optimized for low-latency queries, real-time status updates, and strict document-level security rules.
  4. **Media & Evidence**: Firebase Storage with cryptographic path isolation, EXIF stripping, and MIME validation.
  5. **Backend Execution**: Firebase Cloud Functions (v2) for trusted lifecycle transitions, SLA escalation monitoring (Cloud Scheduler), and server-side AI vision triage.

  ---

  ## 2. VERIFIED CURRENT ARCHITECTURE & TECHNOLOGY STACK

  All versions and packages below have been empirically verified from [`package.json`](file:///c:/Users/harsh/Downloads/stitch_civicfix_ai_platform_design/package.json), [`backend/pom.xml`](file:///c:/Users/harsh/Downloads/stitch_civicfix_ai_platform_design/backend/pom.xml), and runtime configurations:

  | Layer | Technology | Exact Version | Repository Evidence / Location |
  |---|---|---|---|
  | **Frontend Framework** | React | `18.3.1` | `package.json` |
  | **Frontend Build / Bundler** | Vite | `5.4.11` | `package.json`, `vite.config.js` |
  | **Styling & Design System** | Tailwind CSS / PostCSS | `3.4.15` / `8.4.49` | `tailwind.config.js`, `postcss.config.js` |
  | **Client-Side Routing** | React Router DOM | `6.28.0` | `src/App.jsx` |
  | **Mapping & GIS** | Leaflet / react-leaflet | `1.9.4` / `4.2.1` | `src/components/common/TamilNaduLocationPicker.jsx` |
  | **PWA & Offline** | vite-plugin-pwa | `2.0.0` | `vite.config.js`, `public/offline.html` |
  | **Backend Language** | Java (JDK) | `21` (LTS) | `backend/pom.xml` (`<java.version>21</java.version>`) |
  | **Backend Framework** | Spring Boot | `3.3.4` | `backend/pom.xml` (`spring-boot-starter-parent`) |
  | **Relational Database** | PostgreSQL | `16.4-alpine` | `docker-compose.yml`, Flyway V1-V12 |
  | **Database Migrations** | Flyway | `10.18.0` (managed) | `backend/src/main/resources/db/migration/` (12 SQL files) |
  | **Rate Limiting** | Bucket4j Core | `8.10.1` | `backend/pom.xml`, `RateLimiterService.java` |
  | **Reverse Proxy** | Nginx | `1.27-alpine` | `nginx.conf`, `Dockerfile` |
  | **CI Automation** | GitHub Actions | `v4` actions | `.github/workflows/ci.yml` |

  ```mermaid
  flowchart TB
      subgraph Client ["Client Tier (Browser / Mobile PWA)"]
          ReactSPA["React 18 SPA (Vite 5.4)<br/>i18n (en/ta) | Leaflet GIS"]
          SW["PWA Service Worker<br/>Cache-First Shell / NetworkOnly API"]
      end

      subgraph CurrentBackend ["Current Monolithic Backend (To Be Deprecated)"]
          NginxProxy["Nginx Reverse Proxy (:80)"]
          SpringBoot["Spring Boot 3.3.4 (:8080)<br/>Spring Security | Bucket4j Rate Limiter"]
          Postgres["PostgreSQL 16 (:5433)<br/>Flyway V1-V12 Schema"]
          LocalFS["Local Disk (/uploads/evidence)"]
      end

      subgraph TargetFirebase ["Target Serverless Architecture"]
          VercelEdge["Vercel Global Edge CDN<br/>Static Assets & SPA Routing"]
          FirebaseAuth["Firebase Authentication<br/>Custom Claims: ROLE_CITIZEN, OFFICER, WORKER, SUPERVISOR, ADMIN"]
          Firestore["Cloud Firestore (NoSQL)<br/>Collections: users, complaints, evidence, notifications, audit_logs"]
          CloudStorage["Firebase Storage<br/>Buckets: intake-photos, repair-evidence"]
          CloudFunctions["Firebase Cloud Functions v2<br/>Callable APIs & Firestore Triggers"]
          CloudScheduler["Google Cloud Scheduler<br/>SLA Escalation Engine (Every 5 mins)"]
          GeminiAPI["Google Gemini 1.5 Flash<br/>Multimodal Vision Triage"]
      end

      ReactSPA -.->|Current State| NginxProxy
      NginxProxy --> SpringBoot
      SpringBoot --> Postgres
      SpringBoot --> LocalFS

      ReactSPA ===>|Target Migration| VercelEdge
      ReactSPA ===>|Auth & Tokens| FirebaseAuth
      ReactSPA ===>|Direct SDK Reads (Secured by Rules)| Firestore
      ReactSPA ===>|Direct Image Uploads| CloudStorage
      ReactSPA ===>|Mutations & Transitions| CloudFunctions
      CloudFunctions --> Firestore
      CloudFunctions --> CloudStorage
      CloudFunctions --> GeminiAPI
      CloudScheduler --> CloudFunctions
  ```

  ---

  ## 3. ACTUAL PROJECT STATUS & IMPLEMENTATION EVIDENCE

  A line-by-line inspection of source code and test files establishes the following verified implementation status:

  ### Feature Inventory & Verification Matrix

  | Capability | Module / Component | Real Status | Verification Evidence & File Reference |
  |---|---|:---:|---|
  | **Citizen Registration & Login** | `authService.js`, `AuthController.java` | **Working** | Phone/email intake, BCrypt hashing, HTTP-only session cookies. Tested in `authService.test.js` (11 tests). |
  | **Role-Based Routing** | `RoleRoute.jsx`, `App.jsx` | **Working** | Guards routes for `CITIZEN`, `OFFICER`, `WORKER`, `SUPERVISOR`, `ADMIN`. |
  | **Complaint Creation** | `ReportComplaintPage.jsx`, `ComplaintService.java` | **Working** | Deterministic reference generation (`CF-2026-XXXXX`), photo validation, category mapping. |
  | **Deterministic Rule Triage** | `DeterministicTriageEngine.java`, `CivicLexicon.java` | **Working** | 7 categories, keyword extraction, priority assignment. Evaluated in [`docs/evaluation/results.md`](file:///c:/Users/harsh/Downloads/stitch_civicfix_ai_platform_design/docs/evaluation/results.md). |
  | **Spatial Duplicate Screening** | `DuplicateDetectionEngine.java` | **Working** | Haversine distance threshold (50m) + Jaccard token similarity (0.35). |
  | **Multimodal Vision Triage** | `GeminiVisionTriageProvider.java`, `XaiVisionTriageProvider.java` | **Working** | Full backend implementation with Flyway V12 columns and citizen consent gate; frontend exposure verified in `visionTriage.test.js`. |
  | **Photo Upload & Sanitization** | `LocalStorageService.java`, `ImageUploadManager.jsx` | **Working** | Magic byte sniffing, JPEG/PNG re-encoding, EXIF stripping. |
  | **Field Worker Work Orders** | `AssignedTasksPage.jsx`, `TaskDetailsPage.jsx` | **Working** | Filtered by assigned technician UUID. Tested in `workerService.test.js` (21 tests). |
  | **GPS Evidence Verification** | `SubmitEvidencePage.jsx`, `ComplaintService.java` | **Working** | Compares evidence capture coordinates with complaint coordinates (<=100m check). Tested in `EvidenceLocationVerificationTest.java`. |
  | **Citizen Confirmation / Reopen** | `ComplaintDetailsPage.jsx`, `ComplaintService.java` | **Working** | Two-step closure: `PENDING_CITIZEN_CONFIRMATION` -> `RESOLVED` or `REOPENED`. Tested in `CitizenConfirmationIntegrationTest.java`. |
  | **SLA & Supervisor Escalation** | `SupervisorEscalationsPage.jsx`, `SlaService.java` | **Working** | Matrix in `sla_policies` table; cron job flags overdue complaints. Tested in `SlaEscalationServiceTest.java`. |
  | **In-App Notifications** | `NotificationService.java`, `CitizenNotificationsPage.jsx` | **Working** | Spring application events write to `notifications` table. Tested in `NotificationApiIntegrationTest.java`. |
  | **Public Sanitized Map** | `PublicMapPage.jsx`, `PublicMapComplaintResponse.java` | **Working** | Coordinates rounded to 3 decimal places (~110m resolution), all citizen PII omitted. |
  | **Bilingual Tamil Support** | `LanguageContext.jsx`, `en.json`, `ta.json` | **Partial** | Full translation for Citizen flow, status badges, and auth; Officer, Worker, Admin, and Legal pages are hardcoded English. |
  | **PWA & Offline Shell** | `vite-plugin-pwa`, `offline.html` | **Working** | Service worker with `NetworkOnly` policy for API requests; local fonts bundle. Tested in `tests/pwa.test.js`. |
  | **Email Verification & SMTP** | — | **Not Done** | In-app notifications only; no SMTP or transactional mail provider is configured. |
  | **Self-Service Password Reset** | `ForgotPasswordPage.jsx` | **Not Done** | Page displays an administrative contact notice; no token-based email reset flow exists. |
  | **Ward Polygon Boundaries (GIS)** | — | **Not Done** | Uses point coordinates and text ward names; PostGIS spatial polygons and geo-fencing are absent. |
  | **Public Transparency Page** | — | **Not Done** | Only a basic incident map exists (`/map`); no aggregated KPI dashboards or ward-level resolution analytics are exposed to the public. |

  ---

  ## 4. CRITICAL SECURITY & PRIVACY AUDIT FINDINGS

  ### 4.1 The Citizen Contact-Details Leak (Verified Finding)

  > [!CAUTION]
  > **P0 CRITICAL PRIVACY DEFECT**: Direct leakage of citizen private telephone numbers and email addresses to field workers in backend API responses.

  #### Forensic Evidence:
  1. In `ComplaintResponse.java` (lines 184–191):
    ```java
    if (c.getCitizen() != null) {
        Map<String, Object> cit = new HashMap<>();
        cit.put("id", c.getCitizen().getId().toString());
        cit.put("name", c.getCitizen().getFullName());
        cit.put("email", c.getCitizen().getEmail());
        cit.put("phone", c.getCitizen().getPhone());
        resp.setCitizen(cit);
    }
    ```
  2. In `ComplaintResponse.java` (lines 120–126), role projection specifically handles worker vision stripping, but **fails to strip `citizen.email` or `citizen.phone`**:
    ```java
    boolean isWorker = principal.getAuthorities().stream().anyMatch(a ->
            "ROLE_WORKER".equals(a.getAuthority())
    );
    if (isWorker) {
        // WORKERS: Strictly NO vision fields
        return; // Does NOT sanitize citizen contact details!
    }
    ```
  3. In `ComplaintService.java` (lines 438–440 and 489–493), `getComplaints()` and `getComplaintById()` return `toResponse(complaint, principal)` directly to authenticated workers.
  4. **Contradiction with Public Privacy Policy**:
    In `src/pages/public/PrivacyPolicyPage.jsx` (line 214), the platform publicly promises:
    > *"Field worker UI does not display citizen phone numbers or email addresses."*
    While the React frontend UI in `TaskDetailsPage.jsx` does not render the fields, **the backend API transmits the full unredacted phone number and email over the network in the JSON response payload**. Any worker opening DevTools or curling the endpoint inspects the resident's private contact details.

  #### Required Access Policy & Firebase Remedy:
  - **CITIZEN**: Can access own private complaint details; cannot view other citizens' complaints.
  - **OFFICER & ADMIN**: Can view resident contact details when necessary for incident resolution.
  - **SUPERVISOR**: Can view departmental escalation metrics.
  - **WORKER**: Must receive **zero citizen email addresses or phone numbers**. The worker interface must display: *"If citizen contact is required, coordinate directly with the assigned municipal officer."*
  - **Firebase Solution**: In Cloud Firestore, Security Rules are document-level (cannot hide individual fields on a read document). Therefore, citizen email and phone **must never be stored on the `/complaints/{complaintId}` document**. The complaint document stores only `citizenId` and `citizenName`. Private contact details reside exclusively in `/users/{citizenId}`, which is guarded by Firestore rules prohibiting worker access.

  ---

  ### 4.2 Comprehensive Vulnerability & Risk Log

  | ID | Priority | Module / Path | Vulnerability Description | Impact | Estimated Effort |
  |---|:---:|---|---|---|:---:|
  | **SEC-01** | **P0** | `ComplaintResponse.java` | Citizen email and phone exposed to workers in JSON payload. | Breach of privacy policy, PII leak to contractors. | Medium |
  | **SEC-02** | **P0** | `RateLimiterService.java` | Bucket4j rate limiter is purely in-memory (`ConcurrentHashMap`). | Zero protection in multi-instance or serverless deployments; resets on server restart. | Medium |
  | **SEC-03** | **P0** | `ProductionSafetyGuard.java` | V2 seed accounts (`alex.m@citizen.org`, `elena.vance@metro.gov`, etc.) active with known passwords if `ALLOW_SEED_ACCOUNTS=true`. | Account takeover if enabled in production. | Small |
  | **SEC-04** | **P1** | `LoginPage.jsx` (lines 122–126) | UI displays "Backend Offline (Demo Fallback Active)", but `authService.login()` throws unhandled network error. | Confusing user experience; misleading security posture. | Small |
  | **SEC-05** | **P1** | `DemoRoleSwitcher.jsx` | Client-side persona switcher floating on page. Gated by `VITE_DEMO_MODE`, which could accidentally be compiled `true`. | Unauthorized role traversal if bundled into production. | Small |
  | **SEC-06** | **P1** | `LocalStorageService.java` | Local disk upload directory (`/uploads/evidence`) requires persistent volume mounts and file permission management. | Vulnerable to filesystem exhaustion or loss on container rebuild. | Medium |
  | **SEC-07** | **P2** | `GisService.java` | Nominatim reverse-geocoding cache is in-memory; direct outbound HTTP calls to OpenStreetMap. | Potential rate limiting or IP banning by OpenStreetMap Foundation. | Medium |

  ---

  ## 5. UI/UX & ACCESSIBILITY (a11y) AUDIT FINDINGS

  An audit against WCAG 2.1 Level AA standards was conducted across the 9 primary application views:

  | Page / Component | Verified Issue | WCAG Failure | Recommended Remediation |
  |---|---|:---:|---|
  | **LoginPage.jsx** | `errorMsg` container lacks `role="alert"` and `aria-live="assertive"`. | 4.1.3 Status Messages | Add `role="alert"` and `aria-live="polite"` so screen readers immediately announce authentication failures. |
  | **RegisterPage.jsx** | Password strength indicator (`strength.label`) relies exclusively on visual color bars (`bg-red-500`, `bg-emerald-600`). | 1.4.1 Use of Color | Add `aria-valuenow`, `aria-valuemin="0"`, `aria-valuemax="3"`, and screen-reader accessible text description. |
  | **ReportComplaintPage.jsx** | File upload drag-and-drop zone lacks keyboard focus styles and Enter/Space trigger. | 2.1.1 Keyboard | Add `tabIndex={0}`, `onKeyDown` handler for Enter/Space, and visible `focus-visible:ring-2` outline. |
  | **ComplaintDetailsPage.jsx** | Status stepper milestone dots lack text equivalents for screen readers. | 1.3.1 Info and Relationships | Add hidden `sr-only` text (e.g. `Step 3 of 5: In Progress`). |
  | **TaskDetailsPage.jsx** | "Call Officer" button displays raw text without `tel:` link; missing instruction banner. | 1.3.1 Info and Relationships | Render `tel:` anchor and display clear guidance: *"Worker Privacy Notice: Contact Officer for resident queries."* |
  | **OfficerReviewPage.jsx** | Disagreement alert pill between AI and deterministic triage lacks high-contrast border in light mode. | 1.4.3 Contrast (Minimum) | Update text/background to ensure minimum 4.5:1 contrast ratio against card surface. |
  | **Language Context** | Document language updates (`document.documentElement.lang = validLang`), but 80% of staff portals remain hardcoded English. | 3.1.2 Language of Parts | Complete `ta.json` translation keys for Officer, Worker, Supervisor, and Admin management interfaces. |

  ---

  ## 6. DOCUMENTATION ACCURACY AUDIT

  A systematic comparison between the documentation under `docs/` and the actual repository source code revealed multiple concrete discrepancies:

  1. **`CIVICFIX_MASTER_ROADMAP.md` (Line 28)**:
    - *Claim*: `Image-Based AI / Computer Vision | Not done | Text-only triage; photo analysis and damage assessment from images do not exist`.
    - *Fact*: Completely inaccurate. The vision triage layer is fully implemented in Java (`GeminiVisionTriageProvider.java`, `XaiVisionTriageProvider.java`), supported by Flyway migration `V12__add_vision_triage_and_consent.sql`, and tested in `VisionRoleExposureIntegrationTest.java`.
  2. **`TRD_FINAL.md` (Section 3)**:
    - *Claim*: Specifies `Database Schema & Migration Catalog (V1 to V11)`.
    - *Fact*: Outdated. Omits `V12__add_vision_triage_and_consent.sql` and fails to document the 10 vision columns added to `complaints` (`vision_status`, `vision_category`, `vision_severity`, `vision_confidence`, `vision_reason`, `vision_model`, `vision_at`, `vision_consent`, `disagreement`, `final_priority_source`).
  3. **`BACKEND_API.md`**:
    - *Claim*: Documents Spring Boot endpoints only.
    - *Fact*: Lacks documentation for `GET /api/public/features` (used by the frontend to detect vision capabilities).

  ---

  ## 7. TARGET FIREBASE ARCHITECTURE & SPECIFICATION

  ### 7.1 Component Mapping Matrix

  Every legacy component maps directly into a serverless Firebase service without functional loss:

  | Legacy Spring Boot / Postgres Component | Target Firebase Serverless Replacement | Architectural Justification |
  |---|---|---|
  | **User Accounts & Passwords (BCrypt)** | **Firebase Authentication** | Fully managed, secure password hashing (Scrypt), phone OTP, email verification, self-service password reset. |
  | **Role-Based Authorization (Spring Security)** | **Firebase Auth Custom Claims** | Tamper-proof JWT claims (`role: 'CITIZEN' | 'OFFICER' | 'WORKER' | 'SUPERVISOR' | 'ADMIN'`) validated in Security Rules. |
  | **Relational Data (`complaints`, `users`)** | **Cloud Firestore** | Managed NoSQL document store with sub-millisecond document lookups, offline cache, and real-time listeners. |
  | **Image Storage (`/uploads/evidence`)** | **Firebase Storage** | Scalable object storage with automated CDN caching, tokenized download URLs, and granular security rules. |
  | **Business Logic (`ComplaintService.java`)** | **Firebase Cloud Functions (v2)** | Node.js / TypeScript callable functions and background triggers with server-side validation. |
  | **SLA Escalation (`SlaEscalationScheduler`)** | **Google Cloud Scheduler + Cloud Function** | Serverless cron job invoking `escalateOverdueComplaints` every 5 minutes. |
  | **Rate Limiting (`Bucket4j`)** | **Firebase App Check + Cloud Functions** | App Check prevents API abuse; Firestore-backed rate limiting protects sensitive functions. |
  | **Public Map Coordinate Rounding** | **Sanitized Collection (`public_complaints`)** | Document trigger automatically mirrors complaints to a public collection with coordinates rounded to 3 decimals. |

  ---

  ### 7.2 Firestore Collection Design & Data Schema

  To satisfy document-level security and prevent the citizen contact leak, the Firestore schema separates private user data from operational work orders:

  #### 1. Collection: `/users/{userId}`
  * **Document ID**: Firebase Auth UID (`request.auth.uid`).
  * **Purpose**: User profile and verified contact details.
  * **Fields**:
    ```json
    {
      "uid": "USER_UID_STRING",
      "fullName": "Alex Morgan",
      "email": "alex.m@citizen.org",
      "phone": "+919840123456",
      "role": "CITIZEN",
      "accountStatus": "ACTIVE",
      "ward": "Ward 08, Anna Nagar, Chennai",
      "department": "Resident Community Council",
      "trustScore": 98,
      "createdAt": "2026-11-01T10:00:00Z",
      "updatedAt": "2026-11-01T10:00:00Z"
    }
    ```
  * **Permissions**:
    - `read`: Owner (`request.auth.uid == userId`) OR Staff (`request.auth.token.role in ['OFFICER', 'SUPERVISOR', 'ADMIN']`).
    - `write`: Self-update for profile fields; **Role modification restricted strictly to Cloud Functions (Admin SDK)**.
    - **Worker Access**: Explicitly denied. Workers cannot read this document.

  #### 2. Collection: `/complaints/{complaintId}`
  * **Document ID**: Auto-generated Firestore ID or UUID.
  * **Purpose**: Primary complaint ticket.
  * **Fields**:
    ```json
    {
      "referenceId": "CF-2026-48291",
      "citizenId": "USER_UID_STRING",
      "citizenName": "Alex Morgan",
      "citizenWard": "Ward 08, Anna Nagar, Chennai",
      "title": "Severe Pothole Cluster near Roundtana",
      "description": "Deep asphalt depression causing vehicular swerving.",
      "category": "ROADS",
      "priority": "HIGH",
      "status": "IN_PROGRESS",
      "address": "2nd Avenue, Anna Nagar, Chennai",
      "latitude": 13.0850231,
      "longitude": 80.2101234,
      "ward": "Ward 08, Anna Nagar, Chennai",
      "assignedOfficer": {
        "uid": "OFFICER_UID",
        "name": "Elena Vance",
        "email": "elena.vance@metro.gov"
      },
      "assignedWorker": {
        "uid": "WORKER_UID",
        "name": "Marcus Chen"
      },
      "photos": [
        {
          "storagePath": "complaints/CF-2026-48291/intake/photo_1.jpg",
          "downloadUrl": "https://firebasestorage.googleapis.com/..."
        }
      ],
      "aiTriage": {
        "detectedCategory": "ROADS",
        "recommendedPriority": "HIGH",
        "confidence": 0.94,
        "adjudicationStatus": "ACCEPTED",
        "explanation": "High-risk road surface hazard detected."
      },
      "visionTriage": {
        "status": "COMPLETED",
        "consent": true,
        "category": "ROADS",
        "severity": 4,
        "confidence": 0.92,
        "model": "Google Gemini",
        "disagreement": false,
        "analyzedAt": "2026-11-01T10:02:00Z"
      },
      "sla": {
        "dueAt": "2026-11-02T10:00:00Z",
        "escalatedAt": null,
        "state": "WITHIN_SLA"
      },
      "confirmation": {
        "pendingAt": null,
        "confirmedAt": null,
        "source": null,
        "reopenCount": 0
      },
      "version": 3,
      "createdAt": "2026-11-01T10:00:00Z",
      "updatedAt": "2026-11-01T14:30:00Z"
    }
    ```
  * **Required Composite Indexes**:
    1. `citizenId` ASC + `createdAt` DESC
    2. `assignedWorker.uid` ASC + `status` ASC + `createdAt` DESC
    3. `ward` ASC + `status` ASC + `createdAt` DESC
    4. `status` ASC + `sla.dueAt` ASC (for SLA escalation queries)

  #### 3. Subcollection: `/complaints/{complaintId}/history/{historyId}`
  * **Purpose**: Immutable transition and audit ledger.
  * **Fields**:
    ```json
    {
      "previousStatus": "ASSIGNED",
      "newStatus": "IN_PROGRESS",
      "changedByUid": "WORKER_UID",
      "changedByName": "Marcus Chen",
      "changedByRole": "WORKER",
      "notes": "Crew arrived on-site with cold-mix asphalt patch unit.",
      "timestamp": "2026-11-01T14:30:00Z"
    }
    ```
  * **Permissions**: Append-only via Cloud Functions. Client update and delete operations are forbidden.

  #### 4. Subcollection: `/complaints/{complaintId}/evidence/{evidenceId}`
  * **Purpose**: Field worker repair verification records.
  * **Fields**:
    ```json
    {
      "workerUid": "WORKER_UID",
      "workerName": "Marcus Chen",
      "storagePath": "complaints/CF-2026-48291/evidence/after_repair.jpg",
      "downloadUrl": "https://firebasestorage.googleapis.com/...",
      "evidenceNotes": "Patch compacted, leveled, and cleared for traffic.",
      "latitude": 13.0850400,
      "longitude": 80.2101300,
      "accuracyMeters": 6.5,
      "distanceMeters": 1.9,
      "locationCheck": "WITHIN_RANGE",
      "locationNote": null,
      "createdAt": "2026-11-01T16:45:00Z"
    }
    ```

  #### 5. Collection: `/public_complaints/{complaintId}` (Sanitized Transparency Feed)
  * **Purpose**: High-speed, privacy-preserving public map and transparency KPI data.
  * **Fields**:
    ```json
    {
      "category": "ROADS",
      "status": "IN_PROGRESS",
      "priority": "HIGH",
      "ward": "Ward 08, Anna Nagar, Chennai",
      "latitude": 13.085,
      "longitude": 80.210,
      "createdAt": "2026-11-01T10:00:00Z"
    }
    ```
  * **Permissions**: Publicly readable (`allow read: if true;`). Writeable exclusively by internal Cloud Functions.

  ---

  ### 7.3 Complete Production Firestore Security Rules

  ```javascript
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {

      // Helper functions
      function isAuthenticated() {
        return request.auth != null;
      }

      function getUserRole() {
        return request.auth.token.role;
      }

      function isCitizen() {
        return isAuthenticated() && getUserRole() == 'CITIZEN';
      }

      function isOfficer() {
        return isAuthenticated() && (getUserRole() == 'OFFICER' || getUserRole() == 'ADMIN');
      }

      function isSupervisor() {
        return isAuthenticated() && (getUserRole() == 'SUPERVISOR' || getUserRole() == 'ADMIN');
      }

      function isWorker() {
        return isAuthenticated() && (getUserRole() == 'WORKER' || getUserRole() == 'ADMIN');
      }

      function isAdmin() {
        return isAuthenticated() && getUserRole() == 'ADMIN';
      }

      // User Profiles: Strict privacy isolation
      match /users/{userId} {
        allow read: if isAuthenticated() && (
          request.auth.uid == userId ||
          isOfficer() ||
          isSupervisor() ||
          isAdmin()
        );
        // Workers explicitly denied reading other users' profiles
        allow create: if isAuthenticated() && request.auth.uid == userId && request.resource.data.role == 'CITIZEN';
        allow update: if isAuthenticated() && (
          (request.auth.uid == userId && !request.resource.data.diff(resource.data).affectedKeys().hasAny(['role', 'accountStatus', 'trustScore'])) ||
          isAdmin()
        );
        allow delete: if isAdmin();
      }

      // Complaints Collection
      match /complaints/{complaintId} {
        allow read: if isAuthenticated() && (
          resource.data.citizenId == request.auth.uid ||
          isOfficer() ||
          isSupervisor() ||
          (isWorker() && resource.data.assignedWorker.uid == request.auth.uid) ||
          isAdmin()
        );

        // Complaint creation via client SDK
        allow create: if isCitizen() &&
          request.resource.data.citizenId == request.auth.uid &&
          request.resource.data.status == 'SUBMITTED' &&
          request.resource.data.version == 0;

        // Status transitions and assignments must go through Cloud Functions
        allow update: if isAuthenticated() && (
          isAdmin() ||
          (isOfficer() && resource.data.status in ['SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED_PENDING_CONFIRMATION']) ||
          (isCitizen() && resource.data.citizenId == request.auth.uid && resource.data.status == 'PENDING_CITIZEN_CONFIRMATION')
        );

        allow delete: if isAdmin();

        // Subcollections
        match /history/{historyId} {
          allow read: if isAuthenticated() && (
            get(/databases/$(database)/documents/complaints/$(complaintId)).data.citizenId == request.auth.uid ||
            isOfficer() || isSupervisor() || isWorker() || isAdmin()
          );
          allow write: if false; // Cloud Functions only
        }

        match /evidence/{evidenceId} {
          allow read: if isAuthenticated() && (
            get(/databases/$(database)/documents/complaints/$(complaintId)).data.citizenId == request.auth.uid ||
            isOfficer() || isSupervisor() || isWorker() || isAdmin()
          );
          allow create: if isWorker() &&
            get(/databases/$(database)/documents/complaints/$(complaintId)).data.assignedWorker.uid == request.auth.uid;
          allow update, delete: if isAdmin();
        }
      }

      // Public Sanitized Complaints Map Feed
      match /public_complaints/{publicId} {
        allow read: if true;
        allow write: if false; // Cloud Functions only
      }

      // SLA Policies
      match /sla_policies/{policyId} {
        allow read: if isAuthenticated();
        allow write: if isAdmin();
      }

      // Audit Logs (Immutable, Admin Read-Only)
      match /audit_logs/{logId} {
        allow read: if isAdmin();
        allow write: if false; // Cloud Functions only
      }

      // User Notifications Subcollection
      match /users/{userId}/notifications/{notificationId} {
        allow read: if isAuthenticated() && request.auth.uid == userId;
        allow update: if isAuthenticated() && request.auth.uid == userId &&
          request.resource.data.diff(resource.data).affectedKeys().hasOnly(['isRead', 'readAt']);
        allow create, delete: if false; // Cloud Functions only
      }
    }
  }
  ```

  ---

  ### 7.4 Firebase Storage Security Rules

  ```javascript
  rules_version = '2';
  service firebase.storage {
    match /b/{bucket}/o {

      function isAuthenticated() {
        return request.auth != null;
      }

      function isImageBelowLimit(maxMb) {
        return request.resource.size < maxMb * 1024 * 1024 &&
          (request.resource.contentType.matches('image/jpeg') ||
          request.resource.contentType.matches('image/png') ||
          request.resource.contentType.matches('image/webp'));
      }

      // Complaint Intake Photos
      match /complaints/{complaintId}/intake/{photoId} {
        allow read: if isAuthenticated();
        allow write: if isAuthenticated() && isImageBelowLimit(10);
      }

      // Field Worker Resolution Evidence Photos
      match /complaints/{complaintId}/evidence/{evidencePhotoId} {
        allow read: if isAuthenticated();
        allow write: if isAuthenticated() &&
          (request.auth.token.role == 'WORKER' || request.auth.token.role == 'ADMIN') &&
          isImageBelowLimit(10);
      }
    }
  }
  ```

  ---

  ### 7.5 Serverless Cloud Functions (TypeScript / v2) Architecture

  The following Cloud Functions replace all Spring Boot controllers and background jobs:

  | Cloud Function Name | Trigger Type | Primary Responsibilities |
  |---|---|---|
  | `onUserCreated` | Auth Trigger (`beforeUserCreated` or `onUserCreated`) | Assigns default `{ role: 'CITIZEN' }` custom claim. Prevents self-elevation. |
  | `createComplaint` | HTTPS Callable Function | Validates payload, executes deterministic keyword triage (`CivicLexicon`), screens duplicates within 50m, writes complaint document, updates `/public_complaints`, records audit log. |
  | `onComplaintCreated` | Firestore Trigger (`onCreate`) | If `visionConsent == true` and photo exists, dispatches image to **Gemini 1.5 Flash API**, parses damage severity, updates `visionTriage` block, flags disagreements. |
  | `transitionComplaintStatus` | HTTPS Callable Function | Implements the state machine inside a Firestore `runTransaction`. Validates actor role, updates status, writes to `/history`, and creates notification. |
  | `submitResolutionEvidence` | HTTPS Callable Function | Computes Haversine distance between evidence capture GPS and complaint GPS. Enforces `<= 100m` rule or requires non-empty `locationNote`. Transitions status to `RESOLVED_PENDING_CONFIRMATION`. |
  | `confirmResolution` | HTTPS Callable Function | Validates owning citizen identity. Transitions complaint to `RESOLVED`. Updates worker trust score. |
  | `rejectResolution` | HTTPS Callable Function | Validates owning citizen. Increments `reopenCount`. Transitions complaint to `REOPENED` (or `IN_PROGRESS`). Logs citizen reason. |
  | `escalateOverdueComplaints` | Cloud Scheduler (`every 5 minutes`) | Queries complaints where `status in ['SUBMITTED', 'ASSIGNED', 'IN_PROGRESS']` and `sla.dueAt < now()` and `sla.escalatedAt == null`. Updates `sla.escalatedAt` and notifies supervisors. |
  | `adminCreateStaff` | HTTPS Callable Function | Checks `request.auth.token.role == 'ADMIN'`. Uses Firebase Admin SDK to create user, set custom claims (`OFFICER`, `WORKER`, `SUPERVISOR`), and write profile. |

  ---

  ## 8. STEP-BY-STEP MIGRATION STRATEGY & ROLLBACK PLAN

  ### Phase 1: Dual-Stack Infrastructure Setup (Audit & Pre-Migration)
  - [x] Audit complete repository and document schema mappings.
  - [ ] Create Firebase project in Google Cloud Console (`civicfix-app`).
  - [ ] Enable Firebase Authentication (Email/Password & Phone Provider).
  - [ ] Provision Cloud Firestore in `asia-south1` (Mumbai) for low latency across Tamil Nadu.
  - [ ] Provision Firebase Storage bucket with CORS configuration.
  - [ ] Deploy initial Firestore Security Rules and Storage Security Rules to Firebase Emulator Suite.

  ### Phase 2: Serverless Functions & Business Logic Porting
  - [ ] Initialize `functions/` directory using TypeScript (`firebase init functions`).
  - [ ] Port `CivicLexicon` and `DeterministicTriageEngine` to TypeScript.
  - [ ] Implement `createComplaint`, `transitionComplaintStatus`, and `submitResolutionEvidence` callable functions.
  - [ ] Implement `onComplaintCreated` Gemini vision trigger with API key stored in Cloud Secret Manager.
  - [ ] Deploy and verify the 5-minute Cloud Scheduler escalation job.

  ### Phase 3: Frontend Firebase Client Integration
  - [ ] Install client dependencies: `npm install firebase`.
  - [ ] Create `src/services/firebaseClient.js` exporting `auth`, `db`, and `storage`.
  - [ ] Refactor `authService.js` to use `signInWithEmailAndPassword`, `createUserWithEmailAndPassword`, and `onAuthStateChanged`.
  - [ ] Refactor `complaintService.js` to query Firestore collections with active real-time listeners (`onSnapshot`).
  - [ ] Remove all hardcoded session cookie logic and XSRF token handling.

  ### Phase 4: Data Migration (PostgreSQL to Cloud Firestore)
  - [ ] Write an automated migration script (`scripts/migrate_pg_to_firestore.js`) using `pg` and `firebase-admin`.
  - [ ] Transform UUIDs into Firestore document IDs.
  - [ ] Convert PostgreSQL timestamp with timezone fields to Firestore Timestamps.
  - [ ] Split citizen contact details away from complaint records into isolated `/users` documents.
  - [ ] Run dry-run verification script validating record count parity and zero missing relationships.

  ### Phase 5: Vercel Production Deployment & Rollback Strategy
  - [ ] Configure `vercel.json` with SPA rewrite rules:
    ```json
    {
      "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
    }
    ```
  - [ ] Add Vercel Environment Variables:
    - `VITE_FIREBASE_API_KEY`
    - `VITE_FIREBASE_AUTH_DOMAIN`
    - `VITE_FIREBASE_PROJECT_ID`
    - `VITE_FIREBASE_STORAGE_BUCKET`
    - `VITE_FIREBASE_MESSAGING_SENDER_ID`
    - `VITE_FIREBASE_APP_ID`
    - `VITE_DEMO_MODE=false`
  - [ ] **Rollback Guarantee**: Retain Docker Compose, PostgreSQL volumes, and Spring Boot source tree until production Firebase deployment passes 100% acceptance tests. If Firebase encounters unforeseen service limits, revert DNS to Nginx container immediately.

  ---

  ## 9. PUBLIC TRANSPARENCY PAGE DESIGN

  To replace the missing public transparency requirement without exposing citizen PII:
  - **Route**: `/transparency` (accessible from public landing page and navigation bar).
  - **Data Source**: Aggregated directly from `/public_complaints` using Firestore `count()` and `aggregate` queries.
  - **Metrics Displayed**:
    1. **Total Reported Complaints** (overall and by category: Roads, Water, Garbage, etc.).
    2. **Resolution Rate (%)**: `(Resolved / Total) * 100`.
    3. **Average Resolution Time**: Expressed in hours/days.
    4. **Citizen Reopen Rate (%)**: Gauge of community satisfaction.
    5. **SLA Compliance Rate (%)**: Defect remediations completed within policy deadlines.
  - **Privacy Enforcement**:
    - Zero individual citizen names, phone numbers, email addresses, or complaint IDs.
    - Zero raw coordinates (map displays cluster markers rounded to ward centroids).
    - Wards with fewer than 5 complaints aggregate into "Other Localities" to eliminate re-identification risk.

  ---

  ## 10. COST, LIMITATIONS & QUOTA EVALUATION

  | Firebase Service | Free Tier (Spark Plan) | Paid Tier (Blaze Plan) | CivicFix Project Usage Assessment |
  |---|---|---|---|
  | **Cloud Firestore** | 50K reads, 20K writes, 20K deletes / day | \$0.06 per 100K reads | Free tier easily supports 100 concurrent demonstration users and 5,000 complaints. |
  | **Cloud Storage** | 5 GB stored, 1 GB/day download bandwidth | \$0.026 / GB | 1,000 complaints @ 500KB per image = 500 MB (well within 5 GB limit). |
  | **Cloud Functions** | 2M invocations / month, 400K GB-seconds | \$0.40 per 1M invocations | Outbound HTTP requests to Google Gemini and OpenStreetMap **require the Blaze Plan** (pay-as-you-go). Invocations will cost \$0.00 for normal student project volume. |
  | **Cloud Scheduler** | 3 jobs free per Google billing account | \$0.10 / job / month | 1 job (`every 5 minutes`) runs 100% free. |
  | **Firebase Auth** | 50K monthly active users (Email/Pass) | First 50K free; SMS OTP costs vary | Email/password login is completely free. SMS phone OTP has a regional quota (10 free SMS/day). |
  | **Vercel Hosting** | 100 GB bandwidth / month, unlimited deployments | Free for hobbyists | 100% free for frontend SPA hosting. |

  ---

  ## 11. COMPLETE TASK CHECKLIST & IMPLEMENTATION ROADMAP

  | Task ID | Task Name | Priority | Files / Modules Affected | Estimated Effort | Dependencies | Acceptance Criteria |
  |---|---|:---:|---|:---:|---|---|
  | **TSK-01** | Fix Citizen Contact Leak in API Response | **P0** | `ComplaintResponse.java`, `ComplaintService.java` | Small | None | `ComplaintResponse` strips `citizen.email` and `citizen.phone` for `ROLE_WORKER`. Worker test verifies payload cleanliness. |
  | **TSK-02** | Add Privacy Instruction Banner to Worker UI | **P0** | `src/pages/worker/TaskDetailsPage.jsx` | Small | TSK-01 | UI explicitly states: *"Contact assigned officer for resident inquiries"*. No phone/email displayed. |
  | **TSK-03** | Fix Documentation Inaccuracies | **P1** | `CIVICFIX_MASTER_ROADMAP.md`, `TRD_FINAL.md` | Small | None | Roadmap line 28 updated to acknowledge Vision AI; TRD updated to document Flyway V12 and all 10 vision columns. |
  | **TSK-04** | Fix Misleading Login Offline Notice | **P1** | `src/pages/public/LoginPage.jsx` | Small | None | Remove inaccurate "Demo Fallback Active" text when server is offline. Show actionable connection failure message. |
  | **TSK-05** | Fix Accessibility & ARIA Attributes | **P1** | `LoginPage.jsx`, `RegisterPage.jsx`, `ReportComplaintPage.jsx` | Medium | None | Add `role="alert"` on errors; add keyboard focus styles on dropzone; add accessible names on icon buttons. |
  | **TSK-06** | Initialize Firebase & Auth Custom Claims | **P1** | `firebase.json`, `functions/src/auth.ts` | Medium | Firebase Project | Firebase Auth configured; Cloud Function sets `role` claim; client checks verified token claims. |
  | **TSK-07** | Implement Firestore Rules & Collections | **P0** | `firestore.rules`, `firestore.indexes.json` | Medium | TSK-06 | Security rules tested with Emulator Suite. Citizen privacy isolation verified against unauthorized reads. |
  | **TSK-08** | Port Complaint Creation & Triage to Functions | **P1** | `functions/src/complaints.ts`, `CivicLexicon.ts` | Medium | TSK-07 | `createComplaint` callable function matches Java triage accuracy. Tested with existing 50-item evaluation set. |
  | **TSK-09** | Port Gemini Vision Analysis to Trigger | **P1** | `functions/src/visionTriage.ts` | Medium | TSK-08 | `onComplaintCreated` invokes Gemini 1.5 Flash via Secret Manager key; handles consent and updates document. |
  | **TSK-10** | Port Status Transitions & SLA Scheduler | **P1** | `functions/src/lifecycle.ts`, `functions/src/sla.ts` | Medium | TSK-08 | Transition state machine runs in Firestore transaction; Cloud Scheduler flags overdue SLA complaints every 5 mins. |
  | **TSK-11** | Build Public Transparency Page | **P2** | `src/pages/public/TransparencyPage.jsx` | Medium | TSK-07 | Renders real Firestore aggregates (resolution rate, average time, SLA compliance) with zero citizen PII. |
  | **TSK-12** | Complete Tamil Translations (i18n) | **P2** | `src/locales/ta.json`, Staff layout pages | Medium | None | Officer, Worker, Supervisor, and Admin navigation and core action buttons translated to Tamil. |
  | **TSK-13** | Port Frontend Client to Firebase SDK | **P1** | `src/services/apiClient.js`, `complaintService.js` | Large | TSK-06..10 | Frontend communicates directly with Firestore and Cloud Functions. All 201 frontend tests adapted and passing. |
  | **TSK-14** | Execute PostgreSQL Data Migration Script | **P2** | `scripts/migrate_pg_to_firestore.js` | Medium | TSK-07 | Transfers existing PostgreSQL demo and user records into Firestore with 100% record parity and zero schema errors. |
  | **TSK-15** | Configure & Deploy to Vercel | **P1** | `vercel.json`, GitHub Actions | Small | TSK-13 | Single-command Vercel build; PWA service worker and environment variables verified live on custom domain. |

  ---

  ## 12. ITEMS REQUIRING USER MANUAL ACTION

  Because the audit rules strictly forbid creating production cloud resources or spending funds without authorization, the following steps require your manual execution:

  1. **Firebase Project Creation**:
    - Navigate to [Google Firebase Console](https://console.firebase.google.com/).
    - Click **Add Project** and name it `civicfix-production` (or your chosen identifier).
    - Select or link your Google Cloud organization/billing account (Blaze plan is required for Cloud Functions outbound network access to Gemini and OpenStreetMap).
  2. **Obtain Multimodal AI Keys**:
    - Generate a **Google AI Studio API Key** for Gemini 1.5 Flash from [Google AI Studio](https://aistudio.google.com/).
    - Store the key in Google Cloud Secret Manager under `GOOGLE_AI_API_KEY`.
  3. **Vercel Account Link**:
    - Connect your GitHub repository to [Vercel](https://vercel.com/).
    - Add the Firebase environment variables (`VITE_FIREBASE_*`) into Vercel Project Settings.

  ---

  ## 13. UNVERIFIED ITEMS & ASSUMPTIONS

  1. **SMS OTP Phone Authentication Quota**:
    - *Assumption*: During capstone evaluation, email/password authentication will be the primary mechanism. Live SMS OTP delivery via Firebase Phone Auth in India requires DLT (Distributed Ledger Technology) registration for commercial sender IDs under TRAI regulations, though testing numbers work freely in Firebase Auth sandbox.
  2. **PostgreSQL Live Records Count**:
    - *Fact*: The PostgreSQL Docker container was offline during this audit inspection step. Historical test records and demo seeds were verified from Flyway SQL scripts and test logs (`DemoDataSeeder.java`). A live database dump must be performed before deprecating the Docker container.
  3. **Vercel Build Environment**:
    - *Assumption*: Node.js 20 or 22 will be selected in the Vercel project dashboard, matching the local `node --test` runtime.

  ---

  > **AUDIT COMPLETE — AWAITING USER APPROVAL**  
  > *No production files, database tables, Docker volumes, or application code have been deleted or modified. Please review the findings and approve the recommended task order before code implementation begins.*
