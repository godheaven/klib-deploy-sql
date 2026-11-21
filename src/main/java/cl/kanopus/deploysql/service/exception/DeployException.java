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
package cl.kanopus.deploysql.service.exception;

import cl.kanopus.deploysql.DeployResult;

/**
 * Thrown by {@link cl.kanopus.deploysql.DeploySQL} when a script fails and {@code continueOnError} is disabled (the default). The partial result is available through {@link #getResult()}.
 *
 * @since 4.20.0
 */
public class DeployException extends Exception {

    private static final long serialVersionUID = 1771546004658286044L;

    private final transient DeployResult result;

    public DeployException(String message, DeployResult result, Throwable cause) {
        super(message, cause);
        this.result = result;
    }

    public DeployResult getResult() {
        return result;
    }
}
