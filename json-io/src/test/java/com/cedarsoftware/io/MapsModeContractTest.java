package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.convert.Convert;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Maps-mode contract, case by case: what {@code toMaps()} returns for 143 cases, and what it writes back. Each case
 * runs twice, with the app's classes present and with them absent -- the same JSON, naming classes that are not on the
 * classpath, which is all Maps mode can tell of a class that is not there -- since Maps mode reads JSON without the
 * classes it names. The cases are those of the matrix that measured every release from 4.88.0 to 4.117.0; the outcomes
 * are those decided for 4.118.0 (see its changelog), and where the classes change an outcome, the case says how:
 * <ul>
 *   <li>Below the root the tree is JSON: an object a class is named for stays a JsonObject, its numbers Long, its text
 *   text, and no class named anywhere is initialized or constructed. A value the JSON names -- a JDK value type, a
 *   simple enum present on the classpath -- is that value wherever it stands; anything else is kept as written.</li>
 *   <li>The root is what {@code asClass()} / {@code asType()} asks for, or a clear error; with no class asked, a JSON
 *   object is the JsonObject, whose fields a root {@code @type} class present converts (kept in 4.x).</li>
 *   <li>json-io's own text passes through unchanged, and references stay references, in a typed list too.</li>
 *   <li>A conversion registered for the class is used first, before its factory, in either mode; a tree can be read as
 *   a class in a second step.</li>
 * </ul>
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
class MapsModeContractTest {
    /** The JSON names the app's classes "app.X": X of this class when present, a package not there when absent. */
    private static final String HERE = MapsModeContractTest.class.getName() + "$";
    private static final String GONE = "gone.";
    /** Absent: as present. */
    private static final String SAME = "\u0000same";

    private static final Set<String> INITIALIZED = ConcurrentHashMap.newKeySet();
    private static final Set<String> CONSTRUCTED = ConcurrentHashMap.newKeySet();

    // ---- The app's classes

    public static class Person {
        public String name;
        public int age;
        public LocalDate born;
        public Status status;

        public String toString() {
            return "Person(" + name + "," + age + "," + born + "," + status + ")";
        }
    }

    /** Cannot be built from {"holder":..}: its only constructor demands a number starting with P-. */
    public static class Policy {
        public final String number;

        public Policy(String number) {
            if (number == null || !number.startsWith("P-")) {
                throw new IllegalArgumentException("policy number must start with P-");
            }
            this.number = number;
        }

        public String toString() {
            return "Policy(" + number + ")";
        }
    }

    public static class Holder {
        public int count;
        public Person person;
        public Object any;
        public List<Person> people;
        public Map<String, Person> byName;
    }

    public enum Status { NEW, SHIPPED }

    /** An enum with a field and a constant with a body: not a simple enum. */
    public enum Rich {
        A("a") {
            @Override
            public String x() {
                return "A";
            }
        },
        B("b");

        public final String code;

        Rich(String code) {
            this.code = code;
        }

        public String x() {
            return "?";
        }
    }

    /** Each says when its class is initialized and when one is constructed. */
    public static class NoisyA {
        static {
            INITIALIZED.add("NoisyA");
        }

        public int age;

        public NoisyA() {
            CONSTRUCTED.add("NoisyA");
        }
    }

    public static class NoisyB {
        static {
            INITIALIZED.add("NoisyB");
        }

        public int age;

        public NoisyB() {
            CONSTRUCTED.add("NoisyB");
        }
    }

    public static class NoisyC {
        static {
            INITIALIZED.add("NoisyC");
        }

        public int age;

        public NoisyC() {
            CONSTRUCTED.add("NoisyC");
        }
    }

    /** A boutique's order: typed fields, collections of its lines, a back reference, a line in an Object field. */
    public static class Order {
        public enum Status { NEW, SHIPPED }

        public static class Line {
            public int qty;
            public BigDecimal price;
            public Order order;
        }

        public long id = 1234567890123L;
        public int count = 2;
        public Date when = new Date(1700000000000L);
        public Status status = Status.SHIPPED;
        public List<Line> lines = new ArrayList<>();
        public Map<String, Line> byName = new LinkedHashMap<>();
        public List<List<Line>> batches = new ArrayList<>();
        public Object any;

