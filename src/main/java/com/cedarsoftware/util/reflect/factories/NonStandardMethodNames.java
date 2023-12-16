package com.cedarsoftware.util.reflect.factories;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class NonStandardMethodNames {

    private final Map<Class<?>, Map<String, String>> classToMapping;

    public NonStandardMethodNames(Map<Class<?>, Map<String, String>> classToMapping) {
        this.classToMapping = classToMapping;
    }

    public void addMapping(Class<?> c, String fieldName, String methodName) {
        this.classToMapping.computeIfAbsent(c, k -> new ConcurrentHashMap<>()).put(fieldName, methodName);
    }

    public Optional<String> getMapping(Class<?> c, String fieldName) {
        Map<String, String> mapping = this.classToMapping.get(c);
        return mapping == null ? Optional.empty() : Optional.ofNullable(mapping.get(fieldName));
    }
}
