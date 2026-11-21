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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of a {@link DeploySQL#deploy(String)} run.
 *
 * @since 4.20.0
 */
public final class DeployResult {

    /** Outcome of a single catalog entry. */
    public enum Status {
        /** The script was executed successfully. */
        EXECUTED,
        /** The script is one-time and was already executed successfully. */
        SKIPPED,
        /** The script failed (its error is recorded in {@code CATALOG_SCRIPT_SQL_EXECUTION}). */
        FAILED
    }

    /**
     * Result of one script.
     *
     * @param script
     *            the script key (path as declared in the catalog, normalized)
     * @param label
     *            the catalog label of the script
     * @param status
     *            the outcome
     * @param millis
     *            execution time in milliseconds (0 when skipped)
     * @param errorMessage
     *            the error message when {@link Status#FAILED}, otherwise {@code null}
     */
    public record ScriptResult(String script, String label, Status status, long millis, String errorMessage) {
    }

    private final String database;
    private final int total;
    private final List<ScriptResult> scripts = new ArrayList<>();

    DeployResult(String database, int total) {
        this.database = database;
        this.total = total;
    }

    void add(ScriptResult result) {
        scripts.add(result);
    }

    /** Label of the catalog database. */
    public String getDatabase() {
        return database;
    }

    /** Number of scripts declared in the catalog. */
    public int getTotal() {
        return total;
    }

    public int getExecuted() {
        return count(Status.EXECUTED);
    }

    public int getSkipped() {
        return count(Status.SKIPPED);
    }

    public int getFailed() {
        return count(Status.FAILED);
    }

    /** Scripts that were not attempted because the run stopped at a failure. */
    public int getNotRun() {
        return total - scripts.size();
    }

    /** {@code true} when no script failed. */
    public boolean isSuccessful() {
        return getFailed() == 0;
    }

    /** Per-script results, in catalog order (scripts not attempted are absent). */
    public List<ScriptResult> getScripts() {
        return Collections.unmodifiableList(scripts);
    }

    public List<ScriptResult> getFailedScripts() {
        return scripts.stream().filter(s -> s.status() == Status.FAILED).toList();
    }

    private int count(Status status) {
        return (int) scripts.stream().filter(s -> s.status() == status).count();
    }

    @Override
    public String toString() {
        return "DeployResult[database=" + database + ", total=" + total + ", executed=" + getExecuted() + ", skipped=" + getSkipped() + ", failed=" + getFailed() + ", notRun=" + getNotRun() + "]";
    }
}
