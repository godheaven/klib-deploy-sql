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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Checks, for every engine, that the tracking table DDL declares the columns written by {@link MasterDAOImpl}.
 */
class TrackingSchemaTest {

    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+(\\w+)\\s*\\((.*?)\\);", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN = Pattern.compile("^\\s*(\\w+)\\s+(\\w+)");

    private record Table(String name, List<String> columns, List<String> types) {
    }

    private static Table parse(DatabaseType type, String file) throws IOException {
        try (InputStream in = TrackingSchemaTest.class.getClassLoader().getResourceAsStream(type.getSchemaPath() + file)) {
            Assertions.assertNotNull(in, type + " " + file);
            String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = CREATE_TABLE.matcher(ddl);
            Assertions.assertTrue(m.find(), "no CREATE TABLE in " + type + " " + file);
            List<String> columns = new ArrayList<>();
            List<String> types = new ArrayList<>();
            for (String line : m.group(2).split("\\R")) {
                Matcher c = COLUMN.matcher(line);
                if (c.find() && !"CONSTRAINT".equalsIgnoreCase(c.group(1))) {
                    columns.add(c.group(1).toUpperCase(Locale.ROOT));
                    types.add(c.group(2).toUpperCase(Locale.ROOT));
                }
            }
            return new Table(m.group(1).toUpperCase(Locale.ROOT), columns, types);
        }
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void catalogTableMatchesInsert(DatabaseType type) throws IOException {
        Table table = parse(type, "001_TABLE_CATALOG_SCRIPT_SQL.SQL");
        Assertions.assertEquals("CATALOG_SCRIPT_SQL", table.name());
        Assertions.assertEquals(MasterDAOImpl.CATALOG_COLUMNS, table.columns());
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void executionTableMatchesInsert(DatabaseType type) throws IOException {
        Table table = parse(type, "002_TABLE_CATALOG_SCRIPT_SQL_EXECUTION.SQL");
        Assertions.assertEquals("CATALOG_SCRIPT_SQL_EXECUTION", table.name());
        Assertions.assertEquals(MasterDAOImpl.EXECUTION_COLUMNS, table.columns());
        String dateType = table.types().get(table.columns().indexOf("EXECUTION_DATE"));
        Assertions.assertTrue(List.of("TIMESTAMP", "DATETIME2").contains(dateType), type + ": EXECUTION_DATE " + dateType);
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void lockTableMatchesInsert(DatabaseType type) throws IOException {
        Table table = parse(type, "003_TABLE_CATALOG_SCRIPT_SQL_LOCK.SQL");
        Assertions.assertEquals("CATALOG_SCRIPT_SQL_LOCK", table.name());
        Assertions.assertEquals(MasterDAOImpl.LOCK_COLUMNS, table.columns());
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void exitMessageColumnSizeMatchesTruncation(DatabaseType type) throws IOException {
        try (InputStream in = TrackingSchemaTest.class.getClassLoader().getResourceAsStream(type.getSchemaPath() + "002_TABLE_CATALOG_SCRIPT_SQL_EXECUTION.SQL")) {
            String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("EXIT_MESSAGE\\s+[\\w ]+\\(\\s*(\\d+)").matcher(ddl);
            Assertions.assertTrue(m.find(), type.name());
            Assertions.assertEquals(MasterDAOImpl.EXIT_MESSAGE_MAX_LENGTH, Integer.parseInt(m.group(1)));
        }
    }

    @org.junit.jupiter.api.Test
    void db2UpgradeAddsMissingColumns() throws IOException {
        try (InputStream in = TrackingSchemaTest.class.getClassLoader().getResourceAsStream("schema/db2/upgrade/002_ADD_EXECUTION_COLUMNS.SQL")) {
            String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertTrue(ddl.contains("ADD COLUMN MILISECONDS"));
            Assertions.assertTrue(ddl.contains("ADD COLUMN EXIT_MESSAGE VARCHAR(2500)"));
        }
    }

    @org.junit.jupiter.api.Test
    void truncate() {
        Assertions.assertNull(MasterDAOImpl.truncate(null, 3));
        Assertions.assertEquals("ab", MasterDAOImpl.truncate("ab", 3));
        Assertions.assertEquals("abc", MasterDAOImpl.truncate("abcdef", 3));
    }
}
