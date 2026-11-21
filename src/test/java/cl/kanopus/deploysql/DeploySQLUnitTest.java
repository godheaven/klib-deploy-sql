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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.kanopus.deploysql.data.master.MasterDAO;
import cl.kanopus.deploysql.service.exception.DeployException;
import java.sql.SQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of the run orchestration with a mocked DAO.
 */
class DeploySQLUnitTest {

    private static final String CATALOG = "src/test/resources/catalog.xml";

    private final MasterDAO dao = mock(MasterDAO.class);

    private DeploySQL deploySQL() {
        return new DeploySQL("u", "p", "jdbc:postgresql://localhost/db").setMasterDaoFactory(() -> dao);
    }

    @Test
    void rollbackAndLockReleaseFailuresDoNotHideTheScriptError() throws Exception {
        when(dao.saveCatalog(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(dao).executeScript(anyString(), anyString());
        doThrow(new SQLException("rollback failed")).when(dao).rollbackTransaction();
        doThrow(new SQLException("release failed")).when(dao).releaseLock(anyString());

        DeployException ex = Assertions.assertThrows(DeployException.class, () -> deploySQL().deploy(CATALOG));
        Assertions.assertEquals("boom", ex.getResult().getFailedScripts().get(0).errorMessage());
        Assertions.assertEquals("rollback failed", ex.getCause().getSuppressed()[0].getMessage());
        verify(dao).saveCatalogExecution(eq("src/test/resources/scripts/test1.sql"), eq("ERROR"), anyLong(), eq("boom"));
        verify(dao).closeConnection();
    }

    @Test
    void trackingErrorsAbortTheRun() throws Exception {
        when(dao.saveCatalog(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenThrow(new SQLException("tracking down"));
        DeploySQL deploy = deploySQL().setContinueOnError(true);
        SQLException ex = Assertions.assertThrows(SQLException.class, () -> deploy.deploy(CATALOG));
        Assertions.assertEquals("tracking down", ex.getMessage());
        verify(dao, never()).executeScript(anyString(), anyString());
        verify(dao).releaseLock(anyString());
        verify(dao).closeConnection();
    }

    @Test
    void skippedScriptsAreNotExecuted() throws Exception {
        when(dao.saveCatalog(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn(false);
        DeployResult result = deploySQL().setTransactional(false).setLockEnabled(false).deploy(CATALOG);
        Assertions.assertEquals(2, result.getSkipped());
        Assertions.assertEquals("KANOPUS-LOCAL", result.getDatabase());
        verify(dao, never()).executeScript(anyString(), anyString());
        verify(dao, never()).acquireLock(anyString(), any(), any());
        verify(dao, never()).beginTransaction();
    }

    @Test
    void errorMessage() {
        Assertions.assertEquals("root", DeploySQL.errorMessage(new SQLException("root")));
        Assertions.assertEquals("root | top", DeploySQL.errorMessage(new IllegalStateException("top", new SQLException("root"))));
        Assertions.assertEquals("java.lang.NullPointerException", DeploySQL.errorMessage(new NullPointerException()));
        Assertions.assertEquals("java.lang.NullPointerException", DeploySQL.errorMessage(new RuntimeException(new NullPointerException())));
    }

    @Test
    void lockWaitTimeoutIsRequired() {
        DeploySQL deploy = deploySQL();
        Assertions.assertThrows(NullPointerException.class, () -> deploy.setLockWaitTimeout(null));
    }
}
