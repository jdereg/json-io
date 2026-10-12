package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cedarsoftware.io.annotation.IoAnyGetter;
import com.cedarsoftware.io.annotation.IoShowType;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing an object whose values are objects with the same fields as a keyed table: the fields once in the header,
 * {@code servers[2:]{host,port}:}, then one row per entry, {@code alpha: a.example.com,8080} (TOON spec 9.5, new in
 * spec v4.0). json-io 4.113.0 and earlier wrote such an object field by field. A Map's entries are its entries, an
 * object's are its fields. Everything written here is read back, in strict and non-strict mode, and compared with
 * what was written.
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
class ToonWriteKeyedTabularTest {

    public static class Point {
        public int x;
        public int y;

        public Point() { }
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Point3 extends Point {
        public int z;

        public Point3() { }
        public Point3(int x, int y, int z) {
            super(x, y);
            this.z = z;
        }
    }

    public static class Route {
        public Point from;
        public Point to;

        public Route() { }
        public Route(Point from, Point to) {
            this.from = from;
            this.to = to;
        }
    }

    public static class Segment {
        public Route out;
        public Route back;
    }

    public static class Named {
        public String name;
        public Point from;
        public Point to;
    }

    public static class Counted {
        public int count;
        public Point from;
        public Point to;
    }

    public static class TwoMaps {
        public Map<String, String> first = new LinkedHashMap<>();
        public Map<String, String> second = new LinkedHashMap<>();
    }

    public static class Places {
        public Map<String, Point> byName = new LinkedHashMap<>();
        public Map<Long, Point> byId;
        public Map<Object, Point> byAnything;
        public Map<String, Object> anything;
        public Route route;
        public List<Route> routes;
    }

    public static class Flexible {
        public Point from;
        public Point to;
        private final transient Map<String, Object> extra = new LinkedHashMap<>();

        @IoAnyGetter
        public Map<String, Object> extra() {
            return extra;
        }
    }

    public static class FlexibleHolder {
        public Flexible flexible;
    }

    public static class Shown {
        public Route plain;
        @IoShowType
        public Route shown;
    }

    private static final WriteOptions DEFAULT = new WriteOptionsBuilder().build();
    private static final WriteOptions NO_NULLS = new WriteOptionsBuilder().skipNullFields(true).build();
    private static final WriteOptions LEGACY = new WriteOptionsBuilder().toonLegacyForms(true).build();

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** Written text, after it has been read back in both modes into {@code type} and found equal to {@code value}. */
    private static String toon(Object value, WriteOptions options, Class<?> type) {
        String toon = JsonIo.toToon(value, options);
        for (boolean strict : new boolean[] {true, false}) {
            Object back = JsonIo.fromToon(toon, new ReadOptionsBuilder().strictToon(strict).build()).asClass(type);
            Map<String, Object> diff = new LinkedHashMap<>();
            assertTrue(DeepEquals.deepEquals(value, back, diff),
                    (strict ? "strict" : "non-strict") + " read differs: " + diff.get("diff") + "\n" + toon);
        }
        return toon;
    }

    private static String toon(Object value) {
        return toon(value, DEFAULT, Object.class);
    }

    // ---------------------------------------------------------------- Maps

    @Test
    void anObjectOfUniformObjectsIsAKeyedTable() {
        Object doc = map("servers", map(
                "alpha", map("host", "a.example.com", "port", 8080L),
                "beta", map("host", "b.example.com", "port", 9090L)));

        assertEquals("servers[2:]{host,port}:\n"
                + "  alpha: a.example.com,8080\n"
                + "  beta: b.example.com,9090", toon(doc));
    }

