package com.cedarsoftware.util.io;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class holds a JSON object in a LinkedHashMap.
 * LinkedHashMap used to keep fields in same order as they are
 * when reflecting them in Java.  Instances of this class hold a
 * Map-of-Map representation of a Java object, read from the JSON
 * input stream.
 *
 * @param <K> field name in Map-of-Map
 * @param <V> Value
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class JsonObject<K, V> extends LinkedHashMap<K, V>
{
    public static final String KEYS = "@keys";
    public static final String ITEMS = "@items";
    public static final String ID = "@id";
    public static final String REF = "@ref";
    public static final String TYPE = "@type";
    static Set<String> primitives = new HashSet<>();
    static Set<String> primitiveWrappers = new HashSet<>();

    Object target;
    boolean isMap = false;
    String type;
    long id = -1;
    int line;
    int col;

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
        return "mLen:" + getLenientSize() + " type:" + type + " l,c:" + line + "," + col + " id:" + id;
    }


    public long getId()
    {
        return id;
    }

    public boolean hasId()
    {
        return id != -1;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getType()
    {
        return type;
    }

    public Object getTarget()
    {
        return target;
    }

    public void setTarget(Object target)
    {
        this.target = target;
    }

    public Class getTargetClass()
    {
        return target.getClass();
    }

    public boolean isLogicalPrimitive()
    {
        return primitiveWrappers.contains(type) || primitives.contains(type) || "date".equals(type) ||
                "java.math.BigInteger".equals(type) || "java.math.BigDecimal".equals(type);
    }

    public Object getPrimitiveValue()
    {
        if ("boolean".equals(type) || "double".equals(type) || "long".equals(type))
        {
            return get("value");
        }
        else if ("byte".equals(type))
        {
            Number b = (Number) get("value");
            return b.byteValue();
        }
        else if ("char".equals(type))
        {
            String c = (String) get("value");
            return c.charAt(0);
        }
        else if ("float".equals(type))
        {
            Number f = (Number) get("value");
            return f.floatValue();
        }
        else if ("int".equals(type))
        {
            Number integer = (Number) get("value");
            return integer.intValue();
        }
        else if ("short".equals(type))
        {
            Number s = (Number) get("value");
            return s.shortValue();
        }
        else if ("date".equals(type))
        {
            Object date = get("value");
            if (date instanceof Long)
            {
                return new Date((Long)(date));
            }
            else if (date instanceof String)
            {
                return Readers.DateReader.parseDate((String) date);
            }
            else
            {
                throw new JsonIoException("Unknown date type: " + type);
            }
        }
        else if ("java.math.BigInteger".equals(type))
        {
            Object value = get("value");
            return Readers.bigIntegerFrom(value);
        }
        else if ("java.math.BigDecimal".equals(type))
        {
            Object value = get("value");
            return Readers.bigDecimalFrom(value);
        }
        else
        {
            throw new JsonIoException("Invalid primitive type, line " + line + ", col " + col);
        }
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

    public Class getComponentType()
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
    }

    public V put(K key, V value)
    {
        if (key == null)
        {
            return super.put(null, value);
        }

        if (key.equals(TYPE))
        {
            String oldType = type;
            type = (String) value;
            return (V) oldType;
        }
        else if (key.equals(ID))
        {
            Long oldId = id;
            id = (Long) value;
            return (V) oldId;
        }
        else if ((ITEMS.equals(key) && containsKey(KEYS)) || (KEYS.equals(key) && containsKey(ITEMS)))
        {
            isMap = true;
        }
        return super.put(key, value);
    }

    public void clear()
    {
        super.clear();
        type = null;
    }

    void clearArray()
    {
        remove(ITEMS);
    }

    /**
     * @return int line where this object '{' started in the JSON stream
     */
    public int getLine()
    {
        return line;
    }

    /**
     * @return int column where this object '{' started in the JSON stream
     */
    public int getCol()
    {
        return col;
    }

    public int size()
    {
        if (containsKey(ITEMS))
        {
            Object value = get(ITEMS);
            if (value instanceof Object[])
            {
                return ((Object[])value).length;
            }
            else if (value == null)
            {
                return 0;
            }
            else
            {
                throw new JsonIoException("JsonObject with @items, but no array [] associated to it, line " + line + ", col " + col);
            }
        }
        else if (containsKey(REF))
        {
            return 0;
        }

        return super.size();
    }
}
