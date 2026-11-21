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

import cl.kanopus.deploysql.application.config.Catalog;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogUtilsTest {

    @TempDir
    Path dir;

    @Test
    void loadCatalog() throws Exception {
        Catalog catalog = CatalogUtils.loadCatalog("src/test/resources/catalog.xml");
        Assertions.assertEquals("KANOPUS-LOCAL", catalog.getDatabase().getLabel());
        Catalog.Database.Scripts.Script script = catalog.getDatabase().getScripts().getRecords().get(0);
        Assertions.assertEquals("src/test/resources/scripts/test1.sql", script.getFilename());
        Assertions.assertTrue(script.getOnetime());
        Assertions.assertEquals("DATA", script.getType());
        Assertions.assertEquals("test1", script.getLabel());
    }

    @Test
    void invalidCatalogIsRejected() throws Exception {
        Path xml = dir.resolve("catalog.xml");
        Files.writeString(xml, "<catalog><database><label>X</label></database></catalog>");
        Assertions.assertThrows(org.xml.sax.SAXException.class, () -> CatalogUtils.loadCatalog(xml.toFile()));
    }

    @Test
    void externalEntitiesAreRejected() throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET");
        Path xml = dir.resolve("catalog.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE catalog [ <!ENTITY xxe SYSTEM "%s"> ]>
                <catalog><database><label>&xxe;</label><scripts>
                <script onetime="true" type="DATA" label="a">a.sql</script>
                </scripts></database></catalog>
                """.formatted(secret.toUri()), StandardCharsets.UTF_8);
        Exception ex = Assertions.assertThrows(Exception.class, () -> CatalogUtils.loadCatalog(xml.toFile()));
        Assertions.assertInstanceOf(org.xml.sax.SAXParseException.class, ex);
        Assertions.assertTrue(ex.getMessage().contains("accessExternalDTD"), ex.getMessage());
        Assertions.assertFalse(String.valueOf(ex.getMessage()).contains("TOP-SECRET"));
    }

    @Test
    void getName() {
        Assertions.assertEquals("a.sql", CatalogUtils.getName("dir\\sub//a.sql"));
        Assertions.assertEquals("a.sql", CatalogUtils.getName("a.sql"));
    }

    @Test
    void getScriptKey() {
        File base = dir.toFile();
        Assertions.assertEquals("scripts/a.sql", CatalogUtils.getScriptKey(base, "./scripts//a.sql"));
        Assertions.assertEquals("scripts/a.sql", CatalogUtils.getScriptKey(base, " scripts\\a.sql "));
        Assertions.assertEquals("b/a.sql", CatalogUtils.getScriptKey(base, "scripts/../b/a.sql"));
        Assertions.assertEquals("scripts/a.sql", CatalogUtils.getScriptKey(base, dir.resolve("scripts/a.sql").toString()));
        Assertions.assertEquals("scripts/a.sql", CatalogUtils.getScriptKey(null, "scripts/a.sql"));
        String outside = dir.getParent().resolve("other/a.sql").toAbsolutePath().toString().replace('\\', '/');
        Assertions.assertEquals(outside, CatalogUtils.getScriptKey(base, outside));
        Assertions.assertEquals(".", CatalogUtils.getScriptKey(base, "."));
    }
}
