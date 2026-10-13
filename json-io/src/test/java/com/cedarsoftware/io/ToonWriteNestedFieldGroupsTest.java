package com.cedarsoftware.io;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.cedarsoftware.io.annotation.IoAnyGetter;
import com.cedarsoftware.io.annotation.IoFormat;
import com.cedarsoftware.io.annotation.IoShowType;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing a table whose column holds objects with the same fields: the column is a nested field group in the
 * header, {@code orders[2]{id,customer{name,country},total}:}, and each row carries one cell per leaf field in the
 * header's depth-first order (TOON spec 9.3, new in spec v4.0). json-io 4.113.0 and earlier wrote such a list item
 * by item. Everything written here is read back, in strict and non-strict mode, and compared with what was written.
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
class ToonWriteNestedFieldGroupsTest {

    public static class Customer {
        public String name;
        public String country;

        public Customer() { }
        public Customer(String name, String country) {
            this.name = name;
            this.country = country;
        }
    }

    public static class VipCustomer extends Customer {
        public int level = 1;

        public VipCustomer() { }
        public VipCustomer(String name, String country) {
            super(name, country);
        }
    }

    public static class Order {
        public int id;
        public Customer customer;
        public double total;

        public Order() { }
        public Order(int id, Customer customer, double total) {
            this.id = id;
            this.customer = customer;
            this.total = total;
        }
    }

    public static class Geo {
        public Point point;
        public Geo() { }
        public Geo(double lat, double lon) {
            point = new Point(lat, lon);
        }
    }

    public static class Point {
        public double lat;
        public double lon;
        public Point() { }
        public Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static class Place {
        public int id;
        public Geo geo;
        public Map<String, String> labels;

        public Place() { }
        public Place(int id, double lat, double lon, Map<String, String> labels) {
            this.id = id;
            this.geo = new Geo(lat, lon);
            this.labels = labels;
        }
    }

    public static class Shop {
        public List<Order> orders = new ArrayList<>();
        public Order[] array;
        public List<Object> anything;
        public List<Place> places;
        @IoShowType
        public List<Order> shown;
    }

    public static class Tagged {
        public int id;
        @IoShowType
        public Customer customer;

        public Tagged() { }
        public Tagged(int id, Customer customer) {
            this.id = id;
            this.customer = customer;
        }
    }

    public static class TaggedHolder {
        public List<Tagged> items = new ArrayList<>();
    }

    public static class Dated {
        @IoFormat("dd/MM/yyyy")
        public LocalDate day;
        @IoFormat("%07.2f")
        public double amount;
        public Customer customer;

        public Dated() { }
        public Dated(LocalDate day, double amount, Customer customer) {
            this.day = day;
            this.amount = amount;
            this.customer = customer;
        }
    }

    public static class DatedHolder {
        public List<Dated> items = new ArrayList<>();
    }

    public static class Flexible {
        public String id;
        private final transient Map<String, Object> extra = new LinkedHashMap<>();

        public Flexible() { }
        public Flexible(String id) {
            this.id = id;
            extra.put("more", id + "!");
        }

        @IoAnyGetter
        public Map<String, Object> extra() {
            return extra;
        }
    }

    public static class Oddities {
        public int n;
        public Flexible flexible;
        public Optional<String> label;

        public Oddities() { }
        public Oddities(int n, Flexible flexible, Optional<String> label) {
            this.n = n;
            this.flexible = flexible;
            this.label = label;
        }
    }

    public static class OdditiesHolder {
        public List<Oddities> items = new ArrayList<>();
    }

    private static final WriteOptions DEFAULT = new WriteOptionsBuilder().build();
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
    void aColumnOfUniformObjectsIsANestedFieldGroup() {
        Object doc = map("orders", listOf(
                map("id", 1L, "customer", map("name", "Ada", "country", "DK"), "total", 99L),
                map("id", 2L, "customer", map("name", "Bob", "country", "UK"), "total", 149L)));

        assertEquals("orders[2]{id,customer{name,country},total}:\n"
                + "  1,Ada,DK,99\n"
                + "  2,Bob,UK,149", toon(doc));
    }

