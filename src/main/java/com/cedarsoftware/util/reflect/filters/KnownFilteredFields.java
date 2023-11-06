package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.io.MetaUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class KnownFilteredFields {
    private final Map<Class, Set<String>> classToMapping = new ConcurrentHashMap<>();

    private static final KnownFilteredFields instance = new KnownFilteredFields();

    public static KnownFilteredFields instance() {
        return instance;
    }

    private KnownFilteredFields() {
        addKnownFilters();
    }

    private void addKnownFilters() {
        addMapping(Throwable.class, MetaUtils.listOf("backtrace", "depth", "suppressedExceptions"));
        addMapping(StackTraceElement.class, MetaUtils.listOf("declaringClassObject", "format"));
    }

    public void addMapping(Class c, String fieldName) {
        this.classToMapping.computeIfAbsent(c, k -> new ConcurrentSkipListSet<>()).add(fieldName);
    }

    public void addMapping(Class c, Collection<String> fieldName) {
        this.classToMapping.computeIfAbsent(c, k -> new ConcurrentSkipListSet<>()).addAll(fieldName);
    }

    public boolean contains(Field field) {
        Set<String> set = this.classToMapping.get(field.getDeclaringClass());
        return set != null && set.contains(field.getName());
    }
}
