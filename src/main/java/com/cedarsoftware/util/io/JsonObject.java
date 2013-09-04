package com.cedarsoftware.util.io;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

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
 *         <br/>
 *         Copyright (c) John DeRegnaucourt
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class JsonObject<K, V> extends LinkedHashMap<K, V>
{
    Object target;
    boolean isMap = false;
    String type;
    long id = -1;
    long pos = -1;   // Parse position of Object (used for error messages)

    public long getId()
    {
        return id;
    }

    public boolean hasId()
    {
        return id != -1;
    }

    void setType(String type)
    {
        this.type = type.intern();
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

    public boolean isPrimitive()
    {
        return ("int".equals(type) || "long".equals(type) || "boolean".equals(type) || "double".equals(type) ||
                "byte".equals(type) || "short".equals(type) || "float".equals(type) || "char".equals(type));
    }

    public static boolean isPrimitiveWrapper(Class c)
    {
        return Integer.class.equals(c) || Long.class.equals(c) || Boolean.class.equals(c) || Double.class.equals(c) ||
                Byte.class.equals(c) || Short.class.equals(c) || Float.class.equals(c) || Character.class.equals(c);
    }

    public Object getPrimitiveValue() throws IOException
    {
        if ("int".equals(type))
        {
            Number integer = (Number) get("value");
            return integer.intValue();
        }
        if ("long".equals(type))
        {
            return get("value");
        }
        if ("boolean".equals(type))
        {
            return get("value");
        }
        if ("byte".equals(type))
        {
            Number b = (Number) get("value");
            return b.byteValue();
        }
        if ("double".equals(type))
        {
            return get("value");
        }
        if ("float".equals(type))
        {
            Number f = (Number) get("value");
            return f.floatValue();
        }
        if ("short".equals(type))
        {
            Number s = (Number) get("value");
            return s.shortValue();
        }
        if ("char".equals(type))
        {
            String s = (String) get("value");
            return s.charAt(0);
        }
        throw new IOException("Invalid primitive type, pos = " + pos);
    }

    // Map APIs
    public boolean isMap()
    {
        if (isMap || target instanceof Map)
        {
            return true;
        }

        if (type == null)
        {
            return false;
        }
        try
        {
            Class c = JsonReader.classForName2(type);
            if (Map.class.isAssignableFrom(c))
            {
                return true;
            }
        }
        catch (IOException ignored)  { }

        return false;

    }

    // Collection APIs
    public boolean isCollection()
    {
        if (containsKey("@items") && !containsKey("@keys"))
        {
            return ((target instanceof Collection) || (type != null && !type.contains("[")));
        }

        if (type == null)
        {
            return false;
        }
        try
        {
            Class c = JsonReader.classForName2(type);
            if (Collection.class.isAssignableFrom(c))
            {
                return true;
            }
        }
        catch (IOException ignored)  { }

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
            return containsKey("@items") && !containsKey("@keys");
        }
        return target.getClass().isArray();
    }

    public Object[] getArray()
    {
        return (Object[]) get("@items");
    }

    public int getLength() throws IOException
    {
        if (isArray())
        {
            if (target == null)
            {
                Object[] items = (Object[]) get("@items");
                return items.length;
            }
            return Array.getLength(target);
        }
        if (isCollection())
        {
            Object[] items = (Object[]) get("@items");
            return items == null ? 0 : items.length;
        }
        throw new IOException("getLength() called on a non-collection, pos = " + pos);
    }

    public Class getComponentType()
    {
        return target.getClass().getComponentType();
    }

    void moveBytesToMate()
    {
        byte[] bytes = (byte[]) target;
        Object[] items = getArray();
        int len = items.length;

        for (int i = 0; i < len; i++)
        {
            bytes[i] = ((Number) items[i]).byteValue();
        }
    }

    public void clear()
    {
        super.clear();
        type = null;
    }

    void clearArray()
    {
        remove("@items");
    }
}
