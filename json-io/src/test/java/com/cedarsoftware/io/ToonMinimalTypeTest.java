package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.MultiKeyMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOON with type info turned on ({@code showTypeInfoMinimal()}, {@code showTypeInfoMinimalPlus()},
 * {@code showTypeInfoAlways()}). Minimal writes {@code $type} only where the declared type does not determine the
 * class the reader builds -- a subclass in a field or list, anything in an {@code Object} slot, an exception's cause
 * -- so a graph of plain DTOs costs one line (the root's type) over TOON without types, and a polymorphic graph reads
 * back with its classes. The types whose class name is not what json-io reads (a CompactMap's generated subclass, an
 * EnumSet's RegularEnumSet) are written as JSON writes them.
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
class ToonMinimalTypeTest {

    private static final WriteOptions MINIMAL = new WriteOptionsBuilder().showTypeInfoMinimal().build();
    private static final WriteOptions MINIMAL_PLUS = new WriteOptionsBuilder().showTypeInfoMinimalPlus().build();
    private static final WriteOptions ALWAYS = new WriteOptionsBuilder().showTypeInfoAlways().build();
    private static final WriteOptions[] TYPED = {MINIMAL, MINIMAL_PLUS, ALWAYS};

    public enum Color { RED, GREEN, BLUE }

    public static class Person {
        public String name;
        public int age;
        public List<String> tags;

        public Person() { }

        Person(String name, int age, String... tags) {
            this.name = name;
            this.age = age;
            this.tags = new ArrayList<>(Arrays.asList(tags));
        }
    }

    public static class Team {
        public String name;
        public Person lead;
        public List<Person> people;
        public Map<String, Person> byName;
        public Set<String> labels;
        public List<Map<String, String>> notes;
    }

    private static Team team() {
        Team team = new Team();
        team.name = "core";
        team.lead = new Person("ann", 41, "x");
        team.people = new ArrayList<>(Arrays.asList(new Person("bob", 30, "a", "b"), new Person("cy", 25, "c")));
        team.byName = new LinkedHashMap<>();
        team.byName.put("dee", new Person("dee", 33, "d"));
        team.labels = new java.util.LinkedHashSet<>(Arrays.asList("fast", "small"));
        team.notes = new ArrayList<>();
        for (String text : new String[] {"one", "two"}) {
            Map<String, String> note = new LinkedHashMap<>();
            note.put("text", text);
            team.notes.add(note);
        }
        return team;
    }

    private static String typeLine(Class<?> c) {
        return "$type: " + c.getName() + "\n";
    }

    // ---------- Where the declared type determines the class: no $type ----------

    /**
     * A graph of plain DTOs: lists, sets and maps of the natural classes (a list of maps too), fields of their
     * declared class.
     */
    @Test
    void declaredTypesThatDetermineTheClassAreNotWritten() {
        Team team = team();
        String plain = JsonIo.toToon(team, null);
        for (WriteOptions options : new WriteOptions[] {MINIMAL, MINIMAL_PLUS}) {
            assertEquals(typeLine(Team.class) + plain, JsonIo.toToon(team, options));
        }
        WriteOptions noRoot = new WriteOptionsBuilder().showTypeInfoMinimal().omitRootTypeInfo().build();
        assertEquals(plain, JsonIo.toToon(team, noRoot));
    }

    // ---------- Where it does not: $type, and the classes read back ----------

    public abstract static class Shape {
        public String name;
    }

    public static class Circle extends Shape {
        public double radius;

        public Circle() { }

        Circle(String name, double radius) {
            this.name = name;
            this.radius = radius;
        }
    }

    public static class Square extends Shape {
        public double side;

        public Square() { }

        Square(String name, double side) {
            this.name = name;
            this.side = side;
        }
    }

    public static class Drawing {
        public Shape main;
        public List<Shape> shapes;
        public List<Shape> circles;
        public Shape[] array;
        public Map<String, Shape> byName;
        public Object anything;
        public Object[] objects;
        public Exception problem;
    }

    private static Drawing drawing() {
        Drawing d = new Drawing();
        d.main = new Circle("sun", 9);
        d.shapes = new ArrayList<>(Arrays.asList(new Circle("c", 1), new Square("s", 2)));
        d.circles = new ArrayList<>(Arrays.asList(new Circle("c1", 1), new Circle("c2", 2)));
        d.array = new Shape[] {new Square("a", 3), new Circle("b", 4)};
        d.byName = new LinkedHashMap<>();
        d.byName.put("one", new Square("one", 5));
        d.anything = new BigDecimal("1.25");
        d.objects = new Object[] {new BigDecimal("2.5"), "text", 7L, new Circle("o", 6)};
        d.problem = new RuntimeException("outer", new IllegalStateException("inner"));
        return d;
    }

    private static void assertDrawing(Drawing back, String toon) {
        assertInstanceOf(Circle.class, back.main, toon);
        assertEquals(9.0, ((Circle) back.main).radius, toon);
        assertInstanceOf(Circle.class, back.shapes.get(0), toon);
        assertInstanceOf(Square.class, back.shapes.get(1), toon);
        assertEquals(2.0, ((Square) back.shapes.get(1)).side, toon);
        assertInstanceOf(Circle.class, back.circles.get(0), toon);
        assertInstanceOf(Circle.class, back.circles.get(1), toon);
        assertEquals("c2", back.circles.get(1).name, toon);
        assertInstanceOf(Square.class, back.array[0], toon);
        assertInstanceOf(Circle.class, back.array[1], toon);
        assertInstanceOf(Square.class, back.byName.get("one"), toon);
        assertEquals(new BigDecimal("1.25"), back.anything, toon);
        assertInstanceOf(BigDecimal.class, back.objects[0], toon);
        assertEquals("text", back.objects[1], toon);
        assertEquals(7L, back.objects[2], toon);
        assertInstanceOf(Circle.class, back.objects[3], toon);
        assertSame(RuntimeException.class, back.problem.getClass(), toon);
        assertEquals("outer", back.problem.getMessage(), toon);
        assertSame(IllegalStateException.class, back.problem.getCause().getClass(), toon);
        assertEquals("inner", back.problem.getCause().getMessage(), toon);
    }

    @Test
    void polymorphicFieldsListsArraysMapsAndExceptionsReadBack() {
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(drawing(), options);
            assertDrawing(JsonIo.fromToon(toon, null).asClass(Drawing.class), toon);
            // the root's type is written, so no class need be named on reading
            assertDrawing((Drawing) JsonIo.fromToon(toon, null).asClass(null), toon);
        }
    }

    /** Only the values whose class the declared type does not give carry it. */
    @Test
    void minimalTypesOnlyWhatTheDeclarationDoesNotGive() {
        Drawing d = new Drawing();
        d.main = new Circle("sun", 9);
        d.anything = new BigDecimal("1.25");
        String toon = JsonIo.toToon(d, new WriteOptionsBuilder().showTypeInfoMinimal().omitRootTypeInfo().build());
        assertEquals("main:\n"
                + "  $type: " + Circle.class.getName() + "\n"
                + "  radius: 9\n"
                + "  name: sun\n"
                + "shapes: null\n"
                + "circles: null\n"
                + "array: null\n"
                + "byName: null\n"
                + "anything:\n"
                + "  $type: BigDecimal\n"
                + "  value: \"1.25\"\n"
                + "objects: null\n"
                + "problem: null", toon.trim());
    }

    // ---------- Scalars where the declaration does not give their class ----------

    public static class Slots {
        public Object number;
        public Object text;
        public Object whole;
        public Object integer;
        public Object color;
        public Object chars;
        public Object optional;
        public List<Object> list;
        public Map<String, Object> map;
    }

    @Test
    void scalarsInObjectSlotsKeepTheirClass() {
        Slots s = new Slots();
        s.number = new BigDecimal("3.5");
        s.text = "plain";
        s.whole = 12L;
        s.integer = 5;
        s.color = Color.GREEN;
        s.chars = new char[] {'h', 'i'};
        s.optional = Optional.of("in");
        s.list = new ArrayList<>(Arrays.asList(new BigDecimal("1.5"), 6, Color.RED, "s"));
        s.map = new LinkedHashMap<>();
        s.map.put("d", new BigDecimal("2.5"));
        s.map.put("i", 8);
        for (WriteOptions options : new WriteOptions[] {MINIMAL, ALWAYS}) {
            String toon = JsonIo.toToon(s, options);
            Slots back = JsonIo.fromToon(toon, null).asClass(Slots.class);
            assertEquals(new BigDecimal("3.5"), back.number, toon);
            assertEquals("plain", back.text, toon);
            assertEquals(12L, back.whole, toon);
            assertEquals(5, back.integer, toon);
            assertSame(Color.GREEN, back.color, toon);
            assertArrayEquals(new char[] {'h', 'i'}, (char[]) back.chars, toon);
            assertEquals(Optional.of("in"), back.optional, toon);
            assertEquals(Arrays.asList(new BigDecimal("1.5"), 6, Color.RED, "s"), back.list, toon);
            assertEquals(new BigDecimal("2.5"), back.map.get("d"), toon);
            assertEquals(8, back.map.get("i"), toon);
        }
    }

    public static class Holders {
        public Optional<BigDecimal> number;
        public Optional<Object> anything;
        public Optional<Shape> shape;
        public List<Optional<Object>> list;
        public java.util.OptionalInt count;
        public Object optionalInObject;
        public Object countInObject;
        public Object semaphore;
        public Object references;
        public java.util.concurrent.atomic.AtomicReferenceArray<Object> referenceField;
    }

    /**
     * An object written as what it holds (an Optional's content) keeps that content's class: by its field's type
     * argument where that gives it (Optional&lt;BigDecimal&gt;), with its type where not. A holder with no type
     * parameter (OptionalInt) says what it holds; one in an Object slot carries its own type.
     */
    @Test
    void holdersKeepWhatTheyHold() {
        Holders h = new Holders();
        h.number = Optional.of(new BigDecimal("1.5"));
        h.anything = Optional.of(new BigDecimal("2.5"));
        h.shape = Optional.of(new Circle("c", 1));
        h.list = new ArrayList<>(Arrays.asList(Optional.of(new BigDecimal("3.5")), Optional.empty()));
        h.count = java.util.OptionalInt.of(4);
        h.optionalInObject = Optional.of(7);
        h.countInObject = java.util.OptionalInt.of(5);
        h.semaphore = new java.util.concurrent.Semaphore(3, true);
        h.references = new java.util.concurrent.atomic.AtomicReferenceArray<>(new Object[] {new BigDecimal("6.5"), "x"});
        h.referenceField = new java.util.concurrent.atomic.AtomicReferenceArray<>(new Object[] {new Square("s", 2)});
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(h, options);
            Holders back = JsonIo.fromToon(toon, null).asClass(Holders.class);
            assertEquals(Optional.of(new BigDecimal("1.5")), back.number, toon);
            assertEquals(Optional.of(new BigDecimal("2.5")), back.anything, toon);
            assertInstanceOf(Circle.class, back.shape.get(), toon);
            assertEquals(Arrays.asList(Optional.of(new BigDecimal("3.5")), Optional.empty()), back.list, toon);
            assertEquals(java.util.OptionalInt.of(4), back.count, toon);
            // minimal-plus writes an int in an Object slot plain, so it reads back as a long
            assertEquals(Optional.of(options == MINIMAL_PLUS ? (Object) 7L : (Object) 7), back.optionalInObject, toon);
            assertEquals(java.util.OptionalInt.of(5), back.countInObject, toon);
            assertEquals(3, ((java.util.concurrent.Semaphore) back.semaphore).availablePermits(), toon);
            assertTrue(((java.util.concurrent.Semaphore) back.semaphore).isFair(), toon);
            java.util.concurrent.atomic.AtomicReferenceArray<?> refs =
                    (java.util.concurrent.atomic.AtomicReferenceArray<?>) back.references;
            assertEquals(new BigDecimal("6.5"), refs.get(0), toon);
            assertEquals("x", refs.get(1), toon);
            assertInstanceOf(Square.class, back.referenceField.get(0), toon);
        }
        // Where the type argument gives the content's class, the content carries none
        String minimal = JsonIo.toToon(h, MINIMAL);
        assertTrue(minimal.contains("\nnumber: \"1.5\"\n"), minimal);     // a BigDecimal, its text
        assertTrue(minimal.contains("\ncount: 4\n"), minimal);
    }

    /** Minimal-plus lets an int in an Object slot read back as a long, as JSON's minimal-plus does. */
    @Test
    void minimalPlusWritesSmallNumbersPlain() {
        Slots s = new Slots();
        s.integer = 5;
        s.number = new BigDecimal("3.5");
        String toon = JsonIo.toToon(s, MINIMAL_PLUS);
        assertTrue(toon.contains("\ninteger: 5\n"), toon);
        Slots back = JsonIo.fromToon(toon, null).asClass(Slots.class);
        assertEquals(5L, back.integer, toon);
        assertEquals(new BigDecimal("3.5"), back.number, toon);
    }

    // ---------- The types json-io writes under another name ----------

    private static Map<String, Object> caseInsensitiveCompactMap() {
        Map<String, Object> map = CompactMap.<String, Object>builder().caseSensitive(false).compactSize(5).build();
        map.put("Key", "v");
        map.put("Other", new BigDecimal("4.5"));
        return map;
    }

    public static class Compacts {
        public Map<String, Object> map;
        public Set<String> set;
    }

    @Test
    void compactMapAndCompactSetKeepTheirConfiguration() {
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(caseInsensitiveCompactMap(), options);
            Map<?, ?> back = (Map<?, ?>) JsonIo.fromToon(toon, null).asClass(null);
            assertInstanceOf(CompactMap.class, back, toon);
            assertEquals("v", back.get("KEY"), toon);
            assertEquals(new BigDecimal("4.5"), back.get("other"), toon);

            Compacts c = new Compacts();
            c.map = caseInsensitiveCompactMap();
            c.set = CompactSet.<String>builder().caseSensitive(false).build();
            c.set.add("Abc");
            toon = JsonIo.toToon(c, options);
            Compacts cb = JsonIo.fromToon(toon, null).asClass(Compacts.class);
            assertEquals("v", cb.map.get("KEY"), toon);
            assertInstanceOf(CompactSet.class, cb.set, toon);
            assertTrue(cb.set.contains("ABC"), toon);
        }
    }

    @Test
    void multiKeyMapReadsBack() {
        MultiKeyMap<String> map = new MultiKeyMap<>();
        map.putMultiKey("v", "a", "b");
        map.put("single", "w");
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(map, options);
            MultiKeyMap<?> back = (MultiKeyMap<?>) JsonIo.fromToon(toon, null).asClass(null);
            assertEquals("v", back.getMultiKey("a", "b"), toon);
            assertEquals("w", back.get("single"), toon);
            if (options != ALWAYS) {
                assertFalse(toon.contains("LinkedHashMap"), toon);   // its entries are plain maps, as in JSON
            }
        }
    }

    public static class Configured {
        public MultiKeyMap<String> keys;
    }

    /**
     * In a field of its own class, which needs no $type, a container's configuration is still written, as JSON writes
     * it: TOON wrote the map's entries alone, and a case-insensitive MultiKeyMap came back case-sensitive.
     */
    @Test
    void aContainersConfigurationIsWrittenWhereItsClassGoesWithoutSaying() {
        Configured c = new Configured();
        c.keys = MultiKeyMap.<String>builder().caseSensitive(false).build();
        c.keys.put("Key", "v");
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(c, options);
            Configured back = JsonIo.fromToon(toon, null).asClass(Configured.class);
            assertEquals("v", back.keys.get("KEY"), toon);
            assertFalse(back.keys.getCaseSensitive(), toon);
        }
    }

    public static class Sets {
        public Set<Color> colors;
        public EnumSet<TimeUnit> units;
    }

    @Test
    void enumSetsReadBackAtTheRootAndInFields() {
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(EnumSet.of(Color.RED, Color.BLUE), options);
            assertEquals(EnumSet.of(Color.RED, Color.BLUE), JsonIo.fromToon(toon, null).asClass(null), toon);
            toon = JsonIo.toToon(EnumSet.noneOf(Color.class), options);
            assertEquals(EnumSet.noneOf(Color.class), JsonIo.fromToon(toon, null).asClass(null), toon);

            Sets s = new Sets();
            s.colors = EnumSet.of(Color.GREEN);
            s.units = EnumSet.of(TimeUnit.SECONDS, TimeUnit.DAYS);
            toon = JsonIo.toToon(s, options);
            Sets back = JsonIo.fromToon(toon, null).asClass(Sets.class);
            assertEquals(EnumSet.of(Color.GREEN), back.colors, toon);
            assertInstanceOf(EnumSet.class, back.colors, toon);
            assertEquals(EnumSet.of(TimeUnit.SECONDS, TimeUnit.DAYS), back.units, toon);
        }
    }

    // ---------- Map keys the declaration does not give ----------

    @Test
    void mapKeysKeepTheirClassWhereNothingDeclaresIt() {
        Map<Object, String> map = new LinkedHashMap<>();
        map.put(7, "int");
        map.put(8L, "long");
        map.put(Color.RED, "enum");
        map.put("s", "string");
        for (WriteOptions options : new WriteOptions[] {MINIMAL, ALWAYS}) {
            String toon = JsonIo.toToon(map, options);
            assertEquals(map, JsonIo.fromToon(toon, null).asClass(null), toon);
        }
        Map<Integer, String> numbers = new HashMap<>();
        numbers.put(1, "one");
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(numbers, options);
            Map<?, ?> back = (Map<?, ?>) JsonIo.fromToon(toon, null).asClass(null);
            assertInstanceOf(HashMap.class, back, toon);
            Object key = back.keySet().iterator().next();
            assertEquals(options == MINIMAL_PLUS ? (Object) 1L : (Object) 1, key, toon);
        }
    }

    // ---------- The root ----------

    @Test
    void rootsOfEveryKindReadBackWithNoClassNamed() {
        for (WriteOptions options : new WriteOptions[] {MINIMAL, ALWAYS}) {
            List<Object> list = new ArrayList<>(Arrays.asList(new BigDecimal("1.5"), 2, new Circle("c", 1)));
            String toon = JsonIo.toToon(list, options);
            assertEquals(Arrays.asList(new BigDecimal("1.5"), 2), ((List<?>) JsonIo.fromToon(toon, null)
                    .asClass(null)).subList(0, 2), toon);
            assertInstanceOf(Circle.class, ((List<?>) JsonIo.fromToon(toon, null).asClass(null)).get(2), toon);

            toon = JsonIo.toToon(new BigDecimal("6.25"), options);
            assertEquals(new BigDecimal("6.25"), JsonIo.fromToon(toon, null).asClass(null), toon);

            Exception e = new IllegalArgumentException("outer", new IllegalStateException("inner"));
            toon = JsonIo.toToon(e, options);
            Throwable back = (Throwable) JsonIo.fromToon(toon, null).asClass(null);
            assertSame(IllegalArgumentException.class, back.getClass(), toon);
            assertSame(IllegalStateException.class, back.getCause().getClass(), toon);

            toon = JsonIo.toToon(Collections.unmodifiableList(Arrays.asList("a", "b")), options);
            assertEquals(Arrays.asList("a", "b"), JsonIo.fromToon(toon, null).asClass(null), toon);
        }
    }

    /** With the root's type left off, a root list's elements are still resolved from their own types. */
    @Test
    void rootListWithoutItsTypeStillResolvesTypedElements() {
        WriteOptions noRoot = new WriteOptionsBuilder().showTypeInfoMinimal().omitRootTypeInfo().build();
        List<Shape> shapes = new ArrayList<>(Arrays.asList(new Circle("c", 1), new Square("s", 2)));
        String toon = JsonIo.toToon(shapes, noRoot);
        List<?> back = JsonIo.fromToon(toon, null).asClass(List.class);
        assertInstanceOf(Circle.class, back.get(0), toon);
        assertInstanceOf(Square.class, back.get(1), toon);

        List<Object> mixed = new ArrayList<>(Arrays.asList(new BigDecimal("1.5"), "x"));
        toon = JsonIo.toToon(mixed, noRoot);
        assertEquals(mixed, JsonIo.fromToon(toon, null).asClass(List.class), toon);
    }

    // ---------- Where TOON writes values on one line, or as a table ----------

    public static class Line {
        public String name;
        public Object amount;

        public Line() { }

        Line(String name, Object amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    public static class Ledger {
        public List<Line> lines;
        public Object[] scalars;
        public List<Map<String, Object>> rows;
    }

    /** A value that carries its type cannot sit in a table's cell or on a list's line: the rows are written out. */
    @Test
    void typedValuesAreNotFlattenedIntoTablesOrLines() {
        Ledger ledger = new Ledger();
        ledger.lines = new ArrayList<>(Arrays.asList(new Line("a", new BigDecimal("1.5")),
                new Line("b", new BigDecimal("2.5"))));
        ledger.scalars = new Object[] {new BigDecimal("3.5"), "x"};
        ledger.rows = new ArrayList<>();
        for (String amount : new String[] {"4.5", "5.5"}) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("amount", new BigDecimal(amount));
            ledger.rows.add(row);
        }
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(ledger, options);
            Ledger back = JsonIo.fromToon(toon, null).asClass(Ledger.class);
            assertEquals(new BigDecimal("1.5"), back.lines.get(0).amount, toon);
            assertEquals(new BigDecimal("2.5"), back.lines.get(1).amount, toon);
            assertArrayEquals(new Object[] {new BigDecimal("3.5"), "x"}, back.scalars, toon);
            assertEquals(new BigDecimal("4.5"), back.rows.get(0).get("amount"), toon);
            assertEquals(new BigDecimal("5.5"), back.rows.get(1).get("amount"), toon);
        }
    }

    public static class Circles {
        public List<Circle> circles;
    }

    /** A field's declared element type is for that field's list, not for the next list written after it. */
    @Test
    void aFieldsElementTypeStaysWithItsField() {
        List<Object> outer = new ArrayList<>();
        outer.add(new Circles());                                            // circles: null
        outer.add(new ArrayList<>(Arrays.asList(new Circle("c", 1))));      // a raw list: its Circle needs its type
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", new Circles());
        map.put("b", new ArrayList<>(Arrays.asList(new Circle("c", 1))));
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(outer, options);
            List<?> back = (List<?>) JsonIo.fromToon(toon, null).asClass(null);
            assertInstanceOf(Circle.class, ((List<?>) back.get(1)).get(0), toon);
            toon = JsonIo.toToon(map, options);
            Map<?, ?> backMap = (Map<?, ?>) JsonIo.fromToon(toon, null).asClass(null);
            assertInstanceOf(Circle.class, ((List<?>) backMap.get("b")).get(0), toon);
        }
    }

    public static class Empties {
        public Map<String, Object> map;
        public List<String> list;
        public Object any;
    }

    /** An empty map or list still says what it is, where its declared type does not. */
    @Test
    void emptyContainersKeepTheirClass() {
        Empties e = new Empties();
        e.map = new HashMap<>();
        e.list = new java.util.LinkedList<>();
        e.any = new java.util.TreeMap<>();
        for (WriteOptions options : TYPED) {
            String toon = JsonIo.toToon(e, options);
            Empties back = JsonIo.fromToon(toon, null).asClass(Empties.class);
            assertSame(HashMap.class, back.map.getClass(), toon);
            assertSame(java.util.LinkedList.class, back.list.getClass(), toon);
            assertSame(java.util.TreeMap.class, back.any.getClass(), toon);
        }
    }

    // ---------- With cycles ----------

    public static class TwoMaps {
        public Map<String, Object> first;
        public Map<String, Object> second;
    }

    /** An object written through its custom writer's data keeps its identity: written once, referenced after. */
    @Test
    void sharedCompactMapStaysShared() {
        TwoMaps two = new TwoMaps();
        two.first = caseInsensitiveCompactMap();
        two.second = two.first;
        for (WriteOptions options : TYPED) {
            WriteOptions cycles = new WriteOptionsBuilder(options).cycleSupport(true).build();
            String toon = JsonIo.toToon(two, cycles);
            TwoMaps back = JsonIo.fromToon(toon, null).asClass(TwoMaps.class);
            assertSame(back.first, back.second, toon);
            assertEquals("v", back.first.get("KEY"), toon);
        }
    }

    public static class Node {
        public String name;
        public Object next;
    }

    @Test
    void referencesAndTypesTogether() {
        Node a = new Node();
        Node b = new Node();
        a.name = "a";
        b.name = "b";
        a.next = b;
        b.next = a;
        for (WriteOptions options : TYPED) {
            WriteOptions cycles = new WriteOptionsBuilder(options).cycleSupport(true).build();
            String toon = JsonIo.toToon(a, cycles);
            Node back = JsonIo.fromToon(toon, null).asClass(Node.class);
            assertEquals("b", ((Node) back.next).name, toon);
            assertSame(back, ((Node) back.next).next, toon);
        }
    }

    /** Type info off (the TOON default): no $type, whatever the graph. */
    @Test
    void defaultWritesNoTypes() {
        assertFalse(JsonIo.toToon(drawing(), null).contains("$type"));
        assertFalse(JsonIo.toToon(caseInsensitiveCompactMap(), null).contains("$type"));
    }
}
