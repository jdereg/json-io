package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A field declared with its class's type variables (T value, T[] arr, List&lt;T&gt; items) takes them from the type
 * of the object holding it -- the declared type of the field that holds that object (Box&lt;Integer&gt;), its
 * owner's (OC&lt;String&gt;.Inner), or a subclass that binds them. The object's parameterized type was dropped for
 * its raw class while it was instantiated, so T was left unresolved ("Unresolved type: java.util.List&lt;T&gt;"),
 * became its bound (a String "5" refused for Number), or stayed Object (a Long stored where a String was declared,
 * a ClassCastException later; "java.lang.Long" for a T[]).
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
class GenericFieldTypeTest {

    public static class Box<T extends Number> {
        public T value;
        public List<T> list;
    }

    public static class UsesBox {
        public Box<Integer> box;
    }

    public static class IntBox extends Box<Integer> {
    }

    public static class UsesIntBox {
        public IntBox box;
    }

    public static class OC<T> {
        public class Inner {
            public T value;
        }
    }

    public static class UsesInner {
        public OC<String>.Inner inner;
    }

    public static class G<T> {
        public T[] arr;
    }

    public static class UsesG {
        public G<Integer> g;
    }

    public static class Item {
        public String name;

        public Item() {
        }

        Item(String name) {
            this.name = name;
        }
    }

    public static class Gen<T> {
        public List<T> items;
        public Map<String, T> byName;
        public List<List<T>> nested;
    }

    public static class UsesGen {
        public Gen<Item> gen;
    }

    public static class Base<T> {
        public T v;
    }

    public static class Mid<U> extends Base<List<U>> {
    }

    public static class UsesMid {
        public Mid<Long> mid;
    }

    private static <T> T read(String json, Class<T> type) {
        return JsonIo.toJava(json, null).asClass(type);
    }

    @Test
    void aTypeVariableTakesTheFieldsTypeArgument() {
        UsesBox u = read("{\"box\":{\"value\":\"5\",\"list\":[\"1\",\"2\"]}}", UsesBox.class);
        assertEquals(Integer.valueOf(5), u.box.value);
        assertEquals(Arrays.asList(1, 2), u.box.list);
        assertInstanceOf(Integer.class, ((List<?>) u.box.list).get(0));
    }

    @Test
    void aSubclassBindsTheTypeVariable() {
        UsesIntBox u = read("{\"box\":{\"value\":\"7\",\"list\":[\"8\"]}}", UsesIntBox.class);
        assertEquals(Integer.valueOf(7), u.box.value);
        assertInstanceOf(Integer.class, ((List<?>) u.box.list).get(0));
    }

    /** OC&lt;String&gt;.Inner: the owner's argument binds T; a number in the JSON becomes the declared String. */
    @Test
    void theOwnersTypeArgumentBindsAnInnerClassField() {
        UsesInner u = read("{\"inner\":{\"value\":5}}", UsesInner.class);
        String s = u.inner.value;
        assertEquals("5", s);
    }

    @Test
    void anArrayOfTheTypeVariable() {
        UsesG u = read("{\"g\":{\"arr\":[1,2]}}", UsesG.class);
        Integer[] arr = u.g.arr;
        assertEquals(Arrays.asList(1, 2), Arrays.asList(arr));
    }

    @Test
    void containersOfTheTypeVariable() {
        UsesGen u = read("{\"gen\":{\"items\":[{\"name\":\"a\"},{\"name\":\"b\"}],\"byName\":{\"c\":{\"name\":\"c\"}},"
                + "\"nested\":[[{\"name\":\"d\"}]]}}", UsesGen.class);
        Item first = u.gen.items.get(0);
        assertEquals("a", first.name);
        Item c = u.gen.byName.get("c");
        assertEquals("c", c.name);
        Item d = u.gen.nested.get(0).get(0);
        assertEquals("d", d.name);
    }

    /** A superclass's T bound by a subclass's own argument: Mid&lt;Long&gt; extends Base&lt;List&lt;Long&gt;&gt;. */
    @Test
    void aTypeVariableBoundThroughTheHierarchy() {
        UsesMid u = read("{\"mid\":{\"v\":[\"1\",\"2\"]}}", UsesMid.class);
        List<Long> v = u.mid.v;
        assertEquals(Arrays.asList(1L, 2L), v);
    }

    /** A generic class read raw, with no argument anywhere: its containers take their elements as they come. */
    @Test
    void aRawGenericClassReadsItsContainersAsTheyCome() {
        Gen<?> g = read("{\"items\":[1,\"s\"],\"nested\":[[2]]}", Gen.class);
        assertEquals(Arrays.asList(1L, "s"), g.items);
        assertEquals(Arrays.asList(Arrays.asList(2L)), g.nested);
    }

    /** What json-io writes for such fields reads back as it was. */
    @Test
    void roundTrip() {
        UsesGen u = new UsesGen();
        u.gen = new Gen<>();
        u.gen.items = new ArrayList<>(Arrays.asList(new Item("a"), new Item("b")));
        u.gen.byName = new LinkedHashMap<>();
        u.gen.byName.put("x", new Item("x"));
        u.gen.nested = new ArrayList<>();
        u.gen.nested.add(new ArrayList<>(Arrays.asList(new Item("n"))));
        for (WriteOptions options : new WriteOptions[] {new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().showTypeInfoNever().build()}) {
            UsesGen back = read(JsonIo.toJson(u, options), UsesGen.class);
            assertEquals("b", back.gen.items.get(1).name);
            assertEquals("x", back.gen.byName.get("x").name);
            assertEquals("n", back.gen.nested.get(0).get(0).name);
        }
    }
}
