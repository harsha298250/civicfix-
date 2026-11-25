# CivicFix — Environment Variables Checklist

This document details all required and optional environment variables across the Frontend and Backend for the Render production deployment.

> [!CAUTION]
> Never commit actual secret keys, database credentials, or service account JSON files to the Git repository. Set these strictly in the Render dashboard or secret files.

---

## 1. Frontend Static Site (`civicfix-frontend`)

Configure these variables under **Render Dashboard** $\to$ **civicfix-frontend** $\to$ **Environment**:

| Variable Name | Required | Production Value / Description | Sensitive |
| :--- | :---: | :--- | :---: |
| `VITE_API_BASE_URL` | **YES** | `https://civicfix-backend-wx6m.onrender.com/api` | No |
| `VITE_DEMO_MODE` | **YES** | `false` | No |
| `VITE_FIREBASE_API_KEY` | **YES** | Web API Key from Firebase Console (`civicfix-15ee3`) | No |
| `VITE_FIREBASE_AUTH_DOMAIN` | **YES** | `civicfix-15ee3.firebaseapp.com` | No |
| `VITE_FIREBASE_PROJECT_ID` | **YES** | `civicfix-15ee3` | No |
| `VITE_FIREBASE_STORAGE_BUCKET` | **YES** | `civicfix-15ee3.firebasestorage.app` | No |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | **YES** | Messaging Sender ID from Firebase project settings | No |
| `VITE_FIREBASE_APP_ID` | **YES** | Web App ID from Firebase Console (`1:...:web:...`) | No |

---

## 2. Backend Web Service (`civicfix-backend`)

Configure these variables under **Render Dashboard** $\to$ **civicfix-backend** $\to$ **Environment**:

### 2.1 Core Runtime & Profiles
| Variable Name | Required | Production Value | Sensitive |
| :--- | :---: | :--- | :---: |
| `SPRING_PROFILES_ACTIVE` | **YES** | `prod` | No |
| `SERVER_PORT` | **YES** | `8080` (or leave empty for Render `$PORT`) | No |
| `FORWARD_HEADERS_STRATEGY` | **YES** | `framework` | No |

### 2.2 Relational Database (Neon PostgreSQL)
| Variable Name | Required | Production Value / Description | Sensitive |
| :--- | :---: | :--- | :---: |
| `DATABASE_URL` | **YES** | Full Neon PostgreSQL JDBC connection URL (`sslmode=require`) | **YES** |
| `DB_USER` | **YES** | Neon PostgreSQL database username | **YES** |
| `DB_PASSWORD` | **YES** | Neon PostgreSQL database user password | **YES** |
| `HIKARI_MAX_POOL_SIZE` | No | `10` | No |
| `HIKARI_MIN_IDLE` | No | `2` | No |

### 2.3 Firebase Authentication (Admin SDK)
| Variable Name | Required | Production Value / Description | Sensitive |
| :--- | :---: | :--- | :---: |
| `FIREBASE_ENABLED` | **YES** | `true` | No |
| `FIREBASE_PROJECT_ID` | **YES** | `civicfix-15ee3` | No |
| `FIREBASE_CREDENTIALS_PATH` | **YES** | `/etc/secrets/civicfix-firebase-admin.json` | No |

> [!NOTE]
> The service account JSON file should be uploaded as a **Render Secret File** named `civicfix-firebase-admin.json` mounted at `/etc/secrets/civicfix-firebase-admin.json`.

### 2.4 Binary Object Storage (Neon Private S3)
| Variable Name | Required | Production Value / Description | Sensitive |
| :--- | :---: | :--- | :---: |
| `STORAGE_PROVIDER` | **YES** | `neon` | No |
| `NEON_STORAGE_BUCKET` | **YES** | `civicfix-complaint-images` | No |
| `AWS_REGION` | **YES** | `us-east-2` | No |
| `AWS_ENDPOINT_URL_S3` | **YES** | Neon S3 endpoint URL | No |
| `AWS_ACCESS_KEY_ID` | **YES** | Neon Object Storage Access Key ID | **YES** |
| `AWS_SECRET_ACCESS_KEY` | **YES** | Neon Object Storage Secret Access Key | **YES** |

### 2.5 Transactional Notifications (Resend)
| Variable Name | Required | Production Value / Description | Sensitive |
| :--- | :---: | :--- | :---: |
| `RESEND_API_KEY` | **YES** | Resend API Key (`re_...`) | **YES** |
| `RESEND_FROM_EMAIL` | **YES** | Verified sender email (e.g., `notifications@civicfix.org`) | No |

### 2.6 Frontend Alignment & CORS
| Variable Name | Required | Production Value / Description | Sensitive |
| :--- | :---: | :--- | :---: |
| `FRONTEND_BASE_URL` | **YES** | `https://civicfix-frontend.onrender.com` | No |
| `FRONTEND_ORIGIN` | **YES** | `https://*.onrender.com,https://civicfix-red.vercel.app` | No |
| `ALLOW_SEED_ACCOUNTS` | **YES** | `false` | No |
| `TRUSTED_PROXY_ENABLED` | **YES** | `true` | No |
