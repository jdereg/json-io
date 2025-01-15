package com.cedarsoftware.io.factory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.io.SealedSupplier;
import com.cedarsoftware.io.util.SealableList;
import com.cedarsoftware.io.util.SealableMap;
import com.cedarsoftware.io.util.SealableNavigableMap;
import com.cedarsoftware.io.util.SealableNavigableSet;
import com.cedarsoftware.io.util.SealableSet;

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
public class UnmodifiableFactory implements JsonReader.ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        SealedSupplier supplier = resolver.getSealedSupplier();
        if (NavigableSet.class.isAssignableFrom(c) || SortedSet.class.isAssignableFrom(c)) {
            return new SealableNavigableSet<>(supplier);
        } else if (Set.class.isAssignableFrom(c)) {
            return new SealableSet<>(supplier);
        } else if (List.class.isAssignableFrom(c) || Collection.class.isAssignableFrom(c)) {
            return new SealableList<>(supplier);
        } else if (NavigableMap.class.isAssignableFrom(c) || SortedMap.class.isAssignableFrom(c)) {
            return new SealableNavigableMap<>(supplier);
        } else if (Map.class.isAssignableFrom(c)) {
            return new SealableMap<>(supplier);
        }
        throw new JsonIoException("SealableFactory handed Class for which it was not expecting: " + c.getName());
    }
}