    /** What 4.113.0 wrote, and what toonLegacyForms(true) still writes. */
    @Test
    void theLegacyFormsWriteTheObjectFieldByField() {
        Object doc = map("servers", map(
                "alpha", map("host", "a.example.com", "port", 8080L),
                "beta", map("host", "b.example.com", "port", 9090L)));

        assertEquals("servers:\n"
                + "  alpha:\n"
                + "    host: a.example.com\n"
                + "    port: 8080\n"
                + "  beta:\n"
                + "    host: b.example.com\n"
                + "    port: 9090", toon(doc, LEGACY, Object.class));
    }

    @Test
    void theRootObjectIsAKeyedTableWithNoKey() {
        Object doc = map("alice", map("age", 30L, "city", "Berlin"), "bob", map("age", 25L, "city", "Oslo"));

        assertEquals("[2:]{age,city}:\n"
                + "  alice: 30,Berlin\n"
                + "  bob: 25,Oslo", toon(doc));
        assertEquals("alice:\n"
                + "  age: 30\n"
                + "  city: Berlin\n"
                + "bob:\n"
                + "  age: 25\n"
                + "  city: Oslo", toon(doc, LEGACY, Object.class));
    }

    /** The first entry gives the field order; a nested object of uniform objects in a row is a nested field group. */
    @Test
    void theFirstEntryGivesTheFieldOrderAndObjectsInARowAreGroups() {
        Object doc = map(
                "m", map("a", map("x", 1L, "y", 2L), "b", map("y", 4L, "x", 3L)),
                "regions", map(
                        "eu", map("name", "Europe", "geo", map("lat", 50L, "lon", 10L)),
                        "us", map("geo", map("lon", -100L, "lat", 40L), "name", "America")),
                "deep", map(
                        "one", map("t", map("p", map("v", 1L), "q", map("v", 2L))),
                        "two", map("t", map("p", map("v", 3L), "q", map("v", 4L)))));

        assertEquals("m[2:]{x,y}:\n"
                + "  a: 1,2\n"
                + "  b: 3,4\n"
                + "regions[2:]{name,geo{lat,lon}}:\n"
                + "  eu: Europe,50,10\n"
                + "  us: America,40,-100\n"
                + "deep[2:]{t{p{v},q{v}}}:\n"
                + "  one: 1,2\n"
                + "  two: 3,4", toon(doc));
    }

    @Test
    void keysAndCellsAreQuotedAsKeysAndValuesWithTheDelimiterInUse() {
        Object doc = map("ids", map(
                "42", map("v", 1L, "note", "x|y", "n", null),
                "my key", map("v", 2L, "note", "p,q", "n", ""),
                "a:b", map("v", 3L, "note", "#3", "n", "-")));

        assertEquals("ids[3:]{v,note,n}:\n"
                + "  \"42\": 1,x|y,null\n"
                + "  \"my key\": 2,\"p,q\",\"\"\n"
                + "  \"a:b\": 3,\"#3\",\"-\"", toon(doc));
        assertEquals("ids[3:|]{v|note|n}:\n"
                + "  \"42\": 1|\"x|y\"|null\n"
                + "  \"my key\": 2|p,q|\"\"\n"
                + "  \"a:b\": 3|\"#3\"|\"-\"",
                toon(doc, new WriteOptionsBuilder().toonDelimiter('|').build(), Object.class));
        assertEquals("ids[3:\t]{v\tnote\tn}:\n"
                + "  \"42\": 1\tx|y\tnull\n"
                + "  \"my key\": 2\tp,q\t\"\"\n"
                + "  \"a:b\": 3\t\"#3\"\t\"-\"",
                toon(doc, new WriteOptionsBuilder().toonDelimiter('\t').build(), Object.class));
    }

