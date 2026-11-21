# CIVICFIX — MASTER DEFECT & ISSUE REGISTER

> **Document Status**: Complete Baseline Issue Log (Phase 0 Audit)  
> **Classification**: Defect Tracking, Threat Modeling & Remediation Specifications  
> **Total Logged Issues**: 24 Issues (4 P0 Critical, 8 P1 High, 8 P2 Medium, 4 P3 Low)

---

## 1. ISSUE SUMMARY BY SEVERITY

| Severity Level | Definition | Total Count |
|---|---|:---:|
| **P0 — Critical** | Immediate security vulnerability, PII exposure, data integrity violation, or unauthorized privilege escalation. | **4** |
| **P1 — High** | Major functional defect, broken workflow, missing authorization check, or production failure. | **8** |
| **P2 — Medium** | Incomplete module, UX inconsistency, lack of boundary validation, or resiliency gap. | **8** |
| **P3 — Low** | Documentation discrepancy, dead button stub, or minor UI styling defect. | **4** |

---

## 2. COMPREHENSIVE DEFECT & ISSUE REGISTER

### P0 — CRITICAL DEFECTS

#### `ISSUE-SEC-01`: Citizen Contact Details Leaked to Field Workers in API Payload
* **Severity**: `P0 — Critical`
* **Module**: Complaint Management / Privacy & Data Protection
* **File**: `backend/src/main/java/com/civicfix/complaint/dto/ComplaintResponse.java`
* **Class & Method**: `ComplaintResponse.fromEntity(Complaint c)` (lines 184–191) & `ComplaintResponse.fromEntity(Complaint c, UserPrincipal principal)` (lines 82–88)
* **Finding**: `fromEntity(Complaint c)` unconditionally extracts `c.getCitizen().getEmail()` and `c.getCitizen().getPhone()` and inserts them into the `citizen` map of the response DTO. The role-aware projection method `applyRoleVisionProjection()` only sanitizes vision-specific fields (`visionStatus`, `visionCategory`, etc.) and leaves `citizen.email` and `citizen.phone` completely unredacted when called by authenticated field workers (`ROLE_WORKER`).
* **Source-Code Evidence**:
  ```java
  // Lines 184-191 of ComplaintResponse.java
  if (c.getCitizen() != null) {
      Map<String, Object> cit = new HashMap<>();
      cit.put("id", c.getCitizen().getId().toString());
      cit.put("name", c.getCitizen().getFullName());
      cit.put("email", c.getCitizen().getEmail()); // UNCONDITIONAL LEAK
      cit.put("phone", c.getCitizen().getPhone()); // UNCONDITIONAL LEAK
      resp.setCitizen(cit);
  }
  ```
* **Impact**: Field technicians (and any contractor with a worker account) can view the private personal mobile phone numbers and email addresses of reporting citizens by inspecting network traffic or calling `GET /api/complaints/{id}` in browser DevTools. This directly violates the platform's stated privacy policy (`PrivacyPolicyPage.jsx`, line 214).
* **Realistic Exploitation Scenario**: An attacker logs in as a field technician (`worker@civicfix.local`), retrieves assigned work orders via `GET /api/complaints`, and scrapes all resident phone numbers and email addresses to compile an unauthorized local resident database.
* **Recommended Fix**: Update `ComplaintResponse.applyRoleVisionProjection()` (or rename to `applyRoleProjections()`) to inspect caller authorities. If the caller has `ROLE_WORKER`, remove or nullify `email` and `phone` from `resp.getCitizen()`. Only `ROLE_CITIZEN` (for their own ticket), `ROLE_OFFICER`, and `ROLE_ADMIN` should receive citizen contact details.
* **Required Regression Test**: `WorkerTaskPrivacyTest`: Authenticate as a worker, call `GET /api/complaints/{id}`, and assert that `response.citizen.email == null` and `response.citizen.phone == null`.
* **Dependencies**: None.

---

