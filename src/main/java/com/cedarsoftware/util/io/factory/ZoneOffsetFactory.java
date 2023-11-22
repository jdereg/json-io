package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

import java.time.ZoneOffset;

public class ZoneOffsetFactory implements JsonReader.ClassFactory {
    @Override
    public ZoneOffset newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object value = jObj.getValue();

        if (value instanceof String) {
            return fromString((String) value);
        }

        return fromJsonObject(jObj, context);
    }

    protected ZoneOffset fromString(String id) {
        return ZoneOffset.of(id);
    }

    protected ZoneOffset fromJsonObject(JsonObject job, ReaderContext context) {

        JsonObject o = context.getReferences().get(job);

        if (o.getTarget() != null) {
            return (ZoneOffset) o.getTarget();
        }

        String id = (String) job.get("id");

        if (id != null) {
            return fromString(id);
        }

        return null;
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
