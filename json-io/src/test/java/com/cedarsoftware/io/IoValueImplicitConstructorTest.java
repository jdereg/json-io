package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class with an {@code @IoValue} method and no one-parameter {@code @IoCreator} reads back from its value through
 * the constructor whose one parameter has the type the method returns, as Jackson uses a single-argument constructor.
 * Up to now such a class did not read back from its value: an error naming the missing {@code @IoCreator}, and in
 * TOON, which now writes the value, a class that read back from its fields through 4.117.0 stopped reading back.
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
class IoValueImplicitConstructorTest {
    /** Written as its name, read back by its public one-String constructor. */
    public static class Tag {
        private final String name;

        public Tag(String name) {
            this.name = name;
        }

        @IoValue
        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Tag && Objects.equals(((Tag) o).name, name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public String toString() {
            return "Tag(" + name + ")";
        }
    }

    /** A private constructor taking the value is used too; the value read (a Long) is converted to its int. */
    public static class Code {
        private final int code;

        private Code(int code) {
            this.code = code;
        }

        static Code of(int code) {
            return new Code(code);
        }

        @IoValue
        public int code() {
            return code;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Code && ((Code) o).code == code;
        }

        @Override
        public int hashCode() {
            return code;
        }

        @Override
        public String toString() {
            return "Code(" + code + ")";
        }
    }

    /** The constructor takes the boxed form of what the method returns. */
    public static class Score {
        private final int score;

        public Score(Integer score) {
            this.score = score;
        }

        @IoValue
        public int score() {
            return score;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Score && ((Score) o).score == score;
        }

        @Override
        public int hashCode() {
            return score;
        }
    }

    /** Two one-parameter constructors: the one that takes what the method returns builds it. */
    public static class Label {
        private final String text;

        public Label(String text) {
            this.text = text;
        }

        public Label(StringBuilder text) {
            this.text = "built from a StringBuilder: " + text;
        }

        @IoValue
        public String text() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Label && Objects.equals(((Label) o).text, text);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(text);
        }
    }

    /** A two-parameter @IoCreator reads the fields; the constructor taking the value reads the value. */
    public static class Range {
        private final int lo;
        private final int hi;

        @IoCreator
        public Range(@IoProperty("lo") int lo, @IoProperty("hi") int hi) {
            this.lo = lo;
            this.hi = hi;
        }

        public Range(String text) {
            this(Integer.parseInt(text.substring(0, text.indexOf('~'))), Integer.parseInt(text.substring(text.indexOf('~') + 1)));
        }

        @IoValue
        public String text() {
            return lo + "~" + hi;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Range && ((Range) o).lo == lo && ((Range) o).hi == hi;
        }

        @Override
        public int hashCode() {
            return lo * 31 + hi;
        }
    }

    /** Its @IoCreator lower-cases; the constructor taking the same String stores it as given. */
    public static class Lower {
        private final String text;

        private Lower(String text) {
            this.text = text;
        }

        @IoCreator
        public static Lower of(@IoProperty("text") String text) {
            return new Lower(text.toLowerCase());
        }

        @IoValue
        public String text() {
            return text;
        }
    }

    /** One constructor takes the boxed value, declared first; the other takes exactly what the method returns. */
    public static class Exact {
        private final int n;
        private final String via;

        public Exact(Integer n) {
            this.n = n;
            this.via = "Integer";
        }

        public Exact(int n) {
            this.n = n;
            this.via = "int";
        }

        @IoValue
        public int n() {
            return n;
        }
    }

    /** No constructor takes the value alone: one takes it and more. */
    public static class Opaque {
        private String v;

        public Opaque() {
        }

        public Opaque(String v, String more) {
            this.v = v + more;
        }

        @IoValue
        public String v() {
            return v;
        }
    }

    public static class Holder {
        public Tag tag;
        public Code code;
        public Score score;
        public Label label;
        public Range range;
        public Object any;
        public List<Tag> tags;
        public Map<String, Code> codes;
        public Map<Tag, String> byTag;
        public Tag[] tagArray;
        public Optional<Tag> optional;
    }

    public static class OpaqueHolder {
        public Opaque opaque;
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.tag = new Tag("t");
        h.code = Code.of(42);
        h.score = new Score(7);
        h.label = new Label("l");
        h.range = new Range(1, 5);
        h.any = new Tag("in Object");
        h.tags = new ArrayList<>(Arrays.asList(new Tag("a"), new Tag("b")));
        h.codes = new LinkedHashMap<>();
        h.codes.put("one", Code.of(1));
        h.byTag = new LinkedHashMap<>();
        h.byTag.put(new Tag("key"), "v");
        h.tagArray = new Tag[] {new Tag("x")};
        h.optional = Optional.of(new Tag("o"));
        return h;
    }

