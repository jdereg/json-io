package com.cedarsoftware.util.io.factory;

import java.time.Period;

public class PeriodFactory extends ConvertableFactory {
    @Override
    public Class<?> getType() {
        return Period.class;
    }
}
