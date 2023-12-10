package com.cedarsoftware.util.reflect.factories;

import java.util.concurrent.ConcurrentHashMap;

public class NonStandardInjectorNames extends NonStandardMethodNames {
    private static final NonStandardInjectorNames instance = new NonStandardInjectorNames();

    public static NonStandardInjectorNames instance() {
        return instance;
    }

    private NonStandardInjectorNames() {
        super(new ConcurrentHashMap<>());
        addFieldToMethodMappings();
    }

    private void addFieldToMethodMappings() {
        addMapping(Throwable.class, "cause", "initCause");
    }
}
