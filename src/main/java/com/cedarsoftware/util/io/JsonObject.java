package com.cedarsoftware.util.io;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.io.factory.DateFactory;
import lombok.Getter;
import lombok.Setter;

/**
 * This class holds a JSON object in a LinkedHashMap.
 * LinkedHashMap used to keep fields in same order as they are
 * when reflecting them in Java.  Instances of this class hold a
 * Map-of-Map representation of a Java object, read from the JSON
 * input stream.
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
public class JsonObject extends LinkedHashMap<Object, Object>
{
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
    
    static Set<String> primitives = new HashSet<>();
    static Set<String> primitiveWrappers = new HashSet<>();

    @Getter
    @Setter
    Object target;
    boolean isMap = false;

    boolean isFinished = false;
    @Getter
    @Setter
    String type;
    @Getter
    long id = -1L;
    /**
     * -- GETTER --
     *
     * @return int line where this object '{' started in the JSON stream
     */
    @Getter
    int line;
    /**
     * -- GETTER --
     *
     * @return int column where this object '{' started in the JSON stream
     */
    @Getter
    int col;

    Integer hash = null;

    static
    {
        primitives.add("boolean");
        primitives.add("byte");
        primitives.add("char");
        primitives.add("double");
        primitives.add("float");
        primitives.add("int");
        primitives.add("long");
        primitives.add("short");

        primitiveWrappers.add("java.lang.Boolean");
        primitiveWrappers.add("java.lang.Byte");
        primitiveWrappers.add("java.lang.Character");
        primitiveWrappers.add("java.lang.Double");
        primitiveWrappers.add("java.lang.Float");
        primitiveWrappers.add("java.lang.Integer");
        primitiveWrappers.add("java.lang.Long");
        primitiveWrappers.add("java.lang.Short");
    }

    public String toString()
    {
        return "mLen:" + getLenientSize() + " type:" + type + " line:" + line + ", col:" + col + " id:" + id;
    }

    /**
     * A JsonObject starts off with an id of -1.  Also, an id of 0 is not considered a valid id.
     * It must be 1 or greater.  JsonWriter utilizes this fact.
     */
    public boolean hasId()
    {
        return id > 0L;
    }

    public boolean isFinished() { return isFinished; }

    public Object setFinishedTarget(Object o, boolean isFinished)
    {
        this.target = o;
        this.isFinished = isFinished;
        return this.target;
    }

    public Class<?> getTargetClass()
    {
        return target.getClass();
    }

    public boolean isLogicalPrimitive()
    {
        if (type == null) {
            return false;
        }
        switch (type)
        {
            case "boolean":
            case "java.lang.Boolean":
            case "double":
            case "java.lang.Double":
            case "long":
            case "java.lang.Long":
            case "byte":
            case "java.lang.Byte":
            case "char":
            case "java.lang.Character":
            case "float":
            case "java.lang.Float":
            case "int":
            case "java.lang.Integer":
            case "short":
            case "java.lang.Short":
            case "date":
            case "java.util.Date":
            case "BigInt":
            case "java.math.BigInteger":
            case "BigDec":
            case "java.math.BigDecimal":
                return true;
            case "class":
            case "java.lang.Class":
                return true;
            default:
                return false;
        }
    }

    public Object getPrimitiveValue()
    {
        final Object value = getValue();
        switch(type)
        {
            case "boolean":
            case "java.lang.Boolean":
            case "double":
            case "java.lang.Double":
            case "long":
            case "java.lang.Long":
                return value;
            case "byte":
            case "java.lang.Byte":
                Number b = (Number) value;
                return b.byteValue();
            case "char":
            case "java.lang.Character":
                String c = (String) value;
                return c.charAt(0);
            case "float":
            case "java.lang.Float":
                Number f = (Number) value;
                return f.floatValue();
            case "int":
            case "java.lang.Integer":
                Number integer = (Number) value;
                return integer.intValue();
            case "short":
            case "java.lang.Short":
                Number s = (Number) value;
                return s.shortValue();
            case "date":
            case "java.util.Date":
                if (value instanceof Long)
                {
                    return new Date((Long)value);
                }
                else if (value instanceof String)
                {
                    return DateFactory.parseDate((String) value);
                }
                else
                {
                    throw new JsonIoException("Unknown date type: " + type);
                }
            case "BigInt":
            case "java.math.BigInteger":
                return Readers.bigIntegerFrom(value);
            case "BigDec":
            case "java.math.BigDecimal":
                return Readers.bigDecimalFrom(value);
            case "class":
            case "java.lang.Class":
                Class<?> clz = MetaUtils.classForName((String)value, JsonObject.class.getClassLoader());
                return clz;
        }

        throw new JsonIoException("Invalid primitive type, line " + line + ", col " + col);
    }

    /**
     * @return boolean true if this object references another object, false otherwise.
     */
    public boolean isReference()
    {
        return containsKey(REF);
    }

    public Long getReferenceId()
    {
        return (Long) get(REF);
    }

    // Map APIs
    public boolean isMap()
    {
        return isMap || target instanceof Map;
    }

    // Collection APIs
    public boolean isCollection()
    {
        if (target instanceof Collection)
        {
            return true;
        }
        if (containsKey(ITEMS) && !containsKey(KEYS))
        {
            return type != null && !type.contains("[");
        }
        return false;
    }

    // Array APIs
    public boolean isArray()
    {
        if (target == null)
        {
            if (type != null)
            {
                return type.contains("[");
            }
            return containsKey(ITEMS) && !containsKey(KEYS);
        }
        return target.getClass().isArray();
    }

    // Return the array that this JSON object wraps.  This is used when there is a Collection class (like ArrayList)
    // represented in the JSON.  This also occurs if a specified array type is used (not Object[], but Integer[], for
    // example).
    public Object[] getArray()
    {
        return (Object[]) get(ITEMS);
    }

    public int getLength()
    {
        Integer items = getLenientSize();
        if (items != null)
        {
            return items;
        }
        throw new JsonIoException("getLength() called on a non-collection, line " + line + ", col " + col);
    }

    private Integer getLenientSize() {
        if (isArray())
        {
            if (target == null)
            {
                Object[] items = (Object[]) get(ITEMS);
                return items == null ? 0 : items.length;
            }
            return Array.getLength(target);
        }
        if (isCollection() || isMap())
        {
            Object[] items = (Object[]) get(ITEMS);
            return items == null ? 0 : items.length;
        }
        return null;
    }

    public Class<?> getComponentType()
    {
        return target.getClass().getComponentType();
    }

    void moveBytesToMate()
    {
        final byte[] bytes = (byte[]) target;
        final Object[] items = getArray();
        final int len = items.length;

        for (int i = 0; i < len; i++)
        {
            bytes[i] = ((Number) items[i]).byteValue();
        }
        hash = null;
    }

    void moveCharsToMate()
    {
        Object[] items = getArray();
        if (items == null)
        {
             target = null;
        }
        else if (items.length == 0)
        {
            target = new char[0];
        }
        else if (items.length == 1)
        {
            String s = (String) items[0];
            target = s.toCharArray();
        }
        else
        {
            throw new JsonIoException("char[] should only have one String in the [], found " + items.length + ", line " + line + ", col " + col);
        }
        hash = null;
    }

    public Object put(Object key, Object value)
    {
        hash = null;
        if (key == null)
        {
            return super.put(null, value);
        }

        if (key.equals(TYPE))
        {
            String oldType = type;
            type = (String) value;
            return oldType;
        }
        else if (key.equals(ID))
        {
            Long oldId = id;
            id = (Long) value;
            return oldId;
        }
        else if ((ITEMS.equals(key) && containsKey(KEYS)) || (KEYS.equals(key) && containsKey(ITEMS)))
        {
            isMap = true;
        }
        return super.put(key, value);
    }

    public Object setValue(Object o)
    {
        return this.put(VALUE, o);
    }

    public Object getValue() {
        return this.get(VALUE);
    }

    public void clear()
    {
        super.clear();
        type = null;
        hash = null;
        id = -1L;
    }

    void clearArray()
    {
        remove(ITEMS);
        hash = null;
    }

    public int size()
    {
        if (containsKey(ITEMS))
        {
            return getArray().length;
        }
        else if (containsKey(REF))
        {
            return 0;
        }

        return super.size();
    }

    private int calculateArrayHash()
    {
        int hashCode = 0;
        Object array = get(ITEMS);
        if (array != null)
        {
            int len = Array.getLength(array);
            for (int j = 0; j < len; j++)
            {
                Object elem = Array.get(array, j);
                hashCode += elem == null ? 0 : elem.hashCode();
            }
        }
        else
        {
            hashCode = super.hashCode();
        }
        return hashCode;
    }

    public int hashCode()
    {
        if (hash == null)
        {
            if (isArray() || isCollection())
            {
                hash = calculateArrayHash();
            }
            else
            {
                hash = keySet().hashCode() + values().hashCode();
            }
        }
        return hash;
    }

    public boolean equals(Object other)
    {
        return this == other;
    }
}