        static Order sample() {
            Order o = new Order();
            Line a = new Line();
            a.qty = 3;
            a.price = new BigDecimal("9.50");
            a.order = o;
            Line b = new Line();
            b.qty = 4;
            b.price = new BigDecimal("1.25");
            b.order = o;
            o.lines.add(a);
            o.lines.add(b);
            o.byName.put("first", a);
            o.batches.add(new ArrayList<>(Arrays.asList(b)));
            Line c = new Line();
            c.qty = 5;
            c.price = new BigDecimal("2.00");
            o.any = c;
            return o;
        }
    }

    // ---- The cases

    /** Classes named below the root are never initialized or constructed; nor is the root's. */
    @Test
    void noClassNamedIsInitializedOrBuilt() {
        Table t = new Table();
        t.add("noisy.nested-field", p -> noisy(p, "{\"a\":{\"@type\":\"app.NoisyA\",\"age\":1}}"),
                "init=[] new=[] J{a=J<app.NoisyA>{age=Long:1}}", SAME);
        t.add("noisy.in-list", p -> noisy(p, "[{\"@type\":\"app.NoisyC\",\"age\":1}]"),
                "init=[] new=[] Object[][J<app.NoisyC>{age=Long:1}]", SAME);
        t.add("noisy.root", p -> noisy(p, "{\"@type\":\"app.NoisyB\",\"age\":1}"),
                "init=[] new=[] J<app.NoisyB>{age=Integer:1}", "init=[] new=[] J<app.NoisyB>{age=Long:1}");
        t.verify(3);
    }

