package com.cedarsoftware.io;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
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
    // Uses bounded LRU cache to prevent memory leaks in long-running applications
    private static volatile int maxCacheSize = 1000; // Default, can be configured
    private static final Map<Type, Boolean> typeResolvedCache = new ConcurrentHashMap<Type, Boolean>() {
        @Override
        public Boolean put(Type key, Boolean value) {
            if (size() >= maxCacheSize) {
                // Simple eviction strategy - clear cache when it gets too large
                // This prevents unbounded growth while maintaining performance benefits
                clear();
            }
            return super.put(key, value);
        }
    };

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
        // Fix NPE vulnerability - check null first before any operations
        if (type == null) {
            return;
        }
        
        if (type == this.type || type.equals(this.type)) {
            return;
        }
        if (type == Object.class && this.type != null) {
            return;
        }

        // Fix race condition - use computeIfAbsent for atomic check-and-put
        Boolean isResolved = typeResolvedCache.computeIfAbsent(type, t -> !hasUnresolvedType(t));

        if (!isResolved) {
            // Don't allow a TypeVariable of T, V or any other unresolved type to be set.
            // Forces resolution ahead of calling this method.
            throw new JsonIoException("Unresolved type: " + type);
        }

        this.type = type;
    }

    public Class<?> getRawType() {
        if (type == null) {
            return null;
        }
        return TypeUtilities.getRawClass(type);
    }

    public String getRawTypeName() {
        if (type == null) {
            return null;
        }
        Class<?> rawClass = TypeUtilities.getRawClass(type);
        return rawClass != null ? rawClass.getName() : null;
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
     * @deprecated This method will be removed in a future version. Use getRawTypeName() instead.
     */
    @Deprecated
    String getJavaTypeName() {
        Class<?> rawType = getRawType();
        return rawType != null ? rawType.getName() : null;
    }

    /**
     * Do not use this method.  Call setType() instead.
     * @deprecated This method will be removed in a future version. Use setType() instead.
     */
    @Deprecated
    public void setJavaType(Class<?> type) {
        setType(type);
    }

    /**
     * Do not use this method.  Call getType() or getRawType() instead.
     * @deprecated This method will be removed in a future version. Use getRawType() instead.
     */
    @Deprecated
    public Class<?> getJavaType() {
        return getRawType();
    }

    /**
     * Sets the maximum size of the type resolution cache.
     * This affects all JsonValue instances as the cache is static.
     * 
     * @param cacheSize int maximum number of entries to cache. Must be at least 1.
     * @throws JsonIoException if cacheSize is less than 1
     */
    public static void setMaxTypeResolutionCacheSize(int cacheSize) {
        if (cacheSize < 1) {
            throw new JsonIoException("Type resolution cache size must be at least 1, value: " + cacheSize);
        }
        maxCacheSize = cacheSize;
        // Clear existing cache to apply new size limit immediately
        typeResolvedCache.clear();
    }

    /**
     * Gets the current maximum size of the type resolution cache.
     * 
     * @return int current maximum cache size
     */
    public static int getMaxTypeResolutionCacheSize() {
        return maxCacheSize;
    }

    /**
     * Gets the current number of entries in the type resolution cache.
     * 
     * @return int current cache size
     */
    public static int getTypeResolutionCacheSize() {
        return typeResolvedCache.size();
    }

    /**
     * Clears the type resolution cache.
     * This may be useful for memory management in long-running applications.
     */
    public static void clearTypeResolutionCache() {
        typeResolvedCache.clear();
    }
}
