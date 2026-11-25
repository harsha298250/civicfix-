-- CivicFix AI — V26 Bootstrap Production Staff Accounts
-- Authoritative production bootstrap for Municipal Governance roles:
-- ADMIN, OFFICER, SUPERVISOR, WORKER, CITIZEN.
-- Uses dedicated non-seed UUIDs (10000000-...) so they are never touched by ProductionSafetyGuard.
-- Phone is explicitly set to NULL to prevent collision with existing unique phone constraints (users_phone_key).
-- Protected by conditional WHERE NOT EXISTS on both (id OR email) to guarantee zero unique-constraint conflicts,
-- zero overwrites of existing user records, and zero unauthorized password resets.
-- SECURITY WARNING: The initial bootstrap password hash ($2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q)
-- corresponds to a known string committed in Git history and is NOT production-secure.
-- All accounts must be rotated immediately upon first login via password reset or admin provisioning.

-- 1. Ensure System Roles Exist
INSERT INTO roles (name) VALUES 
    ('CITIZEN'),
    ('OFFICER'),
    ('WORKER'),
    ('SUPERVISOR'),
    ('ADMIN')
ON CONFLICT (name) DO NOTHING;

-- 2. Insert Platform Administrator (idempotent; no-op if id or email exists)
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
)
SELECT
    '10000000-0000-0000-0000-000000000001',
    'Priya Sharma (Platform Administrator)',
    'admin@civicfix.gov',
    NULL,
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Platform Security & Governance',
    'Greater Chennai Corporation Headquarters',
    100,
    TRUE,
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '10000000-0000-0000-0000-000000000001' OR email = 'admin@civicfix.gov'
);

-- 3. Insert Municipal Officer (idempotent; no-op if id or email exists)
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
)
SELECT
    '10000000-0000-0000-0000-000000000002',
    'Elena Vance (Municipal Officer)',
    'officer@civicfix.gov',
    NULL,
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Central Municipal Operations',
    'District 04 Headquarters',
    100,
    TRUE,
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '10000000-0000-0000-0000-000000000002' OR email = 'officer@civicfix.gov'
);

-- 4. Insert Municipal Supervisor (idempotent; no-op if id or email exists)
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
)
SELECT
    '10000000-0000-0000-0000-000000000003',
    'Sarah Jenkins (Municipal Supervisor)',
    'supervisor@civicfix.gov',
    NULL,
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Public Works & Infrastructure Oversight',
    'All Zones Central Supervisory',
    100,
    TRUE,
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '10000000-0000-0000-0000-000000000003' OR email = 'supervisor@civicfix.gov'
);

-- 5. Insert Field Maintenance Crew / Worker (idempotent; no-op if id or email exists)
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
)
SELECT
    '10000000-0000-0000-0000-000000000004',
    'Marcus Chen (Field Technician)',
    'worker@civicfix.gov',
    NULL,
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'SDOT Rapid Asphalt Maintenance',
    'Unit 4 - SDOT Road Crew',
    96,
    FALSE,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '10000000-0000-0000-0000-000000000004' OR email = 'worker@civicfix.gov'
);

-- 6. Insert Resident Citizen (idempotent; no-op if id or email exists)
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
)
SELECT
    '10000000-0000-0000-0000-000000000005',
    'Alex Morgan (Citizen User)',
    'citizen@civicfix.gov',
    NULL,
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Resident Community Council',
    'Ward 08, Anna Nagar, Chennai',
    98,
    FALSE,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '10000000-0000-0000-0000-000000000005' OR email = 'citizen@civicfix.gov'
);

-- 7. Assign Roles Safely with Strict Dual (UUID + Email) Matching
-- Enforces that elevated roles are NEVER granted to another account that happens to share an email or UUID.
-- Admin -> ADMIN
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.id = '10000000-0000-0000-0000-000000000001'
  AND u.email = 'admin@civicfix.gov'
  AND r.name = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Officer -> OFFICER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.id = '10000000-0000-0000-0000-000000000002'
  AND u.email = 'officer@civicfix.gov'
  AND r.name = 'OFFICER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Supervisor -> SUPERVISOR
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.id = '10000000-0000-0000-0000-000000000003'
  AND u.email = 'supervisor@civicfix.gov'
  AND r.name = 'SUPERVISOR'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Worker -> WORKER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.id = '10000000-0000-0000-0000-000000000004'
  AND u.email = 'worker@civicfix.gov'
  AND r.name = 'WORKER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Citizen -> CITIZEN
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.id = '10000000-0000-0000-0000-000000000005'
  AND u.email = 'citizen@civicfix.gov'
  AND r.name = 'CITIZEN'
ON CONFLICT (user_id, role_id) DO NOTHING;
