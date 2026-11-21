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
package cl.kanopus.deploysql.data.master.impl;

import cl.kanopus.deploysql.application.enums.DatabaseType;
import cl.kanopus.deploysql.application.utils.JdbcUrlUtils;
import cl.kanopus.deploysql.data.impl.AbstractDAO;
import cl.kanopus.deploysql.data.master.MasterDAO;
import cl.kanopus.deploysql.service.exception.DeployLockException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

/**
 * @author Pablo Diaz Saavedra
 * @email pabloandres.diazsaavedra@gmail.com
 * @company Kanopus.cl
 */
@Slf4j
public class MasterDAOImpl extends AbstractDAO implements MasterDAO {

    /** Columns written by {@link #saveCatalog}; they must exist in {@code 001_TABLE_CATALOG_SCRIPT_SQL.SQL}. */
    public static final List<String> CATALOG_COLUMNS = List.of("CATALOG_ID", "OBJECT_TYPE", "LABEL", "FILENAME", "ONE_TIME");

    /**
     * Columns written by {@link #saveCatalogExecution}; they must exist in {@code 002_TABLE_CATALOG_SCRIPT_SQL_EXECUTION.SQL}.
     */
    public static final List<String> EXECUTION_COLUMNS = List.of("CATALOG_ID", "EXECUTION_DATE", "MILISECONDS", "STATUS", "EXIT_MESSAGE");

    /** Columns written by {@link #acquireLock}; they must exist in {@code 003_TABLE_CATALOG_SCRIPT_SQL_LOCK.SQL}. */
    public static final List<String> LOCK_COLUMNS = List.of("LOCK_ID", "LOCKED_AT", "LOCKED_BY");

    /** Size of the {@code EXIT_MESSAGE} column; longer messages are truncated. */
    public static final int EXIT_MESSAGE_MAX_LENGTH = 2500;

    /** Size of the {@code FILENAME} column. */
    public static final int FILENAME_MAX_LENGTH = 255;

    private static final int LOCKED_BY_MAX_LENGTH = 255;
    private static final int LOCK_ROW_ID = 1;
    private static final long LOCK_POLL_MILLIS = 500;

    @Override
    public void openConnection(String label, String user, String password, String jdbcUrl) throws SQLException {
        int timeout = 30;
        log.debug("[{}] Getting connection to database --> timeout: [{}], url: [{}]", label, timeout, JdbcUrlUtils.mask(jdbcUrl));
        openConnection(jdbcUrl, user, password, timeout);
    }

    @Override
    public void createSchema() throws SQLException {

        if (!existTable("CATALOG_SCRIPT_SQL")) {
            executeScriptAudit("001_TABLE_CATALOG_SCRIPT_SQL.SQL");
            log.info("schema catalog scripts table has been created successfully");
        }
        if (!existTable("CATALOG_SCRIPT_SQL_EXECUTION")) {
            executeScriptAudit("002_TABLE_CATALOG_SCRIPT_SQL_EXECUTION.SQL");
            log.info("schema catalog execution table has been created successfully");
        } else if (databaseType == DatabaseType.DB2 && !existColumns("CATALOG_SCRIPT_SQL_EXECUTION", "MILISECONDS", "EXIT_MESSAGE")) {
            // Tables created by versions < 4.20.0 on DB2 did not match the INSERT statement
            executeScriptAudit("upgrade/002_ADD_EXECUTION_COLUMNS.SQL");
            log.info("schema catalog execution table has been upgraded successfully");
        }
        if (!existTable("CATALOG_SCRIPT_SQL_LOCK")) {
            executeScriptAudit("003_TABLE_CATALOG_SCRIPT_SQL_LOCK.SQL");
            log.info("schema catalog lock table has been created successfully");
        }
    }

    @Override
    public boolean saveCatalog(String type, String objectname, String filename, boolean onetime) throws SQLException {
        return saveCatalog(type, objectname, filename, null, onetime);
    }

    /**
     * Registers the script and decides whether it must be executed. The decision is based on the catalog row plus the successful executions, so a script whose previous tracking insert failed is not skipped forever. SQL errors are propagated to the caller.
     */
    @Override
    public boolean saveCatalog(String type, String objectname, String scriptKey, String legacyScriptName, boolean onetime) throws SQLException {
        String key = normalizeKey(scriptKey);
        String catalogId = getCatalogId(key);
        if (catalogId == null && legacyScriptName != null) {
            String legacyKey = normalizeKey(legacyScriptName);
            if (!legacyKey.equals(key)) {
                catalogId = getCatalogId(legacyKey);
                if (catalogId != null) {
                    execute("UPDATE catalog_script_sql SET filename = ? WHERE catalog_id = ?", key, catalogId);
                    log.info("Catalog entry {} has been re-keyed to {}", legacyKey, key);
                }
            }
        }

        if (catalogId == null) {
            catalogId = Instant.now().toEpochMilli() + "_" + UUID.randomUUID();
            insertCatalog(catalogId, type, objectname, key, onetime);
            return true;
        }
        updateCatalog(key, onetime);
        return !onetime || !existFileNameExecutedSuccess(key);
    }

    @Override
    public void saveCatalogExecution(String filename, String status, long timeExecution, String messageError) throws SQLException {
        String key = normalizeKey(filename);
        String catalogId = getCatalogId(key);
        if (catalogId == null) {
            throw new SQLException("Script " + key + " is not registered in CATALOG_SCRIPT_SQL");
        }
        insertCatalogExecution(catalogId, timeExecution, status, truncate(messageError, EXIT_MESSAGE_MAX_LENGTH));
    }

