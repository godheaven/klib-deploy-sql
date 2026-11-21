/*-
 * !--
 * For support and inquiries regarding this library, please contact:
 *   soporte@kanopus.cl
 *
 * Project website:
 *   https://www.kanopus.cl
 * %%
 * Copyright (C) 2025 Pablo Díaz Saavedra
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * --!
 */
package cl.kanopus.deploysql;

import cl.kanopus.deploysql.service.exception.DeployException;
import cl.kanopus.deploysql.service.exception.DeployLockException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end tests against PostgreSQL running in Docker (Testcontainers).
 */
@Testcontainers
class DeploySQLTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("test").withUsername("user_test").withPassword("pass_test");

    @TempDir
    Path dir;

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static DeploySQL deploySQL() {
        return new DeploySQL(POSTGRES.getUsername(), POSTGRES.getPassword(), POSTGRES.getJdbcUrl());
    }

    private record Entry(String file, String type, boolean onetime) {
    }

    private Path script(String name, String sql) throws IOException {
        Path file = dir.resolve("scripts").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, sql, StandardCharsets.UTF_8);
        return file;
    }

    private String catalog(Entry... entries) throws IOException {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<catalog><database><label>TEST</label><scripts>\n");
        for (Entry e : entries) {
            sb.append("<script onetime=\"").append(e.onetime()).append("\" type=\"").append(e.type()).append("\" label=\"").append(e.file()).append("\">")
                    .append(dir.resolve("scripts").resolve(e.file()).toAbsolutePath()).append("</script>\n");
        }
        sb.append("</scripts></database></catalog>");
        Path file = dir.resolve("catalog.xml");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file.toString();
    }

    private static List<String> queryColumn(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(queryColumn(sql).get(0));
    }

    private static void sql(String sql) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void testExecuteWithNoDatabase() {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/db_test";
        Exception exception = Assertions.assertThrows(SQLException.class, () -> new DeploySQL("user_test", "pass_test", url).execute("src/test/resources/catalog.xml"));
        Assertions.assertEquals("FATAL: database \"db_test\" does not exist", exception.getMessage());
    }

    @Test
    void testUnsupportedUrl() {
        DeploySQL deploy = new DeploySQL("u", "p", "jdbc:h2:mem:test");
        SQLException ex = Assertions.assertThrows(SQLException.class, () -> deploy.execute("src/test/resources/catalog.xml"));
        Assertions.assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }

    @Test
    void testExecuteDefaultCatalog() throws Exception {
        // classpath catalog.xml with paths relative to the working directory
        Assertions.assertEquals(2, deploySQL().execute());
        Assertions.assertEquals(List.of("Región de Ñuble"), queryColumn("SELECT name FROM region"));
        Assertions.assertEquals(List.of("SRC/TEST/RESOURCES/SCRIPTS/TEST1.SQL", "SRC/TEST/RESOURCES/SCRIPTS/TEST2.SQL"), queryColumn("SELECT filename FROM catalog_script_sql ORDER BY filename"));

        DeployResult second = deploySQL().deploy();
        Assertions.assertEquals(1, second.getSkipped());
        Assertions.assertEquals(1, second.getExecuted());
    }

    @Test
    void testRerunSkipsOneTimeAndRepeatsOthers() throws Exception {
        script("001_table.sql", "CREATE TABLE counter (n integer);");
        script("002_insert.sql", "INSERT INTO counter VALUES (1);");
        String catalog = catalog(new Entry("001_table.sql", "TABLE", true), new Entry("002_insert.sql", "DATA", false));

        DeployResult first = deploySQL().deploy(catalog);
        Assertions.assertEquals(2, first.getTotal());
        Assertions.assertEquals(2, first.getExecuted());
        Assertions.assertTrue(first.isSuccessful());

        DeployResult second = deploySQL().deploy(catalog);
        Assertions.assertEquals(1, second.getExecuted());
        Assertions.assertEquals(1, second.getSkipped());
        Assertions.assertEquals(0, second.getFailed());
        Assertions.assertEquals(DeployResult.Status.SKIPPED, second.getScripts().get(0).status());
        Assertions.assertEquals(2, count("SELECT count(*) FROM counter"));
        Assertions.assertEquals(3, count("SELECT count(*) FROM catalog_script_sql_execution WHERE status='SUCCESS'"));
        // scripts are keyed by their path relative to the catalog directory
        Assertions.assertEquals(List.of("SCRIPTS/001_TABLE.SQL", "SCRIPTS/002_INSERT.SQL"), queryColumn("SELECT filename FROM catalog_script_sql ORDER BY filename"));
        Assertions.assertTrue(second.toString().contains("skipped=1"));
    }

    @Test
    void testOneTimeFlagIsStoredConsistently() throws Exception {
        script("a.sql", "SELECT 1;");
        deploySQL().deploy(catalog(new Entry("a.sql", "DATA", true)));
        Assertions.assertEquals(List.of("1"), queryColumn("SELECT one_time FROM catalog_script_sql"));

        // second run (update path) must keep the same value
        deploySQL().deploy(catalog(new Entry("a.sql", "DATA", true)));
        Assertions.assertEquals(List.of("1"), queryColumn("SELECT one_time FROM catalog_script_sql"));

        // switching the script to repeatable re-executes it
        DeployResult result = deploySQL().deploy(catalog(new Entry("a.sql", "DATA", false)));
        Assertions.assertEquals(List.of("0"), queryColumn("SELECT one_time FROM catalog_script_sql"));
        Assertions.assertEquals(1, result.getExecuted());
    }

    @Test
    void testFailureStopsRunAndRollsBack() throws Exception {
        script("1_ok.sql", "CREATE TABLE t1 (id integer);");
        script("2_bad.sql", "CREATE TABLE t2 (id integer);\nINSERT INTO missing_table VALUES (1);");
        script("3_ok.sql", "CREATE TABLE t3 (id integer);");
        String catalog = catalog(new Entry("1_ok.sql", "TABLE", true), new Entry("2_bad.sql", "TABLE", true), new Entry("3_ok.sql", "TABLE", true));

        DeployException ex = Assertions.assertThrows(DeployException.class, () -> deploySQL().deploy(catalog));
        DeployResult result = ex.getResult();
        Assertions.assertEquals(1, result.getExecuted());
        Assertions.assertEquals(1, result.getFailed());
        Assertions.assertEquals(1, result.getNotRun());
        Assertions.assertFalse(result.isSuccessful());
        Assertions.assertEquals("scripts/2_bad.sql", result.getFailedScripts().get(0).script());
        Assertions.assertTrue(ex.getMessage().contains("missing_table"), ex.getMessage());
        Assertions.assertInstanceOf(Exception.class, ex.getCause());

        // the failing script was rolled back entirely, the next one was not run
        Assertions.assertEquals(0, count("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('t2','t3')"));
        Assertions.assertEquals(1, count("SELECT count(*) FROM information_schema.tables WHERE table_name = 't1'"));
        List<String> messages = queryColumn("SELECT exit_message FROM catalog_script_sql_execution WHERE status='ERROR'");
        Assertions.assertEquals(1, messages.size());
        Assertions.assertTrue(messages.get(0).contains("missing_table"), messages.get(0));

        // the legacy int API also fails fast
        Assertions.assertThrows(DeployException.class, () -> deploySQL().execute(catalog));

        // once fixed, the failed one-time script is executed again (it has no successful execution)
        script("2_bad.sql", "CREATE TABLE t2 (id integer);");
        DeployResult fixed = deploySQL().deploy(catalog);
        Assertions.assertEquals(List.of(DeployResult.Status.SKIPPED, DeployResult.Status.EXECUTED, DeployResult.Status.EXECUTED),
                fixed.getScripts().stream().map(DeployResult.ScriptResult::status).toList());
    }

    @Test
    void testContinueOnError() throws Exception {
        script("1_bad.sql", "SELECT * FROM missing_table;");
        script("2_ok.sql", "CREATE TABLE t2 (id integer);");
        String catalog = catalog(new Entry("1_bad.sql", "DATA", true), new Entry("2_ok.sql", "TABLE", true));

        DeploySQL deploy = deploySQL().setContinueOnError(true);
        Assertions.assertTrue(deploy.isContinueOnError());
        DeployResult result = deploy.deploy(catalog);
        Assertions.assertEquals(1, result.getFailed());
        Assertions.assertEquals(1, result.getExecuted());
        Assertions.assertEquals(0, result.getNotRun());
        Assertions.assertEquals(1, count("SELECT count(*) FROM information_schema.tables WHERE table_name = 't2'"));
        Assertions.assertEquals(2, deploy.execute(catalog));
    }

    @Test
    void testNonTransactionalKeepsPartialWork() throws Exception {
        script("bad.sql", "CREATE TABLE t1 (id integer);\nINSERT INTO missing_table VALUES (1);");
        DeploySQL deploy = deploySQL().setTransactional(false).setContinueOnError(true);
        Assertions.assertFalse(deploy.isTransactional());
        DeployResult result = deploy.deploy(catalog(new Entry("bad.sql", "TABLE", true)));
        Assertions.assertEquals(1, result.getFailed());
        Assertions.assertEquals(1, count("SELECT count(*) FROM information_schema.tables WHERE table_name = 't1'"));
    }

    @Test
    void testLongErrorMessageIsTruncated() throws Exception {
        String longName = "x".repeat(60);
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < 80; i++) {
            sb.append(i == 0 ? "" : ", ").append(longName).append(i);
        }
        script("bad.sql", sb.append(" FROM missing_table;").toString());
        DeployResult result = deploySQL().setContinueOnError(true).deploy(catalog(new Entry("bad.sql", "DATA", true)));
        Assertions.assertTrue(result.getFailedScripts().get(0).errorMessage().length() > 2500);
        Assertions.assertEquals(List.of("2500"), queryColumn("SELECT length(exit_message) FROM catalog_script_sql_execution"));
    }

    @Test
    void testFunctionMode() throws Exception {
        script("fn.sql", """
                CREATE OR REPLACE FUNCTION greeting(name varchar) RETURNS varchar AS $BODY$
                BEGIN
                    RETURN 'Hola ' || name || '; ñ';
                END;
                $BODY$ LANGUAGE plpgsql;
                """);
        DeployResult result = deploySQL().deploy(catalog(new Entry("fn.sql", "FUNCTION", false)));
        Assertions.assertEquals(1, result.getExecuted());
        Assertions.assertEquals(List.of("Hola mundo; ñ"), queryColumn("SELECT greeting('mundo')"));
    }

    @Test
    void testFunctionModeMissingFileFails() throws Exception {
        String catalog = catalog(new Entry("missing.sql", "FUNCTION", false));
        DeployException ex = Assertions.assertThrows(DeployException.class, () -> deploySQL().deploy(catalog));
        Assertions.assertTrue(ex.getMessage().contains("missing.sql"), ex.getMessage());
    }

    @Test
    void testCatalogRowWithoutExecutionIsNotSkipped() throws Exception {
        script("a.sql", "CREATE TABLE t_a (id integer);");
        String catalog = catalog(new Entry("a.sql", "TABLE", true));
        deploySQL().deploy(catalog);
        // simulate a previous run whose execution record was lost
        sql("DELETE FROM catalog_script_sql_execution");
        sql("DROP TABLE t_a");

        DeployResult result = deploySQL().deploy(catalog);
        Assertions.assertEquals(1, result.getExecuted());
        Assertions.assertEquals(1, count("SELECT count(*) FROM information_schema.tables WHERE table_name = 't_a'"));
    }

    @Test
    void testLegacyBaseNameKeyIsRecognized() throws Exception {
        script("legacy.sql", "CREATE TABLE t_legacy (id integer);");
        String catalog = catalog(new Entry("legacy.sql", "TABLE", true));
        deploySQL().deploy(catalog);
        // emulate a row written by a version < 4.20.0 (key = upper-case base name)
        sql("UPDATE catalog_script_sql SET filename = 'LEGACY.SQL'");

        DeployResult result = deploySQL().deploy(catalog);
        Assertions.assertEquals(1, result.getSkipped());
        Assertions.assertEquals(List.of("SCRIPTS/LEGACY.SQL"), queryColumn("SELECT filename FROM catalog_script_sql"));
    }

    @Test
    void testLockHeldByAnotherDeployment() throws Exception {
        script("a.sql", "SELECT 1;");
        String catalog = catalog(new Entry("a.sql", "DATA", false));
        deploySQL().deploy(catalog); // creates the schema
        sql("INSERT INTO catalog_script_sql_lock (lock_id, locked_at, locked_by) VALUES (1, now(), 'other-host')");

        DeploySQL deploy = deploySQL().setLockWaitTimeout(Duration.ofMillis(700));
        Assertions.assertEquals(Duration.ofMillis(700), deploy.getLockWaitTimeout());
        DeployLockException ex = Assertions.assertThrows(DeployLockException.class, () -> deploy.deploy(catalog));
        Assertions.assertTrue(ex.getMessage().contains("other-host"), ex.getMessage());

        // a stale lock is broken
        deploy.setStaleLockTimeout(Duration.ofMinutes(5));
        Assertions.assertEquals(Duration.ofMinutes(5), deploy.getStaleLockTimeout());
        try (Connection c = connect(); var ps = c.prepareStatement("UPDATE catalog_script_sql_lock SET locked_at = ?")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now().minus(Duration.ofHours(1))));
            ps.executeUpdate();
        }
        Assertions.assertEquals(1, deploy.deploy(catalog).getExecuted());
        // the lock is released after the run
        Assertions.assertEquals(0, count("SELECT count(*) FROM catalog_script_sql_lock"));
    }

    @Test
    void testLockDisabled() throws Exception {
        script("a.sql", "SELECT 1;");
        String catalog = catalog(new Entry("a.sql", "DATA", false));
        deploySQL().deploy(catalog);
        sql("INSERT INTO catalog_script_sql_lock (lock_id, locked_at, locked_by) VALUES (1, now(), 'other-host')");
        DeploySQL deploy = deploySQL().setLockEnabled(false);
        Assertions.assertFalse(deploy.isLockEnabled());
        Assertions.assertEquals(1, deploy.deploy(catalog).getExecuted());
        Assertions.assertEquals(List.of("other-host"), queryColumn("SELECT locked_by FROM catalog_script_sql_lock"));
    }

    @Test
    void testExecutionDateHasTime() throws Exception {
        script("a.sql", "SELECT 1;");
        deploySQL().deploy(catalog(new Entry("a.sql", "DATA", false)));
        Assertions.assertEquals(List.of("timestamp without time zone"),
                queryColumn("SELECT data_type FROM information_schema.columns WHERE table_name = 'catalog_script_sql_execution' AND column_name = 'execution_date'"));
    }
}