    /** What is not an object of at least two uniform objects stays as it was written before. */
    @Test
    void anObjectThatIsNotOfUniformObjectsIsWrittenFieldByField() {
        Object doc = map(
                "single", map("only", map("a", 1L, "b", 2L)),
                "differing", map("dev", map("host", "x"), "prod", map("host", "y", "port", 1L)),
                "primitive", map("a", map("x", 1L), "b", 2L),
                "aNull", map("a", map("x", 1L), "b", null),
                "anArray", map("a", map("tags", listOf("x")), "b", map("tags", listOf("y"))),
                "empties", map("a", map(), "b", map()),
                "empty", map());

        assertEquals("single:\n"
                + "  only:\n"
                + "    a: 1\n"
                + "    b: 2\n"
                + "differing:\n"
                + "  dev:\n"
                + "    host: x\n"
                + "  prod:\n"
                + "    host: y\n"
                + "    port: 1\n"
                + "primitive:\n"
                + "  a:\n"
                + "    x: 1\n"
                + "  b: 2\n"
                + "aNull:\n"
                + "  a:\n"
                + "    x: 1\n"
                + "  b: null\n"
                + "anArray:\n"
                + "  a:\n"
                + "    tags[1]: x\n"
                + "  b:\n"
                + "    tags[1]: y\n"
                + "empties:\n"
                + "  a:\n"
                + "  b:\n"
                + "empty:", toon(doc));
    }

    /**
     * A list item is no field: it is never a keyed table itself (spec 9.5), but its fields are fields. The first
     * one's header stands on the hyphen line and its rows two levels below; another field's rows one below it.
     */
    @Test
    void aListItemIsNeverAKeyedTableButItsFieldsAre() {
        Object doc = map("items", listOf(
                map("a", map("x", 1L), "b", map("x", 2L)),
                map("config", map("a", map("x", 1L), "b", map("x", 2L)), "status", "ok"),
                map("status", "down", "config", map("a", map("x", 3L), "b", map("x", 4L))),
                5L));

        assertEquals("items[4]:\n"
                + "  - a:\n"
                + "      x: 1\n"
                + "    b:\n"
                + "      x: 2\n"
                + "  - config[2:]{x}:\n"
                + "      a: 1\n"
                + "      b: 2\n"
                + "    status: ok\n"
                + "  - status: down\n"
                + "    config[2:]{x}:\n"
                + "      a: 3\n"
                + "      b: 4\n"
                + "  - 5", toon(doc));
    }

    /** A keyed table inside a keyed table's entry is a nested field group; under another field it is a table again. */
    @Test
    void keyedTablesNestAsFieldsDo() {
        Object doc = map("outer", map(
                "inner", map("a", map("x", 1L), "b", map("x", 2L)),
                "other", "y"));

        assertEquals("outer:\n"
                + "  inner[2:]{x}:\n"
                + "    a: 1\n"
                + "    b: 2\n"
                + "  other: y", toon(doc));
    }

    /** With toonKeyFolding a folded path's value is written as it was; a field that is not folded is a keyed table. */
    @Test
    void aFoldedPathKeepsItsValuesForm() {
        Object doc = map(
                "a", map("b", map("p", map("x", 1L), "q", map("x", 2L))),
                "m", map("p", map("x", 1L), "q", map("x", 2L)));
        WriteOptions folding = new WriteOptionsBuilder().toonKeyFolding(true).build();

        String toon = JsonIo.toToon(doc, folding);

        assertEquals("a.b:\n"
                + "  p.x: 1\n"
                + "  q.x: 2\n"
                + "m[2:]{x}:\n"
                + "  p: 1\n"
                + "  q: 2", toon);
        Object back = JsonIo.fromToon(toon, new ReadOptionsBuilder().toonExpandPaths(true).build())
                .asClass(Object.class);
        assertTrue(DeepEquals.deepEquals(doc, back), toon);
    }

    // ---------------------------------------------------------------- objects

