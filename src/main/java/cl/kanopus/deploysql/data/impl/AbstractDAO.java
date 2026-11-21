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
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

public abstract class AbstractDAO {

    private static final String UNSUPPORTED_URL_MESSAGE = "Cannot determine data type related to connection url.\nThere is only support for the following database types: Oracle, Sql Server, Postgresql, DB2";

    protected Connection connection = null;
    protected DatabaseType databaseType = null;

    public void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        connection = null;
        databaseType = null;
    }

    /**
     * Opens a JDBC connection.
     * <p>
     * Drivers are discovered through the JDBC 4 {@link java.util.ServiceLoader} mechanism, so no {@code Class.forName}
     * is needed. The login timeout is passed as an engine specific connection property instead of
     * {@link DriverManager#setLoginTimeout(int)}, which is a JVM-wide setting that would affect every other
     * connection of the application. A timeout already present in the JDBC url takes precedence.
     */
    protected DatabaseType openConnection(String url, String user, String password, Integer timeout) throws SQLException {
        try {
            DatabaseType type = evaluateType(url);
            Properties properties = new Properties();
            if (user != null) {
                properties.setProperty("user", user);
            }
            if (password != null) {
                properties.setProperty("password", password);
            }
            addLoginTimeout(type, url, properties, timeout == null ? 30 : timeout);
            connection = DriverManager.getConnection(url, properties);
            databaseType = type;
            return databaseType;
        } catch (SQLException se) {
            throw se;
        } catch (Exception ex) {
            throw new SQLException("Error getting connection", ex);
        }
    }

    private static void addLoginTimeout(DatabaseType type, String url, Properties properties, int timeoutSeconds) {
        String property;
        String value;
        if (type == DatabaseType.ORACLE) {
            property = "oracle.net.CONNECT_TIMEOUT";
            value = String.valueOf(timeoutSeconds * 1000L);
        } else {
            property = "loginTimeout";
            value = String.valueOf(timeoutSeconds);
        }
        if (!url.toLowerCase(Locale.ROOT).contains(property.toLowerCase(Locale.ROOT))) {
            properties.setProperty(property, value);
        }
    }

    protected int queryForInt(String sql, Object... params) throws SQLException {
        return (int) queryForLong(sql, params);
    }

    protected long queryForLong(String sql, Object... params) throws SQLException {
        try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {
            bind(prepareStatement, params);
            try (ResultSet resultset = prepareStatement.executeQuery()) {
                return resultset.next() ? resultset.getLong(1) : 0;
            }
        }
    }

    protected int execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {
            bind(prepareStatement, params);
            return prepareStatement.executeUpdate();
        }
    }

    protected String queryForString(String sql, Object... params) throws SQLException {
        try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {
            bind(prepareStatement, params);
            try (ResultSet resultset = prepareStatement.executeQuery()) {
                return resultset.next() ? resultset.getString(1) : null;
            }
        }
    }

    protected List<Object> find(String sql, RowMapper<?> rowMapper, Object... params) throws SQLException {
        try (PreparedStatement prepareStatement = connection.prepareStatement(sql)) {
            prepareStatement.setFetchSize(100);
            bind(prepareStatement, params);
            try (ResultSet resultset = prepareStatement.executeQuery()) {
                return convertResultSetToRowMapper(resultset, rowMapper);
            }
        }
    }

    private static void bind(PreparedStatement prepareStatement, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof Timestamp timestamp) {
                prepareStatement.setTimestamp(i + 1, timestamp);
            } else if (param instanceof Date date) {
                prepareStatement.setTimestamp(i + 1, new Timestamp(date.getTime()));
            } else if (param instanceof Instant instant) {
                prepareStatement.setTimestamp(i + 1, Timestamp.from(instant));
            } else {
                prepareStatement.setObject(i + 1, param);
            }
        }
    }

    private List<Object> convertResultSetToRowMapper(ResultSet rs, RowMapper<?> rowMapper) throws SQLException {
        int i = 0;
        List<Object> list = new ArrayList<>();
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
        executeScriptSql(new FileSystemResource(filename));
    }

    /**
     * Executes a SQL script resource. The content is always decoded as UTF-8, independently of the platform default
     * charset.
     */
    protected void executeScriptSql(Resource resource) {
        ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, StandardCharsets.UTF_8));
    }

    protected void setAutoCommit(boolean autoCommit) throws SQLException {
        if (connection.getAutoCommit() != autoCommit) {
            connection.setAutoCommit(autoCommit);
        }
    }

    protected static DatabaseType evaluateType(String url) {
        if (url != null) {
            for (DatabaseType type : DatabaseType.values()) {
                if (url.startsWith(type.getPrefix())) {
                    return type;
                }
            }
        }
        throw new UnsupportedOperationException(UNSUPPORTED_URL_MESSAGE);
    }
}
