package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A read that fails names where in the document: its message's first line ends " (at $.lines[1].qty)", a JSONPath --
 * a field or a map's key {@code .name} (or {@code ["name"]}), an element {@code [i]}. It named the class and field at
 * most ("Line.qty: Value 'three' not parseable .."), and for an element of an array or a value of a map nothing: which
 * of a document's lines, or which key, was not said. The message's beginning and its cause are as they were.
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
class ErrorLocationTest {
    public static class Line {
        public int qty;
        public BigDecimal price;
        public int[] sizes;
        public Line sub;
    }

    /** Its only constructor refuses what the JSON gives it: a message of several lines. */
    public static class Strict {
        public final String number;

        public Strict(String number) {
            if (number == null || !number.startsWith("P-")) {
                throw new IllegalArgumentException("number must start with P-");
            }
            this.number = number;
        }
    }

    public static class Order {
        public List<Strict> stricts;
        public List<Line> lines;
        public Map<String, Integer> counts;
        public int[] sizes;
        public Line main;
        public Line[] arr;
        public Map<String, Line> byName;
        public List<List<Integer>> grid;
        public Map<Integer, String> numbered;
    }

    private static JsonIoException readError(String json) {
        return assertThrows(JsonIoException.class, () -> JsonIo.toJava(json, null).asClass(Order.class));
    }

    private static String firstLine(JsonIoException e) {
        return e.getMessage().split("\n")[0];
    }

    @Test
    void aFieldOfAnElementOfAList() {
        JsonIoException e = readError("{\"lines\":[{\"qty\":1},{\"qty\":\"three\"}]}");
        assertTrue(firstLine(e).endsWith("(at $.lines[1].qty)"), e.getMessage());
        assertTrue(e.getMessage().startsWith(Line.class.getName() + ".qty: Value 'three'"), e.getMessage());    // as it was
    }

    @Test
    void elementsAndMapValues() {
        String[][] cases = {
                {"{\"counts\":{\"a\":1,\"b\":\"two\"}}", "$.counts.b"},
                {"{\"sizes\":[1,\"x\"]}", "$.sizes[1]"},
                {"{\"arr\":[{\"qty\":1},{\"qty\":\"three\"}]}", "$.arr[1].qty"},
                {"{\"byName\":{\"x y\":{\"qty\":\"three\"}}}", "$.byName[\"x y\"].qty"},
                {"{\"grid\":[[1,2],[3,\"z\"]]}", "$.grid[1][1]"},
                {"{\"lines\":[{\"sizes\":[1,\"w\"]}]}", "$.lines[0].sizes"},
                {"{\"numbered\":{\"@keys\":[\"one\"],\"@items\":[\"a\"]}}", "$.numbered[\"@keys\"][0]"},
                {"{\"main\":{\"sub\":{\"sub\":{\"price\":\"cheap\"}}}}", "$.main.sub.sub.price"},
        };
        for (String[] c : cases) {
            JsonIoException e = readError(c[0]);
            assertTrue(firstLine(e).endsWith("(at " + c[1] + ")"), c[0] + " -> " + e.getMessage());
        }
    }

    @Test
    void atTheRoot() {
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"qty\":\"root\"}", null).asClass(Line.class));
        assertTrue(firstLine(e).endsWith("(at $.qty)"), e.getMessage());
        e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("[{\"qty\":1},{\"qty\":\"three\"}]", null).asType(new TypeHolder<List<Line>>() {}));
        assertTrue(firstLine(e).endsWith("(at $[1].qty)"), e.getMessage());
        e = assertThrows(JsonIoException.class, () -> JsonIo.toJava("[{\"qty\":\"x\"}]", null).asClass(Line[].class));
        assertTrue(firstLine(e).endsWith("(at $[0].qty)"), e.getMessage());
    }

    /** On a message of several lines, at the end of its first. */
    @Test
    void onTheFirstLine() {
        JsonIoException e = readError("{\"stricts\":[{\"number\":\"P-1\"},{\"number\":\"X\"}]}");
        assertTrue(e.getMessage().contains("\n"), e.getMessage());
        assertTrue(firstLine(e).endsWith("(at $.stricts[1])"), e.getMessage());
    }

    public static class Wrapper {
        public Line inner;
    }

    public static class Holder {
        public Wrapper w;
    }

    /**
     * A ClassFactory that reads a part itself (resolver.toJava) starts a read within the read: only the outermost names
     * the place, once -- the inner one's place is within the part, not the document.
     */
    @Test
    void aReadWithinTheReadNamesThePlaceOnce() {
        ClassFactory factory = new ClassFactory() {
            @Override
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
                Wrapper w = new Wrapper();
                w.inner = (Line) resolver.toJava(Line.class, jObj.get("inner"));
                return w;
            }

            @Override
            public boolean isObjectFinal() {
                return true;
            }
        };
        ReadOptions options = new ReadOptionsBuilder().addClassFactory(Wrapper.class, factory).build();
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"w\":{\"inner\":{\"sub\":{\"qty\":\"bad\"}}}}", options).asClass(Holder.class));
        String message = e.getMessage();
        assertEquals(message.indexOf("(at "), message.lastIndexOf("(at "), message);
    }

    /** Its cause is the conversion's, as it was. */
    @Test
    void theCauseIsKept() {
        JsonIoException e = readError("{\"main\":{\"qty\":\"three\"}}");
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, cause, e.getMessage());
    }

    /**
     * Maps mode names where too. An object below an element of an array of a class, read and released as the read
     * goes, is not found: its message is as it was, without a place.
     */
    @Test
    void mapsModeAndWhatIsNotFound() {
        String set = "{\"set\":{\"@type\":\"java.util.EnumSet\",\"@enum\":\"" + Thread.State.class.getName()
                + "\",\"@items\":[\"NEW\",\"LATER\"]}}";
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toMaps(set, null).asClass(null));
        assertTrue(firstLine(e).endsWith("(at $.set)"), e.getMessage());
        e = readError("{\"arr\":[{\"sub\":{\"qty\":\"deep\"}}]}");
        assertFalse(e.getMessage().contains("(at "), e.getMessage());
        assertTrue(e.getMessage().startsWith(Line.class.getName() + ".qty: Value 'deep'"), e.getMessage());
    }
}
