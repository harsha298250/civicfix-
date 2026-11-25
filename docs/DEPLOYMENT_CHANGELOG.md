# CivicFix — Deployment Changelog

**Release Target:** Render Full-Stack Production Cutover
**Repository:** `https://github.com/harsha298250/civicfix-`
**Branch:** `main`
**Date:** November 2026

This changelog records all architectural and code adjustments implemented to transition CivicFix to Render while aligning Firebase Authentication, Neon Object Storage, and Neon PostgreSQL.

---

## 1. Summary of Changes by Domain

### 1.1 Architecture & Hosting Configuration
- **Added `render.yaml`:**
  - Defined Render Infrastructure-as-Code blueprint containing:
    - `civicfix-frontend`: Static Site (`npm ci && npm run build`, publish directory `dist`, SPA rewrite rule `/*` $\to$ `/index.html`).
    - `civicfix-backend`: Referenced existing Web Service running Java 21 Docker image.
- **Removed `vercel.json`:**
  - Decommissioned Vercel-specific routing and build configuration.
- **Updated `.env.example`:**
  - Added complete, clean reference for all required frontend environment variables (`VITE_API_BASE_URL`, `VITE_FIREBASE_*`, `VITE_DEMO_MODE=false`).

### 1.2 Authentication & User Onboarding
- **`src/pages/public/LoginPage.jsx`:**
  - Aligned primary login form to authenticate through `authService.login()`, calling `firebaseAuthService.signInWithEmail()`.
  - Removed redundant secondary "Sign in with Firebase" button.
  - Decoupled citizen vs staff tabs from role assignment; authentication is pure identity verification, with role determined by backend Neon database.
- **`src/services/firebaseAuth.js`:**
  - Added user-friendly error mapping for Firebase error codes (`auth/invalid-credential`, `auth/wrong-password`, `auth/user-not-found`, `auth/user-disabled`, `auth/too-many-requests`).
  - Added `registerOrLinkStaff(email, password)` helper to seamlessly create or link Firebase credentials during municipal staff onboarding.
  - Maintained `browserLocalPersistence` for Remember Me and `browserSessionPersistence` for session-only logins.
- **`src/services/authService.js`:**
  - Delegated `login()` to `firebaseAuthService.signInWithEmail()` when Firebase is configured, while retaining mock credentials fallback for isolated testing.
- **`src/services/apiClient.js`:**
  - Added automatic injection of `Authorization: Bearer <idToken>` on all outgoing HTTP requests when a user is signed into Firebase.
  - Removed legacy Vercel hostname checks.
  - Implemented URL normalization to ensure `https://civicfix-backend-wx6m.onrender.com/api` and `https://civicfix-backend-wx6m.onrender.com` both resolve cleanly.
- **`src/pages/public/StaffActivationPage.jsx`:**
  - Integrated Firebase credential registration on staff activation: staff entering a password now registers/links with Firebase, obtains an ID token, and dispatches `{ token, newPassword, idToken }` to the backend.
- **`backend/src/main/java/com/civicfix/auth/StaffActivationService.java` & `ActivateStaffRequest.java`:**
  - Added `idToken` support to `ActivateStaffRequest`.
  - Updated `activateStaff` to verify `idToken` via `FirebaseTokenVerifier`, asserting the Firebase email strictly matches the invited staff email in Neon.
  - Binds `firebaseUid`, sets `emailVerified = true`, marks `accountStatus = ACTIVE`, atomically consumes the activation token, and preserves existing staff roles.
  - Added dual getter/setter for `password` and `newPassword` to ensure backward compatibility.

### 1.3 Spring Boot Backend Configuration & Security
- **`backend/src/main/java/com/civicfix/config/FirebaseProperties.java`:**
  - Updated default `projectId` to `civicfix-15ee3`.
