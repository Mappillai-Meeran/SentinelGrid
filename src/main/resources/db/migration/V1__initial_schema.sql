-- ==============================================================================
-- SentinelGrid Flyway Migration Baseline: V1__initial_schema.sql
-- Database: PostgreSQL 16
-- Tables: pharmacies, medicines, roles, users, inventory, reservations,
--         idempotency_records, audit_logs, manager_settings, manager_staff
-- ==============================================================================

-- 1. Pharmacies
CREATE TABLE pharmacies (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    contact_number VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    name VARCHAR(255) NOT NULL,
    closing_time TIME(6) WITHOUT TIME ZONE,
    opening_time TIME(6) WITHOUT TIME ZONE,
    timezone VARCHAR(255),
    is_24_seven BOOLEAN DEFAULT false
);

-- 2. Medicines
CREATE TABLE medicines (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    brand VARCHAR(255),
    category VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    dosage VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    requires_prescription BOOLEAN NOT NULL
);

-- 3. Roles
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT roles_name_check CHECK (name IN ('PATIENT', 'PHARMACIST', 'ADMIN'))
);

-- 4. Users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    pharmacy_id BIGINT,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT users_role_check CHECK (role IN ('PATIENT', 'PHARMACIST', 'PHARMACY_MANAGER', 'ADMIN')),
    CONSTRAINT fk_users_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id)
);

-- 5. Inventory
CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    quantity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL,
    version BIGINT,
    medicine_id BIGINT NOT NULL,
    pharmacy_id BIGINT NOT NULL,
    CONSTRAINT inventory_quantity_check CHECK (quantity >= 0),
    CONSTRAINT inventory_reserved_quantity_check CHECK (reserved_quantity >= 0),
    CONSTRAINT uk_inventory_pharmacy_medicine UNIQUE (pharmacy_id, medicine_id),
    CONSTRAINT fk_inventory_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_inventory_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id)
);

-- 6. Reservations
CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    cancelled_at TIMESTAMP(6) WITHOUT TIME ZONE,
    confirmed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    version BIGINT,
    medicine_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    pharmacy_id BIGINT NOT NULL,
    doctor_name VARCHAR(100),
    doctor_reg_number VARCHAR(100),
    prescription_number VARCHAR(100),
    prescription_verified_at TIMESTAMP(6) WITHOUT TIME ZONE,
    prescription_verified_by VARCHAR(100),
    prescription_verified BOOLEAN DEFAULT false,
    CONSTRAINT reservations_quantity_check CHECK (quantity >= 1),
    CONSTRAINT reservations_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT fk_reservations_patient FOREIGN KEY (patient_id) REFERENCES users(id),
    CONSTRAINT fk_reservations_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id),
    CONSTRAINT fk_reservations_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id)
);

CREATE INDEX idx_reservations_status_expires_at ON reservations (status, expires_at);

-- 7. Idempotency Records
CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    reservation_id BIGINT,
    user_id BIGINT NOT NULL,
    CONSTRAINT uk_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_idempotency_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id)
);

-- 8. Audit Logs
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    details VARCHAR(2000),
    entity_id BIGINT NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    performed_by VARCHAR(255) NOT NULL,
    "timestamp" TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL
);

-- 9. Manager Settings
CREATE TABLE manager_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    coverage VARCHAR(500) NOT NULL,
    hold_expiry_minutes INTEGER NOT NULL,
    hours VARCHAR(255) NOT NULL,
    low_stock_threshold INTEGER NOT NULL,
    max_emergency_hold_quantity INTEGER NOT NULL,
    pharmacy_name VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    verification_status VARCHAR(255) NOT NULL
);

-- 10. Manager Staff
CREATE TABLE manager_staff (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    reservations_handled INTEGER NOT NULL,
    role VARCHAR(255) NOT NULL,
    shift VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    pharmacy_id BIGINT,
    CONSTRAINT uk_manager_staff_username UNIQUE (username),
    CONSTRAINT fk_manager_staff_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies(id)
);

