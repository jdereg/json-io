package com.cedarsoftware.io;

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
 *         limitations under the License.
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
    protected Class<?> javaType = null;
    protected Class<?> hintType = null;
    protected Object target = null;
    protected boolean isFinished = false;
    protected long id = -1L;
    protected Long refId = null;
    protected int line;

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }

    protected int col;

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
        if (target != null) {
            this.javaType = target.getClass();
        }
    }

    public Object setFinishedTarget(Object o, boolean isFinished) {
        this.target = o;
        this.javaType = o.getClass();
        this.isFinished = isFinished;
        return this.target;
    }

    public Object getTarget() {
        return target;
    }

    abstract public boolean isArray();
    
    public Class<?> getJavaType() {
        return javaType == null ? hintType : javaType;
    }

    public void setJavaType(Class<?> type) {
        javaType = type;
        if (hintType == null) {
            hintType = type;
        }
    }

    public void setTypeSafely(Class<?> type) {
        // Rule 1: If the passed type is Object.class or null, do nothing and return.
        if (type == null || type == Object.class || target != null) {
            return;
        }

        // Rule 2: If both hintType and javaType are null, set hintType to type and return.
        if (hintType == null && javaType == null) {
            hintType = type;
            return;
        }

        // Rule 3: If hintType is not null and javaType is null,
        // determine the "more derived" type and set javaType and hintType accordingly.
        if (hintType != null && javaType == null) {
            if (isMoreDerivedThan(type, hintType)) {
                javaType = type;
            } else {
                javaType = hintType;
                hintType = type;
            }
            return;
        }

        // Additional rule: Ensure javaType is always the most derived type
        // and hintType the next most derived if all are non-null.
        if (hintType != null && javaType != null) {
            Class<?> mostDerived = getMostDerived(type, hintType, javaType);
            Class<?> middleDerived = getMiddleDerived(type, hintType, javaType, mostDerived);

            javaType = mostDerived;
            hintType = middleDerived;
        }
    }

    private boolean isMoreDerivedThan(Class<?> candidate, Class<?> current) {
        if (candidate.isArray() && current.isArray()) {
            return candidate.getComponentType().isAssignableFrom(current.getComponentType());
        }
        return candidate.isAssignableFrom(current);
    }

    private Class<?> getMostDerived(Class<?> a, Class<?> b, Class<?> c) {
        if (isMoreDerivedThan(a, b) && isMoreDerivedThan(a, c)) {
            return a;
        } else if (isMoreDerivedThan(b, c)) {
            return b;
        } else {
            return c;
        }
    }

    private Class<?> getMiddleDerived(Class<?> a, Class<?> b, Class<?> c, Class<?> mostDerived) {
        if (mostDerived == a) {
            return isMoreDerivedThan(b, c) ? b : c;
        } else if (mostDerived == b) {
            return isMoreDerivedThan(a, c) ? a : c;
        } else {
            return isMoreDerivedThan(a, b) ? a : b;
        }
    }

    public void setHintType(Class<?> type) {
        this.hintType = type;
    }

    public String getJavaTypeName() {
        Class<?> type = getJavaType();
        if (type == null) {
            return null;
        }
        return type.getName();
    }

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

    void clear()
    {
        id = -1;
        javaType = null;
        refId = null;
    }
}
