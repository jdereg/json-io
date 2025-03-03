package com.cedarsoftware.io;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.util.TypeUtilities;

import static com.cedarsoftware.util.TypeUtilities.hasUnresolvedType;

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
    public static final String ENUM = "@enum";
    public static final String SHORT_TYPE = "@t";
    public static final String SHORT_ITEMS = "@e";
    public static final String SHORT_KEYS = "@k";
    public static final String SHORT_ID = "@i";
    public static final String SHORT_REF = "@r";
    public static final String VALUE = "value";
    Type type = null;
    protected Object target = null;
    protected boolean isFinished = false;
    protected long id = -1L;
    protected Long refId = null;
    protected int line;
    protected int col;

    // Cache for storing whether a Type is fully resolved.
    private static final Map<Type, Boolean> typeResolvedCache = new ConcurrentHashMap<>();

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }

    public boolean isReference() {
        return refId != null;
    }

    public Long getReferenceId() {
        return refId;
    }

    public void setReferenceId(Long id) {
        refId = id;
        isFinished = true;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void setFinished() {
        isFinished = true;
    }

    public Object setTarget(Object target) {
        this.target = target;
        if (target != null) {
            setType(target.getClass());
        }
        return target;
    }

    public Object setFinishedTarget(Object o, boolean isFinished) {
        this.isFinished = isFinished;
        return setTarget(o);
    }

    public Object getTarget() {
        return target;
    }

    abstract public boolean isArray();
    
    public Type getType() {
        return type;
    }

    /**
     * Sets the type on this JsonValue.
     * Uses a cache to avoid repeated resolution checks for the same types.
     */
    public void setType(Type type) {
        if (type == null || type == this.type || type.equals(this.type)) {
            return;
        }
        if (type == Object.class && this.type != null) {
            return;
        }

        // Check cache for previously resolved types
        Boolean isResolved = typeResolvedCache.get(type);
        if (isResolved == null) {
            // Type hasn't been seen before - check if it's resolved
            isResolved = !hasUnresolvedType(type);
            typeResolvedCache.put(type, isResolved);
        }

        if (!isResolved) {
            // Don't allow a TypeVariable of T, V or any other unresolved type to be set.
            // Forces resolution ahead of calling this method.
            throw new JsonIoException("Unresolved type: " + type);
        }

        this.type = type;
    }

    public Class<?> getRawType() {
        return TypeUtilities.getRawClass(type);
    }

    public String getRawTypeName() {
        if (type != null) {
            return TypeUtilities.getRawClass(type).getName();
        }
        return null;
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
    public boolean hasId() {
        return id > 0L;
    }

    void clear() {
        id = -1;
        type = null;
        refId = null;
    }

    /**
     * Do not use this method.  Call getRawTypeName() instead.
     * @deprecated
     */
    @Deprecated
    String getJavaTypeName() {
        return getRawType().getName();
    }

    /**
     * Do not use this method.  Call setType() instead.
     * @deprecated
     */
    @Deprecated
    public void setJavaType(Class<?> type) {
        setType(type);
    }

    /**
     * Do not use this method.  Call getType() or getRawType() instead.
     * @deprecated
     */
    @Deprecated
    public Class<?> getJavaType() {
        return getRawType();
    }
}
