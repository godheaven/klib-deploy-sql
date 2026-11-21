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
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

@Slf4j
public class CatalogUtils {

    private CatalogUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Locates the catalog file: {@code file} itself, or {@code catalog.xml}, {@code ./catalog.xml} or {@code ../catalog.xml} as fallbacks (see {@link FileUtils#getFile(String...)}).
     */
    public static File resolveCatalogFile(String file) throws FileNotFoundException {
        return FileUtils.getFile(file, "catalog.xml", "./catalog.xml", "../catalog.xml");
    }

    public static Catalog loadCatalog(String file) throws Exception {
        return loadCatalog(resolveCatalogFile(file));
    }

    /**
     * Validates the catalog against {@code catalog.xsd} and unmarshals it. DTDs and external entities are rejected (XXE protection).
     *
     * @since 4.20.0
     */
    public static Catalog loadCatalog(File xml) throws IOException, SAXException, JAXBException, XMLStreamException {
        log.debug("catalog.xml loaded successfully with path: {}", xml.getAbsolutePath());
        CatalogUtils.validateXMLSchema(xml);

        Unmarshaller jaxbUnmarshaller = JAXBContext.newInstance(Catalog.class).createUnmarshaller();
        try (InputStream in = Files.newInputStream(xml.toPath())) {
            XMLStreamReader reader = secureInputFactory().createXMLStreamReader(in);
            try {
                return (Catalog) jaxbUnmarshaller.unmarshal(reader);
            } finally {
                reader.close();
            }
        }
    }

    private static XMLInputFactory secureInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    public static void validateXMLSchema(File xml) throws SAXException, IOException {
        log.info("Starting XML validation against XSD schema.");

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // Prevent XXE: no external DTDs or schemas may be fetched while compiling the schema
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        // 1) Open catalog.xsd from classpath (into de JAR)
        try (InputStream xsdIn = CatalogUtils.class.getResourceAsStream("/catalog.xsd")) {
            if (xsdIn == null) {
                throw new IOException("catalog.xsd not found on classpath");
            }

            // Compile the schema
            Schema schema = factory.newSchema(new StreamSource(xsdIn));
            // Validate the XML; the validator must not resolve external DTDs/entities either
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            try (InputStream in = Files.newInputStream(xml.toPath())) {
                validator.validate(new StreamSource(in, xml.toURI().toString()));
            }
        }
    }

    /**
     * Base name of a script path (legacy key used up to 4.19.x).
     */
    public static String getName(String filename) {
        filename = filename.replaceAll("[/\\\\]+", "/");
        int lastPath = filename.lastIndexOf("/");
        if (lastPath != -1) {
            filename = filename.substring(lastPath + 1);
        }
        return filename;
    }

    /**
     * Key under which a script is tracked: its path as declared in the catalog, with separators normalized to {@code /} and {@code .}/{@code ..} segments resolved. Absolute paths located under the catalog directory are made relative to it, so the key does not depend on where the project is checked out.
     *
     * @param catalogDirectory
     *            directory of the catalog file (may be {@code null})
     * @param filename
     *            script path as declared in the catalog
     * @since 4.20.0
     */
    public static String getScriptKey(File catalogDirectory, String filename) {
        String path = filename.trim().replaceAll("[/\\\\]+", "/");
        try {
            Path scriptPath = Paths.get(path).normalize();
            if (scriptPath.isAbsolute() && catalogDirectory != null) {
                Path base = catalogDirectory.toPath().toAbsolutePath().normalize();
                if (scriptPath.startsWith(base)) {
                    scriptPath = base.relativize(scriptPath);
                }
            }
            String normalized = scriptPath.toString().replace('\\', '/');
            return normalized.isEmpty() ? path : normalized;
        } catch (InvalidPathException ex) {
            return path;
        }
    }
}
