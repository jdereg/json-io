package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.util.Date;

public class SqlDateFactory extends DateFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj) {
        return new java.sql.Date(((Date) super.newInstance(c, jObj)).getTime());
    }
}