    /** An object whose fields all hold objects with the same fields is a keyed table too: its fields are the rows. */
    @Test
    void anObjectWhoseFieldsHoldUniformObjectsIsAKeyedTable() {
        Places places = new Places();
        places.byName.put("home", new Point(1, 2));
        places.byName.put("work", new Point(3, 4));
        places.byId = new LinkedHashMap<>();
        places.byId.put(7L, new Point(5, 6));
        places.byId.put(8L, new Point(7, 8));
        places.route = new Route(new Point(9, 10), new Point(11, 12));
        places.routes = new ArrayList<>(listOf(
                new Route(new Point(1, 1), new Point(2, 2)), new Route(new Point(3, 3), new Point(4, 4))));

        assertEquals("byName[2:]{x,y}:\n"
                + "  home: 1,2\n"
                + "  work: 3,4\n"
                + "byId[2:]{x,y}:\n"
                + "  \"7\": 5,6\n"
                + "  \"8\": 7,8\n"
                + "route[2:]{x,y}:\n"
                + "  from: 9,10\n"
                + "  to: 11,12\n"
                + "routes[2]{from{x,y},to{x,y}}:\n"
                + "  1,1,2,2\n"
                + "  3,3,4,4", toon(places, NO_NULLS, Places.class));
    }

    @Test
    void theRootObjectIsAKeyedTableWhenItsFieldsHoldUniformObjects() {
        Route route = new Route(new Point(1, 2), new Point(3, 4));
        Segment segment = new Segment();
        segment.out = route;
        segment.back = new Route(new Point(5, 6), new Point(7, 8));

        assertEquals("[2:]{x,y}:\n"
                + "  from: 1,2\n"
                + "  to: 3,4", toon(route, DEFAULT, Route.class));
        assertEquals("[2:]{from{x,y},to{x,y}}:\n"
                + "  out: 1,2,3,4\n"
                + "  back: 5,6,7,8",
                toon(segment, new WriteOptionsBuilder().cycleSupport(false).build(), Segment.class));
        assertEquals("from:\n"
                + "  x: 1\n"
                + "  y: 2\n"
                + "to:\n"
                + "  x: 3\n"
                + "  y: 4", toon(route, LEGACY, Route.class));
    }

    @Test
    void anObjectWithAnotherKindOfFieldIsWrittenFieldByField() {
        Named named = new Named();
        named.name = "n";
        named.from = new Point(1, 2);
        named.to = new Point(3, 4);
        Route oneNull = new Route(new Point(1, 2), null);
        Route twoClasses = new Route(new Point(1, 2), new Point3(3, 4, 5));

        assertEquals("name: n\n"
                + "from:\n"
                + "  x: 1\n"
                + "  y: 2\n"
                + "to:\n"
                + "  x: 3\n"
                + "  y: 4", toon(named, DEFAULT, Named.class));
        assertEquals("from:\n"
                + "  x: 1\n"
                + "  y: 2\n"
                + "to: null", toon(oneNull, DEFAULT, Route.class));
        // with the null field left out, one entry remains: still no table
        assertEquals("from:\n"
                + "  x: 1\n"
                + "  y: 2", toon(oneNull, NO_NULLS, Route.class));
        assertEquals("from:\n"
                + "  x: 1\n"
                + "  y: 2\n"
                + "to:\n"
                + "  z: 5\n"
                + "  x: 3\n"
                + "  y: 4", JsonIo.toToon(twoClasses, DEFAULT));
    }

    /** A field of a primitive type is a value like any other: an object that has one is no keyed table. */
    @Test
    void anObjectWithAPrimitiveFieldIsWrittenFieldByField() {
        Counted counted = new Counted();
        counted.count = 3;
        counted.from = new Point(1, 2);
        counted.to = new Point(3, 4);

        assertEquals("count: 3\n"
                + "from:\n"
                + "  x: 1\n"
                + "  y: 2\n"
                + "to:\n"
                + "  x: 3\n"
                + "  y: 4", toon(counted, DEFAULT, Counted.class));
    }

