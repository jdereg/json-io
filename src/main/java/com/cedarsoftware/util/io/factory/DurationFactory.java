package com.cedarsoftware.util.io.factory;

import java.time.Duration;

public class DurationFactory extends ConvertableFactory {
    public Class<?> getType() {
        return Duration.class;
    }
}