    /** The root is what is asked for, or a clear error. */
    @Test
    void theRootIsWhatIsAskedFor() {
        String mixed = "[1,\"a\",true,null,2.5]";
        String mixedItems = "[Long:1,String:a,Boolean:true,null,Double:2.5]";
        String nyText = "\"2024-01-15T10:30:00-05:00[America/New_York]\"";
        String ny = "ZonedDateTime:2024-01-15T10:30-05:00[America/New_York]";
        Object[][] rows = {
            // the JSON, the class asked for, what Maps mode returns with the classes present, and absent
            {"\"hello\"", null, "String:hello", SAME},
            {"42", null, "Long:42", SAME},
            {"3.5", null, "Double:3.5", SAME},
            {"true", null, "Boolean:true", SAME},
            {"null", null, "null", SAME},
            {"\"2026-10-25T10:15:30-04:00[America/New_York]\"", ZonedDateTime.class,
                    "ZonedDateTime:2026-10-25T10:15:30-04:00[America/New_York]", SAME},
            {"\"42\"", Integer.class, "Integer:42", SAME},
            {"42", String.class, "String:42", SAME},
            {"\"9.50\"", BigDecimal.class, "BigDecimal:9.50", SAME},
            {"\"2026-10-25\"", LocalDate.class, "LocalDate:2026-10-25", SAME},
            {"\"f81d4fae-7dec-11d0-a765-00a0c91e6bf6\"", UUID.class, "UUID:f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                    SAME},
            {mixed, null, "Object[]" + mixedItems, SAME},
            {mixed, List.class, "ArrayList" + mixedItems, SAME},
            {mixed, ArrayList.class, "ArrayList" + mixedItems, SAME},
            {mixed, Object[].class, "Object[]" + mixedItems, SAME},
            {"[1,2]", Set.class, "LinkedHashSet[Long:1,Long:2]", SAME},
            {"[{\"a\":1},[2]]", List.class, "ArrayList[J{a=Long:1},ArrayList[Long:2]]", SAME},
            {"{\"a\":1,\"b\":\"x\",\"c\":[1,2],\"d\":{\"e\":true}}", null,
                    "J{a=Long:1,b=String:x,c=Object[][Long:1,Long:2],d=J{e=Boolean:true}}", SAME},
            {"{\"a\":1,\"b\":\"x\"}", Map.class, "J{a=Long:1,b=String:x}", SAME},
            {"{\"a\":1,\"b\":\"x\"}", LinkedHashMap.class, "LinkedHashMap{String:a=Long:1,String:b=String:x}", SAME},
            {"{\"b\":\"x\",\"a\":1}", TreeMap.class, "TreeMap{String:a=Long:1,String:b=String:x}", SAME},
            {"{\"b\":\"x\",\"a\":1}", ConcurrentHashMap.class,
                    "ConcurrentHashMap{String:a=Long:1,String:b=String:x}", SAME},
            {"{\"@type\":\"ZonedDateTime\",\"zonedDateTime\":" + nyText + "}", null, ny, SAME},
            {"{\"@type\":\"java.lang.Integer\",\"value\":5}", null, "Integer:5", SAME},
            {"{\"@type\":\"ArrayList\",\"@items\":[\"a\",\"b\"]}", null,
                    "J<ArrayList>{@items=Object[][String:a,String:b]}", SAME},
            {"{\"@type\":\"ArrayList\",\"@items\":[\"a\",\"b\"]}", List.class, "ArrayList[String:a,String:b]", SAME},
            {"[\"2024-01-15\",\"2024-02-01\"]", LocalDate[].class,
                    "LocalDate[][LocalDate:2024-01-15,LocalDate:2024-02-01]", SAME},
            {"[1,2]", int[].class, "int[][Integer:1,Integer:2]", SAME},
            {"\"MONDAY\"", DayOfWeek.class, "DayOfWeek:MONDAY", SAME},
            {"{\"a\":1}", Optional.class, "Optional[J{a=Long:1}]", SAME},
            {"\"abc\"", Optional.class, "Optional[String:abc]", SAME},
            // the app's classes, asked for by class: present only
            {"\"SHIPPED\"", Status.class, "Status:SHIPPED", null},
            {"\"A\"", Rich.class, "Rich:A", null},
            {"{\"name\":\"Ann\",\"age\":30}", Person.class, "Person:Person(Ann,30,null,null)", null},
            {"{\"holder\":\"Ann\"}", Policy.class, "THROWS JsonIoException: Unable to instantiate: app.Policy - "
                    + "Most recent error: policy number must start with P-", null},
            {"{\"@type\":\"app.Person\",\"name\":\"Ann\",\"age\":30}", Person.class,
                    "Person:Person(Ann,30,null,null)", null},
        };
        Table t = new Table();
        for (Object[] row : rows) {
            String json = (String) row[0];
            Class<?> asked = (Class<?>) row[1];
            t.add("root " + json + " asClass(" + (asked == null ? null : asked.getSimpleName()) + ")",
                    p -> maps(p, json, asked), (String) row[2], (String) row[3]);
        }
        t.add("root [\"2024-01-15\"] asType(List<LocalDate>)", p -> text(JsonIo.toMaps("[\"2024-01-15\"]", null)
                .asType(new TypeHolder<List<LocalDate>>() { })), "ArrayList[LocalDate:2024-01-15]", SAME);
        t.add("root {\"a\":\"2024-01-15\"} asType(Map<String,LocalDate>)", p -> text(JsonIo
                        .toMaps("{\"a\":\"2024-01-15\"}", null).asType(new TypeHolder<Map<String, LocalDate>>() { })),
                "LinkedHashMap{String:a=LocalDate:2024-01-15}", SAME);
        t.add("root [{\"name\":\"Ann\"}] asType(List<Person>)", p -> text(JsonIo.toMaps("[{\"name\":\"Ann\"}]", null)
                .asType(new TypeHolder<List<Person>>() { })), "ArrayList[Person:Person(Ann,0,null,null)]", null);
        t.add("root asClass(Map) is the JsonObject", p -> JsonIo.toMaps("{\"a\":1}", null).asClass(Map.class)
                .getClass().getSimpleName(), "JsonObject", SAME);
        t.verify(40);
    }

    /**
     * A root {@code @type} class present converts the root's fields (kept in 4.x); below the root the tree stays JSON,
     * and a value that does not convert stays as written.
     */
    @Test
    void aRootTypeConvertsOnlyTheRootsFields() {
        String nested = "person=J{name=String:Ann,age=Long:30,born=String:1990-05-01},people=Object[][J{age=Long:4}],"
                + "byName=J{x=J{age=Long:5}},any=J<app.Person>{age=Long:6}}";
        Table t = new Table();
        t.add("typed.root-person", p -> maps(p, "{\"@type\":\"app.Person\",\"name\":\"Ann\",\"age\":30,"
                        + "\"born\":\"1990-05-01\",\"status\":\"NEW\"}", null),
                "J<app.Person>{name=String:Ann,age=Integer:30,born=LocalDate:1990-05-01,status=Status:NEW}",
                "J<app.Person>{name=String:Ann,age=Long:30,born=String:1990-05-01,status=String:NEW}");
        t.add("typed.root-blank", p -> maps(p, "{\"@type\":\"app.Person\",\"name\":\"\",\"born\":\"\","
                        + "\"status\":\"\"}", null),
                "J<app.Person>{name=String:\"\",born=null,status=null}",
                "J<app.Person>{name=String:\"\",born=String:\"\",status=String:\"\"}");
        t.add("typed.nested", p -> maps(p, "{\"@type\":\"app.Holder\",\"count\":2,\"person\":{\"name\":\"Ann\","
                        + "\"age\":30,\"born\":\"1990-05-01\"},\"people\":[{\"age\":4}],\"byName\":{\"x\":{\"age\":5}},"
                        + "\"any\":{\"@type\":\"app.Person\",\"age\":6}}", null),
                "J<app.Holder>{count=Integer:2," + nested, "J<app.Holder>{count=Long:2," + nested);
        t.add("typed.nested-untyped-root", p -> maps(p, "{\"h\":{\"@type\":\"app.Holder\",\"count\":2,"
                        + "\"person\":{\"age\":30}},\"p\":[{\"@type\":\"app.Person\",\"age\":7}]}", null),
                "J{h=J<app.Holder>{count=Long:2,person=J{age=Long:30}},p=Object[][J<app.Person>{age=Long:7}]}", SAME);
        t.add("skew.root", p -> maps(p, "{\"@type\":\"app.Holder\",\"count\":\"three\"}", null),
                "J<app.Holder>{count=String:three}", SAME);
        t.add("skew.nested", p -> maps(p, "{\"h\":{\"@type\":\"app.Holder\",\"count\":\"three\"}}", null),
                "J{h=J<app.Holder>{count=String:three}}", SAME);
        t.add("skew.enum", p -> maps(p, "{\"@type\":\"app.Person\",\"status\":\"RETURNED\"}", null),
                "J<app.Person>{status=String:RETURNED}", SAME);
        t.verify(7);
    }

    /**
     * A value the JSON names is that value wherever it stands: a JDK value type, and a simple enum whose class is
     * present. An enum with constant bodies, an array and an object of a class stay JSON below the root.
     */
    @Test
    void aValueIsThatValueWhereverItStands() {
        String[][] rows = {
            // the name, the JSON, below the root and at the root with the classes present, then absent
            {"Integer", "{\"@type\":\"Integer\",\"value\":5}", "Integer:5", SAME, SAME, SAME},
            {"BigDecimal", "{\"@type\":\"BigDecimal\",\"value\":\"1.50\"}", "BigDecimal:1.50", SAME, SAME, SAME},
            {"ZonedDateTime", "{\"@type\":\"ZonedDateTime\",\"zonedDateTime\":\"2024-01-15T10:30:00-05:00"
                    + "[America/New_York]\"}", "ZonedDateTime:2024-01-15T10:30-05:00[America/New_York]", SAME, SAME,
                    SAME},
            {"Date", "{\"@type\":\"Date\",\"date\":1700000000000}", "Date:1700000000000", SAME, SAME, SAME},
            {"UUID", "{\"@type\":\"UUID\",\"value\":\"123e4567-e89b-12d3-a456-426614174000\"}",
                    "UUID:123e4567-e89b-12d3-a456-426614174000", SAME, SAME, SAME},
            {"Class", "{\"@type\":\"Class\",\"value\":\"java.lang.String\"}", "Class:java.lang.String", SAME, SAME,
                    SAME},
            {"DayOfWeek", "{\"@type\":\"java.time.DayOfWeek\",\"name\":\"MONDAY\"}", "DayOfWeek:MONDAY", SAME, SAME,
                    SAME},
            {"Status", "{\"@type\":\"app.Status\",\"name\":\"NEW\"}", "Status:NEW", SAME,
                    "J<app.Status>{name=String:NEW}", "J<app.Status>{name=String:NEW}"},
            {"Rich", "{\"@type\":\"app.Rich\",\"name\":\"A\"}", "J<app.Rich>{name=String:A}", SAME, SAME, SAME},
            {"intArray", "{\"@type\":\"int[]\",\"@items\":[1,2]}", "J<int[]>{@items=Object[][Long:1,Long:2]}",
                    "int[][Integer:1,Integer:2]", SAME, "int[][Integer:1,Integer:2]"},
            {"Person", "{\"@type\":\"app.Person\",\"age\":3}", "J<app.Person>{age=Long:3}",
                    "J<app.Person>{age=Integer:3}", SAME, "J<app.Person>{age=Long:3}"},
        };
        Table t = new Table();
        for (String[] row : rows) {
            String name = "wrap." + row[0];
            String w = row[1];
            String below = row[2];
            String root = row[3] == SAME ? below : row[3];
            String belowAbsent = row[4] == SAME ? below : row[4];
            String rootAbsent = row[5] == SAME ? root : row[5];
            t.add(name + " field", p -> text(field(read(p, "{\"f\":" + w + "}"), "f")), below, belowAbsent);
            t.add(name + " list", p -> text(first(field(read(p, "{\"l\":[" + w + "]}"), "l"))), below, belowAbsent);
            t.add(name + " typed-list", p -> text(first(field(read(p, "{\"l\":{\"@type\":\"ArrayList\",\"@items\":["
                    + w + "]}}"), "l"))), below, belowAbsent);
            t.add(name + " root-array", p -> text(first(read(p, "[" + w + "]"))), below, belowAbsent);
            t.add(name + " map-key", p -> text(((Map<?, ?>) field(read(p, "{\"m\":{\"@type\":\"LinkedHashMap\","
                    + "\"@keys\":[" + w + "],\"@items\":[\"x\"]}}"), "m")).keySet().iterator().next()), below,
                    belowAbsent);
            t.add(name + " root", p -> text(read(p, w)), root, rootAbsent);
        }
        t.verify(66);
    }

    /** json-io's own text passes through unchanged, and holds the values it was written from. */
    @Test
    void jsonIosOwnTextPassesThrough() {
        Object[][] rows = {
            {"Integer", 5, "Integer:5", SAME},
            {"BigDecimal", new BigDecimal("1.50"), "BigDecimal:1.50", SAME},
            {"ZonedDateTime", ZonedDateTime.parse("2024-01-15T10:30:00-05:00[America/New_York]"),
                    "ZonedDateTime:2024-01-15T10:30-05:00[America/New_York]", SAME},
            {"Date", new Date(1700000000000L), "Date:1700000000000", SAME},
            {"UUID", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                    "UUID:123e4567-e89b-12d3-a456-426614174000", SAME},
            {"Class", String.class, "Class:java.lang.String", SAME},
            {"DayOfWeek", DayOfWeek.MONDAY, "DayOfWeek:MONDAY", SAME},
            {"Status", Status.NEW, "Status:NEW", "J<app.Status>{name=String:NEW}"},
            {"Rich", Rich.A, "J<app.Rich>{name=String:A}", SAME},
            {"intArray", new int[] {1, 2}, "J<int[]>{@items=Object[][Long:1,Long:2]}", SAME},
        };
        Table t = new Table();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", row[1]);
            m.put("list", new ArrayList<>(Arrays.asList(row[1])));
            String written = JsonIo.toJson(m, null);
            String present = (String) row[2];
            String absent = (String) row[3];
            t.add("own." + row[0], p -> {
                Object tree = read(p, written);
                String back = JsonIo.toJson(tree, null);
                return "field=" + text(field(tree, "field")) + " list=" + text(first(field(tree, "list")))
                        + " rewrite " + (back.equals(json(p, written)) ? "same" : "DIFFERS " + back);
            }, "field=" + present + " list=" + present + " rewrite same",
                    absent == SAME ? SAME : "field=" + absent + " list=" + absent + " rewrite same");
        }

        // an Order, written as json-io writes it; then a line's qty changed
        String order = JsonIo.toJson(Order.sample(), null);
        t.add("own.order-default", p -> {
            String json = json(p, order);
            Object root = read(p, order);
            boolean same = JsonIo.toJson(root, null).equals(json);
            JsonObject line = (JsonObject) first(field(root, "lines"));
            line.putAsJson("qty", 99);
            boolean asJson = JsonIo.toJson(root, null).equals(json.replaceFirst("\"qty\":3", "\"qty\":99"));
            line.put("qty", 99);
            boolean typed = JsonIo.toJson(root, null).equals(json.replaceFirst("\"qty\":3",
                    "\"qty\":{\"@type\":\"Integer\",\"value\":99}"));
            return "rewrite " + (same ? "same" : "DIFFERS") + ", putAsJson(qty, 99) " + (asJson ? "99" : "DIFFERS")
                    + ", put(qty, Integer 99) " + (typed ? "typed" : "DIFFERS") + ", " + orderValues(root);
        }, "rewrite same, putAsJson(qty, 99) 99, put(qty, Integer 99) typed, root.count=Integer:2 "
                + "root.status=Status:SHIPPED root.when=Date:1700000000000 line.price=String:9.50 "
                + "any=J<app.Order$Line>{qty=Long:5,price=String:2.00,order=null}",
                "rewrite same, putAsJson(qty, 99) 99, put(qty, Integer 99) typed, root.count=Long:2 "
                + "root.status=String:SHIPPED root.when=Long:1700000000000 line.price=String:9.50 "
                + "any=J<app.Order$Line>{qty=Long:5,price=String:2.00,order=null}");

        // with showTypeInfoAlways(): rewritten with the classes present, a typed value in a field its class declares
        // loses its @type (it reads back the same Order)
        WriteOptions always = new WriteOptionsBuilder().showTypeInfoAlways().build();
        String alwaysOrder = JsonIo.toJson(Order.sample(), always);
        t.add("own.order-always", p -> {
            String json = json(p, alwaysOrder);
            Object root = read(p, alwaysOrder);
            String back = JsonIo.toJson(root, always);
            String rewrite = back.equals(json) ? "same" : DeepEquals.deepEquals(JsonIo.toJava(back, null)
                    .asClass(Order.class), JsonIo.toJava(json, null).asClass(Order.class)) ? "reads back equal"
                    : "DIFFERS";
            return "rewrite " + rewrite + ", " + orderValues(root);
        }, "rewrite reads back equal, root.count=Integer:2 root.status=Status:SHIPPED root.when=Date:1700000000000 "
                + "line.price=BigDecimal:9.50 any=J<app.Order$Line>{qty=Long:5,price=BigDecimal:2.00,order=null}",
                "rewrite same, root.count=Long:2 root.status=J<app.Order$Status>{name=String:SHIPPED} "
                + "root.when=Date:1700000000000 line.price=BigDecimal:9.50 "
                + "any=J<app.Order$Line>{qty=Long:5,price=BigDecimal:2.00,order=null}");
        t.verify(12);
    }