#### `ISSUE-SEC-02`: Duplicate Linking Returns Unprojected Response with Exposed Contact Details
* **Severity**: `P0 — Critical`
* **Module**: Complaint Service / Triage & Duplicates
* **File**: `backend/src/main/java/com/civicfix/complaint/ComplaintService.java`
* **Class & Method**: `ComplaintService.linkDuplicate(...)` (line 1287)
* **Finding**: In `linkDuplicate()`, the return statement invokes `ComplaintResponse.fromEntity(saved)` directly, omitting the `principal` parameter. This completely bypasses all role-based projection, returning unredacted citizen contact info and internal vision fields.
* **Source-Code Evidence**:
  ```java
  // Line 1287 of ComplaintService.java
  return ComplaintResponse.fromEntity(saved);
  ```
* **Impact**: Bypasses role-based data projection during duplicate complaint linking.
* **Recommended Fix**: Update line 1287 to return `toResponse(saved, principal)`.
* **Required Regression Test**: Unit test calling `linkDuplicate` and asserting that the returned response conforms to caller role projection rules.
* **Dependencies**: `ISSUE-SEC-01`.

---

#### `ISSUE-SEC-03`: In-Memory Rate Limiting and Login Lockout Reset on Restart
* **Severity**: `P0 — Critical`
* **Module**: Application Security / Brute-Force Defense
* **File**: `backend/src/main/java/com/civicfix/security/ratelimit/RateLimiterService.java` & `LoginLockoutService.java`
* **Class & Method**: `RateLimiterService` (line 19) & `LoginLockoutService` (line 21)
* **Finding**: Both rate limiting (Bucket4j) and per-account login lockout use in-memory `ConcurrentHashMap` stores. In a distributed, multi-instance, or container-restarted environment, all rate-limit buckets and account lockout records are cleared instantly upon server restart.
* **Source-Code Evidence**:
  ```java
  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LockoutEntry> lockoutMap = new ConcurrentHashMap<>();
  ```
* **Impact**: An attacker can bypass login lockout or rate limits by triggering application restarts or targeting multi-instance replicas where memory is not shared.
* **Recommended Fix**: For single-instance Spring Boot, document this single-node limitation explicitly. For clustered production, back Bucket4j with Redis or a persistent PostgreSQL table.
* **Required Regression Test**: Integration test verifying lockout persistence across simulated service recreation.
* **Dependencies**: None.

---

#### `ISSUE-SEC-04`: Production Safety Guard Vulnerable to Seed Account Re-Activation
* **Severity**: `P0 — Critical`
* **Module**: Security Configuration / Environment Hardening
* **File**: `backend/src/main/java/com/civicfix/security/ProductionSafetyGuard.java`
* **Class & Method**: `ProductionSafetyGuard.enforceSeedAccountPolicy()` (lines 89–109)
* **Finding**: In production (`@Profile("prod")`), default seed accounts with hardcoded credentials (`alex.m@citizen.org`, `elena.vance@metro.gov`, `marcus.chen@fieldops.gov`, `priya.sharma@admin.metro.gov`) remain fully active if an administrator sets `ALLOW_SEED_ACCOUNTS=true` in the environment.
* **Source-Code Evidence**:
  ```java
  if (allowSeedAccounts) {
      log.warn("SECURITY WARNING: ALLOW_SEED_ACCOUNTS=true in production profile. V2 seed accounts remain active.");
      return 0;
  }
  ```
* **Impact**: If `ALLOW_SEED_ACCOUNTS=true` is accidentally deployed to production, anyone with knowledge of the default dev credentials (`CivicSecure#2026!`) can take over the platform administrator account.
* **Recommended Fix**: Add a hard startup failure in `ProductionSafetyGuard.java`: If `SPRING_PROFILES_ACTIVE=prod` and `ALLOW_SEED_ACCOUNTS=true`, refuse startup unless a special override flag `I_KNOW_THIS_IS_DANGEROUS=true` is set, or permanently delete seed account creation from production Flyway migrations.
* **Required Regression Test**: `ProductionSafetyGuardTest`: Assert startup exception when `prod` profile has `ALLOW_SEED_ACCOUNTS=true`.
* **Dependencies**: None.