    /** An object whose fields hold like Maps is a keyed table; prettyPrint writes the object field by field. */
    @Test
    void anObjectOfLikeMapsIsAKeyedTableUnlessPrettyPrinted() {
        TwoMaps maps = new TwoMaps();
        maps.first.put("a", "1");
        maps.first.put("b", "x");
        maps.second.put("b", "y");
        maps.second.put("a", "2");

        assertEquals("[2:]{a,b}:\n"
                + "  first: \"1\",x\n"
                + "  second: \"2\",y", toon(maps, DEFAULT, TwoMaps.class));
        assertEquals("first:\n"
                + "  a: \"1\"\n"
                + "  b: x\n"
                + "second:\n"
                + "  b: y\n"
                + "  a: \"2\"", toon(maps, new WriteOptionsBuilder().prettyPrint(true).build(), TwoMaps.class));
    }

    /** With skipNullFields the fields that are left are the entries. */
    @Test
    void aFieldLeftOutIsNoEntry() {
        Named named = new Named();
        named.from = new Point(1, 2);
        named.to = new Point(3, 4);

        assertEquals("[2:]{x,y}:\n"
                + "  from: 1,2\n"
                + "  to: 3,4", toon(named, NO_NULLS, Named.class));
        assertEquals("name: null\n"
                + "from:\n"
                + "  x: 1\n"
                + "  y: 2\n"
                + "to:\n"
                + "  x: 3\n"
                + "  y: 4", toon(named, DEFAULT, Named.class));
    }

    @Test
    void prettyPrintWritesObjectsFieldByField() {
        Places places = new Places();
        places.byName.put("home", new Point(1, 2));
        places.byName.put("work", new Point(3, 4));
        places.route = new Route(new Point(9, 10), new Point(11, 12));
        places.anything = map("m", map("a", map("x", 1L), "b", map("x", 2L)));
        WriteOptions pretty = new WriteOptionsBuilder().prettyPrint(true).skipNullFields(true).build();

        assertEquals("byName:\n"
                + "  home:\n"
                + "    x: 1\n"
                + "    y: 2\n"
                + "  work:\n"
                + "    x: 3\n"
                + "    y: 4\n"
                + "anything:\n"
                + "  m[2:]{x}:\n"
                + "    a: 1\n"
                + "    b: 2\n"
                + "route:\n"
                + "  from:\n"
                + "    x: 9\n"
                + "    y: 10\n"
                + "  to:\n"
                + "    x: 11\n"
                + "    y: 12", toon(places, pretty, Places.class));
    }

    /** An object with an @IoAnyGetter is not written field by field: its fields are no entries. */
    @Test
    void anObjectWithAnAnyGetterIsWrittenAsBefore() {
        FlexibleHolder holder = new FlexibleHolder();
        holder.flexible = new Flexible();
        holder.flexible.from = new Point(1, 2);
        holder.flexible.to = new Point(3, 4);

        assertEquals("flexible:\n"
                + "  from:\n"
                + "    x: 1\n"
                + "    y: 2\n"
                + "  to:\n"
                + "    x: 3\n"
                + "    y: 4", JsonIo.toToon(holder, DEFAULT));
    }

    /** A map with a key that is itself an object is written as a list of entries, as before. */
    @Test
    void aMapWithAnObjectForAKeyIsNotAKeyedTable() {
        Map<Object, Object> byObject = new LinkedHashMap<>();
        byObject.put(map("k", 1L), map("x", 1L));
        byObject.put(map("k", 2L), map("x", 2L));
        Map<String, Object> doc = map("m", byObject);

        String toon = JsonIo.toToon(doc, DEFAULT);

        assertEquals(JsonIo.toToon(doc, LEGACY), toon);
        assertTrue(!toon.contains(":]"), toon);
    }

