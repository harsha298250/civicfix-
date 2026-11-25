# CivicFix — Zero-Downtime Rollback Plan

This document establishes the contingency and rollback procedures for the CivicFix Render production migration.

---

## 1. Rollback Architecture Strategy

The migration strategy is designed with **dual-active compatibility**:
- The existing Vercel deployment (`https://civicfix-red.vercel.app`) remains fully functional and in sync with the backend throughout the transition.
- The backend CORS configuration permits requests from both `https://*.onrender.com` and `https://civicfix-red.vercel.app`.
- Database migrations (`V24` through `V28`) are strictly **additive and non-destructive**, ensuring complete backward compatibility with previous application versions.

---

## 2. Trigger Conditions for Rollback

A rollback should be initiated if any of the following occur during or immediately following cutover:
1. **Critical Authentication Failure:** Firebase ID tokens fail validation on Render and cannot establish sessions.
2. **CORS Blocking:** Frontend requests on Render fail due to unresolvable CORS origin rejections.
3. **Database Schema Corruption or Deadlock:** Neon PostgreSQL encounters database lock contention or migration failure.
4. **High Error Rate:** HTTP 5xx error rate exceeds 5% of all incoming requests for more than 5 consecutive minutes.
5. **Image Upload Failure:** Complaint photographs fail to persist to Neon S3 storage.

---

## 3. Step-by-Step Rollback Procedures

### Scenario A — Frontend Rollback (Reverting to Vercel)

If the Render frontend encounters rendering, routing, or client-side issues:

1. **DNS Re-pointing (if custom domain is used):**
   - In your DNS provider (e.g., Cloudflare, Namecheap, Route 53):
   - Update the `CNAME` record for `civicfix.org` or `app.civicfix.org` to point back to Vercel:
     ```
     Type: CNAME
     Name: @ or app
     Target: cname.vercel-dns.com
     TTL: 300 seconds (5 minutes)
     ```
2. **Immediate Direct Access:**
   - Inform users or test engineers to use `https://civicfix-red.vercel.app`.
   - Because the backend `civicfix-backend-wx6m.onrender.com` already permits `civicfix-red.vercel.app` in `civicfix.cors.allowed-origins`, Vercel traffic will function immediately without backend restarts.
3. **Deactivate Render Static Site:**
   - In the Render Dashboard, suspend or pause the `civicfix-frontend` service until debugging is complete.

---

### Scenario B — Backend Rollback on Render

If the backend deployment fails or exhibits fatal errors:

1. **Roll Back to Previous Deploy via Render Dashboard:**
   - Navigate to **Render Dashboard** $\to$ **civicfix-backend**.
   - Click the **Deploys** tab.
   - Locate the last known good deploy commit.
   - Click the three dots icon (`...`) next to that deploy and select **Rollback to this deploy**.
   - Render will immediately re-deploy the previous container image without rebuilding.
2. **Revert Configuration / Environment Variables:**
   - If an incorrect environment variable caused the issue, update the variable under **Environment** and click **Save Changes** to trigger an automatic redeploy.

---

### Scenario C — Database Rollback Considerations

- **Flyway Migrations V26, V27, V28:**
  - `V26`: Adds bootstrap accounts using `WHERE NOT EXISTS`. Rollback is not required as it does not modify existing data.
  - `V27`: Adds `google_id`, `email_verified`, `email_verification_tokens`, and `notification_preferences`. These are additive columns and new tables. Previous versions of the code will ignore these columns without error.
  - `V28`: Adds nullable/defaulted columns (`storage_provider`, `object_key`, `sha256_hash`) to `complaint_photos` and `resolution_evidence`. Previous code continues to read and write without issue.
- **No Destructive Drops:** No tables or columns are dropped in V26–V28, ensuring zero data loss during a code-only rollback.

---

## 4. Post-Rollback Diagnostics Checklist

Following an emergency rollback:

1. [ ] Check Render deployment logs for `civicfix-backend` to extract error stack traces:
   - Search for `[FIREBASE_FILTER]`, `[NEON_STORAGE]`, or `SQLException`.
2. [ ] Check browser console errors on the failing client.
3. [ ] Verify that Vercel frontend is communicating successfully with the backend.
4. [ ] Record incident timeline and root cause in project issue tracker before attempting redeployment.