    /** Numbers as JSON holds them; references are references, a typed list's too. */
    @Test
    void numbersAndReferences() {
        Table t = new Table();
        t.add("numbers", p -> maps(p, "{\"i\":1,\"big\":123456789012345678901234567890,\"d\":1.10,\"e\":1e2,"
                        + "\"s\":\"\",\"neg\":-0}", null),
                "J{i=Long:1,big=BigInteger:123456789012345678901234567890,d=Double:1.1,e=Double:100.0,s=String:\"\","
                        + "neg=Long:0}", SAME);
        t.add("ref.backward", p -> maps(p, "{\"a\":{\"@id\":1,\"x\":1},\"b\":{\"@ref\":1}}", null),
                "J{a=J{x=Long:1},b=^2}", SAME);
        t.add("ref.forward", p -> maps(p, "{\"b\":{\"@ref\":1},\"a\":{\"@id\":1,\"x\":1}}", null),
                "J{b=J{x=Long:1},a=^2}", SAME);
        t.add("ref.in-typed-list", p -> maps(p, "{\"a\":{\"@id\":2,\"x\":1},\"l\":{\"@type\":\"ArrayList\","
                + "\"@items\":[{\"@ref\":2}]}}", null), "J{a=J{x=Long:1},l=J<ArrayList>{@items=Object[][^2]}}", SAME);
        t.add("ref.in-array", p -> maps(p, "{\"a\":{\"@id\":2,\"x\":1},\"l\":[{\"@ref\":2}]}", null),
                "J{a=J{x=Long:1},l=Object[][^2]}", SAME);
        t.add("ref.to-root", p -> maps(p, "{\"@id\":1,\"self\":{\"@ref\":1}}", null), "J{self=^1}", SAME);
        t.add("ref.to-root asClass(Map)", p -> {
            Map<?, ?> m = JsonIo.toMaps("{\"@id\":1,\"self\":{\"@ref\":1}}", null).asClass(Map.class);
            return m.getClass().getSimpleName() + " self-is-root=" + (m.get("self") == m) + " rewrite="
                    + JsonIo.toJson(m, null);
        }, "JsonObject self-is-root=true rewrite={\"@id\":1,\"self\":{\"@ref\":1}}", SAME);
        t.verify(7);
    }

