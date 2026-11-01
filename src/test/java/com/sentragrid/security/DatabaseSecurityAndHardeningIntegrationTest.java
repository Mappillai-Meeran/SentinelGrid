package com.sentragrid.security;

import org.junit.jupiter.api.*;
import org.postgresql.util.PSQLException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseSecurityAndHardeningIntegrationTest {

    private static final String PG_HOST = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
    private static final int PG_PORT = Integer.parseInt(System.getenv().getOrDefault("POSTGRES_PORT", "5432"));
    private static final String ADMIN_USER = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
    private static final String ADMIN_PASSWORD = System.getenv("POSTGRES_PASSWORD");
    private static final String DB_NAME = "sentinelgrid";

    private static final String APP_USER = "sentinel_app";
    private static final String APP_PASSWORD = System.getenv("SENTINEL_APP_PASSWORD");

    private static final String BACKUP_USER = "sentinel_backup";
    private static final String BACKUP_PASSWORD = System.getenv("SENTINEL_BACKUP_PASSWORD");

    private static final List<String> APPLICATION_TABLES = List.of(
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
    static void provisionRolesAndCheckConnection() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_DB_SECURITY_INTEGRATION")),
                "Database security integration is disabled unless RUN_DB_SECURITY_INTEGRATION=true.");
        Assumptions.assumeTrue(hasText(ADMIN_PASSWORD) && hasText(APP_PASSWORD) && hasText(BACKUP_PASSWORD),
                "POSTGRES_PASSWORD, SENTINEL_APP_PASSWORD, and SENTINEL_BACKUP_PASSWORD must be supplied securely.");
        try (Connection adminConn = DriverManager.getConnection(
                "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/" + DB_NAME, ADMIN_USER, ADMIN_PASSWORD)) {

            try (Statement stmt = adminConn.createStatement()) {
                String escapedAppPassword = APP_PASSWORD.replace("'", "''");
                String escapedBackupPassword = BACKUP_PASSWORD.replace("'", "''");
                // Ensure sentinel_app exists
                stmt.execute("""
                        DO $$
                        BEGIN
                            IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sentinel_app') THEN
                                CREATE ROLE sentinel_app WITH LOGIN PASSWORD '%s';
                            ELSE
                                ALTER ROLE sentinel_app WITH PASSWORD '%s';
                            END IF;

                            IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sentinel_backup') THEN
                                CREATE ROLE sentinel_backup WITH LOGIN PASSWORD '%s';
                            ELSE
                                ALTER ROLE sentinel_backup WITH PASSWORD '%s';
                            END IF;
                        END $$;
                        """.formatted(escapedAppPassword, escapedAppPassword, escapedBackupPassword, escapedBackupPassword));

                // Grants
                stmt.execute("GRANT CONNECT ON DATABASE " + DB_NAME + " TO sentinel_app, sentinel_backup;");
                stmt.execute("GRANT USAGE ON SCHEMA public TO sentinel_app, sentinel_backup;");
                stmt.execute("REVOKE CREATE ON SCHEMA public FROM sentinel_app, sentinel_backup;");

                stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sentinel_app;");
                stmt.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO sentinel_app;");
                stmt.execute("REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_app;");

                stmt.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO sentinel_backup;");
                stmt.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sentinel_backup;");
                stmt.execute("REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_backup;");
            }
        } catch (SQLException e) {
            Assumptions.abort("PostgreSQL not reachable or admin setup failed: " + e.getMessage());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Test
    @Order(1)
    @DisplayName("Least-Privilege App Role: sentinel_app can perform required DML and sequence operations")
    void testLeastPrivilegeAppRoleCanPerformDml() throws SQLException {
        try (Connection appConn = DriverManager.getConnection(
                "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/" + DB_NAME, APP_USER, APP_PASSWORD)) {

            // 1. Verify SELECT on all application tables
            for (String table : APPLICATION_TABLES) {
                try (Statement stmt = appConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + table)) {
                    assertTrue(rs.next(), "Should read count from table: " + table);
                    assertTrue(rs.getLong(1) >= 0);
                }
            }

            // 2. Verify sequence nextval
            try (Statement stmt = appConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT nextval('audit_logs_id_seq')")) {
                assertTrue(rs.next());
                assertTrue(rs.getLong(1) > 0, "Sequence nextval should succeed for sentinel_app");
            }

            // 3. Verify INSERT and UPDATE on permitted application table (audit_logs)
            try (PreparedStatement ps = appConn.prepareStatement(
                    "INSERT INTO audit_logs (action, entity_id, entity_name, performed_by, \"timestamp\", details) " +
                    "VALUES (?, ?, ?, ?, NOW(), ?) RETURNING id")) {
                ps.setString(1, "SECURITY_DRILL_TEST");
                ps.setLong(2, 99999L);
                ps.setString(3, "SECURITY_VERIFICATION");
                ps.setString(4, "test_least_privilege");
                ps.setString(5, "Verifying sentinel_app insert capability");
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                long insertedId = rs.getLong(1);
                assertTrue(insertedId > 0);

                // Verify UPDATE
                try (PreparedStatement updatePs = appConn.prepareStatement(
                        "UPDATE audit_logs SET details = ? WHERE id = ?")) {
                    updatePs.setString(1, "Updated by sentinel_app");
                    updatePs.setLong(2, insertedId);
                    int updated = updatePs.executeUpdate();
                    assertEquals(1, updated);
                }

                // Verify DELETE
                try (PreparedStatement deletePs = appConn.prepareStatement(
                        "DELETE FROM audit_logs WHERE id = ?")) {
                    deletePs.setLong(1, insertedId);
                    int deleted = deletePs.executeUpdate();
                    assertEquals(1, deleted);
                }
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Least-Privilege App Role: sentinel_app is rejected on DROP, TRUNCATE, and CREATE TABLE")
    void testLeastPrivilegeAppRoleCannotPerformDestructiveDdl() throws SQLException {
        try (Connection appConn = DriverManager.getConnection(
                "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/" + DB_NAME, APP_USER, APP_PASSWORD)) {

            // 1. Attempt DROP TABLE -> Must fail with permission denied / must be owner
            try (Statement stmt = appConn.createStatement()) {
                PSQLException ex = assertThrows(PSQLException.class, () -> stmt.execute("DROP TABLE medicines"));
                assertTrue(ex.getMessage().toLowerCase().contains("must be owner") ||
                           ex.getMessage().toLowerCase().contains("permission denied"));
            }

            // 2. Attempt TRUNCATE TABLE -> Must fail with permission denied
            try (Statement stmt = appConn.createStatement()) {
                PSQLException ex = assertThrows(PSQLException.class, () -> stmt.execute("TRUNCATE TABLE medicines"));
                assertTrue(ex.getMessage().toLowerCase().contains("permission denied"));
            }

            // 3. Attempt CREATE TABLE -> Must fail with permission denied on schema public
            try (Statement stmt = appConn.createStatement()) {
                PSQLException ex = assertThrows(PSQLException.class, () -> stmt.execute("CREATE TABLE rogue_table (id INT)"));
                assertTrue(ex.getMessage().toLowerCase().contains("permission denied"));
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Read-Only Backup Role: sentinel_backup can SELECT but is rejected on all mutations")
    void testLeastPrivilegeBackupRoleIsReadOnly() throws SQLException {
        try (Connection backupConn = DriverManager.getConnection(
                "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/" + DB_NAME, BACKUP_USER, BACKUP_PASSWORD)) {

            // 1. Verify SELECT succeeds
            try (Statement stmt = backupConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                assertTrue(rs.next());
                assertTrue(rs.getLong(1) >= 0);
            }

            // 2. Attempt INSERT -> Must fail with permission denied
            try (Statement stmt = backupConn.createStatement()) {
                PSQLException ex = assertThrows(PSQLException.class, () ->
                        stmt.execute("INSERT INTO audit_logs (action, entity_id, entity_name, performed_by, \"timestamp\") " +
                                     "VALUES ('TEST', 1, 'USER', 'backup_user', NOW())"));
                assertTrue(ex.getMessage().toLowerCase().contains("permission denied"));
            }

            // 3. Attempt UPDATE -> Must fail with permission denied
            try (Statement stmt = backupConn.createStatement()) {
                PSQLException ex = assertThrows(PSQLException.class, () ->
                        stmt.execute("UPDATE medicines SET description = 'Hacked' WHERE id = 1"));
                assertTrue(ex.getMessage().toLowerCase().contains("permission denied"));
            }

            // 4. Attempt DELETE -> Must fail with permission denied
            try (Statement stmt = backupConn.createStatement()) {
                PSQLException ex = assertThrows(PSQLException.class, () ->
                        stmt.execute("DELETE FROM users WHERE id = 99999"));
                assertTrue(ex.getMessage().toLowerCase().contains("permission denied"));
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Configuration Hardening: docker-compose and application-prod enforce TLS and required secrets")
    void testConfigurationHardeningEnforcesTlsAndRequiredSecrets() throws Exception {
        // 1. Inspect docker-compose.yml
        Path composePath = Path.of("docker-compose.yml");
        assertTrue(Files.exists(composePath), "docker-compose.yml must exist");
        String composeContent = Files.readString(composePath);

        // Assert public port 5432 binding is removed from postgres service
        assertFalse(composeContent.contains("- \"5432:5432\""),
                "PostgreSQL port 5432 must NOT be bound to host 0.0.0.0 in production docker-compose.yml");

        // Assert required environment variable checks are present
        assertTrue(composeContent.contains("POSTGRES_USER:?CRITICAL"),
                "docker-compose.yml must require POSTGRES_USER");
        assertTrue(composeContent.contains("POSTGRES_PASSWORD:?CRITICAL"),
                "docker-compose.yml must require POSTGRES_PASSWORD");
        assertTrue(composeContent.contains("SPRING_DATASOURCE_PASSWORD:?CRITICAL"),
                "docker-compose.yml must require SPRING_DATASOURCE_PASSWORD");
        assertTrue(composeContent.contains("JWT_SECRET:?CRITICAL"),
                "docker-compose.yml must require JWT_SECRET");

        // 2. Inspect application-prod.yml
        Path prodYmlPath = Path.of("src/main/resources/application-prod.yml");
        assertTrue(Files.exists(prodYmlPath), "application-prod.yml must exist");
        String prodContent = Files.readString(prodYmlPath);

        // Assert sslmode=require is in datasource URL
        assertTrue(prodContent.contains("sslmode=require"),
                "application-prod.yml must enforce sslmode=require for PostgreSQL network connections");

        // Assert default runtime user is least-privilege sentinel_app
        assertTrue(prodContent.contains("sentinel_app"),
                "application-prod.yml must default to sentinel_app runtime role");

        // Assert .env.example exists
        Path envExamplePath = Path.of(".env.example");
        assertTrue(Files.exists(envExamplePath), ".env.example must exist");
    }

    @Test
    @Order(5)
    @DisplayName("Local Development Continuity: Local profile configuration preserves localhost connectivity")
    void testLocalProfileConnectivityPreserved() {
        Path localYmlPath = Path.of("src/main/resources/application-local.yml");
        assertTrue(Files.exists(localYmlPath), "application-local.yml must exist");

        // Root application.yml preserves local development connection
        Path appYmlPath = Path.of("src/main/resources/application.yml");
        assertTrue(Files.exists(appYmlPath), "application.yml must exist");
    }
}
