package com.cedarsoftware.io;

import java.io.IOException;
import java.util.Map;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
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
public interface WriterContext {

    /**
     * Gets the write options for the current serialization
     * @return WriteOptions
     */
    WriteOptions getWriteOptions();

    /**
     * Allows you to use the current JsonWriter to write an object out.
     */
    void writeObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException;

    /**
     * Write any object fully.
     */
    void writeImpl(Object obj, boolean showType) throws IOException;

    /**
     * Provide access to all objects that are referenced
     */
    Map<Object, Long> getObjsReferenced();
}
