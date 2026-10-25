package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.cedarsoftware.io.annotation.IoValue;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.SystemUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record's components are read as their declared types, as an {@code @IoCreator}'s arguments are: an object, a
 * list, a map, an array, an {@code Optional}, another record, a list of records, an {@code Object} holding one. Only
 * scalars were converted: any other component reached the canonical constructor as read -- a JsonObject or an
 * Object[] -- which it refused ("Cannot cast [Ljava.lang.Object; to java.util.List"), or, for a {@code Map}, kept,
 * so the record held a JsonObject. The records are compiled here (Java 16+), as json-io itself builds on Java 8.
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
class RecordComponentsTest {
    private static final String SOURCE = "package rc;\n"
            + "import java.util.*;\n"
            + "public class Records {\n"
            + "  public static class Address {\n"
            + "    public String street;\n"
            + "    public Address() { }\n"
            + "    public Address(String street) { this.street = street; }\n"
            + "    public boolean equals(Object o) {\n"
            + "      return o instanceof Address && Objects.equals(((Address) o).street, street);\n"
            + "    }\n"
            + "    public int hashCode() { return Objects.hashCode(street); }\n"
            + "  }\n"
            + "  public record Point(int x, int y) { }\n"
            + "  public record Parts(String name, Map<String, Integer> m, List<String> tags, Address addr, Point p,\n"
            + "      int[] nums, Optional<String> opt, List<Point> points, Object any) { }\n"
            + "  public record Totals(Map<String, Integer> m, int total) {\n"
            + "    public Totals {\n"
            + "      m = Map.copyOf(m);\n"
            + "      total = m.values().stream().mapToInt(Integer::intValue).sum();\n"
            + "    }\n"
            + "  }\n"
            + "  public record Sparse(String name, int count, boolean flag) { }\n"
            + "  public record Loop(String name, List<Object> items) { }\n"
            + "  public record Self(String name, Object self) { }\n"
            + "  public record Pair(List<Object> a, List<Object> b, int n) { }\n"
            + "  public record Code(String v) {\n"
            + "    @com.cedarsoftware.io.annotation.IoValue public String v() { return v; }\n"
            + "  }\n"
            + "  public static class Coded { public Code code; public List<Code> codes; }\n"
            + "  public static class Holder { public Parts parts; public Totals totals; }\n"
            + "}\n";

    private static ClassLoader loader;

    @BeforeAll
    static void compile() throws Exception {
        Assumptions.assumeTrue(SystemUtilities.currentJdkMajorVersion() >= 16, "Records need Java 16+");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assumptions.assumeTrue(compiler != null, "No system Java compiler");
        Path dir = SystemUtilities.createTempDirectory("recordComponents").toPath();
        Path src = dir.resolve("Records.java");
        Files.write(src, SOURCE.getBytes(StandardCharsets.UTF_8));
        // json-io's annotations from where they were loaded (the test's class path is a manifest jar javac skips)
        String annotations = Paths.get(IoValue.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
        int rc = compiler.run(null, null, null, "-d", dir.toString(), "-cp", annotations, src.toString());
        assertEquals(0, rc);
        loader = URLClassLoader.newInstance(new URL[] {dir.toUri().toURL()},
                RecordComponentsTest.class.getClassLoader());
    }

    private static Class<?> cls(String name) throws Exception {
        return ClassUtilities.forName("rc.Records$" + name, loader);
    }

    /** A record built by its canonical constructor (a record's only one here). */
    private static Object newRecord(String name, Object... components) throws Exception {
        return ReflectionUtils.getAllConstructors(cls(name))[0].newInstance(components);
    }

    private static Object call(Object record, String accessor) {
        return ReflectionUtils.call(record, accessor);
    }

    private static Object point(int x, int y) throws Exception {
        return newRecord("Point", x, y);
    }

    private static Object parts() throws Exception {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", 2);
        List<Object> points = new ArrayList<>(Arrays.asList(point(5, 6)));
        Object address = ReflectionUtils.getConstructor(cls("Address"), String.class).newInstance("Main");
        return newRecord("Parts", "n", m, new ArrayList<>(Arrays.asList("t1", "t2")), address, point(1, 2),
                new int[] {3, 4}, Optional.of("o"), points, point(7, 8));
    }

    private static ReadOptions readOptions() {
        return new ReadOptionsBuilder().classLoader(loader).build();
    }

    private static void assertParts(Object expected, Object back) throws Exception {
        assertEquals(call(expected, "name"), call(back, "name"));
        assertInstanceOf(LinkedHashMap.class, call(back, "m"));
        assertEquals(call(expected, "m"), call(back, "m"));
        assertEquals(call(expected, "tags"), call(back, "tags"));
        assertEquals(call(expected, "addr"), call(back, "addr"));
        assertEquals(call(expected, "p"), call(back, "p"));
        assertArrayEquals((int[]) call(expected, "nums"), (int[]) call(back, "nums"));
        assertEquals(call(expected, "opt"), call(back, "opt"));
        assertEquals(call(expected, "points"), call(back, "points"));
        assertEquals(cls("Point"), ((List<?>) call(back, "points")).get(0).getClass());
        assertEquals(call(expected, "any"), call(back, "any"));
    }

    @Test
    void everyKindOfComponent() throws Exception {
        Object parts = parts();
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            assertParts(parts, JsonIo.toJava(JsonIo.toJson(parts, options), readOptions()).asClass(cls("Parts")));
        }
        for (WriteOptions options : new WriteOptions[] {new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            String toon = JsonIo.toToon(parts, options);
            assertParts(parts, JsonIo.fromToon(toon, readOptions()).asClass(cls("Parts")));
        }
    }

    /** In a field, and with a compact constructor that copies and sums its map (filled before it is given). */
    @Test
    void inAFieldAndThroughACompactConstructor() throws Exception {
        Object holder = ReflectionUtils.getConstructor(cls("Holder")).newInstance();
        Field partsField = ReflectionUtils.getField(cls("Holder"), "parts");
        Field totalsField = ReflectionUtils.getField(cls("Holder"), "totals");
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("x", 2);
        m.put("y", 3);
        partsField.set(holder, parts());
        totalsField.set(holder, newRecord("Totals", m, 0));
        Object back = JsonIo.toJava(JsonIo.toJson(holder, null), readOptions()).asClass(cls("Holder"));
        assertParts(partsField.get(holder), partsField.get(back));
        Object totals = totalsField.get(back);
        assertEquals(5, call(totals, "total"));
        assertEquals(m, call(totals, "m"));
    }

    /** An absent primitive component is its default; a present one is converted. */
    @Test
    void absentComponents() throws Exception {
        Object sparse = JsonIo.toJava("{\"name\":\"s\"}", readOptions()).asClass(cls("Sparse"));
        assertEquals("s", call(sparse, "name"));
        assertEquals(0, call(sparse, "count"));
        assertEquals(false, call(sparse, "flag"));
        sparse = JsonIo.toJava("{\"count\":\"7\",\"flag\":true}", readOptions()).asClass(cls("Sparse"));
        assertEquals(7, call(sparse, "count"));
        assertEquals(null, call(sparse, "name"));
    }

    /**
     * A record holding itself through a list holds itself: the list is filled once the record exists. One whose
     * component is itself cannot be given it before it exists: an error, not a loop.
     */
    @Test
    void aRecordThatHoldsItself() throws Exception {
        String json = "{\"@id\":1,\"@type\":\"rc.Records$Loop\",\"name\":\"me\",\"items\":[{\"@ref\":1}]}";
        Object loop = JsonIo.toJava(json, readOptions()).asClass(cls("Loop"));
        assertTrue(((List<?>) call(loop, "items")).get(0) == loop);

        String self = "{\"@id\":1,\"@type\":\"rc.Records$Self\",\"name\":\"me\",\"self\":{\"@ref\":1}}";
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava(self, readOptions()).asClass(cls("Self")));
        assertTrue(String.valueOf(e.getMessage()).contains("refers to it"), e.getMessage());
    }