---

### P1 — HIGH SEVERITY DEFECTS

#### `ISSUE-AUTH-01`: Officer Access Lacks Ward and Departmental Boundaries
* **Severity**: `P1 — High`
* **Module**: Municipal Officer Workflow / Jurisdiction
* **File**: `backend/src/main/java/com/civicfix/complaint/ComplaintService.java`
* **Class & Method**: `ComplaintService.getComplaints(...)` (lines 403–418)
* **Finding**: When an authenticated officer queries complaints, if no specific `ward` query parameter is supplied, `ComplaintService` queries the entire database across all municipal wards: `complaintRepository.findAllByOrderByCreatedAtDesc(pageable)`.
* **Impact**: A municipal officer assigned to Anna Nagar (Ward 08) can browse, inspect, and modify complaints in Adyar, T. Nagar, or Royapuram without administrative authorization.
* **Recommended Fix**: Extract the authenticated officer's assigned `ward` and `department` from their `User` profile. Automatically filter queries by `complaint.ward == officer.ward` unless the officer possesses an `ADMIN` role or cross-jurisdiction elevation.
* **Required Regression Test**: Test officer querying complaints without ward filter and assert that returned records match only the officer's ward.
* **Dependencies**: None.

---

#### `ISSUE-AUTH-02`: Supervisor Authorization Missing Departmental Jurisdiction Checks
* **Severity**: `P1 — High`
* **Module**: Supervisor Operations / Escalation Governance
* **File**: `backend/src/main/java/com/civicfix/supervisor/SupervisorService.java`
* **Class & Method**: `SupervisorService.reassignWorker(...)` (lines 78–84) & `getEscalatedComplaints(...)` (lines 57–64)
* **Finding**: A supervisor can query escalated complaints across any ward or category by passing request parameters, and can reassign field workers to complaints outside their department.
* **Impact**: A Roads Department supervisor could reassign workers on Water & Sewerage or Public Health complaints without department approval.
* **Recommended Fix**: Enforce that `complaint.category` matches the supervisor's assigned `department` before permitting reassignment.
* **Required Regression Test**: Test supervisor attempting to reassign a complaint outside their department; assert `HTTP 403 Forbidden`.
* **Dependencies**: None.

---

#### `ISSUE-UI-01`: Misleading Login Offline Notice Causes UI Deadlock
* **Severity**: `P1 — High`
* **Module**: Frontend Authentication / User Experience
* **File**: `src/pages/public/LoginPage.jsx`
* **Class & Method**: `LoginPage` (lines 122–126)
* **Finding**: When the backend server is unreachable, `LoginPage.jsx` renders: `"Backend Offline (Demo Fallback Active)"`. However, `authService.login()` does NOT fall back to demo mode; it throws an error: `"Cannot reach the server. Nothing was saved."`.
* **Impact**: Users assume demo mode is active and repeatedly attempt to log in, only to receive connection error toasts.
* **Recommended Fix**: Update the pill text when offline to `"Backend Offline — Check Server Connection"`. Do not advertise demo fallback unless `VITE_DEMO_MODE=true` is active.
* **Required Regression Test**: Frontend test verifying offline message states in `LoginPage`.
* **Dependencies**: None.

---

