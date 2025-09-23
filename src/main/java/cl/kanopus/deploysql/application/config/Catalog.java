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
package cl.kanopus.deploysql.application.config;

import jakarta.xml.bind.annotation.*;

import java.io.Serializable;
import java.util.List;

@XmlRootElement(name = "catalog")
@XmlAccessorType(XmlAccessType.FIELD)
public class Catalog implements Serializable {

    private static final long serialVersionUID = 4073946422191774978L;

    @XmlElement(name = "database")
    private Database database = null;

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Database implements Serializable {

        private static final long serialVersionUID = -4553896210417615152L;

        @XmlElement(name = "label")
        private String label;

        @XmlElement(name = "scripts")
        private Scripts scripts = null;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Scripts getScripts() {
            return scripts;
        }

        public void setScripts(Scripts scripts) {
            this.scripts = scripts;
        }

        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Scripts implements Serializable {

            private static final long serialVersionUID = 7164562340425337845L;

            @XmlElement(name = "script")
            private List<Script> records;

            public List<Script> getRecords() {
                return records;
            }

            public void setRecords(List<Script> records) {
                this.records = records;
            }

            @XmlAccessorType(XmlAccessType.FIELD)
            public static class Script implements Serializable {

                private static final long serialVersionUID = -6031760111063718871L;

                @XmlValue
                private String filename;
                @XmlAttribute(name = "onetime")
                private Boolean onetime;
                @XmlAttribute(name = "type")
                private String type;
                @XmlAttribute(name = "label")
                private String label;

                public String getType() {
                    return type;
                }

                public void setType(String type) {
                    this.type = type;
                }

                public Boolean getOnetime() {
                    return onetime;
                }

                public void setOnetime(Boolean onetime) {
                    this.onetime = onetime;
                }

                public String getLabel() {
                    return label;
                }

                public void setLabel(String label) {
                    this.label = label;
                }

                public String getFilename() {
                    return filename;
                }

                public void setFilename(String filename) {
                    this.filename = filename;
                }

            }

        }

    }

}
