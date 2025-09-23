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
package cl.kanopus.deploysql.application.enums;

public enum DatabaseType {

    ORACLE("oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@", "jdbc:oracle:thin:@%s:%s:%s", "schema/oracle/"),
    SQLSERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://", "jdbc:sqlserver://%s:%s;databaseName=%s", "schema/sqlserver/"),
    POSTGRES("org.postgresql.Driver", "jdbc:postgresql://", "jdbc:postgresql://%s:%s/%s", "schema/postgres/");

    private final String driverClass;
    private final String prefix;
    private final String url;
    private final String schemaPath;

    DatabaseType(String driverClass, String prefix, String url, String schemaPath) {
        this.driverClass = driverClass;
        this.prefix = prefix;
        this.url = url;
        this.schemaPath = schemaPath;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getUrl() {
        return url;
    }

    public String getSchemaPath() {
        return schemaPath;
    }

}
