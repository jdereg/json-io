package com.cedarsoftware.io;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A conversion the caller registers from a Map to a class ({@code ReadOptionsBuilder.addConverterOverride(Map.class,
 * X.class, ..)}) builds a JSON object read as that class -- at the root, in a field, as an element -- before its
 * ClassFactory and its constructors, from the JSON's own map, as the Converter builds a value type. It was never
 * consulted for a JSON object: the class was built by its constructors ("Unable to instantiate"), the conversion
 * ignored.
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
class RegisteredConversionTest {
    /** Its only constructor refuses the JSON's holder: only the conversion builds it from {"holder":..}. */
    public static class Policy {
        final String number;
        public String note;

        public Policy(String number) {
            if (number == null || !number.startsWith("P-")) {
                throw new IllegalArgumentException("policy number must start with P-");
            }
            this.number = number;
        }
    }

    public static class Holder {
        public Policy policy;
        public Object any;
    }

    private static ReadOptions withConversion() {
        return new ReadOptionsBuilder().addConverterOverride(Map.class, Policy.class,
                (from, converter) -> new Policy("P-" + ((Map<?, ?>) from).get("holder"))).build();
    }

    @Test
    void atTheRoot() {
        Policy policy = JsonIo.toJava("{\"holder\":\"Ann\"}", withConversion()).asClass(Policy.class);
        assertEquals("P-Ann", policy.number);
        // registered from a JsonObject, the map json-io reads
        ReadOptions fromJsonObject = new ReadOptionsBuilder().addConverterOverride(JsonObject.class, Policy.class,
                (from, converter) -> new Policy("P-" + ((Map<?, ?>) from).get("holder"))).build();
        assertEquals("P-Cy", JsonIo.toJava("{\"holder\":\"Cy\"}", fromJsonObject).asClass(Policy.class).number);
    }

    @Test
    void inAFieldAndAsAnElement() {
        String json = "{\"policy\":{\"holder\":\"Ann\"},\"any\":{\"@type\":\"" + Policy.class.getName() + "\",\"holder\":\"Bo\"}}";
        Holder holder = JsonIo.toJava(json, withConversion()).asClass(Holder.class);
        assertEquals("P-Ann", holder.policy.number);
        assertEquals("P-Bo", ((Policy) holder.any).number);
        List<Policy> policies = JsonIo.toJava("[{\"holder\":\"A\"},{\"holder\":\"B\"}]", withConversion())
                .asType(new TypeHolder<List<Policy>>() {});
        assertEquals("P-B", policies.get(1).number);
    }

    /** It is given the JSON's own map: a number as JSON gives it, a nested object a map. */
    @Test
    void itIsGivenTheJsonsMap() {
        Object[] given = new Object[1];
        ReadOptions options = new ReadOptionsBuilder().addConverterOverride(Map.class, Policy.class, (from, converter) -> {
            given[0] = from;
            return new Policy("P-1");
        }).build();
        JsonIo.toJava("{\"holder\":\"Ann\",\"age\":3,\"address\":{\"city\":\"x\"},"
                + "\"when\":{\"@type\":\"Date\",\"date\":1700000000000}}", options).asClass(Policy.class);
        Map<?, ?> map = (Map<?, ?>) given[0];
        assertEquals(3L, map.get("age"));
        assertInstanceOf(Map.class, map.get("address"));
        assertEquals(new java.util.Date(1700000000000L), map.get("when"));     // a typed value is its value
    }

    /** What it builds is the object: the JSON's keys do not then overwrite its fields. */
    @Test
    void itsObjectIsFinal() {
        ReadOptions options = new ReadOptionsBuilder().addConverterOverride(Map.class, Policy.class, (from, converter) -> {
            Policy policy = new Policy("P-1");
            policy.note = "converted";
            return policy;
        }).build();
        assertEquals("converted", JsonIo.toJava("{\"note\":\"json\"}", options).asClass(Policy.class).note);
    }

    @Test
    void beforeItsClassFactory() {
        AtomicInteger factoryCalls = new AtomicInteger();
        ReadOptions options = new ReadOptionsBuilder(withConversion()).addClassFactory(Policy.class, new ClassFactory() {
            @Override
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
                factoryCalls.incrementAndGet();
                return new Policy("P-factory");
            }

            @Override
            public boolean isObjectFinal() {
                return true;
            }
        }).build();
        assertEquals("P-Ann", JsonIo.toJava("{\"holder\":\"Ann\"}", options).asClass(Policy.class).number);
        assertEquals(0, factoryCalls.get());
    }

    /** One that throws is an error naming where, its exception the cause. */
    @Test
    void aConversionThatThrows() {
        ReadOptions options = new ReadOptionsBuilder().addConverterOverride(Map.class, Policy.class,
                (from, converter) -> new Policy(String.valueOf(((Map<?, ?>) from).get("holder")))).build();
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"policy\":{\"holder\":\"Ann\"}}", options).asClass(Holder.class));
        assertTrue(e.getMessage().contains("policy"), e.getMessage());
        assertTrue(e.getMessage().contains("must start with P-"), e.getMessage());
        assertInstanceOf(IllegalArgumentException.class, rootCause(e));
    }

    /** Unchanged: without one, its constructors build it; a value type's override is used as before. */
    @Test
    void whatDidNotChange() {
        assertEquals("P-9", JsonIo.toJava("{\"number\":\"P-9\"}", null).asClass(Policy.class).number);
        Timestamp mark = new Timestamp(86400000L);
        ReadOptions timestamps = new ReadOptionsBuilder().addConverterOverride(Map.class, Timestamp.class,
                (from, converter) -> mark).build();
        assertSame(mark, JsonIo.toJava("{\"time\":5,\"nanos\":0}", timestamps).asClass(Timestamp.class));
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