    /**
     * A record written as the single value its {@code @IoValue} accessor returns is built from it by its canonical
     * constructor, in a field and a list: json-io's own record factory, which builds a record from its components,
     * does not take it (there is no component in a value).
     */
    @Test
    void aRecordWrittenByItsValue() throws Exception {
        Object coded = ReflectionUtils.getConstructor(cls("Coded")).newInstance();
        ReflectionUtils.getField(cls("Coded"), "code").set(coded, newRecord("Code", "x"));
        List<Object> codes = new ArrayList<>(Arrays.asList(newRecord("Code", "y")));
        ReflectionUtils.getField(cls("Coded"), "codes").set(coded, codes);
        String json = JsonIo.toJson(coded, null);
        assertTrue(json.contains("\"code\":\"x\""), json);
        Object back = JsonIo.toJava(json, readOptions()).asClass(cls("Coded"));
        assertEquals("x", call(ReflectionUtils.getField(cls("Coded"), "code").get(back), "v"));
        assertEquals("y", call(((List<?>) ReflectionUtils.getField(cls("Coded"), "codes").get(back)).get(0), "v"));
    }

    /**
     * In Maps mode a record holding an object reads as maps: it was built, from parts that stay maps, and threw. It is
     * no longer built: its components type its fields, and a part it holds twice is one map (an @ref was left).
     */
    @Test
    void inMapsMode() throws Exception {
        Object parts = parts();
        WriteOptions always = new WriteOptionsBuilder().showTypeInfoAlways().build();
        List<Map<?, ?>> reads = Arrays.asList(
                (Map<?, ?>) JsonIo.toMaps(JsonIo.toJson(parts, null), readOptions()).asClass(null),
                (Map<?, ?>) JsonIo.toMaps(JsonIo.toJson(parts, always), readOptions()).asClass(null),
                (Map<?, ?>) JsonIo.fromToonToMaps(JsonIo.toToon(parts, always), readOptions()).asClass(null));
        for (Map<?, ?> back : reads) {
            assertEquals("n", back.get("name"));
            assertEquals("Main", ((Map<?, ?>) back.get("addr")).get("street"));
        }

        List<Object> shared = new ArrayList<>(Arrays.asList("s", 1L));
        Object pair = newRecord("Pair", shared, shared, 5);
        Map<?, ?> back = (Map<?, ?>) JsonIo.toMaps(JsonIo.toJson(pair, null), readOptions()).asClass(null);
        assertSame(back.get("a"), back.get("b"));
        assertEquals(5, back.get("n"));

        String list = JsonIo.toJson(new ArrayList<>(Arrays.asList(parts, pair)), null);
        Object[] items = ((JsonObject) JsonIo.toMaps(list, readOptions()).asClass(null)).getItems();
        assertEquals("Main", ((Map<?, ?>) ((Map<?, ?>) items[0]).get("addr")).get("street"));
        assertEquals(5L, ((Map<?, ?>) items[1]).get("n"));     // in a list it stays JSON, as in a field
    }
}
