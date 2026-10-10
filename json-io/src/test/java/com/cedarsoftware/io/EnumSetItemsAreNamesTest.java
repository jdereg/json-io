package com.cedarsoftware.io;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON writes an EnumSet's items as their names, as it writes an enum constant anywhere else and as TOON writes an
 * EnumSet. An enum with more than two fields had each item written as an object of its fields: for a JDK enum on
 * JDK 17+, whose fields cannot be read, every value was 0 ({@code "scale":0} for {@code TimeUnit.SECONDS}); for an
 * application's enum, values that go back to the constant's own when read. Every json-io reader from 4.50.0 on
 * reads the names. {@code showTypeInfoMinimalLegacy()}, which keeps 4.112.0's text byte for byte, still writes the
 * objects, and those still read.
 */
class EnumSetItemsAreNamesTest {

    public enum Planet {
        MERCURY(3.3e23, 2.44e6), EARTH(5.97e24, 6.37e6);

        final double mass;
        final double radius;
        final String label = name().toLowerCase();

        Planet(double mass, double radius) {
            this.mass = mass;
            this.radius = radius;
        }
    }

    public static class Holder {
        public EnumSet<TimeUnit> units;
        public EnumSet<Planet> planets;
        public Object any;
    }

    private static Holder holder() {
        Holder holder = new Holder();
        holder.units = EnumSet.of(TimeUnit.SECONDS, TimeUnit.DAYS);
        holder.planets = EnumSet.of(Planet.EARTH);
        holder.any = EnumSet.of(TimeUnit.HOURS);
        return holder;
    }

    @Test
    void anEnumSetsItemsAreWrittenAsTheirNames() {
        for (WriteOptions options : new WriteOptions[] {new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            String json = JsonIo.toJson(holder(), options);
            assertTrue(json.contains("\"@items\":[\"SECONDS\",\"DAYS\"]"), json);
            assertTrue(json.contains("\"@items\":[\"EARTH\"]"), json);
            assertTrue(json.contains("\"@items\":[\"HOURS\"]"), json);
            assertFalse(json.contains("\"scale\""), json);
            assertFalse(json.contains("\"mass\""), json);
            Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
            assertEquals(EnumSet.of(TimeUnit.SECONDS, TimeUnit.DAYS), back.units);
            assertEquals(EnumSet.of(Planet.EARTH), back.planets);
            assertEquals(EnumSet.of(TimeUnit.HOURS), back.any);
        }
    }

    /** showTypeInfoMinimalLegacy() keeps 4.112.0's text, objects and all; the object form still reads. */
    @Test
    void theLegacyTextKeepsTheObjectsAndTheyRead() {
        String json = JsonIo.toJson(holder(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build());
        assertTrue(json.contains("\"mass\""), json);      // (TimeUnit has fields only from JDK 9 on: not asserted)
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertEquals(EnumSet.of(TimeUnit.SECONDS, TimeUnit.DAYS), back.units);
        assertEquals(EnumSet.of(Planet.EARTH), back.planets);
    }
}
