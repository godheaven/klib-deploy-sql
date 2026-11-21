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

import java.sql.SQLException;

/**
 * Thrown when the deploy lock (table {@code CATALOG_SCRIPT_SQL_LOCK}) is held by another deployment and could not be
 * acquired within the configured wait timeout.
 */
public class DeployLockException extends SQLException {

    private static final long serialVersionUID = -2311097411830911571L;

    public DeployLockException(String message) {
        super(message);
    }
}
