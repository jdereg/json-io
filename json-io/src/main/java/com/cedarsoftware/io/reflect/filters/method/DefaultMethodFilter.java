package com.cedarsoftware.io.reflect.filters.method;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.cedarsoftware.io.reflect.filters.MethodFilter;
import com.cedarsoftware.util.ReflectionUtils;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
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
public class DefaultMethodFilter implements MethodFilter {
    public boolean filter(Class<?> clazz, String methodName) {
        Method m = ReflectionUtils.getMethod(clazz, methodName);
        if (m == null) {
            return false;
        }

        // Only methods with 0 parameters will make it here (null for parameters above filters out methods that have 1+ parameters)
        return Modifier.isStatic(m.getModifiers()) ||                          // filter any static methods
                !Modifier.isPublic(m.getDeclaringClass().getModifiers()) ||     // filter any method on non-public classes
                ((m.getModifiers() & Modifier.PUBLIC) == 0);                    // filter any non-public method
    }
}