    @Override
    public void executeScript(String type, String filename) {

        if ("FUNCTION".equalsIgnoreCase(type)) {
            String body;
            try (InputStream in = new FileSystemResource(filename).getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot read script " + filename + ": " + ex.getMessage(), ex);
            }
            body = body.replace("'", "''");
            body = body.replace("$BODY$", "'");
            executeScriptSql(new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8)));
        } else {
            executeScriptSql(filename);
        }
    }

    @Override
    public void acquireLock(String owner, Duration waitTimeout, Duration staleTimeout) throws SQLException {
        String lockedBy = truncate(owner, LOCKED_BY_MAX_LENGTH);
        long deadline = System.nanoTime() + (waitTimeout == null ? 0 : waitTimeout.toNanos());
        int attemptsWithoutHolder = 0;
        while (true) {
            SQLException failure;
            try {
                execute("INSERT INTO catalog_script_sql_lock (lock_id, locked_at, locked_by) VALUES (?, ?, ?)", LOCK_ROW_ID, Timestamp.from(Instant.now()), lockedBy);
                log.debug("Deploy lock acquired by {}", lockedBy);
                return;
            } catch (SQLException ex) {
                failure = ex;
            }

            LockHolder holder = currentLockHolder();
            if (holder == null) {
                // the lock was released meanwhile (retry), or the insert failed for another reason
                if (++attemptsWithoutHolder > 3) {
                    throw failure;
                }
                continue;
            }
            if (staleTimeout != null && holder.lockedAt().isBefore(Instant.now().minus(staleTimeout))) {
                log.warn("Breaking stale deploy lock held by {} since {}", holder.lockedBy(), holder.lockedAt());
                execute("DELETE FROM catalog_script_sql_lock WHERE lock_id = ? AND locked_by = ?", LOCK_ROW_ID, holder.lockedBy());
                continue;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new DeployLockException("Another deployment holds the lock (locked by " + holder.lockedBy() + " since " + holder.lockedAt()
                        + "). If no deployment is running, remove it with: DELETE FROM CATALOG_SCRIPT_SQL_LOCK");
            }
            sleep(Math.min(LOCK_POLL_MILLIS, TimeUnit.NANOSECONDS.toMillis(remaining) + 1));
        }
    }

    @Override
    public void releaseLock(String owner) throws SQLException {
        execute("DELETE FROM catalog_script_sql_lock WHERE lock_id = ? AND locked_by = ?", LOCK_ROW_ID, truncate(owner, LOCKED_BY_MAX_LENGTH));
        log.debug("Deploy lock released by {}", owner);
    }

    @Override
    public void beginTransaction() throws SQLException {
        setAutoCommit(false);
    }

    @Override
    public void commitTransaction() throws SQLException {
        try {
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } finally {
            setAutoCommit(true);
        }
    }

    @Override
    public void rollbackTransaction() throws SQLException {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } finally {
            setAutoCommit(true);
        }
    }

    private record LockHolder(String lockedBy, Instant lockedAt) {
    }

    private LockHolder currentLockHolder() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT locked_by, locked_at FROM catalog_script_sql_lock WHERE lock_id = ?")) {
            ps.setInt(1, LOCK_ROW_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp lockedAt = rs.getTimestamp(2);
                    return new LockHolder(rs.getString(1), lockedAt == null ? Instant.EPOCH : lockedAt.toInstant());
                }
                return null;
            }
        }
    }

    private static void sleep(long millis) throws SQLException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for the deploy lock", ex);
        }
    }

    private boolean existTable(String table) {
        try {
            queryForInt("SELECT count(*) FROM " + table);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private boolean existColumns(String table, String... columns) {
        try {
            queryForInt("SELECT count(*) FROM (SELECT " + String.join(", ", columns) + " FROM " + table + ") t");
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private boolean existFileNameExecutedSuccess(String key) throws SQLException {
        String sql = " SELECT count(1) " //
                + " FROM catalog_script_sql_execution e " //
                + " INNER JOIN catalog_script_sql s on e.catalog_id=s.catalog_id " //
                + " WHERE s.filename = ? AND e.status='SUCCESS'";
        return queryForInt(sql, key) > 0;
    }

    private void insertCatalog(String catalogId, String type, String objectname, String key, boolean onetime) throws SQLException {
        String sql = "INSERT INTO catalog_script_sql (" + String.join(", ", CATALOG_COLUMNS) + ") VALUES (?,?,?,?,?)";
        execute(sql, catalogId, type, objectname, key, onetime ? "1" : "0");
    }

    private void updateCatalog(String key, boolean onetime) throws SQLException {
        execute("UPDATE catalog_script_sql SET one_time = ? WHERE filename = ? ", onetime ? "1" : "0", key);
    }

    private String getCatalogId(String key) throws SQLException {
        return queryForString("SELECT catalog_id FROM catalog_script_sql WHERE filename = ? ", key);
    }

    private void insertCatalogExecution(String catalogId, long timeExecution, String status, String messageError) throws SQLException {
        String sql = "INSERT INTO catalog_script_sql_execution (" + String.join(", ", EXECUTION_COLUMNS) + ") VALUES (?, ?, ?, ?, ?)";
        execute(sql, catalogId, Timestamp.from(Instant.now()), timeExecution, status.toUpperCase(Locale.ROOT), messageError);
    }

    private static String normalizeKey(String key) {
        String normalized = key.toUpperCase(Locale.ROOT);
        if (normalized.length() > FILENAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Script path is longer than " + FILENAME_MAX_LENGTH + " characters: " + key);
        }
        return normalized;
    }

    /** Truncates {@code text} to {@code maxLength} characters; {@code null} stays {@code null}. */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
