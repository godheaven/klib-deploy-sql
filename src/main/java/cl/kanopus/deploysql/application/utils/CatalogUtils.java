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

import cl.kanopus.common.util.FileUtils;
import cl.kanopus.deploysql.application.config.Catalog;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class CatalogUtils {


    private CatalogUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Catalog loadCatalog(String file) throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance(Catalog.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

        File xml = FileUtils.getFile(file, "catalog.xml", "./catalog.xml", "../catalog.xml");
        log.debug("catalog.xml loaded successfully with path: {}", xml.getAbsolutePath());

        CatalogUtils.validateXMLSchema(xml);
        return (Catalog) jaxbUnmarshaller.unmarshal(xml);
    }

    public static void validateXMLSchema(File xml) throws SAXException, IOException {
        log.info("Starting XML validation against XSD schema.");

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // Prevent XXE by disabling DOCTYPE and external entities
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        // 1) Open catalog.xsd from classpath (into de JAR)
        try (InputStream xsdIn = CatalogUtils.class.getResourceAsStream("/catalog.xsd")) {
            if (xsdIn == null) {
                throw new IOException("catalog.xsd not found on classpath");
            }

            // Compile the schema
            Schema schema = factory.newSchema(new StreamSource(xsdIn));
            // Validate the XML
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xml));
        }

    }

    public static String getName(String filename) {
        filename = filename.replaceAll("[/\\\\]+", "/");
        int lastPath = filename.lastIndexOf("/");
        if (lastPath != -1) {
            filename = filename.substring(lastPath + 1);
        }
        return filename;

    }

}
