-- Seed data for SentinelGrid Platform

-- Seed Users (Password is 'password123' encoded with BCrypt)
-- Hash: $2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW
INSERT INTO users (id, username, email, password, role, created_at, updated_at)
VALUES 
(1, 'patient1', 'patient1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'PATIENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'pharmacist1', 'pharmacist1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'PHARMACIST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'admin1', 'admin1@sentinel.com', '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Ensure existing users have the correct updated BCrypt hash
UPDATE users SET password = '$2a$10$SHMFKVkVSKISZNrLkhpX0uOfldFniUZ0IVLgB0fhgcvItLGCPGZFW';

-- Seed Pharmacies
INSERT INTO pharmacies (id, name, address, city, latitude, longitude, contact_number, created_at, updated_at)
VALUES
(1, 'Apollo Pharmacy Central', '123 Healthcare Ave, MG Road', 'Bangalore', 12.9716, 77.5946, '+91-9876543210', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'MedPlus Emergency Care', '45 Station Road, Indiranagar', 'Bangalore', 12.9784, 77.6408, '+91-9876543211', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'Wellness Forever 24x7', '78 Ring Road, HSR Layout', 'Bangalore', 12.9121, 77.6445, '+91-9876543212', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

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
(9, 2, 6, 2, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
