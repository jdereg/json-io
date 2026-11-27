package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDK classes whose public constructors do something on their own are refused when JSON names them (java-util
 * 4.119.0's block list). json-io hands an object's values to a JDK class's constructor, matched by type because a
 * JDK constructor's parameters carry no names: JEditorPane(String url) fetched the URL, and DescriptorSupport(String)
 * built any class named in its text, past the block list. Every input here carries no value at all, so even on a
 * build without the block nothing is fetched or built beyond a no-argument constructor.
 */
class GuiComponentTypeRefusedTest {

    /** A class with a field declared as a blocked type: no @type is needed to reach it. */
    public static class Holder {
        public javax.swing.JEditorPane pane;
    }

    private static boolean causedBySecurityException(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SecurityException) {
                return true;
            }
        }
        return false;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javax.swing.JEditorPane",
            "javax.swing.JTextPane",
            "javax.management.modelmbean.DescriptorSupport",
            "java.awt.datatransfer.DataFlavor"
    })
    void aBlockedClassNamedInTheDataIsRefused(String type) {
        String json = "{\"@type\":\"" + type + "\"}";
        Throwable refused = assertThrows(Throwable.class, () -> JsonIo.toJava(json, null).asClass(null), type);
        assertTrue(causedBySecurityException(refused), type + ": " + refused);
    }

    @Test
    void aBlockedClassInsideAnotherObjectIsRefused() {
        String json = "{\"@type\":\"java.util.HashMap\",\"pane\":{\"@type\":\"javax.swing.JEditorPane\"}}";
        Throwable refused = assertThrows(Throwable.class, () -> JsonIo.toJava(json, null).asClass(null));
        assertTrue(causedBySecurityException(refused), refused.toString());
    }

    @Test
    void aFieldDeclaredAsABlockedTypeIsRefusedWithoutAType() {
        String json = "{\"@type\":\"" + Holder.class.getName() + "\",\"pane\":{}}";
        Throwable refused = assertThrows(Throwable.class, () -> JsonIo.toJava(json, null).asClass(null));
        assertTrue(causedBySecurityException(refused), refused.toString());
    }

    /** The AWT value types json-io reads are not components, and still read. */
    @Test
    void awtValueTypesStillRead() {
        Object point = JsonIo.toJava("{\"@type\":\"java.awt.Point\",\"x\":3,\"y\":4}", null).asClass(null);
        assertNotNull(point);
        // (java.awt.Color is left out: it does not round-trip on 4.118.0 either -- a separate issue)
        for (Object value : new Object[]{new java.awt.Point(3, 4), new java.awt.Rectangle(1, 2, 3, 4),
                new java.awt.Dimension(5, 6)}) {
            String json = JsonIo.toJson(value, null);
            Object back = JsonIo.toJava(json, null).asClass(null);
            assertNotNull(back, json);
            assertEquals(value.getClass().getSimpleName(), back.getClass().getSimpleName(), json);
        }
    }
}
