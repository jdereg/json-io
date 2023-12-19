package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.ReaderContext;

import static com.cedarsoftware.util.io.MetaUtils.convert;

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
 *         limitations under the License.*
 */
public abstract class ConvertableFactory implements JsonReader.ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object value = jObj.getValue();
        if (value instanceof JsonObject) {
            do {
                // Allow for {@type:long, value:{@type:int, value:3}}  (and so on...)
                JsonObject jsonObject = (JsonObject) value;
                if (!jsonObject.hasValue()) {
                    throw new JsonIoException("Unknown JSON {}, expecting single value type. Line: " + jsonObject.getLine() + ", col: " + jsonObject.getCol());
                }
                value = jsonObject.getValue();
            } while (value instanceof JsonObject);
        }
        return convert(getType(), value);
    }

    public abstract Class<?> getType();
    
    /**
     * @return true.  Strings are always immutable, final.
     */
    public boolean isObjectFinal() {
        return true;
    }
}
