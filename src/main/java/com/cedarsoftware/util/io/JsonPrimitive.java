package com.cedarsoftware.util.io;

/**
 * This class holds a JSON primitive.  The object stored within is one of the JSON primitives, namely boolean,
 * double, long, String, null.
 *
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
public class JsonPrimitive extends JsonValue {
    private final Object target;

    JsonPrimitive(Object target) {
        this.target = target;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        JsonPrimitive other = (JsonPrimitive) obj;
        if (target == null) {
            return other.target == null;
        } else {
            return target.equals(other.target);
        }
    }

    public int hashCode() {
        return target != null ? target.hashCode() : 0;
    }

    public boolean isJsonPrimitive() {
        return true;
    }

    // Share Java value for JSON value for primitives
    public Object getValue() {
        return target;
    }
}
