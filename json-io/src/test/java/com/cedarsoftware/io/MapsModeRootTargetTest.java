package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In Maps mode the type asked for at the root is what comes back, or an error naming why the JSON cannot be it, as in
 * Java mode: a class is built as Java mode builds it from the tree, the type arguments of a list or map reach its values
 * ({@code List<LocalDate>}). Below the root the tree stays JSON. A class was refused ("rootType .. is not supported"),
 * type arguments were ignored, and a JSON object asked for as a list was an empty list.
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
class MapsModeRootTargetTest {
    public enum Status { NEW, DONE }

    public static class Person {
        public String name;
        public int age;
        public LocalDate born;
        public Status status;
    }

    public static class Owner {
        public String name;
        public Object pet;
    }

    /** Its only constructor refuses what the JSON gives it. */
    public static class Policy {
        public final String number;

        public Policy(String number) {
            if (number == null || !number.startsWith("P-")) {
                throw new IllegalArgumentException("policy number must start with P-");
            }
            this.number = number;
        }
    }

    private static <T> T maps(String json, TypeHolder<T> type) {
        return JsonIo.toMaps(json, null).asType(type);
    }

    private static Object maps(String json, Class<?> c) {
        return JsonIo.toMaps(json, null).asClass(c);
    }

    private static Object mapsFromStream(String json, Class<?> c) {
        return JsonIo.toMaps(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), null).asClass(c);
    }

    private static void assertError(Executable read, String... named) {
        JsonIoException e = assertThrows(JsonIoException.class, read);
        for (String part : named) {
            assertTrue(e.getMessage().contains(part), e.getMessage());
        }
    }

    private static final String ANN = "{\"name\":\"Ann\",\"age\":30,\"born\":\"1990-05-01\",\"status\":\"DONE\"}";

    @Test
    void aClassIsBuilt() {
        for (Object read : new Object[] {maps(ANN, Person.class), mapsFromStream(ANN, Person.class)}) {
            Person ann = (Person) read;
            assertEquals("Ann", ann.name);
            assertEquals(30, ann.age);
            assertEquals(LocalDate.of(1990, 5, 1), ann.born);
            assertEquals(Status.DONE, ann.status);
        }
        Person[] people = (Person[]) maps("[" + ANN + ",{\"name\":\"Bo\"}]", Person[].class);
        assertEquals("Bo", people[1].name);
        assertEquals(Status.DONE, maps("{\"@type\":\"" + Status.class.getName() + "\",\"name\":\"DONE\"}", Status.class));
        // a class the tree names that is not on the classpath stays a map, as the Maps-mode read found it
        Owner owner = (Owner) maps("{\"name\":\"Ann\",\"pet\":{\"@type\":\"com.nowhere.Dog\",\"legs\":4}}", Owner.class);
        assertEquals(4L, ((Map<?, ?>) owner.pet).get("legs"));
    }

    /** A class the JSON cannot be: Java mode's error, naming why (it was "rootType .. is not supported"). */
    @Test
    void aClassThatCannotBeBuiltIsAnError() {
        assertError(() -> maps("{\"holder\":\"Ann\"}", Policy.class), Policy.class.getName());
        assertError(() -> maps("{\"name\":\"Ann\",\"age\":\"thirty\"}", Person.class), "age", "thirty");
        assertError(() -> mapsFromStream("{\"name\":\"Ann\",\"age\":\"thirty\"}", Person.class), "age", "thirty");
        assertError(() -> maps("[1,2]", Person.class), Person.class.getName());
    }

    @Test
    void typeArgumentsReachTheValues() {
        assertEquals(Arrays.asList(LocalDate.of(2024, 1, 15)), maps("[\"2024-01-15\"]", new TypeHolder<List<LocalDate>>() {}));
        assertEquals(LocalDate.of(2024, 1, 15),
                maps("{\"a\":\"2024-01-15\"}", new TypeHolder<Map<String, LocalDate>>() {}).get("a"));
        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 2)), maps("[1,2]", new TypeHolder<Set<Integer>>() {}));
        assertEquals(1, maps("{\"1\":\"a\"}", new TypeHolder<Map<Integer, String>>() {}).keySet().iterator().next());
        assertEquals("Ann", maps("[" + ANN + "]", new TypeHolder<List<Person>>() {}).get(0).name);
        assertEquals("Ann", maps("{\"x\":" + ANN + "}", new TypeHolder<Map<String, Person>>() {}).get("x").name);
        assertEquals("Ann", maps(ANN, new TypeHolder<Optional<Person>>() {}).get().name);
        assertEquals(LocalDate.of(2024, 1, 15),
                maps("[[\"2024-01-15\"]]", new TypeHolder<List<List<LocalDate>>>() {}).get(0).get(0));
    }

    /** Values that do not convert to the type arguments: an error naming the value (they were left as the JSON gave them). */
    @Test
    void valuesThatDoNotConvertAreAnError() {
        assertError(() -> maps("[\"2024-01-15\",\"later\"]", new TypeHolder<List<LocalDate>>() {}), "later");
        assertError(() -> maps("{\"a\":\"later\"}", new TypeHolder<Map<String, LocalDate>>() {}), "later");
        assertError(() -> maps("[{\"age\":\"thirty\"}]", new TypeHolder<List<Person>>() {}), "age", "thirty");
    }

    /**
     * A JSON object asked for as a list, a set or an array: an error (it was an empty one, its fields lost). With type
     * arguments too.
     */
    @Test
    void anObjectIsNotAList() {
        for (Class<?> c : new Class<?>[] {List.class, ArrayList.class, Set.class, Object[].class}) {
            assertError(() -> maps("{\"a\":1}", c), "Return type mismatch", c.getName());
        }
        assertError(() -> maps("{\"a\":1}", new TypeHolder<List<LocalDate>>() {}), "Return type mismatch", "java.util.List");
        assertEquals(new ArrayList<>(), maps("{\"@type\":\"ArrayList\"}", List.class));     // a list the JSON names
    }

    /**
     * An array asked for as an array of maps, lists or numbers holds what the JSON holds, or is an error: the tree's own
     * objects, a reference the object it names (it was a second, empty map); an object in a list (it was a null); a
     * list where a number belongs (it was a 0) or a map belongs (a null) an error.
     */
    @Test
    void anArrayHoldsWhatTheJsonHolds() {
        Map<?, ?>[] maps = (Map<?, ?>[]) maps("[{\"@id\":1,\"a\":1},{\"@ref\":1}]", Map[].class);
        assertInstanceOf(JsonObject.class, maps[0]);
        assertSame(maps[0], maps[1]);
        List<?>[] lists = (List<?>[]) maps("[[{\"a\":1}],[]]", List[].class);
        assertEquals(1L, ((Map<?, ?>) lists[0].get(0)).get("a"));
        assertError(() -> maps("[[1,2]]", int[].class), "Unsupported conversion");
        assertError(() -> maps("[{\"a\":1},[1]]", Map[].class), "Unsupported conversion");
    }

    /** What Maps mode read itself it reads as before; what was an error is an error still. */
    @Test
    void whatDidNotChange() {
        String json = "{\"a\":1,\"b\":[1,2]}";
        assertInstanceOf(JsonObject.class, maps(json, Map.class));
        assertInstanceOf(JsonObject.class, maps(json, new TypeHolder<Map<String, Object>>() {}));
        assertInstanceOf(JsonObject.class, maps(json, (Class<?>) null));
        List<Map<String, Object>> rows = maps("[{\"a\":1}]", new TypeHolder<List<Map<String, Object>>>() {});
        assertInstanceOf(JsonObject.class, rows.get(0));       // the tree's own, as asked for as Map
        assertEquals(Arrays.asList(1L, 2L), maps("[1,2]", List.class));
        assertEquals(Arrays.asList(1L, 2L), maps("[1,2]", new TypeHolder<List<Object>>() {}));
        assertArrayEquals(new Object[] {1L, 2L}, (Object[]) maps("[1,2]", Object[].class));
        assertArrayEquals(new int[] {1, 2}, (int[]) maps("[1,2]", int[].class));
        assertArrayEquals(new LocalDate[] {LocalDate.of(2024, 1, 15)}, (LocalDate[]) maps("[\"2024-01-15\"]", LocalDate[].class));
        assertArrayEquals(new String[] {"x"}, (String[]) maps("{\"@type\":\"HashSet\",\"@items\":[\"x\"]}", String[].class));
        assertEquals(Arrays.asList(1L, 2L), Arrays.asList(((List<?>[]) maps("[[1,2]]", List[].class))[0].toArray()));
        assertEquals(new TreeSet<>(Arrays.asList("a", "b")), maps("[\"b\",\"a\"]", TreeSet.class));
        assertEquals(42, maps("\"42\"", Integer.class));
        assertEquals(5, maps("{\"value\":5}", Integer.class));
        assertEquals(Status.DONE, maps("\"DONE\"", Status.class));
        assertEquals(Optional.of("abc"), maps("\"abc\"", Optional.class));
        assertEquals(Optional.empty(), maps("null", Optional.class));
        assertEquals(Optional.empty(), maps("null", new TypeHolder<Optional<Person>>() {}));
        // errors
        assertError(() -> maps("\"abc\"", Integer.class), "abc");
        assertError(() -> maps("\"abc\"", Status.class), "abc");
        assertError(() -> maps("{\"a\":1}", LocalDate.class), "LocalDate");
        assertError(() -> maps("[\"2024-01-15\",\"x\"]", LocalDate[].class), "x");
        assertError(() -> maps("[1,2]", Map.class), "Return type mismatch");
        assertError(() -> maps("\"Ann\"", Person.class), "Return type mismatch");
        assertError(() -> maps("{\"a\":{\"@ref\":9}}", LocalDate.class));
        assertError(() -> maps("{\"a\":", Person.class));
    }

    /** A conversion registered from a Map to the class builds it here as it does in Java mode. */
    @Test
    void aRegisteredConversionBuildsIt() {
        ReadOptions options = new ReadOptionsBuilder().addConverterOverride(Map.class, Policy.class,
                (from, converter) -> new Policy("P-" + ((Map<?, ?>) from).get("holder"))).build();
        Object read = JsonIo.toMaps("{\"holder\":\"Ann\"}", options).asClass(Policy.class);
        assertEquals("P-Ann", ((Policy) read).number);
    }
}
