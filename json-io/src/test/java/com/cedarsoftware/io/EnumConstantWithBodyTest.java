package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An enum constant with a body ({@code A { .. }}) is written as its enum's constant, as a constant without one is: its
 * {@code @type} the enum's name, never an {@code @id} or {@code @ref}. It was written as its own class
 * ({@code Grade$1} -- numbered by the constants with bodies before it, so renumbered as the enum changes, and named by
 * no reader of another version), with an {@code @id} the first time and a {@code @ref} after, a field declared as the
 * enum holding a {@code @ref}: {@code isEnum()} is false for its class.
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
class EnumConstantWithBodyTest {
    public enum Grade {
        A("a") {
            @Override
            public String label() {
                return "top";
            }
        },
        B("b");

        public final String code;

        Grade(String code) {
            this.code = code;
        }

        public String label() {
            return code;
        }
    }

    /** Written as the value its @IoValue method returns. */
    public enum Coded {
        X {
            @Override
            String extra() {
                return "x";
            }
        },
        Y;

        String extra() {
            return "";
        }

        @com.cedarsoftware.io.annotation.IoValue
        public String code() {
            return name().toLowerCase();
        }
    }

    public static class Holder {
        public Object any;
        public Grade grade;
        public Grade[] grades;
        public Object[] objects;
        public Map<String, Object> map;
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.any = Grade.A;
        h.grade = Grade.A;
        h.grades = new Grade[] {Grade.A, Grade.B};
        h.objects = new Object[] {Grade.A, Grade.A};
        h.map = new LinkedHashMap<>();
        h.map.put("m", Grade.A);
        return h;
    }

    private static void assertReadsBack(Holder back) {
        assertSame(Grade.A, back.any);
        assertSame(Grade.A, back.grade);
        assertSame(Grade.A, back.grades[0]);
        assertSame(Grade.A, back.objects[1]);
        assertSame(Grade.A, back.map.get("m"));
    }

    @Test
    void writtenAsItsEnumInEveryPlace() {
        String grade = Grade.class.getName();
        String typed = "{\"@type\":\"" + grade + "\",\"name\":\"A\"}";
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            String json = JsonIo.toJson(holder(), options);
            assertFalse(json.contains("$1"), json);
            assertFalse(json.contains("@id") || json.contains("@ref"), json);
            assertTrue(json.contains("\"any\":" + typed), json);
            assertTrue(json.contains("\"objects\":[" + typed + "," + typed + "]"), json);
            assertReadsBack(JsonIo.toJava(json, null).asClass(Holder.class));
        }
        String json = JsonIo.toJson(holder(), null);
        assertTrue(json.contains("\"grade\":\"A\",\"grades\":[\"A\",\"B\"]"), json);      // the declared enum's name
        assertEquals(typed, JsonIo.toJson(Grade.A, new WriteOptionsBuilder().showTypeInfoAlways().build()));
        List<Grade> list = Arrays.asList(Grade.A, Grade.A, Grade.B);
        assertEquals("[\"A\",\"A\",\"B\"]", JsonIo.toJson(list, new WriteOptionsBuilder().showTypeInfoNever().build()));
    }

    /** Written as a JSON object of its fields, it is named by its enum too, and declared as it, not referenced. */
    @Test
    void writtenAsAnObjectOfItsFields() {
        WriteOptions asObject = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
        String json = JsonIo.toJson(holder(), asObject);
        assertFalse(json.contains("$1") || json.contains("@id") || json.contains("@ref"), json);
        assertTrue(json.contains("\"any\":{\"@type\":\"" + Grade.class.getName() + "\",\"code\":\"a\",\"name\":\"A\"}"), json);
        assertReadsBack(JsonIo.toJava(json, null).asClass(Holder.class));
    }

    /** Written as its @IoValue value where a type is shown, it is named by its enum. */
    @Test
    void writtenAsItsValue() {
        Object[] objects = {Coded.X};
        String json = JsonIo.toJson(objects, null);
        assertEquals("[{\"@type\":\"" + Coded.class.getName() + "\",\"value\":\"x\"}]", json);
    }

    /** What it was written as reads as before: its own class, its @id and @refs. */
    @Test
    void theOldTextReads() {
        String old = "{\"@type\":\"" + Holder.class.getName() + "\",\"any\":{\"@id\":1,\"@type\":\"" + Grade.class.getName()
                + "$1\",\"name\":\"A\"},\"grade\":{\"@ref\":1},\"grades\":[{\"@ref\":1},\"B\"],\"objects\":[{\"@ref\":1},"
                + "{\"@ref\":1}],\"map\":{\"m\":{\"@ref\":1}}}";
        assertReadsBack(JsonIo.toJava(old, null).asClass(Holder.class));
    }
}
