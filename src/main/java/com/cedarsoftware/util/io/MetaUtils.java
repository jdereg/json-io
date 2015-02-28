package com.cedarsoftware.util.io;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This utility class is used to retrieve the fields from a Class.  It uses reflection
 * to do so, allowing it to obtain the non-public fields.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
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
public class MetaUtils
{
    private static final Map<Class, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>();

    public static Field getField(Class c, String field)
    {
        return getDeepDeclaredFields(c).get(field);
    }

    /**
     * @param c Class instance
     * @return ClassMeta which contains fields of class.  The results are cached internally for performance
     *         when called again with same Class.
     */
    public static Map<String, Field> getDeepDeclaredFields(Class c)
    {
        Map<String, Field> classFields = classMetaCache.get(c);
        if (classFields != null)
        {
            return classFields;
        }

        classFields = new LinkedHashMap<>();
        Class curr = c;

        while (curr != null)
        {
            try
            {
                final Field[] local = curr.getDeclaredFields();

                for (Field field : local)
                {
                    if ((field.getModifiers() & Modifier.STATIC) == 0)
                    {   // speed up: do not process static fields.
                        if ("metaClass".equals(field.getName()) && "groovy.lang.MetaClass".equals(field.getType().getName()))
                        {   // Skip Groovy metaClass field if present
                            continue;
                        }

                        if (!field.isAccessible())
                        {
                            try
                            {
                                field.setAccessible(true);
                            }
                            catch (Exception ignored) { }
                        }
                        if (classFields.containsKey(field.getName()))
                        {
                            classFields.put(curr.getName() + '.' + field.getName(), field);
                        }
                        else
                        {
                            classFields.put(field.getName(), field);
                        }
                    }
                }
            }
            catch (ThreadDeath t)
            {
                throw t;
            }
            catch (Throwable ignored) { }

            curr = curr.getSuperclass();
        }

        classMetaCache.put(c, classFields);
        return classFields;
    }

    public static int getDistance(Class a, Class b)
    {
        if (a.isInterface())
        {
            return getDistanceToInterface(a, b);
        }
        Class curr = b;
        int distance = 0;

        while (curr != a)
        {
            distance++;
            curr = curr.getSuperclass();
            if (curr == null)
            {
                return Integer.MAX_VALUE;
            }
        }

        return distance;
    }

    static int getDistanceToInterface(Class<?> to, Class<?> from)
    {
        Set<Class<?>> possibleCandidates = new LinkedHashSet<>();

        Class<?>[] interfaces = from.getInterfaces();
        // is the interface direct inherited or via interfaces extends interface?
        for (Class<?> interfase : interfaces)
        {
            if (to.equals(interfase))
            {
                return 1;
            }
            // because of multi-inheritance from interfaces
            if (to.isAssignableFrom(interfase))
            {
                possibleCandidates.add(interfase);
            }
        }

        // it is also possible, that the interface is included in superclasses
        if (from.getSuperclass() != null  && to.isAssignableFrom(from.getSuperclass()))
        {
            possibleCandidates.add(from.getSuperclass());
        }

        int minimum = Integer.MAX_VALUE;
        for (Class<?> candidate : possibleCandidates)
        {
            // Could do that in a non recursive way later
            int distance = getDistanceToInterface(to, candidate);
            if (distance < minimum)
            {
                minimum = ++distance;
            }
        }
        return minimum;
    }
}
