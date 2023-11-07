package com.cedarsoftware.util.reflect.factories;

public class NonStandardInjectorNames extends NonStandardMethodNames {
    private static final NonStandardInjectorNames instance = new NonStandardInjectorNames();

    public static NonStandardInjectorNames instance() {
        return instance;
    }

    private NonStandardInjectorNames() {
        addFieldToMethodMappings();
    }

    private void addFieldToMethodMappings() {
        addMapping(Throwable.class, "cause", "initCause");
    }
}
