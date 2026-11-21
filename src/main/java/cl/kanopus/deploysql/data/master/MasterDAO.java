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
package cl.kanopus.deploysql.data.master;

import java.sql.SQLException;
import java.time.Duration;

public interface MasterDAO {

    void openConnection(String label, String user, String password, String url) throws SQLException;

    void closeConnection() throws SQLException;

    void createSchema() throws SQLException;

    /**
     * Registers a script in the catalog table and decides whether it must be executed.
     *
     * @return {@code true} when the script must be executed: it is new, it is repeatable ({@code onetime=false}) or it is one-time but has no successful execution recorded yet.
     */
    boolean saveCatalog(String type, String label, String scriptName, boolean onetime) throws SQLException;

    /**
     * Same as {@link #saveCatalog(String, String, String, boolean)}, but when no catalog row exists for {@code scriptKey} a row registered under {@code legacyScriptName} (the upper-case base filename used by versions prior to 4.20.0) is re-keyed and reused, so already executed scripts are still
     * recognized.
     */
    default boolean saveCatalog(String type, String label, String scriptKey, String legacyScriptName, boolean onetime) throws SQLException {
        return saveCatalog(type, label, scriptKey, onetime);
    }

    void executeScript(String type, String filename) throws SQLException;

    void saveCatalogExecution(String filename, String success, long timeExecution, String string) throws SQLException;

    /**
     * Acquires the deploy lock, waiting at most {@code waitTimeout}. A lock older than {@code staleTimeout} (when not {@code null}) is considered abandoned and is broken.
     */
    default void acquireLock(String owner, Duration waitTimeout, Duration staleTimeout) throws SQLException {
        // no lock support by default
    }

    default void releaseLock(String owner) throws SQLException {
        // no lock support by default
    }

    /** Starts a transaction (disables auto-commit). */
    default void beginTransaction() throws SQLException {
        // no transaction support by default
    }

    /** Commits the current transaction and restores auto-commit. */
    default void commitTransaction() throws SQLException {
        // no transaction support by default
    }

    /** Rolls back the current transaction and restores auto-commit. */
    default void rollbackTransaction() throws SQLException {
        // no transaction support by default
    }
}
