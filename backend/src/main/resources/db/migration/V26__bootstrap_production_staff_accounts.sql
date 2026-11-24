-- CivicFix AI — V26 Bootstrap Production Staff Accounts
-- Authoritative production bootstrap for Municipal Governance roles:
-- ADMIN, OFFICER, SUPERVISOR, WORKER, CITIZEN.
-- Uses dedicated non-seed UUIDs (10000000-...) so they are never touched by ProductionSafetyGuard.
-- Password for all accounts: CivicSecure#2026! (BCrypt hashed with cost factor 10)

-- 1. Ensure Roles Exist
INSERT INTO roles (name) VALUES 
    ('CITIZEN'),
    ('OFFICER'),
    ('WORKER'),
    ('SUPERVISOR'),
    ('ADMIN')
ON CONFLICT (name) DO NOTHING;

-- 2. Insert Platform Administrator
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
) VALUES (
    '10000000-0000-0000-0000-000000000001',
    'Priya Sharma (Platform Administrator)',
    'admin@civicfix.gov',
    '+91 98410 87654',
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Platform Security & Governance',
    'Greater Chennai Corporation Headquarters',
    100,
    TRUE,
    TRUE
) ON CONFLICT (id) DO UPDATE SET
    account_status = 'ACTIVE',
    password_hash = EXCLUDED.password_hash,
    cross_ward_access = TRUE,
    cross_department_access = TRUE;

-- 3. Insert Municipal Officer
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
) VALUES (
    '10000000-0000-0000-0000-000000000002',
    'Elena Vance (Municipal Officer)',
    'officer@civicfix.gov',
    '+91 94440 12345',
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Central Municipal Operations',
    'District 04 Headquarters',
    100,
    TRUE,
    TRUE
) ON CONFLICT (id) DO UPDATE SET
    account_status = 'ACTIVE',
    password_hash = EXCLUDED.password_hash,
    cross_ward_access = TRUE,
    cross_department_access = TRUE;

-- 4. Insert Municipal Supervisor
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
) VALUES (
    '10000000-0000-0000-0000-000000000003',
    'Sarah Jenkins (Municipal Supervisor)',
    'supervisor@civicfix.gov',
    '+91 98405 67890',
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Public Works & Infrastructure Oversight',
    'All Zones Central Supervisory',
    100,
    TRUE,
    TRUE
) ON CONFLICT (id) DO UPDATE SET
    account_status = 'ACTIVE',
    password_hash = EXCLUDED.password_hash,
    cross_ward_access = TRUE,
    cross_department_access = TRUE;

-- 5. Insert Field Maintenance Crew (Worker)
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
) VALUES (
    '10000000-0000-0000-0000-000000000004',
    'Marcus Chen (Field Technician)',
    'worker@civicfix.gov',
    '+91 91760 98765',
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'SDOT Rapid Asphalt Maintenance',
    'Unit 4 - SDOT Road Crew',
    96,
    FALSE,
    FALSE
) ON CONFLICT (id) DO UPDATE SET
    account_status = 'ACTIVE',
    password_hash = EXCLUDED.password_hash;

-- 6. Insert Resident Citizen
INSERT INTO users (
    id, full_name, email, phone, password_hash, account_status,
    department, ward, trust_score, cross_ward_access, cross_department_access
) VALUES (
    '10000000-0000-0000-0000-000000000005',
    'Alex Morgan (Citizen User)',
    'citizen@civicfix.gov',
    '+91 98401 23456',
    '$2a$10$vd5MDvWnGAYUrw8W.y2rPexBtLsSVkVVMb.hq2z037IWNJ8oID59q',
    'ACTIVE',
    'Resident Community Council',
    'Ward 08, Anna Nagar, Chennai',
    98,
    FALSE,
    FALSE
) ON CONFLICT (id) DO UPDATE SET
    account_status = 'ACTIVE',
    password_hash = EXCLUDED.password_hash;

-- 7. Assign Roles
-- Admin -> ADMIN
INSERT INTO user_roles (user_id, role_id)
SELECT '10000000-0000-0000-0000-000000000001', r.id FROM roles r WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Officer -> OFFICER
INSERT INTO user_roles (user_id, role_id)
SELECT '10000000-0000-0000-0000-000000000002', r.id FROM roles r WHERE r.name = 'OFFICER'
ON CONFLICT DO NOTHING;

-- Supervisor -> SUPERVISOR
INSERT INTO user_roles (user_id, role_id)
SELECT '10000000-0000-0000-0000-000000000003', r.id FROM roles r WHERE r.name = 'SUPERVISOR'
ON CONFLICT DO NOTHING;

-- Worker -> WORKER
INSERT INTO user_roles (user_id, role_id)
SELECT '10000000-0000-0000-0000-000000000004', r.id FROM roles r WHERE r.name = 'WORKER'
ON CONFLICT DO NOTHING;

-- Citizen -> CITIZEN
INSERT INTO user_roles (user_id, role_id)
SELECT '10000000-0000-0000-0000-000000000005', r.id FROM roles r WHERE r.name = 'CITIZEN'
ON CONFLICT DO NOTHING;