    /** A tree read as a class in a second step; a conversion registered for a class comes first, before its factory. */
    @Test
    void twoStepsAndRegisteredConversions() {
        Convert<Policy> numbered = (from, converter) -> new Policy("P-" + ((Map<?, ?>) from).get("holder"));
        ClassFactory factory = new ClassFactory() {
            public Object newInstance(Class<?> c, JsonObject j, Resolver r) {
                return new Policy("P-" + j.get("holder"));
            }

            public boolean isObjectFinal() {
                return true;
            }
        };
        ClassFactory failing = new ClassFactory() {
            public Object newInstance(Class<?> c, JsonObject j, Resolver r) {
                throw new IllegalStateException("factory failed");
            }

            public boolean isObjectFinal() {
                return true;
            }
        };
        String ann = "{\"name\":\"Ann\",\"age\":30}";
        String holder = "{\"holder\":\"Ann\"}";
        ReadOptions converts = new ReadOptionsBuilder().addConverterOverride(Map.class, Policy.class, numbered).build();
        Table t = new Table();
        t.add("two-step toJava(JsonObject).asClass(Person)", p -> text(JsonIo.toJava((JsonObject) JsonIo
                        .toMaps(ann, null).asClass(null), null).asClass(Person.class)),
                "Person:Person(Ann,30,null,null)", null);
        t.add("two-step toJava(asClass(Map)).asClass(Person)", p -> text(JsonIo.toJava((JsonObject) JsonIo
                        .toMaps(ann, null).asClass(Map.class), null).asClass(Person.class)),
                "Person:Person(Ann,30,null,null)", null);
        t.add("java Policy plain", p -> text(JsonIo.toJava(holder, null).asClass(Policy.class)),
                "THROWS JsonIoException: Unable to instantiate: app.Policy - Most recent error: policy number must "
                        + "start with P-", null);
        t.add("java Policy converter-override", p -> text(JsonIo.toJava(holder, converts).asClass(Policy.class)),
                "Policy:Policy(P-Ann)", null);
        t.add("maps Policy converter-override", p -> text(JsonIo.toMaps(holder, converts).asClass(Policy.class)),
                "Policy:Policy(P-Ann)", null);
        t.add("java Policy factory", p -> text(JsonIo.toJava(holder, new ReadOptionsBuilder()
                .addClassFactory(Policy.class, factory).build()).asClass(Policy.class)), "Policy:Policy(P-Ann)", null);
        t.add("java Policy factory-throws + converter-override", p -> text(JsonIo.toJava(holder,
                new ReadOptionsBuilder().addClassFactory(Policy.class, failing)
                        .addConverterOverride(Map.class, Policy.class, numbered).build()).asClass(Policy.class)),
                "Policy:Policy(P-Ann)", null);
        t.add("java ZonedDateTime from object", p -> text(JsonIo.toJava("{\"zonedDateTime\":"
                        + "\"2024-01-15T10:30:00-05:00[America/New_York]\"}", null).asClass(ZonedDateTime.class)),
                "ZonedDateTime:2024-01-15T10:30-05:00[America/New_York]", SAME);
        t.verify(8);
    }

