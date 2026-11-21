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

import java.util.regex.Pattern;

/**
 * Helpers to log JDBC urls without leaking credentials.
 */
public final class JdbcUrlUtils {

    private static final String MASK = "****";

    /** {@code password=...}, {@code pwd=...}, {@code passwd=...} parameters (any separator: ; & ? :). */
    private static final Pattern SECRET_PARAMETER = Pattern.compile("(?i)((?:password|passwd|pwd|secret|token|accesstoken|sslpassword|sslkey)\\s*=\\s*)([^;&]*)");

    /** Oracle style {@code jdbc:oracle:thin:user/password@host}. */
    private static final Pattern ORACLE_CREDENTIALS = Pattern.compile("(?i)(jdbc:oracle:[a-z0-9]+:)([^/@]*)/([^@]*)@");

    /** Userinfo in url authority, e.g. {@code jdbc:postgresql://user:password@host/db}. */
    private static final Pattern URL_USERINFO = Pattern.compile("(//[^/@:;?]*):([^@/]*)@");

    private JdbcUrlUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Returns the url with passwords and similar secrets replaced by {@code ****}.
     */
    public static String mask(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String masked = SECRET_PARAMETER.matcher(jdbcUrl).replaceAll("$1" + MASK);
        masked = ORACLE_CREDENTIALS.matcher(masked).replaceAll("$1$2/" + MASK + "@");
        return URL_USERINFO.matcher(masked).replaceAll("$1:" + MASK + "@");
    }
}
