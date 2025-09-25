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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.sql.SQLException;
import java.util.Date;

public class DeploySQL {

    private static final Logger logger = LoggerFactory.getLogger(DeploySQL.class);

    private final String username;
    private final String password;
    private final String jdbcUrl;

    public DeploySQL(String username, String password, String jdbcUrl) {
        this.username = username;
        this.password = password;
        this.jdbcUrl = jdbcUrl;
    }

    public int execute() throws Exception {
        return execute("./catalog.xml");
    }

    public int execute(String catalogXml) throws Exception {

        Catalog catalog = CatalogUtils.loadCatalog(catalogXml);
        Catalog.Database database = catalog.getDatabase();
        String label = database.getLabel();

        logger.info("Catalog has been loaded for {} database", label);

        MasterDAO masterDao = new MasterDAOImpl();
        int totalScripts = 0;
        logger.debug("-----------------------------------------------------------------------");
        try {

            masterDao.openConnection(label, username, password, jdbcUrl);

            logger.info("[{}] Connection successfully established", label);
            logger.info("[{}] Check table schema", label);
            new URI(jdbcUrl.replaceFirst("jdbc:", ""));

            totalScripts = database.getScripts().getRecords().size();

            logger.info("[{}] Catalog asocciated with {} scripts for execution", label, totalScripts);

            masterDao.createSchema();

            int countScript = 1;
            for (Catalog.Database.Scripts.Script script : database.getScripts().getRecords()) {
                Exception exceptionScript = null;
                boolean execute = false;
                long timeExecution = 0;
                long initTime = 0;
                long endTime = 0;
                String scriptFilename = null;
                try {
                    scriptFilename = CatalogUtils.getName(script.getFilename());
                    execute = masterDao.saveCatalog(script.getType(), script.getLabel(), scriptFilename, script.getOnetime());
                    // execute script
                    if (execute) {
                        initTime = (new Date()).getTime();
                        masterDao.executeScript(script.getType(), script.getFilename());
                        endTime = (new Date()).getTime();
                        timeExecution = (endTime - initTime);
                    }

                } catch (Exception ex) {
                    exceptionScript = ex;
                    endTime = (new Date()).getTime();
                    timeExecution = (endTime - initTime);
                } finally {
                    if (execute) {
                        if (exceptionScript == null) {
                            logger.debug("[{}] [{}/{}] (SUCCESS): The script {} has been successfully executed.", label, countScript, totalScripts, scriptFilename);
                            masterDao.saveCatalogExecution(scriptFilename, "SUCCESS", timeExecution, "");
                        } else {
                            logger.debug("[{}] [{}/{}] (ERROR) Error executing script {} : {}", label, countScript, totalScripts, scriptFilename, exceptionScript.getMessage());
                            masterDao.saveCatalogExecution(scriptFilename, "ERROR", timeExecution, exceptionScript.getCause().getMessage());
                        }
                    } else {
                        logger.debug("[{}] [{}/{}] (SKIPPED): The script {} has been skipped.", label, countScript, totalScripts, scriptFilename);
                    }
                    countScript++;
                }

            }

        } catch (SQLException e) {
            throw e;
        } catch (Exception ex) {
            throw ex;
        } finally {
            logger.info("[{}] The database connection is closed ", label);
            masterDao.closeConnection();
        }
        return totalScripts;
    }

}
