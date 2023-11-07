package com.cedarsoftware.util.reflect.factories;

import java.time.Year;
import java.time.YearMonth;

public class NonStandardAccessorNames extends NonStandardMethodNames {
    private static final NonStandardAccessorNames instance = new NonStandardAccessorNames();

    public static NonStandardAccessorNames instance() {
        return instance;
    }

    private NonStandardAccessorNames() {
        addNonStandardFieldToAccessorMethodMappings();
    }

    private void addNonStandardFieldToAccessorMethodMappings() {
        addMapping(Enum.class, "name", "name");
        addMapping(Throwable.class, "detailMessage", "getMessage");
        addMapping(StackTraceElement.class, "declaringClass", "getClassName");
        addMapping(YearMonth.class, "month", "getMonthValue");
        addMapping(Year.class, "year", "getValue");
    }
}
