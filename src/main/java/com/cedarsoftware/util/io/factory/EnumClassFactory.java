package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.ReaderContext;

import java.util.Optional;
import java.util.Set;

public class EnumClassFactory implements JsonReader.ClassFactory {

    private static final Set<String> excludedFields = MetaUtils.setOf("name", "ordinal", "internal");

    @Override
    public Object newInstance(Class<?> c, JsonObject job) {
        Object value = job.get("value");

        if (value instanceof String) {
            // came in as string so we know we're done.
            Enum target = fromString(c, (String) value);
            return job.setFinishedTarget(target, true);
        }

        return fromJsonObject(job);
    }

    @SuppressWarnings("unchecked")
    protected Enum fromString(Class<?> c, String s) {
        return Enum.valueOf((Class<Enum>) c, s);
    }

    private Object fromJsonObject(JsonObject job) {

        String type = job.getType();

        ClassLoader loader = ReaderContext.instance().getReadOptions().getClassLoader();
        Class c = MetaUtils.classForName(type, loader);

        if (c == null) {
            throw new JsonIoException("Unable to load enum: " + type + ", class not found.");
        }

        Optional<Class> cls = MetaUtils.getClassIfEnum(c);
        return Enum.valueOf(cls.orElse(c), findEnumName(job));
    }

    private String findEnumName(JsonObject job) {

        // In case the enum class has it's own 'name' member variable (shadowing the 'name' variable on Enum)
        String name = (String) job.get("java.lang.Enum.name");
        if (name != null) {
            return name;
        }

        return (String) job.get("name");
    }

}
