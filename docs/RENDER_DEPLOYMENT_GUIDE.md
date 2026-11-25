# CivicFix — Render Production Deployment Guide

This guide provides step-by-step instructions for deploying CivicFix to the Render cloud platform.

## 1. Architecture Overview

- **Frontend:** Render Static Site (`civicfix-frontend`) serving the compiled React + Vite Single-Page Application (SPA).
- **Backend:** Existing Render Web Service (`civicfix-backend`) running Java 21 + Spring Boot 3.3.4 in a Docker container at `https://civicfix-backend-wx6m.onrender.com`.
  > [!IMPORTANT]
  > **DO NOT DUPLICATE THE BACKEND.** The backend web service `civicfix-backend` already exists on Render. You only need to deploy the frontend Static Site and verify environment variables on the existing backend.
- **Database:** Neon Serverless PostgreSQL (`civicfix-production`).
- **Object Storage:** Neon Private S3 (`civicfix-complaint-images`).
- **Authentication:** Firebase Authentication (`civicfix-15ee3`).
- **Transactional Email:** Resend API.

---

## 2. Frontend Deployment Options

### Method A — Frontend-Only Blueprint via `render.yaml` (Recommended)

The repository root `render.yaml` is configured as a **frontend-only** Blueprint to prevent duplicate backend creation:

1. Log in to your [Render Dashboard](https://dashboard.render.com).
2. Click **Blueprints** $\to$ **New Blueprint Instance**.
3. Select the repository `https://github.com/harsha298250/civicfix-` (branch `main`).
4. Render will detect the `render.yaml` file:
   - It provisions strictly the Static Site `civicfix-frontend`.
   - It sets `buildCommand: npm ci && npm run build` and `staticPublishPath: dist`.
   - It adds the SPA rewrite rule (`/*` $\to$ `/index.html`) and cache-control headers.
5. In the environment variables prompt, ensure `VITE_API_BASE_URL` points to `https://civicfix-backend-wx6m.onrender.com/api`.
6. Click **Apply**.

---

### Method B — Manual Creation via Render Dashboard

If you prefer to configure the Static Site manually without Blueprints:

1. Go to **Dashboard** $\to$ **New +** $\to$ **Static Site**.
2. Connect your GitHub repository: `https://github.com/harsha298250/civicfix-`.
3. Fill in the service configuration:
   - **Name:** `civicfix-frontend`
   - **Branch:** `main`
   - **Root Directory:** *(leave blank for repository root)*
   - **Build Command:** `npm ci && npm run build`
   - **Publish Directory:** `dist`
4. Add the following **Redirect / Rewrite Rule** under the **Redirects/Rewrites** tab:
   - **Type:** `Rewrite`
   - **Source:** `/*`
   - **Destination:** `/index.html`
   > [!IMPORTANT]
   > This rewrite rule is mandatory for React Router client-side routing. Without it, direct navigation to URLs like `/login`, `/dashboard`, or `/report` will return a 404 error.
5. In the **Environment** tab, add the frontend environment variables:
   - `VITE_API_BASE_URL`: `https://civicfix-backend-wx6m.onrender.com/api`
   - `VITE_DEMO_MODE`: `false`
   - `VITE_FIREBASE_API_KEY`: *(from Firebase Console)*
   - `VITE_FIREBASE_AUTH_DOMAIN`: `civicfix-15ee3.firebaseapp.com`
   - `VITE_FIREBASE_PROJECT_ID`: `civicfix-15ee3`
   - `VITE_FIREBASE_STORAGE_BUCKET`: `civicfix-15ee3.firebasestorage.app`
   - `VITE_FIREBASE_MESSAGING_SENDER_ID`: *(from Firebase Console)*
   - `VITE_FIREBASE_APP_ID`: *(from Firebase Console)*
6. Click **Create Static Site**.

---

## 3. Existing Backend Service Configuration (`civicfix-backend`)

Verify the settings on your **existing** Render Web Service `civicfix-backend`:

1. **Service Type:** Web Service (Docker container)
2. **Health Check Path:** `/api/health`
3. **Secret Files (Render Dashboard $\to$ Environment $\to$ Secret Files):**
   - **File Name:** `civicfix-firebase-admin.json`
   - **Mount Path:** `/etc/secrets/civicfix-firebase-admin.json`
   - **Contents:** Paste the contents of your Google Cloud service account JSON key for Firebase project `civicfix-15ee3`.
4. **Environment Variables:**
   - `SPRING_PROFILES_ACTIVE`: `prod`
   - `FIREBASE_PROJECT_ID`: `civicfix-15ee3`
   - `FIREBASE_CREDENTIALS_PATH`: `/etc/secrets/civicfix-firebase-admin.json`
   - `STORAGE_PROVIDER`: `neon`
   - `NEON_STORAGE_BUCKET`: `civicfix-complaint-images`
   - `AWS_REGION`: `us-east-2`
   - `AWS_ENDPOINT_URL_S3`: *(your Neon S3 endpoint URL)*
   - `AWS_ACCESS_KEY_ID`: *(your Neon access key)*
   - `AWS_SECRET_ACCESS_KEY`: *(your Neon secret key)*
   - `RESEND_API_KEY`: *(your Resend API key)*
   - `FRONTEND_BASE_URL`: `https://civicfix-frontend.onrender.com` (or current active frontend)
   - `FRONTEND_ORIGIN`: `https://civicfix-red.vercel.app,https://civicfix-frontend.onrender.com` (exact origins; do NOT use wildcards)

---

## 4. Firebase Console Configuration

Before testing authentication on the new Render domain:

1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Select project **`civicfix-15ee3`**.
3. Navigate to **Build** $\to$ **Authentication** $\to$ **Settings** $\to$ **Authorized domains**.
4. Click **Add domain** and enter your Render frontend domain:
   - `civicfix-frontend.onrender.com`
   - Any custom domain (e.g. `civicfix.org`).
5. Ensure Google Sign-In and Email/Password sign-in providers are enabled under the **Sign-in method** tab.

---

## 5. Production Cutover & Transition Protocol

1. Deploy the `civicfix-frontend` Static Site on Render.
2. Verify that `https://civicfix-backend-wx6m.onrender.com/api/health` reports status `UP`.
3. Open the newly deployed `civicfix-frontend` URL in an incognito window.
4. Verify login, complaint reporting, and image upload.
5. If using a custom domain (e.g. `civicfix.org`), update DNS CNAME to Render.
6. Keep the Vercel deployment active as a fallback until Render production stability is fully confirmed.
