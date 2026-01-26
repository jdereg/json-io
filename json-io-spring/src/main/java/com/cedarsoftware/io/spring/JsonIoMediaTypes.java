package com.cedarsoftware.io.spring;

import org.springframework.http.MediaType;

/**
 * Media type constants for json-io supported formats.
 * <p>
 * Provides media types for JSON, JSON5, and TOON formats.
 * </p>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public final class JsonIoMediaTypes {

    private JsonIoMediaTypes() {
        // Prevent instantiation
    }

    /**
     * Standard JSON media type: application/json
     */
    public static final String APPLICATION_JSON_VALUE = "application/json";
    public static final MediaType APPLICATION_JSON = MediaType.APPLICATION_JSON;

    /**
     * JSON5 media type: application/vnd.json5
     * <p>
     * JSON5 extends JSON with features like comments, trailing commas,
     * unquoted keys, and more flexible syntax.
     * </p>
     */
    public static final String APPLICATION_JSON5_VALUE = "application/vnd.json5";
    public static final MediaType APPLICATION_JSON5 = MediaType.parseMediaType(APPLICATION_JSON5_VALUE);

    /**
     * TOON media type: application/vnd.toon
     * <p>
     * TOON (Terse Object Oriented Notation) is an optimized format that
     * reduces token count by 40-50%, ideal for LLM/AI applications.
     * </p>
     */
    public static final String APPLICATION_TOON_VALUE = "application/vnd.toon";
    public static final MediaType APPLICATION_TOON = MediaType.parseMediaType(APPLICATION_TOON_VALUE);

    /**
     * Alternative TOON media type with JSON suffix: application/vnd.toon+json
     */
    public static final String APPLICATION_TOON_JSON_VALUE = "application/vnd.toon+json";
    public static final MediaType APPLICATION_TOON_JSON = MediaType.parseMediaType(APPLICATION_TOON_JSON_VALUE);
}