    // ---- The machinery

    /** A case: what it gives, as text, with the app's classes present or absent. */
    private interface Case {
        String run(boolean present) throws Exception;
    }

    /** Cases and what each gives with the classes present, and absent (SAME: as present; null: case names a class). */
    private static final class Table {
        private final List<String> wrong = new ArrayList<>();
        private int count;

        Table add(String name, Case c, String present, String absent) {
            count++;
            check(name, c, true, present);
            if (absent != null) {
                check(name + " (classes absent)", c, false, absent == SAME ? present : absent);
            }
            return this;
        }

        private void check(String name, Case c, boolean present, String expected) {
            String actual;
            try {
                actual = c.run(present);
            } catch (Throwable t) {
                String m = String.valueOf(t.getMessage()).split("\n")[0];
                actual = "THROWS " + t.getClass().getSimpleName() + ": " + m;
            }
            actual = actual.replace(HERE, "app.").replace(GONE, "app.");
            if (!expected.equals(actual)) {
                wrong.add(name + "\n    expected " + expected + "\n    actual   " + actual);
            }
        }

        void verify(int cases) {
            assertEquals(cases, count);
            assertTrue(wrong.isEmpty(), wrong.size() + " of " + count + " cases differ:\n" + String.join("\n", wrong));
        }
    }

