package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.cedarsoftware.util.io.MetaUtils;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
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
public class KnownFilteredFields {
    private final Map<Class, Set<String>> knownFieldFilters = new ConcurrentHashMap<>();

    private final Map<Class, Set<String>> knownInjectorFilters = new ConcurrentHashMap<>();

    private static final KnownFilteredFields instance = new KnownFilteredFields();

    public static KnownFilteredFields instance() {
        return instance;
    }

    private KnownFilteredFields() {
        addKnownFilters();
    }

    private void addKnownFilters() {
        addFieldFilters(Throwable.class, MetaUtils.listOf("backtrace", "depth", "suppressedExceptions", "stackTrace"));
        addFieldFilters(StackTraceElement.class, MetaUtils.listOf("declaringClassObject", "format"));

        addInjectionFilters(Throwable.class, MetaUtils.listOf("detailMessage", "cause", "stackTrace"));
        addInjectionFilters(Enum.class, MetaUtils.listOf("name"));
    }

    public void addFieldFilter(Class c, String fieldName) {
        this.knownFieldFilters.computeIfAbsent(c, k -> new ConcurrentSkipListSet<>()).add(fieldName);
        ClassDescriptors.instance().clearDescriptorCache();
    }

    public void addFieldFilters(Class c, Collection<String> fieldName) {
        this.knownFieldFilters.computeIfAbsent(c, k -> new ConcurrentSkipListSet<>()).addAll(fieldName);
        ClassDescriptors.instance().clearDescriptorCache();
    }

    public void removeFieldFilters(Class c, String fieldName) {
        this.knownFieldFilters.computeIfAbsent(c, k -> new ConcurrentSkipListSet<>()).remove(fieldName);
        ClassDescriptors.instance().clearDescriptorCache();
    }

    public void addInjectionFilter(Class c, String fieldName) {
        this.knownInjectorFilters.computeIfAbsent(c, k -> new ConcurrentSkipListSet<>()).add(fieldName);
        ClassDescriptors.instance().clearDescriptorCache();
    }

    public void addInjectionFilters(Class c, Collection<String> fieldName) {
        this.knownInjectorFilters.computeIfAbsent(c, k -> new ConcurrentSkipListSet<>()).addAll(fieldName);
        ClassDescriptors.instance().clearDescriptorCache();
    }

    public boolean isFieldFiltered(Field field) {
        Set<String> set = this.knownFieldFilters.get(field.getDeclaringClass());
        return set != null && set.contains(field.getName());
    }

    public boolean isInjectionFiltered(Field field) {
        Set<String> set = this.knownInjectorFilters.get(field.getDeclaringClass());
        return set != null && set.contains(field.getName());
    }
}
