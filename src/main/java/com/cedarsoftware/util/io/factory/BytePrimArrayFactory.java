package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

import java.util.Base64;

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
public class BytePrimArrayFactory extends ArrayFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {

        Object o = jObj.getValue();

        if (o instanceof String) {
            return buildFromString(jObj);
        }
        
        return buildFromArray(jObj);
    }

    public Object buildFromArray(JsonObject jObj) {
        Object[] items = jObj.getArray();

        if (items == null) {
            jObj.setTarget(null);
        } else {
            int len = items.length;
            byte[] bytes = new byte[len];

            for (int i = 0; i < len; i++) {
                bytes[i] = (byte) ((items[i] == null) ? null : convert(byte.class, items[i]));
            }
            jObj.setTarget(bytes);
        }

        return jObj.getTarget();
    }

    public Object buildFromString(JsonObject jObj) {
        Object o = jObj.getValue();
        jObj.setTarget(Base64.getDecoder().decode((String) o));
        return jObj.getTarget();
    }

    public boolean isObjectFinal() {
        return true;
    }


    public Class<?> getType() {
        return byte[].class;
    }
}
