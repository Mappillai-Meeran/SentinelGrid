-- ==============================================================================
-- SentinelGrid Flyway Migration: V3__demo_baseline_data.sql
-- Milestone: Cloud Deployment Infrastructure Readiness (Phase 5B)
-- Target: Clean PostgreSQL / Neon Free demonstration baseline data
--
-- Safety and Data Policy:
-- 1. All pharmacy identities are purely fictional demonstration hubs.
-- 2. Contact numbers are synthetic dummy values (+91-000-DEMO-xxx).
-- 3. All inventory records are clearly synthetic demonstration quantities.
-- 4. No privileged credentials, user passwords, or real patient/Rx data are seeded.
-- 5. Safe for clean databases (Neon) and idempotent if run alongside existing data.
-- ==============================================================================

-- 1. Roles Baseline
INSERT INTO roles (name) VALUES
    ('PATIENT'),
    ('PHARMACIST'),
    ('ADMIN')
ON CONFLICT (name) DO NOTHING;

-- 2. Demonstration Emergency Medicines (Standard Catalog)
INSERT INTO medicines (id, name, brand, category, description, dosage, requires_prescription, created_at, updated_at)
VALUES
    (1, 'Remdesivir 100mg Injection', 'Covifor', 'ANTIVIRAL', 'Antiviral medication for severe viral infections (Demonstration Catalog Item)', '100mg', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Tocilizumab 400mg', 'Actemra', 'IMMUNOSUPPRESSANT', 'Immunosuppressive drug for cytokine release syndrome (Demonstration Catalog Item)', '400mg', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'Enoxaparin 40mg Injection', 'Clexane', 'ANTICOAGULANT', 'Low molecular weight heparin blood thinner (Demonstration Catalog Item)', '40mg', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'Favipiravir 400mg', 'FabiFlu', 'ANTIVIRAL', 'Oral antiviral medication (Demonstration Catalog Item)', '400mg', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 'Oxygen Cylinder 10L', 'MedO2', 'RESPIRATORY_SUPPORT', 'Medical grade oxygen cylinder for emergency support (Demonstration Catalog Item)', '10L', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 'Anti-Venom Polyvalent Injection', 'VenoCure', 'EMERGENCY_ANTIVENOM', 'Polyvalent anti-snake venom serum for emergency snake bites (Demonstration Catalog Item)', '10ml', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('medicines_id_seq', (SELECT COALESCE(MAX(id), 1) FROM medicines));

-- 3. Synthetic Demonstration Pharmacy Branches (Fictional Identities Across 3 Cities)
INSERT INTO pharmacies (id, name, address, city, latitude, longitude, contact_number, opening_time, closing_time, is_24_seven, timezone, created_at, updated_at)
VALUES
    -- Puducherry (Branches 1 - 3)
    (1, 'Sentinel Demo Hub - Puducherry Heritage (Synthetic)', 'No. 94, Demo MG Road Precinct, Heritage Sector', 'Puducherry', 11.9356, 79.8337, '+91-000-DEMO-001', '09:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Sentinel Demo Care - Mission St (Synthetic)', 'Demo Mission Street, Old Town Sector', 'Puducherry', 11.9324, 79.8308, '+91-000-DEMO-002', '08:30:00', '22:30:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'Sentinel Demo 24/7 Casualty Hub - Ellaipillaichavady (Synthetic)', '100 Feet Demo Road, Ellaipillaichavady', 'Puducherry', 11.9391, 79.8056, '+91-000-DEMO-003', NULL, NULL, true, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Chennai (Branches 4 - 6)
    (4, 'Sentinel Demo Hub - Anna Nagar (Synthetic)', 'No. 140, A1 Demo Block, Anna Nagar', 'Chennai', 13.0843, 80.2106, '+91-000-DEMO-004', '09:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 'Sentinel Demo Night Dispensary - Perambur (Synthetic)', 'Old No. 36, Demo Mudali Street, Perambur', 'Chennai', 13.1182, 80.2329, '+91-000-DEMO-005', '20:00:00', '04:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 'Sentinel Demo Care - 2nd Avenue (Synthetic)', 'Shop 1, C-32, Demo 2nd Avenue, Anna Nagar', 'Chennai', 13.0850, 80.2181, '+91-000-DEMO-006', '07:00:00', '22:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Bangalore (Branches 7 - 9)
    (7, 'Sentinel Demo Hub - Indira Nagar (Synthetic)', '119, Ground Floor, Demo 10th Cross, 1st Stage, Indira Nagar', 'Bangalore', 12.9784, 77.6408, '+91-000-DEMO-007', '07:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8, 'Sentinel Demo Care - HSR Layout (Synthetic)', 'No. 1713, Demo Sector 2, HSR Layout', 'Bangalore', 12.9121, 77.6446, '+91-000-DEMO-008', '07:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, 'Sentinel Demo 24/7 Casualty Hub - Banashankari (Synthetic)', 'Plot 5, 50 Feet Demo Main Road, 1st Stage, Banashankari', 'Bangalore', 12.9335, 77.5468, '+91-000-DEMO-009', NULL, NULL, true, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('pharmacies_id_seq', (SELECT COALESCE(MAX(id), 1) FROM pharmacies));

-- 4. Synthetic Baseline Demonstration Inventory
INSERT INTO inventory (id, pharmacy_id, medicine_id, quantity, reserved_quantity, version, created_at, updated_at)
VALUES
    -- Puducherry Branch 1 (Main demo branch)
    (1, 1, 1, 10, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 1, 2, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 1, 3, 25, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 1, 6, 4, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Puducherry Branch 2
    (5, 2, 1, 5, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 2, 4, 50, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7, 2, 6, 2, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Puducherry Branch 3 (24/7)
    (8, 3, 2, 2, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, 3, 5, 8, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Chennai Branch 4
    (10, 4, 1, 7, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (11, 4, 5, 5, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Chennai Branch 5
    (12, 5, 3, 18, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (13, 5, 6, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Chennai Branch 6
    (14, 6, 2, 4, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Bangalore Branch 7
    (15, 7, 1, 8, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (16, 7, 6, 5, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Bangalore Branch 8
    (17, 8, 3, 22, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (18, 8, 5, 9, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Bangalore Branch 9 (24/7)
    (19, 9, 2, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (20, 9, 6, 4, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('inventory_id_seq', (SELECT COALESCE(MAX(id), 1) FROM inventory));

-- 5. Demonstration Manager Settings (Branch 1 Baseline Rules)
INSERT INTO manager_settings (
    id, pharmacy_name, address, city, phone, hours, coverage, verification_status,
    hold_expiry_minutes, low_stock_threshold, max_emergency_hold_quantity, created_at, updated_at
) VALUES (
    1,
    'Sentinel Demo Hub - Puducherry Heritage (Synthetic)',
    'No. 94, Demo MG Road Precinct, Heritage Sector',
    'Puducherry',
    '+91-000-DEMO-001',
    '09:00 AM - 11:00 PM',
    'Puducherry Central Demonstration Service Area',
    'Synthetic Demo Hub',
    30,
    8,
    5,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