    /** What 4.113.0 wrote, and what toonLegacyForms(true) still writes. */
    @Test
    void theLegacyFormsWriteTheListItemByItem() {
        Object doc = map("orders", listOf(
                map("id", 1L, "customer", map("name", "Ada", "country", "DK"), "total", 99L),
                map("id", 2L, "customer", map("name", "Bob", "country", "UK"), "total", 149L)));

        assertEquals("orders[2]:\n"
                + "  - id: 1\n"
                + "    customer:\n"
                + "      name: Ada\n"
                + "      country: DK\n"
                + "    total: 99\n"
                + "  - id: 2\n"
                + "    customer:\n"
                + "      name: Bob\n"
                + "      country: UK\n"
                + "    total: 149", toon(doc, LEGACY, Object.class));
    }

    /** The header takes each level's order from the first row; a later row's own order does not show. */
    @Test
    void theFirstRowGivesTheFieldOrderAtEveryLevel() {
        Object doc = map("orders", listOf(
                map("id", 1L, "customer", map("name", "Ada", "country", "DK")),
                map("customer", map("country", "UK", "name", "Bob"), "id", 2L)));

        assertEquals("orders[2]{id,customer{name,country}}:\n"
                + "  1,Ada,DK\n"
                + "  2,Bob,UK", toon(doc));
    }

    @Test
    void groupsStandSideBySideAndNestWithoutLimit() {
        Object doc = map(
                "shipments", listOf(map("id", "s1",
                        "sender", map("name", "ACME", "city", "Berlin"),
                        "receiver", map("name", "Globex", "city", "Oslo"))),
                "items", listOf(
                        map("id", 1L, "geo", map("point", map("lat", 1.5, "lon", 2.5, "alt", map("m", 7L)))),
                        map("id", 2L, "geo", map("point", map("lat", 3L, "lon", 4L, "alt", map("m", 8L))))));

        assertEquals("shipments[1]{id,sender{name,city},receiver{name,city}}:\n"
                + "  s1,ACME,Berlin,Globex,Oslo\n"
                + "items[2]{id,geo{point{lat,lon,alt{m}}}}:\n"
                + "  1,1.5,2.5,7\n"
                + "  2,3,4,8", toon(doc));
    }

    /** In a column, an object of uniform objects is a group of groups: the keyed form is for a field's value. */
    @Test
    void anObjectOfUniformObjectsInAColumnIsAGroupOfGroups() {
        Object doc = map("a", listOf(map("t", map("p", map("x", 1L), "q", map("x", 2L)), "other", 9L)));

        assertEquals("a[1]{t{p{x},q{x}},other}:\n"
                + "  1,2,9", toon(doc));
    }

    @Test
    void namesAreQuotedAsKeysAndCellsAsValuesWithTheDelimiterInUse() {
        Object doc = map("items", listOf(
                map("id", 1L, "the customer", map("full name", "Ada, the first", "a|b", "x|y", "n", null)),
                map("id", 2L, "the customer", map("full name", "Bob", "a|b", "", "n", "null"))));

        assertEquals("items[2]{id,\"the customer\"{\"full name\",\"a|b\",n}}:\n"
                + "  1,\"Ada, the first\",x|y,null\n"
                + "  2,Bob,\"\",\"null\"", toon(doc));
        assertEquals("items[2|]{id|\"the customer\"{\"full name\"|\"a|b\"|n}}:\n"
                + "  1|Ada, the first|\"x|y\"|null\n"
                + "  2|Bob|\"\"|\"null\"",
                toon(doc, new WriteOptionsBuilder().toonDelimiter('|').build(), Object.class));
        assertEquals("items[2\t]{id\t\"the customer\"{\"full name\"\t\"a|b\"\tn}}:\n"
                + "  1\tAda, the first\tx|y\tnull\n"
                + "  2\tBob\t\"\"\t\"null\"",
                toon(doc, new WriteOptionsBuilder().toonDelimiter('\t').build(), Object.class));
    }

    @Test
    void aRootListIsATableWithItsGroups() {
        Object doc = listOf(
                map("id", 1L, "c", map("x", 1L, "y", true)),
                map("id", 2L, "c", map("x", 2L, "y", false)));

        assertEquals("[2]{id,c{x,y}}:\n"
                + "  1,1,true\n"
                + "  2,2,false", toon(doc));
    }

