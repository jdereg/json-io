package com.cedarsoftware.util.io;

import lombok.Getter;

/**
 * This class is the parent class for all parsed JSON objects, arrays, or primitive values.
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
public abstract class JsonValue {
    public static final String KEYS = "@keys";
    public static final String ITEMS = "@items";
    public static final String ID = "@id";
    public static final String REF = "@ref";
    public static final String TYPE = "@type";
    public static final String SHORT_TYPE = "@t";
    public static final String SHORT_ITEMS = "@e";
    public static final String SHORT_KEYS = "@k";
    public static final String SHORT_ID = "@i";
    public static final String SHORT_REF = "@r";
    public static final String VALUE = "value";
//    Class<?> type;
    Object target;
    boolean isFinished = false;
    long id = -1L;
    Long refId = null;
    @Getter
    int line;
    @Getter
    int col;

    public boolean isReference() {
        return refId != null;
    }

    public Long getReferenceId() {
        return refId;
    }

    public void setReferenceId(Long id) {
        refId = id;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void setFinished() {
        isFinished = true;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public Object setFinishedTarget(Object o, boolean isFinished) {
        this.target = o;
        this.isFinished = isFinished;
        return this.target;
    }

    public Object getTarget() {
        return target;
    }

    public boolean isArray() {
        return false;
    }

    public boolean isJsonObject() {
        return false;
    }
    public boolean isJsonArray() {
        return false;
    }

    public boolean isJsonPrimitive() {
        return false;
    }
    
//    public Class<?> getType() {
//        return type;
//    }
//
//    public void setType(Class<?> type) {
//        this.type = type;
//    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    /**
     * A JsonObject starts off with an id of -1.  Also, an id of 0 is not considered a valid id.
     * It must be 1 or greater.  JsonWriter utilizes this fact.
     */
    public boolean hasId()
    {
        return id > 0L;
    }
}
