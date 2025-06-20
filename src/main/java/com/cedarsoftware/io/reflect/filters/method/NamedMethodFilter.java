package com.cedarsoftware.io.reflect.filters.method;

import com.cedarsoftware.io.reflect.filters.MethodFilter;

/**
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
public class NamedMethodFilter implements MethodFilter {
    private final Class<?> clazz;
    private final String methodName;

    public NamedMethodFilter(Class<?> clazz, String methodName) {
        this.clazz = clazz;
        this.methodName = methodName;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Safely handles {@code null} arguments by returning {@code false}.
     */
    public boolean filter(Class<?> clazz, String methodName) {
        if (clazz == null || methodName == null) {
            return false;
        }
        return this.clazz.equals(clazz) && this.methodName.equals(methodName);
    }
}
