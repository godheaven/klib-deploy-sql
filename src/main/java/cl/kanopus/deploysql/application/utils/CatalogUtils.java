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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;

public class CatalogUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogUtils.class);

    private CatalogUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Catalog loadCatalog(String file) throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance(Catalog.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        try {
            File xsd = FileUtils.getFile("catalog.xsd", "./catalog.xsd", "../catalog.xsd");
            LOGGER.debug("catalog.xsd loaded successfull");
            File xml = FileUtils.getFile(file, "catalog.xml", "./catalog.xml", "../catalog.xml");
            LOGGER.debug("catalog.xml loaded successfull");

            CatalogUtils.validateXMLSchema(xsd, xml);
            return (Catalog) jaxbUnmarshaller.unmarshal(xml);
        } catch (Exception ex) {
            LOGGER.error("Error trying to loading catalog.xml file", ex);
            throw ex;
        }

    }

    public static void validateXMLSchema(File xsd, File xml) throws SAXException, IOException {

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // Previene XXE al deshabilitar DOCTYPE y entidades externas
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Schema schema = factory.newSchema(xsd);
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(xml));

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
