package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

import java.util.Date;

public class SqlDateFactory extends DateFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        return new java.sql.Date(((Date) super.newInstance(c, jObj, context)).getTime());
    }
}
