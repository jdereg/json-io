package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;


public class LocalDateFactory implements JsonReader.ClassFactory {

    protected final DateTimeFormatter dateTimeFormatter;

    public LocalDateFactory(DateTimeFormatter dateFormatter) {
        this.dateTimeFormatter = dateFormatter;
    }

    public LocalDateFactory() {
        this(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Override
    public Object newInstance(Class c, Object object, JsonReader reader) {
        var optional = tryToFindValue(object);

        if (optional.isPresent()) {
            return optional.get();
        }

        var job = (JsonObject<String, Object>) object;
        if (job.containsKey("value")) {
            return tryToFindValue(job.get("value")).orElse(null);
        }

        // object with month, day, year on it.
        return assembleObject(job);
    }

    private Optional<LocalDate> tryToFindValue(Object o) {
        if (o instanceof String) {
            return Optional.of(LocalDate.parse((String) o, dateTimeFormatter));
        }

        if (o instanceof Long) {
            return Optional.of(LocalDate.ofEpochDay(((Long) o).longValue()));
        }

        return Optional.empty();
    }

    private LocalDate assembleObject(JsonObject jsonObject) {
        var month = (Number) jsonObject.get("month");
        var day = (Number) jsonObject.get("day");
        var year = (Number) jsonObject.get("year");

        return LocalDate.of(year.intValue(), month.intValue(), day.intValue());
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
