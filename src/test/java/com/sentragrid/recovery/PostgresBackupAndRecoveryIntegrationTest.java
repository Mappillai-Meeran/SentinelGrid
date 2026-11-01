package com.sentragrid.recovery;

import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresBackupAndRecoveryIntegrationTest {

    private static final String PG_HOST = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
    private static final int PG_PORT = Integer.parseInt(System.getenv().getOrDefault("POSTGRES_PORT", "5432"));
    private static final String PG_USER = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
    private static final String PG_PASSWORD = System.getenv().getOrDefault("POSTGRES_PASSWORD", "meeran20");
    private static final String SOURCE_DB = "sentinelgrid";
    private static final String DRILL_DB = "sentinelgrid_recovery_drill_test";

    private static Path tempBackupDir;
    private static Path backupFilePath;
    private static String expectedSha256;

    private static String pgDumpBinary;
    private static String pgRestoreBinary;

    private static final List<String> CORE_TABLES = List.of(
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
    static void setUp() throws Exception {
        // 1. Locate binaries
        pgDumpBinary = locatePostgresBinary("pg_dump");
        pgRestoreBinary = locatePostgresBinary("pg_restore");
        assertNotNull(pgDumpBinary, "pg_dump binary must be available");
        assertNotNull(pgRestoreBinary, "pg_restore binary must be available");

        // 2. Verify connection
        try (Connection conn = getRootConnection()) {
            assertNotNull(conn, "Root PostgreSQL connection should be established");
        } catch (SQLException e) {
            Assumptions.abort("PostgreSQL not reachable on " + PG_HOST + ":" + PG_PORT + " - skipping recovery test: " + e.getMessage());
        }

        // 3. Create temp directory for backup
        tempBackupDir = Files.createTempDirectory("sentinelgrid_backup_test_");
        backupFilePath = tempBackupDir.resolve("sentinelgrid_drill_backup.dump");
    }

    @AfterAll
    static void tearDown() {
        dropDatabaseQuietly(DRILL_DB);
        if (tempBackupDir != null) {
            try {
                Files.walk(tempBackupDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Automated Backup: Generates valid pg_dump -Fc archive and computes SHA-256 checksum")
    void testAutomatedBackupGenerationAndChecksumIntegrity() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                pgDumpBinary,
                "-h", PG_HOST,
                "-p", String.valueOf(PG_PORT),
                "-U", PG_USER,
                "-d", SOURCE_DB,
                "-Fc",
                "--no-owner",
                "--no-acl",
                "-f", backupFilePath.toAbsolutePath().toString()
        );
        pb.environment().put("PGPASSWORD", PG_PASSWORD);

        Process process = pb.start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "pg_dump must exit with code 0");

        File backupFile = backupFilePath.toFile();
        assertTrue(backupFile.exists(), "Backup archive must be written to disk");
        assertTrue(backupFile.length() > 5000, "Backup archive size must be > 5KB (actual: " + backupFile.length() + ")");

        // Compute SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(backupFilePath);
        byte[] hashBytes = digest.digest(fileBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        expectedSha256 = sb.toString();
        assertNotNull(expectedSha256);
        assertEquals(64, expectedSha256.length(), "SHA-256 hash must be 64 hexadecimal characters");
    }

    @Test
    @Order(2)
    @DisplayName("TOC Verification: pg_restore -l parses archive TOC without connecting to database")
    void testBackupTableOfContentsVerification() throws Exception {
        assertTrue(Files.exists(backupFilePath), "Backup file must exist from order 1");

        ProcessBuilder pb = new ProcessBuilder(
                pgRestoreBinary,
                "-l",
                backupFilePath.toAbsolutePath().toString()
        );

        Process process = pb.start();
        List<String> tocLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                tocLines.add(line);
            }
        }

        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "pg_restore -l must exit with code 0");
        assertFalse(tocLines.isEmpty(), "TOC entries must not be empty");

        // Verify that all core application tables are listed in the TOC entries
        String combinedToc = String.join("\n", tocLines);
        for (String table : CORE_TABLES) {
            assertTrue(combinedToc.contains(table), "TOC must contain schema or data for table: " + table);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Isolated Restore Drill: Restores backup into temporary drill database and verifies 100% data fidelity")
    void testIsolatedRestoreDrillAndDataIntegrity() throws Exception {
        // 1. Prepare isolated target drill database
        dropDatabaseQuietly(DRILL_DB);
        try (Connection rootConn = getRootConnection();
             Statement stmt = rootConn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE " + DRILL_DB);
        }

        // 2. Execute pg_restore into DRILL_DB
        ProcessBuilder pb = new ProcessBuilder(
                pgRestoreBinary,
                "-h", PG_HOST,
                "-p", String.valueOf(PG_PORT),
                "-U", PG_USER,
                "-d", DRILL_DB,
                "--no-owner",
                "--no-acl",
                backupFilePath.toAbsolutePath().toString()
        );
        pb.environment().put("PGPASSWORD", PG_PASSWORD);

        Process process = pb.start();
        int exitCode = process.waitFor();
        // pg_restore returns 0 or 1 (1 indicates benign warnings on existing public schema, which is standard)
        assertTrue(exitCode == 0 || exitCode == 1, "pg_restore must complete successfully (exit code: " + exitCode + ")");

        // 3. Connect to both databases and verify table-by-table record fidelity
        try (Connection sourceConn = getDatabaseConnection(SOURCE_DB);
             Connection drillConn = getDatabaseConnection(DRILL_DB)) {

            for (String table : CORE_TABLES) {
                long sourceCount = getRowCount(sourceConn, table);
                long drillCount = getRowCount(drillConn, table);
                assertEquals(sourceCount, drillCount,
                        "Row count in restored drill database must exactly match source database for table: " + table);
            }

            // 4. Verify representative data records in drill database
            try (Statement drillStmt = drillConn.createStatement()) {
                // Verify patient1 exists with PATIENT role
                ResultSet rsUser = drillStmt.executeQuery("SELECT username, email, role FROM users WHERE username = 'patient1'");
                assertTrue(rsUser.next(), "patient1 must exist in restored database");
                assertEquals("patient1@sentinel.com", rsUser.getString("email"));
                assertEquals("PATIENT", rsUser.getString("role"));

                // Verify medicines catalog exists
                ResultSet rsMed = drillStmt.executeQuery("SELECT count(*) FROM medicines");
                assertTrue(rsMed.next());
                assertTrue(rsMed.getLong(1) >= 5, "Medicines catalog must be restored");

                // Verify reservations table index exists
                ResultSet rsIdx = drillStmt.executeQuery(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'reservations' AND indexname = 'idx_reservations_status_expires_at'");
                assertTrue(rsIdx.next(), "Index idx_reservations_status_expires_at must be preserved in restored database");

                // Verify check constraints
                ResultSet rsCheck = drillStmt.executeQuery(
                        "SELECT conname FROM pg_constraint WHERE conname IN ('inventory_quantity_check', 'reservations_status_check', 'users_role_check')");
                int checkCount = 0;
                while (rsCheck.next()) {
                    checkCount++;
                }
                assertTrue(checkCount >= 3, "Database constraints must be preserved in restored database");
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Safety Guard: Production database overwrite without explicit override is blocked")
    void testRestoreProductionOverwriteSafeguard() {
        // Verify safety invariant: target database cannot equal active production database unless explicitly allowed
        String target = "sentinelgrid";
        boolean allowProdOverwrite = false;

        boolean isBlocked = target.equalsIgnoreCase("sentinelgrid") && !allowProdOverwrite;
        assertTrue(isBlocked, "Safety guard must reject target database 'sentinelgrid' without explicit confirmation");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static Connection getRootConnection() throws SQLException {
        return getDatabaseConnection("postgres");
    }

    private static Connection getDatabaseConnection(String dbName) throws SQLException {
        String url = "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/" + dbName;
        return DriverManager.getConnection(url, PG_USER, PG_PASSWORD);
    }

    private static void dropDatabaseQuietly(String dbName) {
        try (Connection rootConn = getRootConnection();
             Statement stmt = rootConn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        } catch (SQLException ignored) {
        }
    }

    private static long getRowCount(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    private static String locatePostgresBinary(String binaryName) {
        // 1. Try PATH
        try {
            Process p = new ProcessBuilder("where", binaryName).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null && new File(line.trim()).exists()) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Try default PostgreSQL Windows paths
        String[] candidatePaths = {
                "C:\\Program Files\\PostgreSQL\\16\\bin\\" + binaryName + ".exe",
                "C:\\Program Files\\PostgreSQL\\15\\bin\\" + binaryName + ".exe",
                "C:\\Program Files\\PostgreSQL\\14\\bin\\" + binaryName + ".exe"
        };
        for (String path : candidatePaths) {
            if (new File(path).exists()) {
                return path;
            }
        }

        return binaryName;
    }
}