    /** One row whose column does not hold an object like the others, and the whole list is written item by item. */
    @Test
    void aColumnThatIsNotUniformKeepsTheListForm() {
        Map<String, Object> nullCell = map("items", listOf(
                map("id", 1L, "c", map("x", 1L)),
                map("id", 2L, "c", null)));
        Map<String, Object> otherFields = map("items", listOf(
                map("id", 1L, "c", map("x", 1L)),
                map("id", 2L, "c", map("y", 2L))));
        Map<String, Object> moreFields = map("items", listOf(
                map("id", 1L, "c", map("x", 1L)),
                map("id", 2L, "c", map("x", 2L, "y", 2L))));
        Map<String, Object> emptyObject = map("items", listOf(
                map("id", 1L, "c", map()),
                map("id", 2L, "c", map())));
        Map<String, Object> arrayInside = map("items", listOf(
                map("id", 1L, "c", map("tags", listOf("a"))),
                map("id", 2L, "c", map("tags", listOf("b")))));
        Map<String, Object> deepDifference = map("items", listOf(
                map("id", 1L, "c", map("d", map("x", 1L))),
                map("id", 2L, "c", map("d", map("x", null, "z", 1L)))));

        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    c:\n"
                + "      x: 1\n"
                + "  - id: 2\n"
                + "    c: null", toon(nullCell));
        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    c:\n"
                + "      x: 1\n"
                + "  - id: 2\n"
                + "    c:\n"
                + "      y: 2", toon(otherFields));
        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    c:\n"
                + "      x: 1\n"
                + "  - id: 2\n"
                + "    c:\n"
                + "      x: 2\n"
                + "      y: 2", toon(moreFields));
        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    c:\n"
                + "  - id: 2\n"
                + "    c:", toon(emptyObject));
        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    c:\n"
                + "      tags[1]: a\n"
                + "  - id: 2\n"
                + "    c:\n"
                + "      tags[1]: b", toon(arrayInside));
        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    c:\n"
                + "      d:\n"
                + "        x: 1\n"
                + "  - id: 2\n"
                + "    c:\n"
                + "      d:\n"
                + "        x: null\n"
                + "        z: 1", toon(deepDifference));
    }

    /** A key spelled differently is another key, also in a map that looks keys up without regard to case. */
    @Test
    void aKeySpelledDifferentlyIsAnotherKey() {
        Map<String, Object> second = new com.cedarsoftware.util.CaseInsensitiveMap<>();
        second.put("X", 2L);
        Object doc = map("items", listOf(map("id", 1L, "c", map("x", 1L)), map("id", 2L, "c", second)));

        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    c:\n"
                + "      x: 1\n"
                + "  - id: 2\n"
                + "    c:\n"
                + "      X: 2", JsonIo.toToon(doc, DEFAULT));
    }

    /**
     * A list directly inside a list has no key, and a header with a field list but no key stands only at the root
     * (spec 9.4): the inner list is written item by item, in the legacy forms too. 4.113.0 wrote it as a table,
     * which a reader of the spec refuses, json-io 4.114.0 among them.
     */
    @Test
    void aListInsideAListIsNeverATable() {
        Object doc = map("a", listOf(
                listOf(map("x", 1L, "c", map("y", 1L)), map("x", 2L, "c", map("y", 2L))),
                listOf(map("x", 3L), map("x", 4L)),
                listOf(1L, 2L)));

        assertEquals("a[3]:\n"
                + "  - [2]:\n"
                + "    - x: 1\n"
                + "      c:\n"
                + "        y: 1\n"
                + "    - x: 2\n"
                + "      c:\n"
                + "        y: 2\n"
                + "  - [2]:\n"
                + "    - x: 3\n"
                + "    - x: 4\n"
                + "  - [2]: 1,2", toon(doc));
        assertEquals("a[3]:\n"
                + "  - [2]:\n"
                + "    - x: 1\n"
                + "      c:\n"
                + "        y: 1\n"
                + "    - x: 2\n"
                + "      c:\n"
                + "        y: 2\n"
                + "  - [2]:\n"
                + "    - x: 3\n"
                + "    - x: 4\n"
                + "  - [2]: 1,2", toon(doc, LEGACY, Object.class));
    }

    /** The same for an array inside a list; and a list that is a field of an inner item is a table again. */
    @Test
    void anArrayInsideAListIsNeverATableButAFieldOfItsItemsIs() {
        Object arrays = map("a", listOf(
                new Object[] {map("x", 5L, "c", map("y", 1L)), map("x", 6L, "c", map("y", 2L))},
                new Object[] {map("x", 7L), map("x", 8L)}));
        Object fieldInside = map("a", listOf(listOf(
                map("rows", listOf(map("x", 1L, "c", map("y", 1L)), map("x", 2L, "c", map("y", 2L)))))));

        assertEquals("a[2]:\n"
                + "  - [2]:\n"
                + "    - x: 5\n"
                + "      c:\n"
                + "        y: 1\n"
                + "    - x: 6\n"
                + "      c:\n"
                + "        y: 2\n"
                + "  - [2]:\n"
                + "    - x: 7\n"
                + "    - x: 8", JsonIo.toToon(arrays, DEFAULT));
        assertEquals("a[2]:\n"
                + "  - [2]:\n"
                + "    - x: 5\n"
                + "      c:\n"
                + "        y: 1\n"
                + "    - x: 6\n"
                + "      c:\n"
                + "        y: 2\n"
                + "  - [2]:\n"
                + "    - x: 7\n"
                + "    - x: 8", JsonIo.toToon(arrays, LEGACY));
        assertEquals("a[1]:\n"
                + "  - [1]:\n"
                + "    - rows[2]{x,c{y}}:\n"
                + "        1,1\n"
                + "        2,2", toon(fieldInside));

        Object fieldInsideAnArray = map("a", listOf((Object) new Object[] {
                map("rows", listOf(map("x", 1L, "c", map("y", 1L)), map("x", 2L, "c", map("y", 2L))))}));
        assertEquals("a[1]:\n"
                + "  - [1]:\n"
                + "    - rows[2]{x,c{y}}:\n"
                + "        1,1\n"
                + "        2,2", JsonIo.toToon(fieldInsideAnArray, DEFAULT));
    }

    /** ... but a list that is a field of a list item has its key, and is a table. */
    @Test
    void aListThatIsAFieldOfAListItemIsATable() {
        Object doc = map("a", listOf(
                map("rows", listOf(map("x", 1L, "c", map("y", 1L)), map("x", 2L, "c", map("y", 2L))), "n", 1L),
                map("n", 2L, "rows", listOf(map("x", 3L, "c", map("y", 3L))))));

        assertEquals("a[2]:\n"
                + "  - rows[2]{x,c{y}}:\n"
                + "      1,1\n"
                + "      2,2\n"
                + "    n: 1\n"
                + "  - n: 2\n"
                + "    rows[1]{x,c{y}}:\n"
                + "      3,3", toon(doc));
    }

    // ---------------------------------------------------------------- objects

    @Test
    void aListOfObjectsWithAnObjectFieldIsATableWithAGroup() {
        Shop shop = new Shop();
        shop.orders.add(new Order(1, new Customer("Ada", "DK"), 99));
        shop.orders.add(new Order(2, new Customer("Bob", "UK"), 149.5));
        shop.array = new Order[] {new Order(3, new Customer("Cy", "SE"), 5)};
        shop.places = listOf(
                new Place(1, 1.5, 2.5, map2("env", "dev", "team", "a")),
                new Place(2, 3, 4, map2("team", "b", "env", "prod")));

        assertEquals("orders[2]{id,customer{name,country},total}:\n"
                + "  1,Ada,DK,99\n"
                + "  2,Bob,UK,149.5\n"
                + "array[1]{id,customer{name,country},total}:\n"
                + "  3,Cy,SE,5\n"
                + "anything: null\n"
                + "places[2]{id,geo{point{lat,lon}},labels{env,team}}:\n"
                + "  1,1.5,2.5,dev,a\n"
                + "  2,3,4,prod,b\n"
                + "shown: null", toon(shop, DEFAULT, Shop.class));
    }

    private static Map<String, String> map2(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    @Test
    void anObjectFieldThatIsNotUniformKeepsTheListForm() {
        Shop nullCustomer = new Shop();
        nullCustomer.orders.add(new Order(1, new Customer("Ada", "DK"), 99));
        nullCustomer.orders.add(new Order(2, null, 1));
        Shop otherClass = new Shop();
        otherClass.orders.add(new Order(1, new Customer("Ada", "DK"), 99));
        otherClass.orders.add(new Order(2, new VipCustomer("Bob", "UK"), 1));
        Shop otherLabels = new Shop();
        otherLabels.places = listOf(
                new Place(1, 1, 2, map2("env", "dev", "team", "a")),
                new Place(2, 3, 4, map2("env", "prod", "squad", "b")));

        String listOfOrders = "orders[2]:\n"
                + "  - id: 1\n"
                + "    customer:\n"
                + "      name: Ada\n"
                + "      country: DK\n"
                + "    total: 99\n"
                + "  - id: 2\n";
        assertEquals(listOfOrders
                + "    customer: null\n"
                + "    total: 1\n"
                + "array: null\nanything: null\nplaces: null\nshown: null",
                JsonIo.toToon(nullCustomer, DEFAULT));
        assertEquals(listOfOrders
                + "    customer:\n"
                + "      level: 1\n"
                + "      name: Bob\n"
                + "      country: UK\n"
                + "    total: 1\n"
                + "array: null\nanything: null\nplaces: null\nshown: null",
                JsonIo.toToon(otherClass, DEFAULT));
        assertEquals("orders: []\n"
                + "array: null\n"
                + "anything: null\n"
                + "places[2]:\n"
                + "  - id: 1\n"
                + "    geo:\n"
                + "      point:\n"
                + "        lat: 1\n"
                + "        lon: 2\n"
                + "    labels:\n"
                + "      env: dev\n"
                + "      team: a\n"
                + "  - id: 2\n"
                + "    geo:\n"
                + "      point:\n"
                + "        lat: 3\n"
                + "        lon: 4\n"
                + "    labels:\n"
                + "      env: prod\n"
                + "      squad: b\n"
                + "shown: null", toon(otherLabels, DEFAULT, Shop.class));
    }

    /** A cell is written as a field's value is: with its @IoFormat pattern. */
    @Test
    void aFormattedFieldIsAFormattedCell() {
        DatedHolder holder = new DatedHolder();
        holder.items.add(new Dated(LocalDate.of(2026, 12, 24), 1.5, new Customer("Ada", "DK")));
        holder.items.add(new Dated(LocalDate.of(2027, 1, 2), 250, new Customer("Bob", "UK")));

        assertEquals("items[2]{day,amount,customer{name,country}}:\n"
                + "  24/12/2026,\"0001.50\",Ada,DK\n"
                + "  02/01/2027,\"0250.00\",Bob,UK", toon(holder, DEFAULT, DatedHolder.class));
    }

    /**
     * An object with an @IoAnyGetter is not written field by field, and an Optional is written as what it holds:
     * neither is a row's object, so the list stays as 4.113.0 wrote it.
     */
    @Test
    void anObjectThatIsNotWrittenFieldByFieldIsNoGroup() {
        OdditiesHolder flexible = new OdditiesHolder();
        flexible.items.add(new Oddities(1, new Flexible("a"), null));
        flexible.items.add(new Oddities(2, new Flexible("b"), null));
        OdditiesHolder optional = new OdditiesHolder();
        optional.items.add(new Oddities(1, null, Optional.of("x")));
        optional.items.add(new Oddities(2, null, Optional.of("y")));
        WriteOptions noNulls = new WriteOptionsBuilder().skipNullFields(true).build();
        WriteOptions legacyNoNulls = new WriteOptionsBuilder().skipNullFields(true).toonLegacyForms(true).build();

        assertEquals("items[2]:\n"
                + "  - n: 1\n"
                + "    flexible:\n"
                + "      id: a\n"
                + "      more: a!\n"
                + "  - n: 2\n"
                + "    flexible:\n"
                + "      id: b\n"
                + "      more: b!", JsonIo.toToon(flexible, noNulls));
        assertEquals(JsonIo.toToon(flexible, legacyNoNulls), JsonIo.toToon(flexible, noNulls));
        assertEquals(JsonIo.toToon(optional, legacyNoNulls), JsonIo.toToon(optional, noNulls));
        assertFalse(JsonIo.toToon(optional, noNulls).contains("{"), JsonIo.toToon(optional, noNulls));
    }

    /** With skipNullFields, a field left out of one row cannot be a column: the list is written item by item. */
    @Test
    void aFieldLeftOutOfOneRowIsNoColumn() {
        Shop shop = new Shop();
        shop.orders.add(new Order(1, new Customer("Ada", null), 99));
        shop.orders.add(new Order(2, new Customer("Bob", "UK"), 1));
        Shop bothLeftOut = new Shop();
        bothLeftOut.orders.add(new Order(1, new Customer("Ada", null), 99));
        bothLeftOut.orders.add(new Order(2, new Customer("Bob", null), 1));
        WriteOptions skipNulls = new WriteOptionsBuilder().skipNullFields(true).build();

        assertEquals("orders[2]:\n"
                + "  - id: 1\n"
                + "    customer:\n"
                + "      name: Ada\n"
                + "    total: 99\n"
                + "  - id: 2\n"
                + "    customer:\n"
                + "      name: Bob\n"
                + "      country: UK\n"
                + "    total: 1", toon(shop, skipNulls, Shop.class));
        assertEquals("orders[2]{id,customer{name},total}:\n"
                + "  1,Ada,99\n"
                + "  2,Bob,1", toon(bothLeftOut, skipNulls, Shop.class));
        assertEquals("orders[2]{id,customer{name,country},total}:\n"
                + "  1,Ada,null,99\n"
                + "  2,Bob,null,1\n"
                + "array: null\nanything: null\nplaces: null\nshown: null", toon(bothLeftOut, DEFAULT, Shop.class));
    }

    /** prettyPrint writes every object field by field, as it always has; a Map's table is not an object's. */
    @Test
    void prettyPrintWritesObjectsFieldByField() {
        Shop shop = new Shop();
        shop.orders.add(new Order(1, new Customer("Ada", "DK"), 99));
        shop.anything = listOf(map("id", 1L, "c", map("x", 1L)), map("id", 2L, "c", map("x", 2L)));
        WriteOptions pretty = new WriteOptionsBuilder().prettyPrint(true).skipNullFields(true).build();

        assertEquals("orders[1]:\n"
                + "  - id: 1\n"
                + "    customer:\n"
                + "      name: Ada\n"
                + "      country: DK\n"
                + "    total: 99\n"
                + "anything[2]{id,c{x}}:\n"
                + "  1,1\n"
                + "  2,2", toon(shop, pretty, Shop.class));
    }

    // ---------------------------------------------------------------- shared objects, cycles

    /** An object held twice is written once and referred to: it needs its $id, which a cell cannot carry. */
    @Test
    void aSharedObjectKeepsTheListFormWhenReferencesAreWritten() {
        Customer shared = new Customer("Eve", "NO");
        Shop shop = new Shop();
        shop.orders.add(new Order(1, shared, 1));
        shop.orders.add(new Order(2, shared, 2));

        String withReferences = JsonIo.toToon(shop, new WriteOptionsBuilder().cycleSupport(true).build());
        String withoutReferences = JsonIo.toToon(shop,
                new WriteOptionsBuilder().cycleSupport(false).skipNullFields(true).build());

        assertEquals("orders[2]:\n"
                + "  - id: 1\n"
                + "    customer:\n"
                + "      $id: 1\n"
                + "      name: Eve\n"
                + "      country: NO\n"
                + "    total: 1\n"
                + "  - id: 2\n"
                + "    customer:\n"
                + "      $ref: 1\n"
                + "    total: 2\n"
                + "array: null\nanything: null\nplaces: null\nshown: null", withReferences);
        Shop back = JsonIo.fromToon(withReferences, null).asClass(Shop.class);
        assertTrue(back.orders.get(0).customer == back.orders.get(1).customer, "one customer, held twice");

        assertEquals("orders[2]{id,customer{name,country},total}:\n"
                + "  1,Eve,NO,1\n"
                + "  2,Eve,NO,2", withoutReferences);
    }

    public static class Node {
        public String name;
        public Node next;
        public Node() { }
        public Node(String name) {
            this.name = name;
        }
    }

    /** An object that holds itself is not a row: with cycleSupport off the cycle is reported, as before. */
    @Test
    void aCycleIsStillReportedOrWrittenAsAReference() {
        Node a = new Node("a");
        Node b = new Node("b");
        a.next = b;
        b.next = a;
        List<Node> nodes = listOf(a, b);
        Map<String, Object> self = map("id", 1L);
        self.put("me", self);
        List<Object> maps = listOf(self, map("id", 2L, "me", map("id", 3L, "me", null)));

        for (WriteOptions legacyOrNot : new WriteOptions[] {
                new WriteOptionsBuilder().cycleSupport(false).build(),
                new WriteOptionsBuilder().cycleSupport(false).toonLegacyForms(true).build()}) {
            JsonIoException e = org.junit.jupiter.api.Assertions.assertThrows(JsonIoException.class,
                    () -> JsonIo.toToon(nodes, legacyOrNot));
            assertTrue(e.getMessage().startsWith("Cycle detected while writing TOON"), e.getMessage());
            e = org.junit.jupiter.api.Assertions.assertThrows(JsonIoException.class,
                    () -> JsonIo.toToon(maps, legacyOrNot));
            assertTrue(e.getMessage().startsWith("Cycle detected while writing TOON"), e.getMessage());
        }
        assertEquals("[2]:\n"
                + "  - $id: 2\n"
                + "    name: a\n"
                + "    next:\n"
                + "      $id: 1\n"
                + "      name: b\n"
                + "      next:\n"
                + "        $ref: 2\n"
                + "  - $ref: 1", JsonIo.toToon(nodes, new WriteOptionsBuilder().cycleSupport(true).build()));
    }

    // ---------------------------------------------------------------- type info

    /** With type info, a value that needs its $type is written as a field, where the $type goes: no group. */
    @Test
    void aValueThatCarriesItsTypeIsNotACell() {
        Shop declared = new Shop();
        declared.orders.add(new Order(1, new Customer("Ada", "DK"), 99));
        declared.orders.add(new Order(2, new Customer("Bob", "UK"), 1));
        Shop subclass = new Shop();
        subclass.orders.add(new Order(1, new VipCustomer("Ada", "DK"), 99));
        subclass.orders.add(new Order(2, new VipCustomer("Bob", "UK"), 1));
        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().skipNullFields(true).build();
        String type = "$type: " + Shop.class.getName() + "\n";

        // the declared class in a declared field: nothing to say
        assertEquals(type
                + "orders[2]{id,customer{name,country},total}:\n"
                + "  1,Ada,DK,99\n"
                + "  2,Bob,UK,1", toon(declared, minimal, Shop.class));
        // a subclass: its $type is written, so it is a field
        assertEquals(type
                + "orders[2]:\n"
                + "  - id: 1\n"
                + "    customer:\n"
                + "      $type: " + VipCustomer.class.getName() + "\n"
                + "      level: 1\n"
                + "      name: Ada\n"
                + "      country: DK\n"
                + "    total: 99\n"
                + "  - id: 2\n"
                + "    customer:\n"
                + "      $type: " + VipCustomer.class.getName() + "\n"
                + "      level: 1\n"
                + "      name: Bob\n"
                + "      country: UK\n"
                + "    total: 1", toon(subclass, minimal, Shop.class));
        // without type info the subclass is a row like any other (and reads back as the declared class)
        assertEquals("orders[2]{id,customer{level,name,country},total}:\n"
                + "  1,1,Ada,DK,99\n"
                + "  2,1,Bob,UK,1", JsonIo.toToon(subclass, new WriteOptionsBuilder().skipNullFields(true).build()));
    }

    /** The same for a Map whose class must be written: it is a field, where its $type goes. */
    @Test
    void aMapThatCarriesItsTypeIsNotAGroup() {
        Shop sortedLabels = new Shop();
        sortedLabels.places = new ArrayList<>(listOf(
                new Place(1, 1, 2, new TreeMap<>(map2("env", "dev", "team", "a"))),
                new Place(2, 3, 4, new TreeMap<>(map2("env", "prod", "team", "b")))));
        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().skipNullFields(true).build();

        String sorted = toon(sortedLabels, minimal, Shop.class);

        assertTrue(sorted.contains("places[2]:\n  - id: 1\n") && sorted.contains("      $type: TreeMap\n"), sorted);
        // with no type info nothing is told, and the list is a table
        assertEquals("orders: []\n"
                + "places[2]{id,geo{point{lat,lon}},labels{env,team}}:\n"
                + "  1,1,2,dev,a\n"
                + "  2,3,4,prod,b",
                JsonIo.toToon(sortedLabels, new WriteOptionsBuilder().skipNullFields(true).build()));
    }

    /**
     * A value in a Map that needs its $type -- an Integer, which reads back as a Long untold -- is no cell. Shown
     * with json-io's own Map of a Maps graph, which itself never carries a $type.
     */
    @Test
    void aMapValueThatCarriesItsTypeIsNotACell() {
        List<Object> rows = new ArrayList<>();
        for (int n = 1; n <= 2; n++) {
            JsonObject inner = new JsonObject();
            inner.put("x", n);                      // an Integer
            JsonObject row = new JsonObject();
            row.put("id", (long) n);
            row.put("c", inner);
            rows.add(row);
        }
        Map<String, Object> doc = map("rows", rows);

        String minimal = JsonIo.toToon(doc,
                new WriteOptionsBuilder().showTypeInfoMinimal().omitRootTypeInfo().build());
        String minimalPlus = JsonIo.toToon(doc,
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().omitRootTypeInfo().build());

        assertEquals("rows:\n"
                + "  $type: ArrayList\n"
                + "  $items[2]:\n"
                + "    - id: 1\n"
                + "      c:\n"
                + "        x:\n"
                + "          $type: Integer\n"
                + "          value: 1\n"
                + "    - id: 2\n"
                + "      c:\n"
                + "        x:\n"
                + "          $type: Integer\n"
                + "          value: 2", minimal);
        // minimal-plus leaves an Integer's type off, so it is a cell
        assertEquals("rows:\n"
                + "  $type: ArrayList\n"
                + "  $items[2]{id,c{x}}:\n"
                + "    1,1\n"
                + "    2,2", minimalPlus);
    }

    /** Rows whose class the reader must be told carry it in the table's $type column, as before; groups follow. */
    @Test
    void theTypeColumnOfATableComesBeforeItsGroups() {
        Shop shop = new Shop();
        shop.anything = new ArrayList<>(listOf(
                new Order(1, new Customer("Ada", "DK"), 99), new Order(2, new Customer("Bob", "UK"), 1)));
        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().skipNullFields(true).build();

        String toon = toon(shop, minimal, Shop.class);

        assertEquals("$type: " + Shop.class.getName() + "\n"
                + "orders: []\n"
                + "anything[2]{\"$type\",id,customer{name,country},total}:\n"
                + "  " + Order.class.getName() + ",1,Ada,DK,99\n"
                + "  " + Order.class.getName() + ",2,Bob,UK,1", toon);
    }

    /** @IoShowType asks for the $type of a field's value, with type info off too: such a value is not a cell. */
    @Test
    void aShowTypeFieldIsNotACell() {
        TaggedHolder holder = new TaggedHolder();
        holder.items.add(new Tagged(1, new Customer("Ada", "DK")));
        holder.items.add(new Tagged(2, new Customer("Bob", "UK")));

        String toon = toon(holder, DEFAULT, TaggedHolder.class);

        assertEquals("items[2]:\n"
                + "  - id: 1\n"
                + "    customer:\n"
                + "      $type: " + Customer.class.getName() + "\n"
                + "      name: Ada\n"
                + "      country: DK\n"
                + "  - id: 2\n"
                + "    customer:\n"
                + "      $type: " + Customer.class.getName() + "\n"
                + "      name: Bob\n"
                + "      country: UK", toon);
    }

    @Test
    void anArrayOfMapsIsATableToo() {
        Object[] rows = {map("id", 1L, "c", map("x", 1L)), map("id", 2L, "c", map("x", 2L))};
        Map<String, Object> doc = map("rows", rows);

        String toon = JsonIo.toToon(doc, DEFAULT);

        assertEquals("rows[2]{id,c{x}}:\n"
                + "  1,1\n"
                + "  2,2", toon);
        WriteOptions plainJson = new WriteOptionsBuilder().showTypeInfoNever().build();
        assertEquals(JsonIo.toJson(map("rows", Arrays.asList(rows)), plainJson),
                JsonIo.toJson(JsonIo.fromToon(toon, null).asClass(Object.class), plainJson));
    }
}
