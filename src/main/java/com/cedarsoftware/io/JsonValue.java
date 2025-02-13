package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

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
    protected Class<?> javaType = null;
    protected Type fullType = null;
    protected Object target = null;
    protected boolean isFinished = false;
    protected long id = -1L;
    protected Long refId = null;
    protected int line;
    protected int col;

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
            setJavaType(target.getClass());
            // If fullType has not already been set, default it to the target's class.
            if (this.fullType == null) {
                this.fullType = target.getClass();
            }
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
    
    public Class<?> getJavaType() {
        return javaType;
    }

    public void setJavaType(Class<?> type) {
        javaType = type;
    }

    public Type getFullType() {
        return fullType;
    }

    public void setFullType(Type type) {
        fullType = type;
        // For backward compatibility during the migration, set the legacy fields
        if (type != null) {
            if (this.javaType == null) {
                Class<?> raw = extractRawClass(type);
                this.javaType = raw;
            }
        }
    }

    String getJavaTypeName() {
        // Then try javaType (resolved class)
        if (javaType != null) {
            return javaType.getName();
        }

        return extractRawClass(fullType).getName();
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

    /**
     * Centralized method that extracts the raw Class from a given Type.
     */
    public static Class<?> extractRawClass(Type type) {
        if (type instanceof Class<?>) {
            // Simple non-generic type.
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            // For something like List<String>, return List.class.
            ParameterizedType pType = (ParameterizedType) type;
            Type rawType = pType.getRawType();
            if (rawType instanceof Class<?>) {
                return (Class<?>) rawType;
            } else {
                // This is unexpected, but could happen in some corner cases.
                return null;
            }
        } else if (type instanceof GenericArrayType) {
            // For a generic array type (e.g., T[] or List<String>[]),
            // first get the component type, then build an array class.
            GenericArrayType arrayType = (GenericArrayType) type;
            Type componentType = arrayType.getGenericComponentType();
            Class<?> componentClass = extractRawClass(componentType);
            if (componentClass != null) {
                // Create an array instance with length 0 and get its class.
                return Array.newInstance(componentClass, 0).getClass();
            }
            return null;
        } else if (type instanceof WildcardType) {
            // For wildcard types like "? extends Number", use the first upper bound.
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds != null && upperBounds.length > 0) {
                return extractRawClass(upperBounds[0]);
            }
            return null;
        } else if (type instanceof TypeVariable) {
            // For type variables (like T), you might want to pick the first bound.
            // (Often, T's bound is Object if no explicit bound is specified.)
            TypeVariable<?> typeVar = (TypeVariable<?>) type;
            Type[] bounds = typeVar.getBounds();
            if (bounds != null && bounds.length > 0) {
                return extractRawClass(bounds[0]);
            }
            return Object.class;
        } else {
            // Unknown type – you might log or throw an error here.
            return null;
        }
    }

    public static Type extractArrayComponentType(Type type) {
        if (type == null) {
            return null;
        }
        if (type instanceof GenericArrayType) {
            return ((GenericArrayType) type).getGenericComponentType();
        } else if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (cls.isArray()) {
                return cls.getComponentType();
            }
        }
        // If it is not an array type, return null to indicate no component type.
        return null;
    }
}