    /** The JSON with "app." naming this class's classes (present), or classes that are not there (absent). */
    private static String json(boolean present, String json) {
        return json.replace(HERE, "app.").replace("app.", present ? HERE : GONE);
    }

    private static Object read(boolean present, String json) {
        return JsonIo.toMaps(json(present, json), null).asClass(null);
    }

    private static String maps(boolean present, String json, Class<?> asked) {
        return text(JsonIo.toMaps(json(present, json), null).asClass(asked));
    }

    private static String noisy(boolean present, String json) {
        String tree = maps(present, json, null);
        return "init=" + INITIALIZED + " new=" + CONSTRUCTED + " " + tree;
    }

    private static Object first(Object container) {
        if (container instanceof Object[]) {
            return ((Object[]) container)[0];
        }
        if (container instanceof List) {
            return ((List<?>) container).get(0);
        }
        return ((JsonObject) container).getItems()[0];
    }

    private static Object field(Object map, String key) {
        return ((Map<?, ?>) map).get(key);
    }

    private static String orderValues(Object root) {
        Object line = first(field(root, "lines"));
        return "root.count=" + text(field(root, "count")) + " root.status=" + text(field(root, "status"))
                + " root.when=" + text(field(root, "when")) + " line.price=" + text(field(line, "price")) + " any="
                + text(field(root, "any"));
    }