#### `ISSUE-UI-02`: Worker Task Details Missing Privacy Guidance Banner
* **Severity**: `P1 — High`
* **Module**: Field Operations / Worker Interface
* **File**: `src/pages/worker/TaskDetailsPage.jsx`
* **Class & Method**: `TaskDetailsPage`
* **Finding**: `TaskDetailsPage.jsx` omits citizen phone and email from the UI, but fails to provide instructions on what the worker should do when resident contact is necessary (e.g. locating a locked gate or hidden sewer line).
* **Impact**: Field technicians are left without a clear protocol when site access requires citizen coordination.
* **Recommended Fix**: Add a prominent operational banner: *"Resident Contact Policy: To protect citizen privacy, direct contact details are restricted. If access or clarification is needed, contact your assigned municipal officer at [Officer Email / Radio]."*
* **Required Regression Test**: Component test verifying banner visibility in `TaskDetailsPage`.
* **Dependencies**: `ISSUE-SEC-01`.

---

#### `ISSUE-GIS-01`: Nominatim Reverse Geocoding Lacks Application-Wide Rate Limiting
* **Severity**: `P1 — High`
* **Module**: GIS / Geolocation Services
* **File**: `backend/src/main/java/com/civicfix/gis/GisService.java`
* **Class & Method**: `GisService.reverseGeocode(...)`
* **Finding**: `GisService` forwards reverse-geocoding requests directly to OpenStreetMap Nominatim. While an in-memory cache exists, rapid coordinate changes across concurrent users can exceed Nominatim's strict policy limit of 1 request per second.
* **Impact**: Risk of the municipal server's IP address being permanently blocked by the OpenStreetMap Foundation.
* **Recommended Fix**: Add a token-bucket rate limiter (1 req/sec) to `GisService` and attach a unique contact `User-Agent` header as mandated by the OSM Nominatim Usage Policy.
* **Required Regression Test**: `GisRateLimiterTest`: Verify requests are throttled to 1/sec.
* **Dependencies**: None.

---

#### `ISSUE-DOC-01`: Master Roadmap Erroneously Marks Computer Vision as "Not Done"
* **Severity**: `P1 — High`
* **Module**: Documentation / Project Tracking
* **File**: `docs/CIVICFIX_MASTER_ROADMAP.md`
* **Class & Method**: Table row 28
* **Finding**: Line 28 states: `| **Image-Based AI / Computer Vision** | Triage | **Not done** | Text-only triage; photo analysis and damage assessment from images do not exist |`. However, the multimodal vision triage layer is fully implemented in Java (`GeminiVisionTriageProvider.java`, `XaiVisionTriageProvider.java`), supported by Flyway migration `V12`, and tested.
* **Impact**: Evaluators or developers reviewing documentation conclude that vision AI is missing, misrepresenting system capabilities.
* **Recommended Fix**: Update roadmap row 28 to `Done` with references to `GeminiVisionTriageProvider.java` and migration `V12`.
* **Required Regression Test**: Documentation link check.
* **Dependencies**: None.

---

#### `ISSUE-DOC-02`: Technical Reference Document (TRD) Omits Migration V12
* **Severity**: `P1 — High`
* **Module**: Documentation / Technical Specification
* **File**: `docs/TRD_FINAL.md`
* **Class & Method**: Section 3 ("Database Schema & Migration Catalog")
* **Finding**: Documents migrations up to `V11` only, completely omitting `V12__add_vision_triage_and_consent.sql` and its 10 database columns.
* **Impact**: Disconnect between documented schema and actual PostgreSQL database state.
* **Recommended Fix**: Append migration `V12` documentation with column definitions and index descriptions in `TRD_FINAL.md`.
* **Required Regression Test**: Documentation validation.
* **Dependencies**: None.

---

#### `ISSUE-DATA-01`: Missing Audited Deletion Pipeline for Complaint Data
* **Severity**: `P1 — High`
* **Module**: Database & Audit / Data Governance
* **File**: `backend/src/main/resources/db/migration/V1__initial_schema.sql`
* **Class & Method**: Foreign key constraints on `complaint_status_history` and `resolution_evidence`
* **Finding**: Tables use `ON DELETE CASCADE` linked to `complaints`. If an administrator deletes a complaint row, all status history and field evidence records are hard-deleted immediately with no archive.
* **Impact**: Total loss of historical audit trails and evidence records upon ticket deletion.
* **Recommended Fix**: Implement soft-deletion (`deleted_at TIMESTAMP WITH TIME ZONE`) on `complaints` or move deleted complaints to a dedicated `complaints_archive` table.
* **Required Regression Test**: Soft-deletion test verifying records are marked deleted rather than dropped.
* **Dependencies**: None.

