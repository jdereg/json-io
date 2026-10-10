package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object that appears twice in a graph is written in TOON once and referenced after, or, where its sharing was not
 * traced, written again -- never a failure, never a reference to nothing.
 * <ul>
 *   <li>An {@code Optional} is written as its content, in the content's place, without type info (TOON's default),
 *       so it can carry no {@code $id}. A shared one threw "Reference tracking failure" when its content came round
 *       again. Its content now carries the identity: {@code $id} where first written, {@code $ref} after.</li>
 *   <li>A container declared with leaf elements ({@code List<String>}, {@code Map<String, String>}) is not traced
 *       for sharing, as in JSON. When another field held the same container, TOON threw (a list) or wrote
 *       {@code $ref: 0}, a reference to nothing (a map). It is now written again, as JSON writes it.</li>
 * </ul>
 */
class ToonSharedReferenceTest {

    public static class Opts {
        public Optional<List<String>> x;
        public Optional<List<String>> y;
    }

    public static class Item {
        public String name;
    }

    public static class OptItems {
        public Optional<Item> x;
        public Optional<Item> y;
    }

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
    void aSharedOptionalOfAListInAListIsWrittenOnceAndReadBackOnce() {
        Optional<List<String>> shared = Optional.of(letters());
        for (int m = 0; m < 2; m++) {     // no type info: the Optional is written as its list
            String toon = JsonIo.toToon(Arrays.asList(shared, shared), MODES[m]);
            List<?> back = JsonIo.fromToon(toon, null).asClass(List.class);
            assertEquals(2, back.size(), MODE_NAMES[m] + "\n" + toon);
            assertEquals(letters(), back.get(0), MODE_NAMES[m]);
            assertSame(back.get(0), back.get(1), MODE_NAMES[m] + ": one list, held twice\n" + toon);
        }
    }

    @Test
    void sharedOptionalFieldsReadBackHoldingTheSameContent() {
        Opts opts = new Opts();
        opts.x = opts.y = Optional.of(letters());
        OptItems items = new OptItems();
        Item item = new Item();
        item.name = "p";
        items.x = items.y = Optional.of(item);
        for (int m = 0; m < MODES.length; m++) {
            String toon = JsonIo.toToon(opts, MODES[m]);
            Opts back = JsonIo.fromToon(toon, null).asClass(Opts.class);
            assertEquals(letters(), back.x.get(), MODE_NAMES[m] + "\n" + toon);
            assertSame(back.x.get(), back.y.get(), MODE_NAMES[m] + "\n" + toon);

            toon = JsonIo.toToon(items, MODES[m]);
            OptItems itemsBack = JsonIo.fromToon(toon, null).asClass(OptItems.class);
            assertEquals("p", itemsBack.x.get().name, MODE_NAMES[m] + "\n" + toon);
            assertSame(itemsBack.x.get(), itemsBack.y.get(), MODE_NAMES[m] + "\n" + toon);
        }
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

    /**
     * A TOON root list whose elements define or name a shared value ($id, $ref) or hold items reads as JSON reads the
     * same root array -- as a raw List, with no type, and in Maps mode. The $ref came back an empty map and the
     * $items unread (4.112.0 too), so a shared list or map at the top level of plain TOON was lost.
     */
    @Test
    void aRootListReadsItsIdsAndReferencesAsJsonDoes() {
        String json = "[{\"@id\":1,\"@items\":[\"a\",\"b\"]},{\"@ref\":1},{\"@id\":2,\"k\":\"v\"},{\"@ref\":2}]";
        String toon = "[4]:\n  -\n    $id: 1\n    $items[2]: a,b\n  -\n    $ref: 1\n  - $id: 2\n    k: v\n  - $ref: 2\n";
        ReadOptions maps = new ReadOptionsBuilder().returnAsJsonObjects().build();
        Object[][] reads = {
                {JsonIo.toJava(json, null).asClass(List.class), JsonIo.fromToon(toon, null).asClass(List.class)},
                {JsonIo.toJava(json, null).asClass(null), JsonIo.fromToon(toon, null).asClass(null)},
                {JsonIo.toMaps(json, null).asClass(null), JsonIo.fromToon(toon, maps).asClass(null)}};
        for (Object[] read : reads) {
            List<?> fromJson = asList(read[0]);
            List<?> fromToon = asList(read[1]);
            assertEquals(4, fromToon.size());
            for (int i = 0; i < 4; i++) {
                assertEquals(describe(fromJson.get(i)), describe(fromToon.get(i)), "element " + i);
            }
            assertSame(fromToon.get(0), fromToon.get(1), "the shared list");
            assertSame(fromToon.get(2), fromToon.get(3), "the shared map");
        }
    }

    private static List<?> asList(Object read) {
        return read instanceof Object[] ? Arrays.asList((Object[]) read) : (List<?>) read;
    }

    private static String describe(Object element) {
        if (element instanceof Object[]) {
            return "Object[]" + Arrays.asList((Object[]) element);
        }
        if (element instanceof JsonObject && ((JsonObject) element).getItems() != null) {
            return "items" + Arrays.asList(((JsonObject) element).getItems());
        }
        return element.getClass().getSimpleName() + element;
    }

    /** Where sharing was traced, a shared object is still written once and referenced after, in every mode. */
    @Test
    void aTracedSharedObjectIsStillWrittenOnceAndReferenced() {
        List<Object> shared = new ArrayList<>(Arrays.asList("a", "b"));
        for (int m = 0; m < MODES.length; m++) {
            String toon = JsonIo.toToon(Arrays.asList(shared, shared), MODES[m]);
            assertTrue(toon.contains("$ref"), MODE_NAMES[m] + "\n" + toon);
            List<?> back = JsonIo.fromToon(toon, null).asClass(List.class);
            assertSame(back.get(0), back.get(1), MODE_NAMES[m] + "\n" + toon);
        }
    }
}
