package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.ConverterOptions;
import com.cedarsoftware.util.convert.DefaultConverterOptions;

import java.util.Map;

public class InstanceCreator {
    private final ReadOptions readOptions;

    public InstanceCreator(ReadOptions readOptions) {
        this.readOptions = readOptions;
    }

    public Object createInstanceUsingType(JsonObject jsonObj) {
        Class<?> c = jsonObj.getJavaType();
        boolean useMaps = readOptions.isReturningJsonObjects();
        Object mate;

        if (c == Object.class && !useMaps) {  // JsonObject
            mate = createJsonObjectInstance();
        } else {
            mate = createRegularInstance(c);
        }
        jsonObj.setTarget(mate);
        return mate;
    }

    private Object createJsonObjectInstance() {
        Class<?> unknownClass = readOptions.getUnknownTypeClass();
        if (unknownClass == null) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.setJavaType(Map.class);
            return jsonObject;
        } else {
            return MetaUtils.newInstance(getConverter(), unknownClass, null);   // can add constructor arg values
        }
    }

    private Object createRegularInstance(Class<?> c) {
        return MetaUtils.newInstance(getConverter(), c, null);  // can add constructor arg values
    }

    private Converter getConverter() {
        ConverterOptions options = new DefaultConverterOptions(); // Instantiate a concrete subclass
        return new Converter(options);
    }
}