---

### P2 — MEDIUM SEVERITY DEFECTS

#### `ISSUE-FEAT-01`: Self-Service Password Reset Missing Email Pipeline
* **Severity**: `P2 — Medium`
* **Module**: Authentication / User Recovery
* **File**: `src/pages/public/ForgotPasswordPage.jsx` & `ResetPasswordPage.jsx`
* **Finding**: The frontend contains password reset views, but the backend lacks an automated password reset token generation and transactional email dispatch service.
* **Impact**: Users who forget passwords must contact municipal administrators manually.
* **Recommended Fix**: Implement `POST /api/auth/forgot-password` and `POST /api/auth/reset-password` using secure random tokens with 15-minute expiration.
* **Dependencies**: None.

---

#### `ISSUE-FEAT-02`: PDF Export Button Unimplemented in Citizen Dashboard
* **Severity**: `P2 — Medium`
* **Module**: Citizen Portal / Export Functionality
* **File**: `src/pages/citizen/CitizenDashboard.jsx`
* **Finding**: "Export PDF" button displays a toast message: `"PDF export feature coming soon"`.
* **Impact**: Citizen cannot download an official receipt or proof of complaint submission.
* **Recommended Fix**: Implement printable HTML view (`window.print()`) or generate PDF via `@react-pdf/renderer` or `jspdf`.
* **Dependencies**: None.

---

#### `ISSUE-FEAT-03`: Monolithic Frontend Component Sizes
* **Severity**: `P2 — Medium`
* **Module**: Frontend Architecture / Maintainability
* **File**: `src/pages/citizen/ReportComplaintPage.jsx` (742 lines) & `ComplaintDetailsPage.jsx` (624 lines)
* **Finding**: Excessively large single-file components mixing form validation, map rendering, image uploading, and modal lifecycle logic.
* **Impact**: Difficult to test, maintain, or optimize render performance.
* **Recommended Fix**: Split `ReportComplaintPage.jsx` into `<ComplaintFormFields>`, `<ComplaintLocationPicker>`, and `<ComplaintMediaUploader>`.
* **Dependencies**: None.

---

#### `ISSUE-A11Y-01`: Missing `role="alert"` on Form Error Containers
* **Severity**: `P2 — Medium`
* **Module**: Accessibility (a11y)
* **File**: `src/pages/public/LoginPage.jsx` (line 133) & `RegisterPage.jsx` (line 209)
* **Finding**: Authentication error banners do not specify `role="alert"` or `aria-live="polite"`.
* **Impact**: Screen readers fail to announce error messages when login or registration fails.
* **Recommended Fix**: Add `role="alert"` and `aria-live="assertive"` to all dynamic error containers.
* **Dependencies**: None.

---

#### `ISSUE-A11Y-02`: Password Strength Indicator Relies Purely on Color
* **Severity**: `P2 — Medium`
* **Module**: Accessibility (a11y)
* **File**: `src/pages/public/RegisterPage.jsx`
* **Finding**: Password strength meter uses colored bars without accessible text equivalents or ARIA value indicators.
* **Impact**: Fails WCAG 1.4.1 (Use of Color). Color-blind or visually impaired users cannot gauge password strength.
* **Recommended Fix**: Add `aria-valuenow`, `aria-valuemin="0"`, `aria-valuemax="3"`, and visible descriptive text (`Weak`, `Moderate`, `Strong`).
* **Dependencies**: None.

---

