package com.sentragrid.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresFlywayMigrationIntegrationTest {

    private static final String PG_HOST = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
    private static final int PG_PORT = Integer.parseInt(System.getenv().getOrDefault("POSTGRES_PORT", "5432"));
    private static final String PG_USER = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
    private static final String PG_PASSWORD = System.getenv().getOrDefault("POSTGRES_PASSWORD", "meeran20");

    private static final String FRESH_DB_NAME = "sentinelgrid_migration_fresh_test";
    private static final String BASELINE_DB_NAME = "sentinelgrid_migration_baseline_test";

    private static final List<String> EXPECTED_TABLES = List.of(
            "pharmacies",
            "medicines",
            "roles",
            "users",
            "inventory",
            "reservations",
            "idempotency_records",
            "audit_logs",
            "manager_settings",
            "manager_staff"
    );

    @BeforeAll
    static void checkPostgreSqlAvailable() {
        try (Connection conn = getRootConnection()) {
            assertNotNull(conn, "Root PostgreSQL connection should be established");
        } catch (SQLException e) {
            Assumptions.abort("PostgreSQL not reachable on " + PG_HOST + ":" + PG_PORT + " - skipping migration integration test: " + e.getMessage());
        }
    }

    @AfterAll
    static void teardownDatabases() {
        dropDatabaseQuietly(FRESH_DB_NAME);
        dropDatabaseQuietly(BASELINE_DB_NAME);
    }

    @Test
    @Order(1)
    @DisplayName("Fresh Database Migration: Applies V1 schema from empty state and creates all 10 tables")
    void testFreshDatabaseMigration() throws SQLException {
        recreateEmptyDatabase(FRESH_DB_NAME);
        DataSource ds = createDataSource(FRESH_DB_NAME);

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();

        MigrateResult result = flyway.migrate();
        assertTrue(result.success, "Flyway migration should succeed on fresh database");
        assertEquals(3, result.migrationsExecuted, "V1, V2, and V3 migrations should have been executed");
        assertEquals("3", result.targetSchemaVersion, "Target schema version should be 3");

        // Verify all 10 tables exist
        List<String> actualTables = getTables(ds);
        for (String expectedTable : EXPECTED_TABLES) {
            assertTrue(actualTables.contains(expectedTable),
                    "Freshly migrated database must contain table: " + expectedTable);
        }

        // Verify flyway_schema_history exists and contains V1 record
        assertTrue(actualTables.contains("flyway_schema_history"),
                "flyway_schema_history table must be created");

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT version, description, type, success FROM flyway_schema_history WHERE version = '1'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "V1 record must exist in flyway_schema_history");
            assertEquals("initial schema", rs.getString("description").toLowerCase());
            assertEquals("SQL", rs.getString("type"));
            assertTrue(rs.getBoolean("success"));
        }

        // Verify key indexes and constraints
        verifyConstraintsAndIndexes(ds);
    }

    @Test
    @Order(2)
    @DisplayName("Hibernate Schema Validation: Fresh migrated database satisfies ddl-auto: validate")
    void testHibernateSchemaValidationOnFreshMigratedDatabase() {
        DataSource ds = createDataSource(FRESH_DB_NAME);

        LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
        emfb.setDataSource(ds);
        emfb.setPackagesToScan("com.sentragrid.entity", "com.sentragrid.manager.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        emfb.setJpaVendorAdapter(vendorAdapter);

        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.hbm2ddl.auto", "validate");
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        emfb.setJpaProperties(jpaProperties);

        assertDoesNotThrow(() -> {
            emfb.afterPropertiesSet();
            emfb.destroy();
        }, "Hibernate ddl-auto: validate must succeed without error on freshly migrated schema");
    }

    @Test
    @Order(3)
    @DisplayName("Existing Database Baseline: Baselines existing tables at V1 with zero data loss")
    void testExistingDatabaseBaselinePreservesData() throws Exception {
        recreateEmptyDatabase(BASELINE_DB_NAME);
        DataSource ds = createDataSource(BASELINE_DB_NAME);

        // 1. Simulate existing schema created before Flyway was introduced
        executeSqlScript(ds, "/db/migration/V1__initial_schema.sql");

        // 2. Insert mock pre-existing data into multiple tables
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    INSERT INTO pharmacies (created_at, updated_at, name, address, city, contact_number, is_24_seven)
                    VALUES (NOW(), NOW(), 'Pre-Existing Apollo Pharmacy', '100 Mount Road', 'Chennai', '+91 44 1111 2222', true)
                    """);
            stmt.executeUpdate("""
                    INSERT INTO medicines (created_at, updated_at, name, brand, category, description, dosage, requires_prescription)
                    VALUES (NOW(), NOW(), 'Pre-Existing Paracetamol 650', 'Dolo', 'ANALGESIC', 'Fever relief', '650mg', false)
                    """);
            stmt.executeUpdate("""
                    INSERT INTO users (created_at, updated_at, username, email, password, role)
                    VALUES (NOW(), NOW(), 'existing_patient', 'existing_patient@sentinel.com', 'hashed_pwd', 'PATIENT')
                    """);
        }

        // 3. Configure Flyway with baseline-on-migrate enabled (matching production/local config)
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .baselineDescription("Initial SentinelGrid Schema")
                .load();

        MigrateResult result = flyway.migrate();
        assertTrue(result.success, "Flyway baseline migration must succeed on pre-existing database");
        assertEquals(2, result.migrationsExecuted, "2 migrations (V2 operational indexes and V3 demo baseline) should run because existing schema was baselined at V1");
        assertEquals("3", result.targetSchemaVersion, "Target schema version should be 3 after applying V2 and V3");

        // 4. Verify flyway_schema_history contains BASELINE entry
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT version, description, type, success FROM flyway_schema_history WHERE version = '1'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "V1 baseline entry must exist in flyway_schema_history");
            assertEquals("Initial SentinelGrid Schema", rs.getString("description"));
            assertEquals("BASELINE", rs.getString("type"));
            assertTrue(rs.getBoolean("success"));
        }

        // 5. Assert ZERO data loss: verify pre-existing records still exist unchanged
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rsPharma = stmt.executeQuery("SELECT name FROM pharmacies WHERE name = 'Pre-Existing Apollo Pharmacy'");
            assertTrue(rsPharma.next(), "Pre-existing pharmacy record must be preserved");

            ResultSet rsMed = stmt.executeQuery("SELECT name FROM medicines WHERE name = 'Pre-Existing Paracetamol 650'");
            assertTrue(rsMed.next(), "Pre-existing medicine record must be preserved");

            ResultSet rsUser = stmt.executeQuery("SELECT username FROM users WHERE username = 'existing_patient'");
            assertTrue(rsUser.next(), "Pre-existing user record must be preserved");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Migration Idempotency: Repeated startup executes 0 migrations and reports success")
    void testMigrationIdempotencyAndRepeatedRuns() {
        DataSource ds = createDataSource(FRESH_DB_NAME);

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        MigrateResult repeatedResult = flyway.migrate();
        assertTrue(repeatedResult.success, "Repeated migration must succeed without exception");
        assertEquals(0, repeatedResult.migrationsExecuted, "Repeated migration must execute 0 new migrations");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static Connection getRootConnection() throws SQLException {
        String rootUrl = "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/postgres";
        return DriverManager.getConnection(rootUrl, PG_USER, PG_PASSWORD);
    }

    private static DataSource createDataSource(String dbName) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[]{PG_HOST});
        ds.setPortNumbers(new int[]{PG_PORT});
        ds.setDatabaseName(dbName);
        ds.setUser(PG_USER);
        ds.setPassword(PG_PASSWORD);
        return ds;
    }

    private static void recreateEmptyDatabase(String dbName) {
        dropDatabaseQuietly(dbName);
        try (Connection rootConn = getRootConnection();
             Statement stmt = rootConn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE " + dbName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database " + dbName, e);
        }
    }

    private static void dropDatabaseQuietly(String dbName) {
        try (Connection rootConn = getRootConnection();
             Statement stmt = rootConn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        } catch (SQLException ignored) {
            // Quiet cleanup
        }
    }

    private static List<String> getTables(DataSource ds) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
        }
        return tables;
    }

    private static void verifyConstraintsAndIndexes(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            // Verify idx_reservations_status_expires_at
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'reservations' AND indexname = 'idx_reservations_status_expires_at'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next(), "idx_reservations_status_expires_at index must exist on reservations table");
            }

            // Verify check constraints
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT conname FROM pg_constraint WHERE conname IN ('inventory_quantity_check', 'reservations_status_check', 'users_role_check')")) {
                ResultSet rs = ps.executeQuery();
                Set<String> constraints = new HashSet<>();
                while (rs.next()) {
                    constraints.add(rs.getString("conname"));
                }
                assertTrue(constraints.contains("inventory_quantity_check"), "inventory_quantity_check must exist");
                assertTrue(constraints.contains("reservations_status_check"), "reservations_status_check must exist");
                assertTrue(constraints.contains("users_role_check"), "users_role_check must exist");
            }
        }
    }

    private static void executeSqlScript(DataSource ds, String resourcePath) throws Exception {
        try (InputStream in = PostgresFlywayMigrationIntegrationTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Resource script " + resourcePath + " must exist on classpath");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        }
    }
}
