package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A StackTraceElement's alias is "StackTraceElement". From 2024 (when every alias moved to config/aliases.txt) to
 * 4.112.0 it was "StackTraceElementElement", a slip that doubled its last word. That name is still read -- JSON and
 * TOON written by those versions -- and {@code showTypeInfoMinimalLegacy()}, which keeps 4.112.0's text byte for
 * byte, still writes it. The alias shows only where a StackTraceElement sits in a slot that does not declare it (an
 * exception's own stack trace never carries it).
 */
class StackTraceElementAliasTest {

    public static class Holder {
        public Object element;
        public Object elements;
    }

    private static final StackTraceElement ELEMENT = new StackTraceElement("C", "m", "F.java", 7);

    private static Holder holder() {
        Holder holder = new Holder();
        holder.element = ELEMENT;
        holder.elements = new StackTraceElement[] {ELEMENT};
        return holder;
    }

    @Test
    void itIsWrittenAsStackTraceElement() {
        for (WriteOptions options : new WriteOptions[] {new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            String json = JsonIo.toJson(holder(), options);
            assertTrue(json.contains("\"StackTraceElement\""), json);
            assertTrue(json.contains("\"StackTraceElement[]\""), json);
            assertFalse(json.contains("StackTraceElementElement"), json);
            String toon = JsonIo.toToon(holder(), options);
            assertFalse(toon.contains("StackTraceElementElement"), toon);
            Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
            assertEquals(ELEMENT, back.element);
            assertArrayEquals(new StackTraceElement[] {ELEMENT}, (StackTraceElement[]) back.elements);
        }
    }

    /** What 4.112.0 and the versions before it wrote reads, in JSON and in TOON. */
    @Test
    void theNameEarlierVersionsWroteIsRead() {
        String json = "{\"element\":{\"@type\":\"StackTraceElementElement\",\"declaringClass\":\"C\","
                + "\"methodName\":\"m\",\"fileName\":\"F.java\",\"lineNumber\":7},"
                + "\"elements\":{\"@type\":\"StackTraceElementElement[]\",\"@items\":[{\"declaringClass\":\"C\","
                + "\"methodName\":\"m\",\"fileName\":\"F.java\",\"lineNumber\":7}]}}";
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertEquals(ELEMENT, back.element);
        assertArrayEquals(new StackTraceElement[] {ELEMENT}, (StackTraceElement[]) back.elements);

        String toon = "element:\n  $type: StackTraceElementElement\n  declaringClass: C\n  methodName: m\n"
                + "  fileName: F.java\n  lineNumber: 7\n";
        assertEquals(ELEMENT, JsonIo.fromToon(toon, null).asClass(Holder.class).element);
    }

    /** showTypeInfoMinimalLegacy() keeps 4.112.0's text byte for byte, this name included. */
    @Test
    void theLegacyTextKeepsTheOldName() {
        String json = JsonIo.toJson(holder(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build());
        assertTrue(json.contains("\"StackTraceElementElement\""), json);
        assertTrue(json.contains("\"StackTraceElementElement[]\""), json);
        assertEquals(ELEMENT, JsonIo.toJava(json, null).asClass(Holder.class).element);
    }

    /** An alias the application chose for StackTraceElement is its own, in legacy mode too. */
    @Test
    void anApplicationsOwnAliasStands() {
        String json = JsonIo.toJson(holder(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy()
                .aliasTypeName(StackTraceElement.class, "Frame").build());
        assertTrue(json.contains("\"Frame\""), json);
    }
}
