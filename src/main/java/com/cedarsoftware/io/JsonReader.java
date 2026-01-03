package com.cedarsoftware.io;

/**
 * Legacy class retained only for backward compatibility.
 * <p>
 * The nested interfaces ({@link ClassFactory}, {@link MissingFieldHandler}, {@link JsonClassReader})
 * are deprecated and kept only so that existing code referencing {@code JsonReader.ClassFactory} etc.
 * continues to compile. New code should use the top-level interfaces directly:
 * <ul>
 *   <li>{@code JsonReader.ClassFactory} → use {@link ClassFactory}</li>
 *   <li>{@code JsonReader.MissingFieldHandler} → use {@link MissingFieldHandler}</li>
 *   <li>{@code JsonReader.JsonClassReader} → use {@link JsonClassReader}</li>
 * </ul>
 * <p>
 * For JSON parsing, use {@link JsonIo}:
 * <pre>
 * // Parse JSON to Java objects
 * Person person = JsonIo.toJava(jsonString, readOptions).asClass(Person.class);
 *
 * // Parse JSON to Map-of-Maps
 * Map map = JsonIo.toMaps(jsonString, readOptions).asClass(Map.class);
 * </pre>
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
 *
 * @deprecated This class is no longer used. Use {@link JsonIo} for JSON parsing.
 */
@Deprecated
public class JsonReader {

    /**
     * Private constructor to prevent instantiation.
     */
    private JsonReader() {
        throw new UnsupportedOperationException("JsonReader is deprecated. Use JsonIo instead.");
    }

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.ClassFactory} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface ClassFactory extends com.cedarsoftware.io.ClassFactory {
    }

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.MissingFieldHandler} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface MissingFieldHandler extends com.cedarsoftware.io.MissingFieldHandler {
    }

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.JsonClassReader} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface JsonClassReader<T> extends com.cedarsoftware.io.JsonClassReader<T> {
    }
}