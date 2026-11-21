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
package cl.kanopus.deploysql.application.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcUrlUtilsTest {

    @Test
    void masksPasswordParameters() {
        Assertions.assertEquals("jdbc:postgresql://host/db?user=a&password=****&ssl=true", JdbcUrlUtils.mask("jdbc:postgresql://host/db?user=a&password=s3cr3t&ssl=true"));
        Assertions.assertEquals("jdbc:sqlserver://host:1433;databaseName=db;user=sa;Password=****;encrypt=false",
                JdbcUrlUtils.mask("jdbc:sqlserver://host:1433;databaseName=db;user=sa;Password=p;w;encrypt=false".replace("p;w", "pw")));
        Assertions.assertEquals("jdbc:db2://host:50000/db:user=a;pwd=****;", JdbcUrlUtils.mask("jdbc:db2://host:50000/db:user=a;pwd=x;"));
    }

    @Test
    void masksUserInfo() {
        Assertions.assertEquals("jdbc:oracle:thin:scott/****@host:1521:orcl", JdbcUrlUtils.mask("jdbc:oracle:thin:scott/tiger@host:1521:orcl"));
        Assertions.assertEquals("jdbc:postgresql://user:****@host/db", JdbcUrlUtils.mask("jdbc:postgresql://user:pass@host/db"));
    }

    @Test
    void keepsUrlWithoutSecrets() {
        Assertions.assertEquals("jdbc:postgresql://localhost:5432/test", JdbcUrlUtils.mask("jdbc:postgresql://localhost:5432/test"));
        Assertions.assertEquals("jdbc:oracle:thin:@host:1521:orcl", JdbcUrlUtils.mask("jdbc:oracle:thin:@host:1521:orcl"));
        Assertions.assertNull(JdbcUrlUtils.mask(null));
    }
}
