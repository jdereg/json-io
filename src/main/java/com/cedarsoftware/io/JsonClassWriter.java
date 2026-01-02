package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;

/**
 * Implement this interface to customize the JSON output for a given class.
 * <p>
 * Register your custom writer using {@link WriteOptionsBuilder#addCustomWrittenClass(Class, JsonClassWriter)}.
 *
 * @param <T> the type of object this writer handles
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
public interface JsonClassWriter<T> {
    /**
     * Write the object in JSON format to the output Writer.
     *
     * @param o        Object to be written in JSON format
     * @param showType boolean indicating whether to include @type in the output
     * @param output   Writer destination where the JSON is written
     * @param context  WriterContext providing access to WriteOptions and other write utilities
     * @throws IOException if an I/O error occurs during writing
     */
    default void write(T o, boolean showType, Writer output, WriterContext context) throws IOException {
    }

    /**
     * Indicates whether this class has a primitive (non-object) JSON form.
     * Most custom writers will not have a primitive form, so the default is false.
     *
     * @param context WriterContext providing access to WriteOptions
     * @return true if this class can be written as a JSON primitive value
     */
    default boolean hasPrimitiveForm(WriterContext context) {
        return false;
    }

    /**
     * Write the object in its primitive JSON form (without surrounding braces).
     * Only called if {@link #hasPrimitiveForm(WriterContext)} returns true.
     *
     * @param o       Object to be written
     * @param output  Writer destination where the JSON is written
     * @param context WriterContext providing access to WriteOptions and writing tools
     * @throws IOException if an I/O error occurs during writing
     */
    default void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
    }

    /**
     * Get the type name to use for @type when writing this object.
     * Override to provide a custom type name.
     *
     * @param o the object being written
     * @return the type name string (defaults to the object's class name)
     */
    default String getTypeName(Object o) {
        return o.getClass().getName();
    }
}
