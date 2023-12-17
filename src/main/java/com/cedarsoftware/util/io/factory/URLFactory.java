package com.cedarsoftware.util.io.factory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

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
public class URLFactory implements JsonReader.ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object value = jObj.getValue();
        try {
            if (value instanceof String) {
                URI uri = URI.create((String) value);
                return uri.toURL();
            }

            return createUrlOldWay(jObj);
        }
        catch (MalformedURLException e) {
            String msg = value == null ? "null" : value.toString();
            throw new JsonIoException("java.net.URL malformed URL: " + msg, e);
        }
    }

    URL createUrlOldWay(JsonObject jObj) throws MalformedURLException {
        String protocol = (String) jObj.get("protocol");
        String host = (String) jObj.get("host");
        String file = (String) jObj.get("file");
        String authority = (String) jObj.get("authority");
        String ref = (String) jObj.get("ref");
        Long port = (Long) jObj.get("port");

        StringBuilder builder = new StringBuilder(protocol + ":");
        if (!protocol.equalsIgnoreCase("jar")) {
            builder.append("//");
        }
        if (authority != null && !authority.isEmpty()) {
            builder.append(authority);
        } else {
            if (host != null && !host.isEmpty()) {
                builder.append(host);
            }
            if (!port.equals(-1L)) {
                builder.append(":" + port);
            }
        }
        if (file != null && !file.isEmpty()) {
            builder.append(file);
        }
        if (ref != null && !ref.isEmpty()) {
            builder.append("#" + ref);
        }
        return new URL(builder.toString());
    }

    /**
     * @return true.  UUIDs are always immutable, final.
     */
    public boolean isObjectFinal() {
        return true;
    }
}