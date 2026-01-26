package com.cedarsoftware.io;

import com.cedarsoftware.util.ClassUtilities;

/**
 * Implement this interface to create custom instances of classes during JSON deserialization.
 * Your factory will be called when json-io encounters an instance of a class you register
 * with {@link ReadOptionsBuilder#addClassFactory(Class, ClassFactory)}.
 * <p>
 * Use the passed in {@link JsonObject} to source values for constructing your class instance.
 * This is useful for classes that require special construction logic, such as immutable objects,
 * classes with private constructors, or classes that need specific initialization.
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
public interface ClassFactory {
    /**
     * Implement this method to return a new instance of the passed in Class. Use the passed
     * in JsonObject to supply values to the construction of the object.
     *
     * @param c        Class of the object that needs to be created
     * @param jObj     JsonObject containing the JSON data (if primitive type use jObj.getValue())
     * @param resolver Resolver instance that has references to ID Map, Converter, ReadOptions
     * @return a new instance of the class. If you fill the new instance using the value(s)
     *         from the JsonObject and no further work is needed for construction, then
     *         override the {@link #isObjectFinal()} method and return true.
     */
    default Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        return ClassUtilities.newInstance(resolver.getConverter(), c, jObj);
    }

    /**
     * @return true if this object is instantiated and completely filled using the contents
     *         from the JsonObject. In this case, no further processing will be performed on
     *         the instance. If the object has sub-objects (complex fields), then return false
     *         so that json-io will continue filling out the remaining portion of the object.
     */
    default boolean isObjectFinal() {
        return false;
    }
}
