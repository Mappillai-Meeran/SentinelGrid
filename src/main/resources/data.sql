-- Seed data for SentinelGrid Platform

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
CHECK (role IN ('PATIENT', 'PHARMACIST', 'PHARMACY_MANAGER', 'ADMIN'));

ALTER TABLE users ADD COLUMN IF NOT EXISTS pharmacy_id BIGINT;

-- Seed Users (Password is 'password123' encoded with BCrypt)
-- Hash: $2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW
INSERT INTO users (id, username, email, password, role, pharmacy_id, created_at, updated_at)
VALUES 
(1, 'patient1', 'patient1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'PATIENT', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'pharmacist1', 'pharmacist1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'PHARMACIST', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'admin1', 'admin1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'ADMIN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'manager1', 'manager1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'PHARMACY_MANAGER', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Ensure existing demo seed users have the correct BCrypt hash & pharmacy assignment
UPDATE users SET password = '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW'
WHERE username IN ('patient1', 'pharmacist1', 'admin1', 'manager1');
UPDATE users SET pharmacy_id = 1 WHERE username IN ('pharmacist1', 'manager1');

-- Keep demo manager login stable even when an existing local database already has a row with id = 4.
INSERT INTO users (username, email, password, role, pharmacy_id, created_at, updated_at)
VALUES ('manager1', 'manager1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'PHARMACY_MANAGER', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO UPDATE
SET email = EXCLUDED.email,
    password = EXCLUDED.password,
    role = EXCLUDED.role,
    pharmacy_id = EXCLUDED.pharmacy_id,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE pharmacies ADD COLUMN IF NOT EXISTS opening_time TIME;
ALTER TABLE pharmacies ADD COLUMN IF NOT EXISTS closing_time TIME;
ALTER TABLE pharmacies ADD COLUMN IF NOT EXISTS is_24_seven BOOLEAN DEFAULT false;
ALTER TABLE pharmacies ADD COLUMN IF NOT EXISTS timezone VARCHAR(255) DEFAULT 'Asia/Kolkata';

-- Seed Pharmacies
INSERT INTO pharmacies (id, name, address, city, latitude, longitude, contact_number, opening_time, closing_time, is_24_seven, timezone, created_at, updated_at)
VALUES
(1, 'Apollo Pharmacy M.G. Road', 'No.94, M.G. Road, Heritage Town', 'Puducherry', 11.9356, 79.8337, '0413-2341000', '09:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Mission Street Emergency Pharmacy', 'Mission Street, Heritage Town', 'Puducherry', 11.9324, 79.8308, '+91-9876543211', '08:30:00', '22:30:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'Rajiv Gandhi WCH Casualty Pharmacy', '100 Feet Road, Ellaipillaichavady', 'Puducherry', 11.9391, 79.8056, '+91-9876543212', NULL, NULL, true, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Apollo Pharmacy Anna Nagar', 'No.140, A1 Block, Anna Nagar', 'Chennai', 13.0843, 80.2106, '044-26221223', '09:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'Apollo Pharmacy Perambur 2', 'Old No.36, New No.89, Vavidelu Mudali Street, Perambur', 'Chennai', 13.1182, 80.2329, '044-25510550', '20:00:00', '04:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 'Apollo Pharmacy Anna Nagar 2nd Avenue', 'Shop No.1, C/32, 2nd Avenue, Anna Nagar', 'Chennai', 13.0850, 80.2181, '044-26200272', '07:00:00', '22:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7, 'Apollo Pharmacy Indira Nagar', '119, Ground Floor, 10th Cross, 1st Stage, Indira Nagar', 'Bangalore', 12.9784, 77.6408, '080-41020665', '07:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(8, 'Apollo Pharmacy HSR Layout', 'No.1713, Ground Floor, HSR Layout, Sector 2', 'Bangalore', 12.9121, 77.6446, '080-41020639', '07:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(9, 'Apollo Pharmacy Banashankari', 'Plot No.5, 50 Feet Main Road, Nagendra Block, Banashankari 1st Stage', 'Bangalore', 12.9335, 77.5468, '080-41020683', '08:00:00', '22:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10, 'Apollo Pharmacy HITEC City', 'Ground Floor, Cyber Towers Road, HITEC City', 'Hyderabad', 17.4435, 78.3772, '040-23110100', '08:00:00', '23:30:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(11, 'Apollo Pharmacy Banjara Hills', 'Road No. 12, Banjara Hills', 'Hyderabad', 17.4156, 78.4347, '040-23607777', NULL, NULL, true, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(12, 'Apollo Pharmacy Jubilee Hills', 'Road No. 36, Jubilee Hills', 'Hyderabad', 17.4319, 78.4071, '040-23550550', '07:00:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(13, 'Apollo Pharmacy Race Course', 'No.18, Race Course Road, Gopalapuram', 'Coimbatore', 11.0014, 76.9698, '0422-2221100', '08:00:00', '22:30:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(14, 'Apollo Pharmacy R.S. Puram', 'DB Road, R.S. Puram', 'Coimbatore', 11.0085, 76.9517, '0422-2470055', '07:30:00', '23:00:00', false, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(15, 'Apollo Pharmacy Peelamedu', 'Avinashi Road, Peelamedu', 'Coimbatore', 11.0267, 76.9995, '0422-2570088', NULL, NULL, true, 'Asia/Kolkata', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    address = EXCLUDED.address,
    city = EXCLUDED.city,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    contact_number = EXCLUDED.contact_number,
    opening_time = EXCLUDED.opening_time,
    closing_time = EXCLUDED.closing_time,
    is_24_seven = EXCLUDED.is_24_seven,
    timezone = EXCLUDED.timezone,
    updated_at = CURRENT_TIMESTAMP;

-- Seed Medicines
INSERT INTO medicines (id, name, brand, category, description, dosage, requires_prescription, created_at, updated_at)
VALUES
(1, 'Remdesivir 100mg Injection', 'Covifor', 'ANTIVIRAL', 'Antiviral medication for severe viral infections', '100mg', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Tocilizumab 400mg', 'Actemra', 'IMMUNOSUPPRESSANT', 'Immunosuppressive drug for cytokine release syndrome', '400mg', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'Enoxaparin 40mg Injection', 'Clexane', 'ANTICOAGULANT', 'Low molecular weight heparin blood thinner', '40mg', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Favipiravir 400mg', 'FabiFlu', 'ANTIVIRAL', 'Oral antiviral medication', '400mg', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'Oxygen Cylinder 10L', 'MedO2', 'RESPIRATORY_SUPPORT', 'Medical grade oxygen cylinder for emergency support', '10L', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 'Anti-Venom Polyvalent Injection', 'VenoCure', 'EMERGENCY_ANTIVENOM', 'Polyvalent anti-snake venom serum for emergency snake bites', '10ml', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Seed Inventories
INSERT INTO inventory (id, pharmacy_id, medicine_id, quantity, reserved_quantity, version, created_at, updated_at)
VALUES
(1, 1, 1, 10, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 2, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 1, 3, 25, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 2, 1, 5, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 2, 4, 50, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 3, 2, 2, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7, 3, 5, 8, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(8, 1, 6, 4, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(9, 2, 6, 2, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10, 4, 1, 7, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(11, 4, 5, 5, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(12, 5, 3, 18, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(13, 5, 6, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(14, 6, 2, 4, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(15, 7, 1, 8, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(16, 7, 6, 5, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(17, 8, 5, 9, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(18, 8, 3, 22, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(19, 9, 2, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(20, 10, 1, 12, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(21, 10, 6, 6, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(22, 11, 2, 4, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(23, 11, 5, 15, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(24, 12, 3, 20, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(25, 13, 1, 8, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(26, 13, 6, 5, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(27, 14, 4, 35, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(28, 15, 2, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(29, 15, 5, 10, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Manager staff pharmacy scoping
ALTER TABLE manager_staff ADD COLUMN IF NOT EXISTS pharmacy_id BIGINT;
UPDATE manager_staff SET pharmacy_id = 1 WHERE username IN ('pharmacist1', 'pharmacist2', 'pharmacist3') AND pharmacy_id IS NULL;

-- Prescription workflow columns on reservations
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS prescription_number VARCHAR(100);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS doctor_name VARCHAR(100);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS doctor_reg_number VARCHAR(100);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS prescription_verified BOOLEAN DEFAULT false;
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS prescription_verified_by VARCHAR(100);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS prescription_verified_at TIMESTAMP;

-- Composite index for reservation expiry scheduler queries
CREATE INDEX IF NOT EXISTS idx_reservations_status_expires_at ON reservations (status, expires_at);

-- Synchronize identity sequences safely with max existing IDs to prevent ID collisions on subsequent inserts.
-- Uses GREATEST with pg_sequences.last_value to guarantee synchronization never lowers an existing sequence.
-- Handles empty tables safely by only executing setval when MAX(id) is present.
SELECT CASE
    WHEN (SELECT MAX(id) FROM inventory) IS NOT NULL
    THEN setval(
        pg_get_serial_sequence('inventory', 'id'),
        GREATEST(
            (SELECT MAX(id) FROM inventory),
            COALESCE((SELECT last_value FROM pg_sequences WHERE schemaname || '.' || sequencename = pg_get_serial_sequence('inventory', 'id') OR sequencename = pg_get_serial_sequence('inventory', 'id')), 1)
        )
    )
    ELSE NULL
END;

SELECT CASE
    WHEN (SELECT MAX(id) FROM users) IS NOT NULL
    THEN setval(
        pg_get_serial_sequence('users', 'id'),
        GREATEST(
            (SELECT MAX(id) FROM users),
            COALESCE((SELECT last_value FROM pg_sequences WHERE schemaname || '.' || sequencename = pg_get_serial_sequence('users', 'id') OR sequencename = pg_get_serial_sequence('users', 'id')), 1)
        )
    )
    ELSE NULL
END;

SELECT CASE
    WHEN (SELECT MAX(id) FROM pharmacies) IS NOT NULL
    THEN setval(
        pg_get_serial_sequence('pharmacies', 'id'),
        GREATEST(
            (SELECT MAX(id) FROM pharmacies),
            COALESCE((SELECT last_value FROM pg_sequences WHERE schemaname || '.' || sequencename = pg_get_serial_sequence('pharmacies', 'id') OR sequencename = pg_get_serial_sequence('pharmacies', 'id')), 1)
        )
    )
    ELSE NULL
END;

SELECT CASE
    WHEN (SELECT MAX(id) FROM medicines) IS NOT NULL
    THEN setval(
        pg_get_serial_sequence('medicines', 'id'),
        GREATEST(
            (SELECT MAX(id) FROM medicines),
            COALESCE((SELECT last_value FROM pg_sequences WHERE schemaname || '.' || sequencename = pg_get_serial_sequence('medicines', 'id') OR sequencename = pg_get_serial_sequence('medicines', 'id')), 1)
        )
    )
    ELSE NULL
END;
