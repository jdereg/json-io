package com.cedarsoftware.io.factory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
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
public class CollectionFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        if (Deque.class.isAssignableFrom(c)) {
            return new ArrayDeque<>();
        } else if (Queue.class.isAssignableFrom(c)) {
            return new LinkedList<>();
        } else if (List.class.isAssignableFrom(c)) {
            return new ArrayList<>();
        } else if (NavigableSet.class.isAssignableFrom(c)) {
            return new TreeSet<>();
        } else if (SortedSet.class.isAssignableFrom(c)) {
            return new TreeSet<>();
        } else if (Set.class.isAssignableFrom(c)) {
            return new LinkedHashSet<>();
        } else if (Collection.class.isAssignableFrom(c)) {
            return new ArrayList<>();
        }
        throw new JsonIoException("CollectionFactory handed Class for which it was not expecting: " + c.getName());
    }
}