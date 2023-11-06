package com.cedarsoftware.util.reflect.factories;

import java.time.Year;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class NonStandardAccessorNames {
    private final Map<Class, Map<String, String>> classToMapping = new ConcurrentHashMap<>();

    private static final NonStandardAccessorNames instance = new NonStandardAccessorNames();

    public static NonStandardAccessorNames instance() {
        return instance;
    }

    private NonStandardAccessorNames() {
        addKnownNonStandardAccessorNames();
    }

    private void addKnownNonStandardAccessorNames() {
        addMapping(Enum.class, "name", "name");
        addMapping(Throwable.class, "detailMessage", "getMessage");
        addMapping(StackTraceElement.class, "declaringClass", "getClassName");
        addMapping(YearMonth.class, "month", "getMonthValue");
        addMapping(Year.class, "year", "getValue");
    }

    public void addMapping(Class c, String fieldName, String methodName) {
        this.classToMapping.computeIfAbsent(c, k -> new ConcurrentHashMap<>()).put(fieldName, methodName);
    }

    public Optional<String> getMapping(Class c, String fieldName) {
        Map<String, String> mapping = this.classToMapping.get(c);
        return mapping == null ? Optional.empty() : Optional.ofNullable(mapping.get(fieldName));
    }
}
