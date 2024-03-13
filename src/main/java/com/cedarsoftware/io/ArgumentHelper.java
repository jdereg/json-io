package com.cedarsoftware.io;

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
public class ArgumentHelper {
    /**
     * @param setting Object setting value from JsonWriter args map.
     * @return boolean true if the value is (boolean) true, Boolean.TRUE, "true" (any case), or non-zero if a Number.
     */
    public static boolean isTrue(Object setting)
    {
        if (setting instanceof Boolean)
        {
            return Boolean.TRUE.equals(setting);
        }

        if (setting instanceof String)
        {
            return "true".equalsIgnoreCase((String) setting);
        }

        if (setting instanceof Number)
        {
            return ((Number)setting).intValue() != 0;
        }

        return false;
    }

    public static Number getNumberWithDefault(Object o, Number def) {
        return o == null ? def : (Number) o;
    }

}
