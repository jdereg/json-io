package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;

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
 *         limitations under the License.*
 */
public abstract class FieldFilter {
    private final int hashCode;

    protected FieldFilter() {
        this.hashCode = getClass().getName().hashCode() + 97;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * returns true if you want to filter the given field
     *
     * @param field - field we're checking.
     * @return true to filter the field, false to keep the field.
     */
    public abstract boolean filter(Field field);
}
