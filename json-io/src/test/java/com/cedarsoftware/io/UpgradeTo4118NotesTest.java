package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upgrade note at the top of 4.118.0 in changelog.md -- "what can change a result" -- one test per row, so the
 * note says what the code does. Each row's example is the one the note gives; 4.117.0 answered every one of them
 * differently (the note's "4.117.0" column).
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class UpgradeTo4118NotesTest {
    private static final String HERE = UpgradeTo4118NotesTest.class.getName() + "$";
    private static final WriteOptions NEVER = new WriteOptionsBuilder().showTypeInfoNever().build();
    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictJson().build();

    public static class Person {
        public String name;
        public int age;
    }

    public enum Status { NEW, SHIPPED }

    public enum Rich {
        A {
            @Override
            public String x() {
                return "a";
            }
        },
        B;

        public String x() {
            return "?";
        }
    }

    public static class Counted {
        static int built;
        public int age;

        public Counted() {
            built++;
        }
    }

    public static class Policy {
        public final String number;

        public Policy(String number) {
            if (number == null || !number.startsWith("P-")) {
                throw new IllegalArgumentException("policy number must start with P-");
            }
            this.number = number;
        }
    }

    public static class Holder {
        public Optional<Person> who;
        public List<Status> statuses;
        public List<Person> people;
        public Object any;
    }

    public static class Line {
        public int qty;
    }

    public static class Order {
        public List<Line> lines;
    }

    public static class Dated {
        public ZonedDateTime at;
    }

    private static Object maps(String json) {
        return JsonIo.toMaps(json, null).asClass(null);
    }

    private static Object first(Object container) {
        return container instanceof Object[] ? ((Object[]) container)[0] : ((JsonObject) container).getItems()[0];
    }

    // ---- Map mode

    /** 4.117.0: a LinkedHashMap copy. */
    @Test
    void asClassMapIsTheTreesRoot() {
        assertInstanceOf(JsonObject.class, JsonIo.toMaps("{\"a\":1}", null).asClass(Map.class));
    }

    /** 4.117.0: not equal to a LinkedHashMap with the same entries; another hash. */
    @Test
    void aJsonObjectEqualsAMapWithTheSameEntries() {
        Map<String, Object> same = new LinkedHashMap<>();
        same.put("a", 1L);
        assertEquals(same, maps("{\"a\":1}"));
        assertEquals(same.hashCode(), maps("{\"a\":1}").hashCode());
    }

    /** 4.117.0: a JsonObject holding "value". */
    @Test
    void aTypedValueInAListIsTheValue() {
        Object uuid = first(((Map<?, ?>) maps("{\"l\":[{\"@type\":\"UUID\",\"value\":"
                + "\"123e4567-e89b-12d3-a456-426614174000\"}]}")).get("l"));
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), uuid);
    }

    /** 4.117.0: a JsonObject holding "name". */
    @Test
    void aSimpleEnumInAListIsTheConstant() {
        assertSame(Status.NEW, first(((Map<?, ?>) maps("{\"l\":[{\"@type\":\"" + HERE + "Status\",\"name\":\"NEW\"}]}"))
                .get("l")));
    }

    /** 4.117.0: "age" an Integer, typed by the class. */
    @Test
    void anObjectInATypedCollectionStaysJson() {
        JsonObject person = (JsonObject) first(((Map<?, ?>) maps("{\"l\":{\"@type\":\"ArrayList\",\"@items\":"
                + "[{\"@type\":\"" + HERE + "Person\",\"age\":3}]}}")).get("l"));
        assertEquals(3L, person.get("age"));
    }

    /** 4.117.0: the root's class was constructed; one that could not be built failed the read. */
    @Test
    void theRootsClassIsNotBuilt() {
        int before = Counted.built;
        JsonObject counted = (JsonObject) maps("{\"@type\":\"" + HERE + "Counted\",\"age\":1}");
        assertEquals(before, Counted.built);
        assertEquals(1, counted.get("age"));     // the root's fields still take the class's types
        assertInstanceOf(JsonObject.class, maps("{\"@type\":\"" + HERE + "Policy\",\"number\":\"x\"}"));
    }

    /** 4.117.0: an empty list. */
    @Test
    void anObjectAskedForAsAListIsAnError() {
        assertThrows(JsonIoException.class, () -> JsonIo.toMaps("{\"a\":1}", null).asClass(List.class));
    }

    /** 4.117.0: Strings. */
    @Test
    void theRootsTypeArgumentsConvertItsValues() {
        List<LocalDate> dates = JsonIo.toMaps("[\"2024-01-15\"]", null).asType(new TypeHolder<List<LocalDate>>() { });
        assertEquals(LocalDate.of(2024, 1, 15), dates.get(0));
    }

    /** 4.117.0: the read failed, "Value 'abc' not parseable as a BigDecimal value". */
    @Test
    void aValueThatDoesNotConvertStaysAsWritten() {
        Map<?, ?> tree = (Map<?, ?>) maps("{\"f\":{\"@type\":\"BigDecimal\",\"value\":\"abc\"}}");
        JsonObject kept = (JsonObject) tree.get("f");
        assertEquals("abc", kept.get("value"));
    }

    /** 4.117.0: an empty JsonObject. */
    @Test
    void aRefInATypedCollectionIsTheObject() {
        Map<?, ?> tree = (Map<?, ?>) maps("{\"a\":{\"@id\":2,\"x\":1},\"l\":{\"@type\":\"ArrayList\","
                + "\"@items\":[{\"@ref\":2}]}}");
        assertSame(tree.get("a"), first(tree.get("l")));
    }

    /** 4.117.0: "Return type mismatch. Expecting: ...Person, found: com.cedarsoftware.io.JsonObject". */
    @Test
    void aTreeReadsAsAClassInASecondStep() {
        JsonObject tree = (JsonObject) maps("{\"name\":\"Ann\",\"age\":30}");
        assertEquals(30, JsonIo.toJava(tree, null).asClass(Person.class).age);
    }

    /** 4.117.0: {"@type":"BigInteger","value":"..."}. */
    @Test
    void plainJsonKeepsItsBigNumbers() {
        assertEquals("{\"id\":123456789012345678901234567890}",
                JsonIo.toJson(maps("{\"id\":123456789012345678901234567890}"), null));
    }

    // ---- Reading, both modes

    /** 4.117.0: "Unable to instantiate (cached): ...Policy", the conversion never used. */
    @Test
    void aRegisteredMapConversionBuildsTheClass() {
        ReadOptions options = new ReadOptionsBuilder().addConverterOverride(Map.class, Policy.class,
                (from, converter) -> new Policy("P-" + ((Map<?, ?>) from).get("holder"))).build();
        assertEquals("P-Ann", JsonIo.toJava("{\"holder\":\"Ann\"}", options).asClass(Policy.class).number);
    }

    /** 4.117.0: held twice (size 3), written back twice. */
    @Test
    void aRepeatedKeyIsHeldOnce() {
        JsonObject tree = (JsonObject) maps("{\"a\":1,\"b\":2,\"a\":3}");
        assertEquals(2, tree.size());
        assertEquals("{\"a\":3,\"b\":2}", JsonIo.toJson(tree, null));
        assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"a\":1,\"a\":3}", STRICT).asClass(null));
    }

    /** 4.117.0: an empty list. */
    @Test
    void anObjectWhereAListIsDeclaredIsAnError() {
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"lines\":{\"qty\":1}}", null).asClass(Order.class));
        assertTrue(e.getMessage().contains("(at $.lines)"), e.getMessage());
    }

    /** 4.117.0: the message named the class and field, not where. */
    @Test
    void anErrorSaysWhere() {
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"lines\":[{\"qty\":1},{\"qty\":\"three\"}]}", null).asClass(Order.class));
        assertTrue(e.getMessage().split("\n")[0].endsWith("(at $.lines[1].qty)"), e.getMessage());
    }

    /** 4.117.0: Optional.empty(). */
    @Test
    void anObjectWhereAnOptionalIsDeclaredIsItsContent() {
        Holder holder = JsonIo.toJava("{\"who\":{\"name\":\"Ann\"}}", null).asClass(Holder.class);
        assertEquals("Ann", holder.who.get().name);
    }

    /** 4.117.0: the element dropped, an empty list. */
    @Test
    void blankTextInAListOfAClassIsNull() {
        Holder holder = JsonIo.toJava("{\"people\":[\"\"]}", null).asClass(Holder.class);
        assertEquals(Collections.singletonList(null), holder.people);
    }

    /** 4.117.0: an empty map, the items lost. */
    @Test
    void anUnknownClassHoldingItemsKeepsThem() {
        Map<?, ?> read = JsonIo.toJava("{\"x\":{\"@type\":\"gone.Bag\",\"@items\":[1,2]}}",
                new ReadOptionsBuilder().failOnUnknownType(false).build()).asClass(Map.class);
        assertEquals(Arrays.asList(1L, 2L), Arrays.asList((Object[]) read.get("x")));
    }

    /** 4.117.0: each accepted. */
    @Test
    void strictJsonRefusesWhatRfc8259Does() {
        for (String json : new String[] {"{\"a\":007}", "[-.5]", "[1.e20]", "[\"a\tb\"]", "[\"\\'\"]"}) {
            assertThrows(JsonIoException.class, () -> JsonIo.toJava(json, STRICT).asClass(null), json);
        }
    }

    /** 4.117.0: a BigDecimal (java-util's parseToMinimalNumericType; TOON reads its decimals through it too). */
    @Test
    void floatPointBothGivesADoubleThatHoldsTheNumber() {
        Map<?, ?> read = JsonIo.toJava("{\"d\":0.30000000000000004}", new ReadOptionsBuilder().floatPointBoth().build())
                .asClass(Map.class);
        assertEquals(0.30000000000000004, read.get("d"));
    }

    /** 4.117.0: "Issue parsing date-time, other characters present: [+05:00]". */
    @Test
    void aBracketedOffsetReads() {
        ZonedDateTime at = JsonIo.toJava("\"2026-01-15T10:00:00+05:00[+05:00]\"", null).asClass(ZonedDateTime.class);
        assertEquals(ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.of("+05:00")), at);
    }

    // ---- Writing

    /** 4.117.0: "...Z[UTC]" (read back as the region UTC) and "...+05:00[+05:00]". */
    @Test
    void aZonedDateTimeIsWrittenAsIso8601WritesIt() {
        Dated dated = new Dated();
        dated.at = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals("{\"at\":\"2026-01-15T10:00:00Z\"}", JsonIo.toJson(dated, NEVER));
        dated.at = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.of("+05:00"));
        assertEquals("{\"at\":\"2026-01-15T10:00:00+05:00\"}", JsonIo.toJson(dated, NEVER));
    }

    /** 4.117.0: {"n":5}, read back as a Long. */
    @Test
    void anIntegerInAJsonObjectKeepsItsType() {
        JsonObject made = new JsonObject();
        made.put("n", 5);
        assertEquals("{\"n\":{\"@type\":\"Integer\",\"value\":5}}", JsonIo.toJson(made, null));
    }

    /** 4.117.0: [{"@type":"...Status","name":"NEW"}]. */
    @Test
    void anEnumInADeclaredListIsItsName() {
        Holder holder = new Holder();
        holder.statuses = new ArrayList<>(Collections.singletonList(Status.NEW));
        assertTrue(JsonIo.toJson(holder, null).contains("\"statuses\":[\"NEW\"]"));
    }

    /** 4.117.0: named by its own class, ...Rich$1. */
    @Test
    void anEnumConstantWithABodyIsNamedByItsEnum() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("r", Rich.A);
        String json = JsonIo.toJson(map, null);
        assertTrue(json.contains("{\"@type\":\"" + HERE + "Rich\",\"name\":\"A\"}"), json);
        assertFalse(json.contains("Rich$1"), json);
    }

    /** 4.117.0: no @type on the elements. */
    @Test
    void alwaysShowsTheTypeOfADeclaredCollectionsElements() {
        Order order = new Order();
        order.lines = new ArrayList<>(Collections.singletonList(new Line()));
        String json = JsonIo.toJson(order, new WriteOptionsBuilder().showTypeInfoAlways().build());
        assertTrue(json.contains("\"@items\":[{\"@type\":\"" + HERE + "Line\""), json);
    }

    /** 4.117.0: "1000". */
    @Test
    void aBigDecimalKeepsANegativeScale() {
        assertEquals("[{\"@type\":\"BigDecimal\",\"value\":\"1E+3\"}]",
                JsonIo.toJson(new Object[] {new BigDecimal("1E+3")}, null));
    }

    /** 4.117.0: {"v":x}, which is not JSON. */
    @Test
    void aCharacterInAMapIsText() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("v", 'x');
        assertEquals("{\"v\":\"x\"}", JsonIo.toJson(map, NEVER));
    }

    /** 4.117.0: written as "?". */
    @Test
    void anUnpairedSurrogateIsEscaped() {
        assertEquals("[\"a\\ud800b\"]", JsonIo.toJson(new String[] {"a\ud800b"}, NEVER));
    }

    /** 4.117.0: written 1, read back as a Long. */
    @Test
    void toonKeepsAWholeNumberDoubleADouble() {
        String toon = JsonIo.toToon(new Object[] {1.0d}, new WriteOptionsBuilder().showTypeInfoMinimal().build());
        assertEquals(1.0d, JsonIo.fromToon(toon, null).asClass(Object[].class)[0]);
    }
}