    /** A value as text: its class and value, containers with their contents, a container seen before as ^n. */
    private static String text(Object value) {
        return text(value, new IdentityHashMap<>());
    }

    private static String text(Object v, IdentityHashMap<Object, Integer> ids) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Map || v instanceof Collection || v.getClass().isArray()) {
            Integer seen = ids.get(v);
            if (seen != null) {
                return "^" + seen;
            }
            ids.put(v, ids.size() + 1);
        }
        StringBuilder sb = new StringBuilder();
        if (v instanceof JsonObject) {
            JsonObject j = (JsonObject) v;
            String type = j.getTypeString();
            sb.append('J').append(type == null ? "" : "<" + type.replaceAll("^java\\.(lang|util|time)\\.", "") + ">");
            sb.append('{');
            int i = 0;
            for (Map.Entry<?, ?> e : j.entrySet()) {
                sb.append(i++ > 0 ? "," : "").append(e.getKey()).append('=').append(text(e.getValue(), ids));
            }
            if (j.getItems() != null && (j.isArray() || j.isEmpty())) {
                sb.append(i > 0 ? "," : "").append("@items=").append(text(j.getItems(), ids));
            }
            return sb.append('}').toString();
        }
        String c = v.getClass().getSimpleName();
        if (v.getClass().isArray()) {
            sb.append(c).append('[');
            for (int i = 0; i < Array.getLength(v); i++) {
                sb.append(i > 0 ? "," : "").append(text(Array.get(v, i), ids));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Map) {
            sb.append(c).append('{');
            int i = 0;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                sb.append(i++ > 0 ? "," : "").append(text(e.getKey(), ids)).append('=').append(text(e.getValue(), ids));
            }
            return sb.append('}').toString();
        }
        if (v instanceof Collection) {
            sb.append(c).append('[');
            int i = 0;
            for (Object o : (Collection<?>) v) {
                sb.append(i++ > 0 ? "," : "").append(text(o, ids));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Optional) {
            Optional<?> o = (Optional<?>) v;
            return o.isPresent() ? "Optional[" + text(o.get(), ids) + "]" : "Optional.empty";
        }
        if (v instanceof Enum) {
            return ((Enum<?>) v).getDeclaringClass().getSimpleName() + ":" + v;
        }
        if (v instanceof Date) {
            return c + ":" + ((Date) v).getTime();
        }
        if (v instanceof Class) {
            return "Class:" + ((Class<?>) v).getName();
        }
        if (v instanceof String && ((String) v).isEmpty()) {
            return "String:\"\"";
        }
        return c + ":" + v;
    }
}
