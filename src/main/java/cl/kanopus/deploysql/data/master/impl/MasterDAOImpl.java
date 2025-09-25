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

import cl.kanopus.deploysql.data.impl.AbstractDAO;
import cl.kanopus.deploysql.data.master.MasterDAO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

/**
 *
 * @author Pablo Diaz Saavedra
 * @email pabloandres.diazsaavedra@gmail.com
 * @company Kanopus.cl
 */
@Slf4j
public class MasterDAOImpl extends AbstractDAO implements MasterDAO {

    @Override
    public void openConnection(String label, String user, String password, String jdbcUrl) throws SQLException {
        Integer timeout = 30;
        log.debug("[{}] Getting connection to database --> user:[{}],  timeout: [{}], url: [{}]", label, user, timeout, jdbcUrl);
        openConnection(jdbcUrl, user, password, timeout);
    }

    @Override
    public void createSchema() throws SQLException {

        if (!existTableCatalogScript()) {
            createTableCatalogScript();
            log.info("schema catalog scripts table has been created successfully");
        }
        if (!existTableCatalogExecution()) {
            createTableCatalogExecution();
            log.info("schema catalog execution table has been created successfully");
        }

    }

    @Override
    public boolean saveCatalog(String type, String objectname, String filename, boolean onetime) throws SQLException {
        boolean execute = false;
        try {
            if (!existFileNameExecuted(filename)) {
                String catalogId = (new Date()).getTime() + "_" + UUID.randomUUID();
                insertCatalog(catalogId, type, objectname, filename, onetime);
                execute = true;
            } else {
                updateCatalog(filename, onetime);
                if (!onetime || !existFileNameExecutedSuccess(filename)) {
                    execute = true;
                }
            }
        } catch (SQLException ex) {
            log.error("Error save catalog: {} ", ex.getMessage());
        }
        return execute;
    }

    @Override
    public void saveCatalogExecution(String filename, String status, long timeExecution, String messageError) throws SQLException {
        try {
            String catalogId = getCatalogId(filename);
            insertCatalogExecution(catalogId, timeExecution, status, messageError);
        } catch (SQLException ex) {
            log.error("Error save Catalog execution ", ex);
        }
    }

    @Override
    public void executeScript(String type, String filename) {

        if ("FUNCTION".equalsIgnoreCase(type)) {
            try {
                Resource resource = new FileSystemResource(filename);
                BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    stringBuilder.append(line).append('\n');
                }
                br.close();
                String body = stringBuilder.toString();
                body = body.replaceAll("'", "''");
                body = body.replaceAll("\\$BODY\\$", "'");

                executeScriptSql(new ByteArrayResource(body.getBytes()));
            } catch (Exception ex) {
                throw new RuntimeException(ex.getMessage(), ex);
            }
        } else {
            executeScriptSql(filename);
        }
    }

    private boolean existTableCatalogScript() throws SQLException {
        boolean exist;
        try {
            super.queryForInt("SELECT count(*) FROM CATALOG_SCRIPT_SQL");
            exist = true;
        } catch (Exception ex) {
            exist = false;
        }
        return exist;
    }

    private void createTableCatalogScript() {
        executeScriptAudit("001_TABLE_CATALOG_SCRIPT_SQL.SQL");
    }

    private boolean existTableCatalogExecution() throws SQLException {
        boolean exist;
        try {
            super.queryForInt("SELECT count(*) FROM CATALOG_SCRIPT_SQL_EXECUTION");
            exist = true;
        } catch (Exception ex) {
            exist = false;
        }
        return exist;
    }

    private void createTableCatalogExecution() {
        executeScriptAudit("002_TABLE_CATALOG_SCRIPT_SQL_EXECUTION.SQL");
    }

    private boolean existFileNameExecuted(String filename) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(" SELECT count(1) ");
        sb.append(" FROM catalog_script_sql_execution e ");
        sb.append(" INNER JOIN catalog_script_sql s on e.catalog_id=s.catalog_id ");
        sb.append(" WHERE s.filename = ?");
        int count = queryForInt(sb.toString(), filename.toUpperCase());
        return (count > 0);
    }

    private boolean existFileNameExecutedSuccess(String filename) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(" SELECT count(1) ");
        sb.append(" FROM catalog_script_sql_execution e ");
        sb.append(" INNER JOIN catalog_script_sql s on e.catalog_id=s.catalog_id ");
        sb.append(" WHERE s.filename = ? AND e.status='SUCCESS'");
        int count = queryForInt(sb.toString(), filename.toUpperCase());
        return (count > 0);
    }

    private void insertCatalog(String catalogId, String type, String objectname, String filename, Boolean onetime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO catalog_script_sql (catalog_id, object_type, label, filename, one_time) ");
        sb.append("VALUES (?,?,?,?,?)");
        execute(sb.toString(), catalogId, type, objectname, filename.toUpperCase(), Boolean.TRUE.equals(onetime) ? "1" : "0");

    }

    private void updateCatalog(String filename, Boolean onetime) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE catalog_script_sql ");
        sb.append("SET one_time = ? WHERE filename = ? ");
        execute(sb.toString(), Boolean.TRUE.equals(onetime) ? "0" : "1", filename.toUpperCase());
    }

    private String getCatalogId(String filename) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT catalog_id FROM catalog_script_sql WHERE filename = ? ");
        return queryForString(sb.toString(), filename.toUpperCase());
    }

    private void insertCatalogExecution(String catalogId, long timeExecution, String status, String messageError) throws SQLException {
        StringBuilder sb = new StringBuilder();
        Date date = new Date();
        sb.append("INSERT INTO catalog_script_sql_execution (catalog_id, execution_date, miliseconds, status, exit_message) ");
        sb.append("VALUES (?, ?, ? , ?, ? )");
        execute(sb.toString(), catalogId, date, timeExecution, status.toUpperCase(), messageError);
    }
}
