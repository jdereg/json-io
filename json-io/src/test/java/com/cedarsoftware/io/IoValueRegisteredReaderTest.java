package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.cedarsoftware.io.annotation.IoClassFactory;
import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoCustomReader;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A class written as the single value its {@code @IoValue} method returns, with a class factory or custom reader
 * registered for it, is read by that factory or reader in that form too: given an object holding the value as its
 * "value", as it is given {"@type":..,"value":..}. The bare value is the form json-io writes such a class in where it
 * is declared, and it went to the class's creator, the factory or reader passed over (4.117.0 too).
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
class IoValueRegisteredReaderTest {
    public static class Email {
        private final String v;

        @IoCreator
        public Email(String v) {
            this.v = v;
        }

        @IoValue
        public String v() {
            return v;
        }
    }

    public static class Bag {
        public Email e = new Email("A@X");
        public List<Email> list = new ArrayList<>(Collections.singletonList(new Email("B@X")));
        public Email[] arr = {new Email("C@X")};
        public Optional<Email> opt = Optional.of(new Email("D@X"));
    }

    /** Lower-cases the value, marking it read by the factory. */
    public static class Lower implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            return new Email("factory:" + ((String) jObj.get("value")).toLowerCase());
        }

        @Override
        public boolean isObjectFinal() {
            return true;
        }
    }

    public static class Reader implements JsonClassReader<Email> {
        @Override
        public Email read(Object jsonObj, Resolver resolver) {
            return new Email("reader:" + ((String) ((JsonObject) jsonObj).get("value")).toLowerCase());
        }
    }

    public static class CodeFactory implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            return new Code("factory:" + ((String) jObj.get("value")).toLowerCase());
        }

        @Override
        public boolean isObjectFinal() {
            return true;
        }
    }

    @IoClassFactory(CodeFactory.class)
    public static class Code {
        private final String v;

        @IoCreator
        public Code(String v) {
            this.v = v;
        }

        @IoValue
        public String v() {
            return v;
        }
    }

    @IoCustomReader(CodeReader.class)
    public static class Tag {
        private final String v;

        @IoCreator
        public Tag(String v) {
            this.v = v;
        }

        @IoValue
        public String v() {
            return v;
        }
    }

    public static class CodeReader implements JsonClassReader<Tag> {
        @Override
        public Tag read(Object jsonObj, Resolver resolver) {
            return new Tag("reader:" + ((JsonObject) jsonObj).get("value"));
        }
    }

    /** An exception written by its value (json-io has its own factory for exceptions). */
    public static class Failure extends RuntimeException {
        private final int code;

        @IoCreator
        public Failure(int code) {
            super("failed: " + code);
            this.code = code;
        }

        @IoValue
        public int code() {
            return code;
        }
    }

    public static class Failed {
        public Failure failure = new Failure(42);
    }

    public static class Annotated {
        public Code code = new Code("X");
        public Tag tag = new Tag("Y");
    }

    /** Values of several classes in turn, in one read. */
    public static class Mixed {
        public Email e1 = new Email("A@X");
        public Code c1 = new Code("P");
        public Email e2 = new Email("B@X");
        public Tag t1 = new Tag("Q");
        public Code c2 = new Code("R");
        public Email e3 = new Email("C@X");
        public Tag t2 = new Tag("S");
    }

    private static void assertReadBy(String by, Bag bag) {
        assertEquals(by + "a@x", bag.e.v);
        assertEquals(by + "b@x", bag.list.get(0).v);
        assertEquals(by + "c@x", bag.arr[0].v);
        assertEquals(by + "d@x", bag.opt.get().v);
    }

    @Test
    void aRegisteredFactory() {
        ReadOptions options = new ReadOptionsBuilder().addClassFactory(Email.class, new Lower()).build();
        assertReadBy("factory:", JsonIo.toJava(JsonIo.toJson(new Bag(), null), options).asClass(Bag.class));
        assertReadBy("factory:", JsonIo.fromToon(JsonIo.toToon(new Bag(), null), options).asClass(Bag.class));
        assertEquals("factory:e@x", JsonIo.toJava("\"E@X\"", options).asClass(Email.class).v);
    }

    @Test
    void aRegisteredReader() {
        ReadOptions options = new ReadOptionsBuilder().addCustomReaderClass(Email.class, new Reader()).build();
        assertReadBy("reader:", JsonIo.toJava(JsonIo.toJson(new Bag(), null), options).asClass(Bag.class));
        assertReadBy("reader:", JsonIo.fromToon(JsonIo.toToon(new Bag(), null), options).asClass(Bag.class));
        assertEquals("reader:e@x", JsonIo.toJava("\"E@X\"", options).asClass(Email.class).v);

        // Turned off, or declining: the creator, as before
        ReadOptions off = new ReadOptionsBuilder().addCustomReaderClass(Email.class, new Reader())
                .addNotCustomReaderClass(Email.class).build();
        assertReadBy("", lowerCased(JsonIo.toJava(JsonIo.toJson(new Bag(), null), off).asClass(Bag.class)));
        JsonClassReader<Email> declines = new JsonClassReader<Email>() {
            @Override
            public Email read(Object jsonObj, Resolver resolver) {
                return null;
            }
        };
        ReadOptions declining = new ReadOptionsBuilder().addCustomReaderClass(Email.class, declines).build();
        assertReadBy("", lowerCased(JsonIo.toJava(JsonIo.toJson(new Bag(), null), declining).asClass(Bag.class)));
    }

    /** Both registered: the factory, as everywhere. */
    @Test
    void aFactoryAndAReader() {
        ReadOptions options = new ReadOptionsBuilder().addClassFactory(Email.class, new Lower())
                .addCustomReaderClass(Email.class, new Reader()).build();
        assertReadBy("factory:", JsonIo.toJava(JsonIo.toJson(new Bag(), null), options).asClass(Bag.class));
    }

    @Test
    void namedByAnnotation() {
        Annotated back = JsonIo.toJava(JsonIo.toJson(new Annotated(), null), null).asClass(Annotated.class);
        assertEquals("factory:x", back.code.v);
        assertEquals("reader:Y", back.tag.v);
    }

    /** Each value is read by what is registered for its own class, the classes coming in turn. */
    @Test
    void severalClassesInOneRead() {
        ReadOptions options = new ReadOptionsBuilder().addClassFactory(Email.class, new Lower()).build();
        Mixed back = JsonIo.toJava(JsonIo.toJson(new Mixed(), null), options).asClass(Mixed.class);
        assertEquals("factory:a@x", back.e1.v);
        assertEquals("factory:p", back.c1.v);
        assertEquals("factory:b@x", back.e2.v);
        assertEquals("reader:Q", back.t1.v);
        assertEquals("factory:r", back.c2.v);
        assertEquals("factory:c@x", back.e3.v);
        assertEquals("reader:S", back.t2.v);
    }

    /** Read back as before, by json-io's own exception factory now: given the value, it builds the same. */
    @Test
    void anExceptionWrittenByItsValue() {
        String json = JsonIo.toJson(new Failed(), null);
        assertEquals("{\"@type\":\"" + Failed.class.getName() + "\",\"failure\":42}", json);
        Failed back = JsonIo.toJava(json, null).asClass(Failed.class);
        assertEquals(42, back.failure.code);
        assertEquals("failed: 42", back.failure.getMessage());
    }

    private static Bag lowerCased(Bag bag) {
        bag.e = new Email(bag.e.v.toLowerCase());
        bag.list.set(0, new Email(bag.list.get(0).v.toLowerCase()));
        bag.arr[0] = new Email(bag.arr[0].v.toLowerCase());
        bag.opt = Optional.of(new Email(bag.opt.get().v.toLowerCase()));
        return bag;
    }
}