    private static void assertSameHolder(Holder expected, Holder actual, boolean typed, String text) {
        assertEquals(expected.tag, actual.tag, text);
        assertEquals(expected.code, actual.code, text);
        assertEquals(expected.score, actual.score, text);
        assertEquals(expected.label, actual.label, text);
        assertEquals(expected.range, actual.range, text);
        if (typed) {
            assertEquals(expected.any, actual.any, text);
        }
        assertEquals(expected.tags, actual.tags, text);
        for (Object t : actual.tags) {
            assertInstanceOf(Tag.class, t, text);
        }
        assertEquals(expected.codes, actual.codes, text);
        for (Object c : ((Map<?, ?>) actual.codes).values()) {
            assertInstanceOf(Code.class, c, text);
        }
        assertEquals(expected.byTag, actual.byTag, text);
        for (Object k : ((Map<?, ?>) actual.byTag).keySet()) {
            assertInstanceOf(Tag.class, k, text);
        }
        assertArrayEquals(expected.tagArray, actual.tagArray, text);
        assertEquals(expected.optional, actual.optional, text);
    }

    @Test
    void jsonReadsTheValueBackInEveryPlace() {
        WriteOptions[] all = {new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().showTypeInfoNever().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (WriteOptions options : all) {
            Holder h = holder();
            String json = JsonIo.toJson(h, options);
            Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
            assertSameHolder(h, back, options != all[1], json);
        }
    }

    @Test
    void toonReadsTheValueBackInEveryPlace() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (WriteOptions options : all) {
            Holder h = holder();
            String toon = JsonIo.toToon(h, options);
            Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
            assertSameHolder(h, back, options != null, toon);
        }
    }

    @Test
    void theClassAskedForAtTheRootIsBuiltFromTheValue() {
        assertEquals(new Tag("r"), JsonIo.toJava("\"r\"", null).asClass(Tag.class));
        assertEquals(Code.of(7), JsonIo.toJava("7", null).asClass(Code.class));
        assertEquals(new Range(2, 3), JsonIo.toJava("\"2~3\"", null).asClass(Range.class));
        assertEquals(new Tag("r"), JsonIo.fromToon("r", null).asClass(Tag.class));
        assertEquals(new Tag("r"), JsonIo.toJava(JsonIo.toJson(new Tag("r"), null), null).asClass(Tag.class));
        assertEquals(new Tag("r"), JsonIo.toJava(JsonIo.toJson(new Tag("r"), null), null).asClass(null));
    }

    /** What TOON wrote through 4.117.0, the fields; and the two-parameter creator's fields. */
    @Test
    void theFieldsStillReadBack() {
        Holder back = JsonIo.fromToon("tag:\n  name: t\ncode:\n  code: 42\nrange:\n  lo: 1\n  hi: 5\n", null)
                .asClass(Holder.class);
        assertEquals(new Tag("t"), back.tag);
        assertEquals(Code.of(42), back.code);
        assertEquals(new Range(1, 5), back.range);
        back = JsonIo.toJava("{\"range\":{\"lo\":1,\"hi\":5}}", null).asClass(Holder.class);
        assertEquals(new Range(1, 5), back.range);
    }

    /** An empty string is the value only for a constructor that takes text; elsewhere it is null, as before. */
    @Test
    void anEmptyStringIsTheValueOnlyWhenTheConstructorTakesText() {
        Holder back = JsonIo.toJava("{\"tag\":\"\",\"code\":\"\"}", null).asClass(Holder.class);
        assertEquals(new Tag(""), back.tag);
        assertNull(back.code);
    }

    @Test
    void anIoCreatorWinsOverTheConstructor() {
        assertEquals("abc", JsonIo.toJava("\"ABC\"", null).asClass(Lower.class).text);
        assertEquals("abc", JsonIo.toJava("{\"@type\":\"" + Lower.class.getName() + "\",\"value\":\"ABC\"}", null)
                .asClass(Lower.class).text);
    }

    @Test
    void theConstructorTakingExactlyTheValueTypeWins() {
        Exact back = JsonIo.toJava("5", null).asClass(Exact.class);
        assertEquals(5, back.n);
        assertEquals("int", back.via);
    }

    @Test
    void aClassThatNothingBuildsFromItsValueSaysWhy() {
        OpaqueHolder h = new OpaqueHolder();
        h.opaque = new Opaque();
        h.opaque.v = "secret";
        String json = JsonIo.toJson(h, null);
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toJava(json, null).asClass(OpaqueHolder.class));
        assertTrue(e.getMessage().contains("@IoValue method v()"), e.getMessage());
        assertTrue(e.getMessage().contains("or a constructor that takes a String: it has neither"), e.getMessage());
    }
}
