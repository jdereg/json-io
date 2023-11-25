package com.cedarsoftware.util.io.factory;

import java.time.Period;

import com.cedarsoftware.util.io.ArgumentHelper;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

public class PeriodFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object value = jObj.getValue();

        if (value instanceof String) {
            return parseString((String) value);
        }

        Number years = ArgumentHelper.getNumberWithDefault(jObj.get("years"), 0);
        Number months = ArgumentHelper.getNumberWithDefault(jObj.get("months"), 0);
        Number days = ArgumentHelper.getNumberWithDefault(jObj.get("days"), 0);

        return Period.of(years.intValue(), months.intValue(), days.intValue());
    }

    Period parseString(String s) {
        return Period.parse(s);
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