    /** ... wherever among the keys it stands: every key is looked at, not only the first. */
    @Test
    void aMapWithAnObjectForALaterKeyIsNotAKeyedTableEither() {
        Map<Object, Object> mixed = new LinkedHashMap<>();
        mixed.put("plain", map("x", 1L));
        mixed.put(map("k", 2L), map("x", 2L));
        Map<String, Object> doc = map("m", mixed);
        WriteOptions noReferences = new WriteOptionsBuilder().cycleSupport(false).build();

        String toon = JsonIo.toToon(doc, noReferences);

        assertTrue(toon.startsWith("m:\n  [2]:\n") && toon.contains("    $key: plain\n    $value:\n      x: 1\n")
                && toon.contains("    $key:\n      k: 2\n    $value:\n      x: 2"), toon);
        assertEquals(JsonIo.toToon(doc, new WriteOptionsBuilder().cycleSupport(false).toonLegacyForms(true).build()),
                toon);
    }

    // ---------------------------------------------------------------- shared objects

    /** A shared entry, or a shared map, needs its $id: such an object is written field by field. */
    @Test
    void aSharedObjectIsWrittenFieldByFieldWhenReferencesAreWritten() {
        Point shared = new Point(9, 9);
        Places sharedEntry = new Places();
        sharedEntry.byName.put("a", shared);
        sharedEntry.byName.put("b", shared);
        Places sharedMap = new Places();
        sharedMap.byName.put("a", new Point(1, 2));
        sharedMap.byName.put("b", new Point(3, 4));
        sharedMap.anything = map("same", sharedMap.byName);
        WriteOptions references = new WriteOptionsBuilder().cycleSupport(true).skipNullFields(true).build();
        WriteOptions noReferences = new WriteOptionsBuilder().cycleSupport(false).skipNullFields(true).build();

        String entryTwice = JsonIo.toToon(sharedEntry, references);
        String mapTwice = JsonIo.toToon(sharedMap, references);

        assertEquals("byName:\n"
                + "  a:\n"
                + "    $id: 1\n"
                + "    x: 9\n"
                + "    y: 9\n"
                + "  b:\n"
                + "    $ref: 1", entryTwice);
        Places back = JsonIo.fromToon(entryTwice, null).asClass(Places.class);
        assertSame(back.byName.get("a"), back.byName.get("b"));

        assertEquals("byName:\n"
                + "  $id: 1\n"
                + "  a:\n"
                + "    x: 1\n"
                + "    y: 2\n"
                + "  b:\n"
                + "    x: 3\n"
                + "    y: 4\n"
                + "anything:\n"
                + "  same:\n"
                + "    $ref: 1", mapTwice);

        assertEquals("byName[2:]{x,y}:\n"
                + "  a: 9,9\n"
                + "  b: 9,9", toon(sharedEntry, noReferences, Places.class));
    }

    // ---------------------------------------------------------------- type info

    /** With type info, what must carry its $type is written field by field; what need not is a keyed table. */
    @Test
    void anObjectThatCarriesItsTypeIsNotAKeyedTable() {
        Places places = new Places();
        places.byName.put("home", new Point(1, 2));             // Map<String, Point>: everything is declared
        places.byName.put("work", new Point(3, 4));
        places.byId = new LinkedHashMap<>();                    // Map<Long, Point>: the keys are declared too
        places.byId.put(7L, new Point(5, 6));
        places.byId.put(8L, new Point(7, 8));
        places.anything = map("m", map("a", new Point(1, 1), "b", new Point(2, 2)));    // values must say what they are
        places.route = new Route(new Point(9, 10), new Point3(11, 12, 13));             // one field holds a subclass
        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().skipNullFields(true).build();

        assertEquals("$type: " + Places.class.getName() + "\n"
                + "byName[2:]{x,y}:\n"
                + "  home: 1,2\n"
                + "  work: 3,4\n"
                + "byId[2:]{x,y}:\n"
                + "  \"7\": 5,6\n"
                + "  \"8\": 7,8\n"
                + "anything:\n"
                + "  m:\n"
                + "    $type: LinkedHashMap\n"
                + "    a:\n"
                + "      $type: " + Point.class.getName() + "\n"
                + "      x: 1\n"
                + "      y: 1\n"
                + "    b:\n"
                + "      $type: " + Point.class.getName() + "\n"
                + "      x: 2\n"
                + "      y: 2\n"
                + "route:\n"
                + "  from:\n"
                + "    x: 9\n"
                + "    y: 10\n"
                + "  to:\n"
                + "    $type: " + Point3.class.getName() + "\n"
                + "    z: 13\n"
                + "    x: 11\n"
                + "    y: 12", toon(places, minimal, Places.class));
    }