#### `ISSUE-I18N-01`: Incomplete Tamil Translations for Staff Portals
* **Severity**: `P2 — Medium`
* **Module**: Internationalization (i18n)
* **File**: `src/locales/ta.json`
* **Finding**: Citizen portal is translated, but Officer, Worker, Supervisor, and Admin management interfaces are hardcoded English.
* **Impact**: Non-English-speaking municipal field staff cannot use the platform in Tamil.
* **Recommended Fix**: Expand `ta.json` to cover officer queue management, worker work orders, and supervisor escalations.
* **Dependencies**: None.

---

#### `ISSUE-GIS-02`: Public Map Does Not Group Low-Density Wards
* **Severity**: `P2 — Medium`
* **Module**: GIS / Resident Privacy
* **File**: `src/pages/public/PublicMapPage.jsx`
* **Finding**: All public complaints are displayed on the public map feed. In sparse wards with only 1 or 2 defects, a 110m rounded marker combined with defect category can re-identify the complainant.
* **Impact**: Potential re-identification of rural or low-density complainants.
* **Recommended Fix**: Enforce small-group k-anonymity: do not show point markers in wards with $< 5$ total tickets; aggregate them into ward-level centroid statistics.
* **Dependencies**: None.

---

#### `ISSUE-CODE-01`: Oversized `ComplaintService.java` Monolith
* **Severity**: `P2 — Medium`
* **Module**: Backend Architecture / Clean Code
* **File**: `backend/src/main/java/com/civicfix/complaint/ComplaintService.java`
* **Finding**: `ComplaintService.java` spans 1,505 lines and contains over 20 distinct responsibilities.
* **Impact**: High coupling, complex unit testing, and risk of merge regressions.
* **Recommended Fix**: Decompose into `ComplaintIntakeService`, `ComplaintAssignmentService`, `ComplaintTransitionService`, and `ComplaintEvidenceService`.
* **Dependencies**: None.

---

### P3 — LOW SEVERITY DEFECTS

#### `ISSUE-DOC-03`: Broken Relative Links in Historical Archive README
* **Severity**: `P3 — Low`
* **Module**: Documentation
* **File**: `docs/archive/README.md`
* **Finding**: Two relative links in historical report indexes point to legacy file paths from Sprint 1.
* **Impact**: 404 file not found when clicking historical archive references.
* **Recommended Fix**: Correct relative file paths in `docs/archive/README.md`.
* **Dependencies**: None.

---

#### `ISSUE-UI-03`: Missing Keyboard Focus Rings on File Dropzone
* **Severity**: `P3 — Low`
* **Module**: UI / Accessibility
* **File**: `src/components/common/ImageUploadManager.jsx`
* **Finding**: Drag-and-drop zone lacks visible `focus-visible:ring-2` focus styling on keyboard tab navigation.
* **Impact**: Keyboard-only users cannot visually verify focus on the file upload target.
* **Recommended Fix**: Add `focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary` to the dropzone container.
* **Dependencies**: None.

---

#### `ISSUE-CODE-02`: Unused Import Cleanliness in Backend DTOs
* **Severity**: `P3 — Low`
* **Module**: Code Cleanliness
* **File**: `backend/src/main/java/com/civicfix/complaint/dto/CreateComplaintRequest.java`
* **Finding**: Unused legacy import statements present in DTO classes.
* **Impact**: Minor code hygiene issue.
* **Recommended Fix**: Run IDE code cleanup or spotless plugin to remove unused imports.
* **Dependencies**: None.

---

#### `ISSUE-CONF-01`: Docker Compose Exposes PostgreSQL Port 5433 to Host Loopback
* **Severity**: `P3 — Low`
* **Module**: DevOps / Container Security
* **File**: `docker-compose.yml` (line 37)
* **Finding**: PostgreSQL port `5433` is mapped to host `127.0.0.1:5433`. In strict isolated production deployments, only the backend container should communicate with the database via the internal Docker bridge network.
* **Impact**: Database accessible to any local process running on the host machine.
* **Recommended Fix**: In production compose overrides, omit host port exposure and connect backend to `postgres:5432` internally.
* **Dependencies**: None.
