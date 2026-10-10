package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * An object that appears twice in a graph is written in TOON once and referenced after, or, where its sharing was not
 * traced, written again -- never a failure, never a reference to nothing. A container declared with leaf elements
 * ({@code List<String>}, {@code Map<String, String>}) is not traced for sharing, as in JSON. When another field held
 * the same container, TOON threw "Reference tracking failure" (a list) or wrote {@code $ref: 0}, a reference to
 * nothing (a map). It is now written again, as JSON writes it.
 */
class ToonSharedReferenceTest {

    public static class ListAndObject {
        public List<String> a;
        public Object b;
    }

    public static class MapAndObject {
        public Map<String, String> a;
        public Object b;
    }

    public static class MapAndList {
        public Map<String, String> a;
        public List<Object> b;
    }

    private static final WriteOptions[] MODES = {
            new WriteOptionsBuilder().build(),
            new WriteOptionsBuilder().showTypeInfoNever().build(),
            new WriteOptionsBuilder().showTypeInfoMinimal().build(),
            new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
            new WriteOptionsBuilder().showTypeInfoAlways().build()};
    private static final String[] MODE_NAMES = {"default", "never", "minimal", "minimalPlus", "always"};

    private static List<String> letters() {
        return new ArrayList<>(Arrays.asList("a", "b"));
    }

    @Test
    void aListDeclaredWithStringsAndHeldByAnotherFieldIsWrittenAgain() {
        ListAndObject shared = new ListAndObject();
        shared.a = letters();
        shared.b = shared.a;
        for (int m = 0; m < MODES.length; m++) {
            String toon = JsonIo.toToon(shared, MODES[m]);
            assertFalse(toon.contains("$ref"), MODE_NAMES[m] + "\n" + toon);
            if (m < 2) {    // no type info: written again just as it was written first, not wrapped
                assertFalse(toon.contains("$items"), MODE_NAMES[m] + "\n" + toon);
            }
            ListAndObject back = JsonIo.fromToon(toon, null).asClass(ListAndObject.class);
            assertEquals(letters(), back.a, MODE_NAMES[m]);
            assertEquals(letters(), back.b, MODE_NAMES[m]);
        }
    }

    @Test
    void aMapDeclaredWithStringsAndHeldElsewhereIsWrittenAgainNotReferencedAsZero() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("k", "v");
        MapAndObject inField = new MapAndObject();
        inField.a = map;
        inField.b = map;
        MapAndList inList = new MapAndList();
        inList.a = map;
        inList.b = new ArrayList<>(Arrays.asList(map, "x"));
        for (int m = 0; m < MODES.length; m++) {
            String toon = JsonIo.toToon(inField, MODES[m]);
            assertFalse(toon.contains("$ref"), MODE_NAMES[m] + "\n" + toon);
            MapAndObject back = JsonIo.fromToon(toon, null).asClass(MapAndObject.class);
            assertEquals(map, back.a, MODE_NAMES[m]);
            assertEquals(map, back.b, MODE_NAMES[m] + "\n" + toon);

            toon = JsonIo.toToon(inList, MODES[m]);
            assertFalse(toon.contains("$ref"), MODE_NAMES[m] + "\n" + toon);
            MapAndList listBack = JsonIo.fromToon(toon, null).asClass(MapAndList.class);
            assertEquals(map, listBack.a, MODE_NAMES[m]);
            assertEquals(map, listBack.b.get(0), MODE_NAMES[m] + "\n" + toon);
        }
    }


}
