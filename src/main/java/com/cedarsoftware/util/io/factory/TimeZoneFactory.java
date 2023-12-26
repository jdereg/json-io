package com.cedarsoftware.util.io.factory;

import java.util.TimeZone;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

/**
 * Abstract class to help create temporal items.
 * <p>
 * All custom writers for json-io subclass this class.  Special writers are not needed for handling
 * user-defined classes.  However, special writers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 * <p>
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
public class TimeZoneFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {

        Object value = jObj.getValue();
        if (value != null) {
            return fromString(jObj, (String) value);
        }

        String zone = (String) jObj.get("zone");
        if (zone != null) {
            return fromString(jObj, (zone));
        }

        throw new JsonIoException("java.util.TimeZone missing 'value' field");
    }

    private Object fromString(JsonObject job, String value) {
        return job.setFinishedTarget(TimeZone.getTimeZone(value), true);
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
