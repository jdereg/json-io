package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In Maps mode a value below the root that does not convert -- {"@type":"BigDecimal","value":"abc"}, a date that is
 * no date, a constant its enum does not have, "three" where the root's class declares an int -- stays the JSON that
 * holds it, as written: the read goes on, the values around it are converted as before, and written back the text is
 * the same. It failed the whole read. In Java mode, and where the caller asks for the type at the root, it is an error
 * still.
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
class MapsModeValueStaysAsWrittenTest {
    public enum Color { RED, GREEN }

    public static class Line {
        public int qty;
        public BigDecimal price;
        public LocalDate when;
    }

    private static Map<?, ?> maps(String json) {
        return (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
    }

    private static Map<?, ?> wrapper(Object value) {
        assertInstanceOf(JsonObject.class, value);
        return (Map<?, ?>) value;
    }

    @Test
    void aValueThatDoesNotConvertStaysAsWritten() {
        String json = "{\"bad\":{\"@type\":\"BigDecimal\",\"value\":\"abc\"},\"ok\":{\"@type\":\"BigDecimal\",\"value\":\"1.50\"},"
                + "\"id\":{\"@type\":\"UUID\",\"value\":\"not-a-uuid\"},\"day\":{\"@type\":\"Date\",\"value\":\"yesterday\"}}";
        Map<?, ?> read = maps(json);
        assertEquals("abc", wrapper(read.get("bad")).get("value"));
        assertEquals(new BigDecimal("1.50"), read.get("ok"));      // the values around it, as before
        assertEquals("not-a-uuid", wrapper(read.get("id")).get("value"));
        assertEquals("yesterday", wrapper(read.get("day")).get("value"));
        assertEquals(json, JsonIo.toJson(read, null));
    }

    /** In a list and among a map's keys; a constant its enum does not have, beside one it has. */
    @Test
    void inAListAMapsKeysAndAnEnum() {
        Map<?, ?> read = maps("{\"dates\":[1,{\"@type\":\"LocalDate\",\"value\":\"soon\"},{\"@type\":\"LocalDate\",\"value\":\"2024-01-15\"}],"
                + "\"colors\":[{\"@type\":\"" + Color.class.getName() + "\",\"name\":\"BLUE\"},{\"@type\":\"" + Color.class.getName()
                + "\",\"name\":\"RED\"}]}");
        Object[] dates = (Object[]) read.get("dates");
        assertEquals("soon", wrapper(dates[1]).get("value"));
        assertEquals(LocalDate.of(2024, 1, 15), dates[2]);
        Object[] colors = (Object[]) read.get("colors");
        assertEquals("BLUE", wrapper(colors[0]).get("name"));
        assertEquals(Color.RED, colors[1]);
        String keyed = "{\"k\":{\"@type\":\"HashMap\",\"@keys\":[{\"@type\":\"Integer\",\"value\":\"z\"}],\"@items\":[1]}}";
        assertEquals(keyed, JsonIo.toJson(maps(keyed), null));
    }

    /** The root's class types the root's fields it can: one its field's type cannot hold stays as written. */
    @Test
    void theRootsClassTypesWhatItCan() {
        String json = "{\"@type\":\"" + Line.class.getName() + "\",\"qty\":\"three\",\"price\":\"9.50\",\"when\":\"later\"}";
        Map<?, ?> read = maps(json);
        assertEquals("three", read.get("qty"));
        assertEquals(new BigDecimal("9.50"), read.get("price"));
        assertEquals("later", read.get("when"));
        Map<?, ?> typed = maps("{\"@type\":\"" + Line.class.getName() + "\",\"qty\":2,\"price\":{\"@type\":\"BigDecimal\",\"value\":\"x\"}}");
        assertEquals(2, typed.get("qty"));
        assertEquals("x", wrapper(typed.get("price")).get("value"));
    }

    /** A root whose own @type is a value type, none asked for: the JSON as written where its value does not convert. */
    @Test
    void aRootOfAValueTypeNoneAskedFor() {
        assertEquals("abc", wrapper(JsonIo.toMaps("{\"@type\":\"BigDecimal\",\"value\":\"abc\"}", null).asClass(null)).get("value"));
        assertEquals("BLUE", wrapper(JsonIo.toMaps("{\"@type\":\"" + Color.class.getName() + "\",\"name\":\"BLUE\"}", null)
                .asClass(null)).get("name"));
    }

    /** Errors still: the type asked for at the root, Java mode, an EnumSet holding a name its enum does not have. */
    @Test
    void whatIsAnErrorStill() {
        String bad = "{\"@type\":\"BigDecimal\",\"value\":\"abc\"}";
        assertThrows(JsonIoException.class, () -> JsonIo.toMaps(bad, null).asClass(BigDecimal.class));
        assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"v\":" + bad + "}", null).asClass(null));
        assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"qty\":\"three\"}", null).asClass(Line.class));
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toMaps("{\"set\":{\"@type\":\"java.util.EnumSet\","
                + "\"@enum\":\"" + Color.class.getName() + "\",\"@items\":[\"RED\",\"BLUE\"]}}", null).asClass(null));
        assertTrue(e.getMessage().contains("BLUE"), e.getMessage());   // naming the name its enum does not have
        List<?> fine = JsonIo.toMaps("[{\"@type\":\"BigDecimal\",\"value\":\"2\"}]", null).asClass(List.class);
        assertEquals(new BigDecimal("2"), fine.get(0));
    }
}
