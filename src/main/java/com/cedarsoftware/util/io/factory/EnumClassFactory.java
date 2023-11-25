package com.cedarsoftware.util.io.factory;

import java.util.Optional;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.ReaderContext;

/**
 * @author Ken Partlow (kpartlow@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class EnumClassFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {

        String name = getEnumName(jObj);
        Optional<Class> cls = MetaUtils.getClassIfEnum(c);

        if (!cls.isPresent()) {
            throw new JsonIoException("Unable to load enum: " + c + ", class not found or is not an Enum.");
        }

        if (name != null) {
            return jObj.setFinishedTarget(this.fromString(cls.get(), name), false);
        }

        Object value = jObj.getValue();

        if (value instanceof String) {
            return jObj.setFinishedTarget(this.fromString(c, (String) value), true);
        } else {
            return fromJsonObject(c, jObj);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Enum fromString(Class<?> c, String s) {
        return Enum.valueOf((Class<Enum>) c, s);
    }

    @SuppressWarnings("unchecked")
    protected Object fromJsonObject(Class<?> c, JsonObject job) {
        Optional<Class> cls = MetaUtils.getClassIfEnum(c);
        if (cls.isPresent()) {
            return Enum.valueOf(cls.get(), this.getEnumName(job));
        } else {
            throw new JsonIoException("Unable to load enum: " + c + ", class not found or is not an Enum.");
        }
    }

    protected String getEnumName(JsonObject job) {
        String name = (String) job.get("Enum.name");
        return name != null ? name : (String) job.get("name");
    }
}
