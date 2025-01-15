package com.cedarsoftware.io.factory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;

/**
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
public class SynchronizedFactory implements JsonReader.ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        if (NavigableSet.class.isAssignableFrom(c)) {
            return Collections.synchronizedNavigableSet(new TreeSet<>());
        } else if (SortedSet.class.isAssignableFrom(c)) {
            return Collections.synchronizedSortedSet(new TreeSet<>());
        } else if (Set.class.isAssignableFrom(c)) {
            return Collections.synchronizedSet(new LinkedHashSet<>());
        } else if (List.class.isAssignableFrom(c)) {
            return Collections.synchronizedList(new ArrayList<>());
        } else if (Collection.class.isAssignableFrom(c)) {
            return Collections.synchronizedCollection(new ArrayList<>());
        } else if (NavigableMap.class.isAssignableFrom(c)) {
            return Collections.synchronizedNavigableMap(new TreeMap<>());
        } else if (SortedMap.class.isAssignableFrom(c)) {
            return Collections.synchronizedSortedMap(new TreeMap<>());
        } else if (Map.class.isAssignableFrom(c)) {
            return Collections.synchronizedMap(new LinkedHashMap<>());
        }
        throw new JsonIoException("SynchronizedFactory handed Class for which it was not expecting: " + c.getName());
    }
}