- **`backend/src/main/resources/application.yml` & `application-prod.yml`:**
  - Configured `firebase.project-id: ${FIREBASE_PROJECT_ID:civicfix-15ee3}`.
  - In `application-prod.yml`, configured secret file path `/etc/secrets/civicfix-firebase-admin.json`.
  - Updated CORS configuration: `civicfix.cors.allowed-origins` allows `https://*.onrender.com` and retains `https://civicfix-red.vercel.app` for seamless DNS cutover.
- **`backend/src/main/java/com/civicfix/config/SecurityConfig.java`:**
  - Updated CORS configuration to use `setAllowedOriginPatterns` for wildcard pattern matching (`https://*.onrender.com`) with credentials support.
- **`backend/src/main/java/com/civicfix/mail/ResendEmailService.java`:**
  - Replaced hardcoded Vercel URL with configurable `${civicfix.frontend.base-url:${FRONTEND_BASE_URL}}`.

### 1.4 Object Storage Hardening
- **`backend/src/main/java/com/civicfix/storage/NeonS3StorageService.java`:**
  - Implemented fail-fast validation in `initS3Client()`: when `STORAGE_PROVIDER=neon` or `s3`, missing `AWS_ACCESS_KEY_ID` or `AWS_SECRET_ACCESS_KEY` immediately throws an `IllegalStateException` rather than silently degrading to local ephemeral storage in production.

### 1.5 Database Migrations
- **`backend/src/main/resources/db/migration/V26__bootstrap_production_staff_accounts.sql`:**
  - Verified non-destructive insertion using dedicated UUIDs (`10000000-...`), `phone = NULL`, and `WHERE NOT EXISTS` condition checking both ID and email.
- **`backend/src/main/resources/db/migration/V27__create_email_verification_tokens_and_auth_enhancements.sql`:**
  - Created `email_verification_tokens` table, added `google_id` and `email_verified` to `users`, and created `notification_preferences`.
- **`backend/src/main/resources/db/migration/V28__add_neon_object_storage_metadata.sql`:**
  - Added `storage_provider`, `object_key`, `sha256_hash`, and `uploader_id` metadata tracking to `complaint_photos` and `resolution_evidence`.

---

## 2. File Modification Audit Table

| File Path | Type | Action | Purpose |
| :--- | :---: | :---: | :--- |
| `render.yaml` | Root | Created | Render Infrastructure-as-Code service blueprint |
| `vercel.json` | Root | Deleted | Decommissioned Vercel deployment configuration |
| `.env.example` | Root | Updated | Complete production frontend environment variables |
| `src/pages/public/LoginPage.jsx` | Frontend | Modified | Primary login aligned to Firebase email/password |
| `src/pages/public/StaffActivationPage.jsx` | Frontend | Modified | Connects staff activation to Firebase identity creation |
| `src/services/firebaseAuth.js` | Frontend | Modified | Error mapping, staff linking, token persistence |
| `src/services/authService.js` | Frontend | Modified | Firebase delegation for login and current user |
| `src/services/apiClient.js` | Frontend | Modified | Dynamic Bearer token injection, URL normalization |
| `backend/src/main/resources/application.yml` | Backend | Modified | Firebase project ID `civicfix-15ee3` default |
| `backend/src/main/resources/application-prod.yml` | Backend | Modified | Firebase secret file, Render CORS patterns |
| `backend/src/main/java/com/civicfix/config/FirebaseProperties.java` | Backend | Modified | Project ID default updated to `civicfix-15ee3` |
| `backend/src/main/java/com/civicfix/config/SecurityConfig.java` | Backend | Modified | Origin patterns for `*.onrender.com` |
| `backend/src/main/java/com/civicfix/mail/ResendEmailService.java` | Backend | Modified | Dynamic `FRONTEND_BASE_URL` injection |
| `backend/src/main/java/com/civicfix/storage/NeonS3StorageService.java` | Backend | Modified | Fail-fast credential validation in production |
| `backend/src/main/java/com/civicfix/auth/dto/ActivateStaffRequest.java` | Backend | Modified | Added `idToken` support, relaxed password constraint |
| `backend/src/main/java/com/civicfix/auth/StaffActivationService.java` | Backend | Modified | Firebase token verification and UID mapping |
