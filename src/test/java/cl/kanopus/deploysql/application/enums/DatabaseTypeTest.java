/*-
 * !--
 * For support and inquiries regarding this library, please contact:
 *   soporte@kanopus.cl
 *
 * Project website:
 *   https://www.kanopus.cl
 * %%
 * Copyright (C) 2025 - 2026 Pablo Díaz Saavedra
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DatabaseTypeTest {

    @Test
    void testDb2Support() {
        String db2Url = "jdbc:db2://localhost:50000/testdb";
        DatabaseType type = null;

        if (db2Url.startsWith(DatabaseType.DB2.getPrefix())) {
            type = DatabaseType.DB2;
        }

        Assertions.assertNotNull(type);
        Assertions.assertEquals(DatabaseType.DB2, type);
        Assertions.assertEquals("com.ibm.db2.jcc.DB2Driver", type.getDriverClass());
        Assertions.assertEquals("schema/db2/", type.getSchemaPath());
    }

    @Test
    void testUrlFormatting() {
        String formattedUrl =
                String.format(DatabaseType.DB2.getUrl(), "localhost", "50000", "testdb");
        Assertions.assertEquals("jdbc:db2://localhost:50000/testdb", formattedUrl);
    }
}