    @Test
    void aMapWhoseClassOrKeysMustBeWrittenIsNotAKeyedTable() {
        Places sorted = new Places();
        sorted.byName = new TreeMap<>();
        sorted.byName.put("home", new Point(1, 2));
        sorted.byName.put("work", new Point(3, 4));
        Places anyKeys = new Places();
        anyKeys.byAnything = new LinkedHashMap<>();
        anyKeys.byAnything.put(7L, new Point(5, 6));
        anyKeys.byAnything.put(8L, new Point(7, 8));
        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().skipNullFields(true).build();
        String type = "$type: " + Places.class.getName() + "\n";

        assertEquals(type
                + "byName:\n"
                + "  $type: TreeMap\n"
                + "  home:\n"
                + "    x: 1\n"
                + "    y: 2\n"
                + "  work:\n"
                + "    x: 3\n"
                + "    y: 4", toon(sorted, minimal, Places.class));
        String keysAsList = toon(anyKeys, minimal, Places.class);
        assertEquals(type
                + "byName:\n"
                + "byAnything:\n"
                + "  $keys[2]: 7,8\n"
                + "  $items[2]{x,y}:\n"
                + "    5,6\n"
                + "    7,8", keysAsList);
        // with no type info nothing is written about either, and both are keyed tables
        assertEquals("byName[2:]{x,y}:\n"
                + "  home: 1,2\n"
                + "  work: 3,4", JsonIo.toToon(sorted, NO_NULLS));
        assertEquals("byName:\n"
                + "byAnything[2:]{x,y}:\n"
                + "  \"7\": 5,6\n"
                + "  \"8\": 7,8", JsonIo.toToon(anyKeys, NO_NULLS));
    }

    @Test
    void theRootIsNotAKeyedTableWhenItCarriesItsType() {
        Route route = new Route(new Point(1, 2), new Point(3, 4));
        String fieldByField = "from:\n"
                + "  x: 1\n"
                + "  y: 2\n"
                + "to:\n"
                + "  x: 3\n"
                + "  y: 4";

        assertEquals("$type: " + Route.class.getName() + "\n" + fieldByField,
                toon(route, new WriteOptionsBuilder().showTypeInfoMinimal().build(), Route.class));
        assertEquals("[2:]{x,y}:\n"
                + "  from: 1,2\n"
                + "  to: 3,4",
                toon(route, new WriteOptionsBuilder().showTypeInfoMinimal().omitRootTypeInfo().build(), Route.class));
        assertTrue(JsonIo.toToon(route, new WriteOptionsBuilder().showTypeInfoAlways().build())
                .contains("  $type: " + Point.class.getName()));
    }

    /** @IoShowType asks for the $type of a field's value, with type info off too: such a value is no keyed table. */
    @Test
    void aShowTypeFieldIsNotAKeyedTable() {
        Shown holder = new Shown();
        holder.plain = new Route(new Point(1, 2), new Point(3, 4));
        holder.shown = new Route(new Point(5, 6), new Point(7, 8));

        assertEquals("plain[2:]{x,y}:\n"
                + "  from: 1,2\n"
                + "  to: 3,4\n"
                + "shown:\n"
                + "  $type: " + Route.class.getName() + "\n"
                + "  from:\n"
                + "    x: 5\n"
                + "    y: 6\n"
                + "  to:\n"
                + "    x: 7\n"
                + "    y: 8", toon(holder, DEFAULT, Shown.class));
    }
}
