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
package cl.kanopus.deploysql.data.impl;

import cl.kanopus.deploysql.application.enums.DatabaseType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public abstract class AbstractDAO {

    protected Connection connection = null;
    protected DatabaseType databaseType = null;

    public void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        connection = null;
        databaseType = null;
    }

    protected DatabaseType openConnection(String url, String user, String password, Integer timeout) throws SQLException {
        try {
            String driverClass = evaluateDriverClass(url);
            Class.forName(driverClass);
            DriverManager.setLoginTimeout(timeout == null ? 30 : timeout);// timeout is only how long the DriverManager waits for a connection
            connection = DriverManager.getConnection(url, user, password);
            databaseType = evaluateType(url);
            return databaseType;
        } catch (SQLException se) {
            throw se;
        } catch (Exception ex) {
            throw new SQLException("Error getting connection", ex);

        }

    }

    protected int queryForInt(String sql, Object... params) throws SQLException {
        return (int) queryForLong(sql, params);
    }

    protected long queryForLong(String sql, Object... params) throws SQLException {
        long number = 0;

        try (PreparedStatement prepareStatement = connection.prepareStatement(sql);) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    if (params[i] instanceof Date) {
                        prepareStatement.setTimestamp((i + 1), new java.sql.Timestamp(((Date) params[i]).getTime()));
                    } else {
                        prepareStatement.setObject((i + 1), params[i]);
                    }
                }
            }
            try (ResultSet resultset = prepareStatement.executeQuery();) {
                if (resultset.next()) {
                    number = resultset.getLong(1);
                } else {
                    number = 0;
                }
            }
        }
        return number;
    }

    protected int execute(String sql, Object... params) throws SQLException {

        try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    if (params[i] instanceof Date) {
                        prepareStatement.setTimestamp((i + 1), new java.sql.Timestamp(((Date) params[i]).getTime()));
                    } else {
                        prepareStatement.setObject((i + 1), params[i]);
                    }
                }
            }
            return prepareStatement.executeUpdate();
        }
    }

    protected String queryForString(String sql, Object... params) throws SQLException {
        String text = null;
        try (PreparedStatement prepareStatement = connection.prepareStatement(sql);) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    if (params[i] instanceof Date) {
                        prepareStatement.setTimestamp((i + 1), new java.sql.Timestamp(((Date) params[i]).getTime()));
                    } else {
                        prepareStatement.setObject((i + 1), params[i]);
                    }
                }
            }
            try (ResultSet resultset = prepareStatement.executeQuery();) {
                if (resultset.next()) {
                    text = resultset.getString(1);
                }
            }
        }
        return text;
    }

    protected List find(String sql, RowMapper rowMapper, Object... params) throws SQLException {

        List list = new ArrayList();
        ResultSet resultset = null;
        PreparedStatement prepareStatement = null;
        try {
            prepareStatement = connection.prepareStatement(sql);
            prepareStatement.setFetchSize(100);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    if (params[i] instanceof Date) {
                        prepareStatement.setTimestamp((i + 1), new java.sql.Timestamp(((Date) params[i]).getTime()));
                    } else {
                        prepareStatement.setObject((i + 1), params[i]);
                    }
                }
            }
            resultset = prepareStatement.executeQuery();
            list = convertResultSetToRowMapper(resultset, rowMapper);
        } finally {
            if (prepareStatement != null) {
                prepareStatement.close();
            }
            if (resultset != null) {
                resultset.close();
            }

        }
        return list;
    }

    private List convertResultSetToRowMapper(ResultSet rs, RowMapper rowMapper) throws SQLException {
        int i = 0;
        List list = new ArrayList();
        while (rs.next()) {
            list.add(rowMapper.mapRow(rs, i++));
        }
        return list;
    }

    protected void executeScriptAudit(String filename) {
        String path = databaseType.getSchemaPath() + filename;
        ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(path), StandardCharsets.UTF_8));
    }

    protected void executeScriptSql(String filename) {
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(filename));
    }

    protected void executeScriptSql(Resource resource) {
        ScriptUtils.executeSqlScript(connection, resource);
    }

    private String evaluateDriverClass(String url) {
        String driverClass = null;
        if (url.startsWith(DatabaseType.ORACLE.getPrefix())) {
            driverClass = DatabaseType.ORACLE.getDriverClass();
        } else if (url.startsWith(DatabaseType.SQLSERVER.getPrefix())) {
            driverClass = DatabaseType.SQLSERVER.getDriverClass();
        } else if (url.startsWith(DatabaseType.POSTGRES.getPrefix())) {
            driverClass = DatabaseType.POSTGRES.getDriverClass();
        }
        if (driverClass == null) {
            throw new UnsupportedOperationException("Cannot determine driverClass related to connection url.\nThere is only support for the following database types: Oracle, Sql Server, Postgresql");
        }
        return driverClass;
    }

    private DatabaseType evaluateType(String url) {
        DatabaseType databaseTypeLocal = null;
        if (url.startsWith(DatabaseType.ORACLE.getPrefix())) {
            databaseTypeLocal = DatabaseType.ORACLE;
        } else if (url.startsWith(DatabaseType.SQLSERVER.getPrefix())) {
            databaseTypeLocal = DatabaseType.SQLSERVER;
        } else if (url.startsWith(DatabaseType.POSTGRES.getPrefix())) {
            databaseTypeLocal = DatabaseType.POSTGRES;
        }
        if (databaseTypeLocal == null) {
            throw new UnsupportedOperationException("Cannot determine data type related to connection url.\nThere is only support for the following database types: Oracle, Sql Server, Postgresql");
        }
        return databaseTypeLocal;
    }

}
