package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;

import java.util.Optional;

public class EnumClassFactory implements JsonReader.ClassFactory {

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj) {
        Object value = jObj.getValue();

        if (value instanceof String) {
            // came in as string so we know we're done.
            Enum<?> target = fromString(c, (String) value);
            return jObj.setFinishedTarget(target, true);
        }

        return fromJsonObject(c, jObj);
    }

    @SuppressWarnings("unchecked")
    protected Enum fromString(Class<?> c, String s) {
        return Enum.valueOf((Class<Enum>) c, s);
    }

    private Object fromJsonObject(Class<?> c, JsonObject job) {
        Optional<Class> cls = MetaUtils.getClassIfEnum(c);

        if (cls.isPresent()) {
            return Enum.valueOf(cls.get(), findEnumName(job));
        }

        throw new JsonIoException("Unable to load enum: " + c + ", class not found or is not an Enum.");
    }

    private String findEnumName(JsonObject job) {

        // In case the enum class has it's own 'name' member variable (shadowing the 'name' variable on Enum)
        String name = (String) job.get("Enum.name");
        if (name != null) {
            return name;
        }

        return (String) job.get("name");
    }

}
