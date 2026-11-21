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

import cl.kanopus.deploysql.application.config.Catalog;
import cl.kanopus.deploysql.application.utils.CatalogUtils;
import cl.kanopus.deploysql.data.master.MasterDAO;
import cl.kanopus.deploysql.data.master.impl.MasterDAOImpl;
import cl.kanopus.deploysql.service.exception.DeployException;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes the SQL scripts declared in a {@code catalog.xml} and records every execution in the tracking tables {@code CATALOG_SCRIPT_SQL} and {@code CATALOG_SCRIPT_SQL_EXECUTION}.
 * <p>
 * Defaults (since 4.20.0):
 * <ul>
 * <li>the run stops at the first failing script and a {@link DeployException} is thrown ({@link #setContinueOnError(boolean)});</li>
 * <li>each script runs in its own transaction ({@link #setTransactional(boolean)}); engines that auto-commit DDL (Oracle, DB2 for some statements, SQL Server for a few) cannot roll those statements back;</li>
 * <li>a lock row in {@code CATALOG_SCRIPT_SQL_LOCK} prevents concurrent deployments against the same database ({@link #setLockEnabled(boolean)}, {@link #setLockWaitTimeout(Duration)}, {@link #setStaleLockTimeout(Duration)}).</li>
 * </ul>
 * Instances are not thread safe.
 */
public class DeploySQL {

    private static final Logger logger = LoggerFactory.getLogger(DeploySQL.class);

    /** Catalog used by {@link #execute()} and {@link #deploy()}. */
    public static final String DEFAULT_CATALOG = "./catalog.xml";

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final String username;
    private final String password;
    private final String jdbcUrl;

    private boolean continueOnError = false;
    private boolean transactional = true;
    private boolean lockEnabled = true;
    private Duration lockWaitTimeout = Duration.ofMinutes(1);
    private Duration staleLockTimeout = null;
    private Supplier<MasterDAO> masterDaoFactory = MasterDAOImpl::new;
    private Exception lastFailure;

    public DeploySQL(String username, String password, String jdbcUrl) {
        this.username = username;
        this.password = password;
        this.jdbcUrl = jdbcUrl;
    }

    /**
     * When {@code true}, a failing script is recorded and the run continues with the next one; {@link #deploy} then returns normally and the failures are reported in the {@link DeployResult}. Default {@code false}: the run stops at the first failure and a {@link DeployException} is thrown.
     */
    public DeploySQL setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
        return this;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    /**
     * When {@code true} (default) every script, together with its SUCCESS record, runs in its own transaction (auto-commit off, commit on success, rollback on failure). Disable it for scripts that cannot run inside a transaction (e.g. PostgreSQL {@code CREATE INDEX CONCURRENTLY}, {@code VACUUM}) or
     * that manage transactions themselves.
     */
    public DeploySQL setTransactional(boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    public boolean isTransactional() {
        return transactional;
    }

    /** Enables the deploy lock (default {@code true}). */
    public DeploySQL setLockEnabled(boolean lockEnabled) {
        this.lockEnabled = lockEnabled;
        return this;
    }

    public boolean isLockEnabled() {
        return lockEnabled;
    }

    /** Maximum time to wait for a lock held by another deployment (default 1 minute). */
    public DeploySQL setLockWaitTimeout(Duration lockWaitTimeout) {
        this.lockWaitTimeout = Objects.requireNonNull(lockWaitTimeout, "lockWaitTimeout");
        return this;
    }

    public Duration getLockWaitTimeout() {
        return lockWaitTimeout;
    }

    /**
     * Age after which a lock is considered abandoned (e.g. the deploying process was killed) and is broken. Default {@code null}: locks are never broken automatically.
     */
    public DeploySQL setStaleLockTimeout(Duration staleLockTimeout) {
        this.staleLockTimeout = staleLockTimeout;
        return this;
    }

    public Duration getStaleLockTimeout() {
        return staleLockTimeout;
    }

    /** For tests: allows replacing the DAO implementation. */
    DeploySQL setMasterDaoFactory(Supplier<MasterDAO> masterDaoFactory) {
        this.masterDaoFactory = Objects.requireNonNull(masterDaoFactory);
        return this;
    }

    /**
     * Runs {@link #DEFAULT_CATALOG}.
     *
     * @return number of scripts declared in the catalog
     * @see #deploy()
     */
    public int execute() throws Exception {
        return execute(DEFAULT_CATALOG);
    }

    /**
     * Runs the given catalog.
     *
     * @return number of scripts declared in the catalog (not the number executed; use {@link #deploy(String)} for detailed counts)
     * @throws DeployException
     *             if a script fails and {@code continueOnError} is disabled (default since 4.20.0)
     */
    public int execute(String catalogXml) throws Exception {
        return deploy(catalogXml).getTotal();
    }

    /**
     * Runs {@link #DEFAULT_CATALOG}.
     *
     * @since 4.20.0
     */
    public DeployResult deploy() throws Exception {
        return deploy(DEFAULT_CATALOG);
    }

    /**
     * Runs the given catalog and returns the executed/skipped/failed counts.
     *
     * @throws DeployException
     *             if a script fails and {@code continueOnError} is disabled (default)
     * @throws SQLException
     *             if the connection, the tracking tables or the deploy lock fail
     * @since 4.20.0
     */
    public DeployResult deploy(String catalogXml) throws Exception {

        File catalogFile = CatalogUtils.resolveCatalogFile(catalogXml);
        Catalog catalog = CatalogUtils.loadCatalog(catalogFile);
        Catalog.Database database = catalog.getDatabase();
        String label = database.getLabel();
        File catalogDirectory = catalogFile.getAbsoluteFile().getParentFile();

        logger.info("Catalog has been loaded for {} database", label);

        List<Catalog.Database.Scripts.Script> scripts = database.getScripts().getRecords();
        DeployResult result = new DeployResult(label, scripts.size());
        Exception firstFailure = null;
        String owner = lockOwner();

        MasterDAO masterDao = masterDaoFactory.get();
        logger.debug("-----------------------------------------------------------------------");
        try {
            masterDao.openConnection(label, username, password, jdbcUrl);
            logger.info("[{}] Connection successfully established", label);
            logger.info("[{}] Check table schema", label);
            logger.info("[{}] Catalog asocciated with {} scripts for execution", label, result.getTotal());

            masterDao.createSchema();

            boolean locked = false;
            try {
                if (lockEnabled) {
                    masterDao.acquireLock(owner, lockWaitTimeout, staleLockTimeout);
                    locked = true;
                }
                int countScript = 1;
                for (Catalog.Database.Scripts.Script script : scripts) {
                    DeployResult.ScriptResult scriptResult = runScript(masterDao, catalogDirectory, script, label, countScript++, result.getTotal());
                    result.add(scriptResult);
                    if (scriptResult.status() == DeployResult.Status.FAILED && firstFailure == null) {
                        firstFailure = lastFailure;
                    }
                    if (scriptResult.status() == DeployResult.Status.FAILED && !continueOnError) {
                        logger.error("[{}] Deployment stopped: script {} failed ({} scripts not run)", label, scriptResult.script(), result.getNotRun());
                        break;
                    }
                }
            } finally {
                if (locked) {
                    releaseLockQuietly(masterDao, owner);
                }
            }
        } finally {
            logger.info("[{}] The database connection is closed ", label);
            masterDao.closeConnection();
        }

        logger.info("[{}] {}", label, result);
        if (!result.isSuccessful() && !continueOnError) {
            DeployResult.ScriptResult failed = result.getFailedScripts().get(0);
            throw new DeployException("Script " + failed.script() + " failed: " + failed.errorMessage(), result, firstFailure);
        }
        return result;
    }

    private DeployResult.ScriptResult runScript(MasterDAO masterDao, File catalogDirectory, Catalog.Database.Scripts.Script script, String label, int countScript, int totalScripts)
            throws SQLException {
        String path = script.getFilename().trim();
        String key = CatalogUtils.getScriptKey(catalogDirectory, path);
        String legacyKey = CatalogUtils.getName(path);
        boolean onetime = Boolean.TRUE.equals(script.getOnetime());

        // tracking errors are infrastructure errors: they abort the run
        boolean execute = masterDao.saveCatalog(script.getType(), script.getLabel(), key, legacyKey, onetime);
        if (!execute) {
            logger.debug("[{}] [{}/{}] (SKIPPED): The script {} has been skipped.", label, countScript, totalScripts, key);
            return new DeployResult.ScriptResult(key, script.getLabel(), DeployResult.Status.SKIPPED, 0, null);
        }

        long start = System.nanoTime();
        try {
            if (transactional) {
                masterDao.beginTransaction();
            }
            masterDao.executeScript(script.getType(), path);
            masterDao.saveCatalogExecution(key, STATUS_SUCCESS, elapsedMillis(start), "");
            if (transactional) {
                masterDao.commitTransaction();
            }
            logger.debug("[{}] [{}/{}] (SUCCESS): The script {} has been successfully executed.", label, countScript, totalScripts, key);
            return new DeployResult.ScriptResult(key, script.getLabel(), DeployResult.Status.EXECUTED, elapsedMillis(start), null);
        } catch (Exception ex) {
            long millis = elapsedMillis(start);
            lastFailure = ex;
            if (transactional) {
                rollbackQuietly(masterDao, ex);
            }
            String message = errorMessage(ex);
            logger.error("[{}] [{}/{}] (ERROR) Error executing script {} : {}", label, countScript, totalScripts, key, message);
            masterDao.saveCatalogExecution(key, STATUS_ERROR, millis, message);
            return new DeployResult.ScriptResult(key, script.getLabel(), DeployResult.Status.FAILED, millis, message);
        }
    }

    private static void rollbackQuietly(MasterDAO masterDao, Exception cause) {
        try {
            masterDao.rollbackTransaction();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
            logger.warn("Rollback failed: {}", rollbackError.getMessage());
        }
    }

    private static void releaseLockQuietly(MasterDAO masterDao, String owner) {
        try {
            masterDao.releaseLock(owner);
        } catch (SQLException ex) {
            logger.warn("The deploy lock could not be released ({}); remove it with: DELETE FROM CATALOG_SCRIPT_SQL_LOCK", ex.getMessage());
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String lockOwner() {
        return ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID();
    }

    /**
     * Message stored for a failed script: the root cause (usually the database error) followed by the top level message (which names the failing statement). Never {@code null}.
     */
    static String errorMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMessage = root.getMessage() != null ? root.getMessage() : root.getClass().getName();
        if (root == ex || ex.getMessage() == null || ex.getMessage().equals(rootMessage)) {
            return rootMessage;
        }
        return rootMessage + " | " + ex.getMessage();
    }
}
