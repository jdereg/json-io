package com.cedarsoftware.io.reflect.filters.field;

import java.lang.reflect.Field;

import com.cedarsoftware.io.reflect.filters.FieldFilter;

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
public class ModifierMaskFilter implements FieldFilter {

    private final int mask;

    public ModifierMaskFilter(int mask) {
        this.mask = mask;
    }

    public boolean filter(Field field) {
        return (field.getModifiers() & mask) != 0;
    }
}
