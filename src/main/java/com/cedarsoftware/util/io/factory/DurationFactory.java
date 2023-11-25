package com.cedarsoftware.util.io.factory;

import java.time.Duration;

import com.cedarsoftware.util.io.ArgumentHelper;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

public class DurationFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object value = jObj.getValue();

        if (value instanceof String) {
            return parseString((String) value);
        }

        Number seconds = ArgumentHelper.getNumberWithDefault(jObj.get("seconds"), 0);
        Number nanos = ArgumentHelper.getNumberWithDefault(jObj.get("nanos"), 0);

        return Duration.ofSeconds(seconds.longValue(), nanos.intValue());
    }

    Duration parseString(String s) {
        return Duration.parse(s);
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
