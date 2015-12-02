package com.cedarsoftware.util.io;

import java.lang.reflect.Field;

/**
 * @author Francis UPTON IV (francisu@gmail.com)
 *         <br>
 *         Copyright (c) Talend, Inc.
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

/**
 * Allows the value of the specified {@link Field} to be replaced when reading or writing.
 *
 * @see JsonWriter#CUSTOM_FIELD_REPLACER_MAP
 * @see JsonReader#CUSTOM_FIELD_REPLACER_MAP
 */
public interface FieldReplacer
{
    /**
     * Optionally replace the value of the specified field. If no replacement is desired, simply
     * return the currentValue.
     * @param field the {@link Field} as specified in the {@link JsonReader#CUSTOM_FIELD_REPLACER_MAP} or {@link JsonWriter#CUSTOM_FIELD_REPLACER_MAP}.
     * @param currentValue the current value of the field.
     * @return the replacement value.
     */
    Object replace(Field field, Object currentValue);
}
