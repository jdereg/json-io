package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cedarsoftware.io.annotation.IoCustomReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * An object in a field is read by its class's custom reader, as at the root and in a collection. The reader ran on it,
 * but its result went to the object, not to the field, which kept the instance made for it, empty: having been read
 * by the reader, the object's fields were left unread (4.117.0 too). A class factory was used there all along.
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
class CustomReaderInFieldTest {
    public static class Pt {
        public int x;

        Pt() {
        }

        Pt(int x) {
            this.x = x;
        }
    }

    public static class Bag {
        public Pt p;
        public Object any;
        public Pt[] arr;
        public List<Pt> list;
        public Pt q;
    }

    /** Reads an object as 1000 more than written, so a read by it shows. */
    public static class PlusThousand implements JsonClassReader<Pt> {
        @Override
        public Pt read(Object jsonObj, Resolver resolver) {
            return new Pt(1000 + ((Number) ((JsonObject) jsonObj).get("x")).intValue());
        }
    }

    @IoCustomReader(TagReader.class)
    public static class Tag {
        public String name;
    }

    public static class TagReader implements JsonClassReader<Tag> {
        @Override
        public Tag read(Object jsonObj, Resolver resolver) {
            Tag tag = new Tag();
            tag.name = "read:" + ((JsonObject) jsonObj).get("name");
            return tag;
        }
    }

    public static class Tagged {
        public Tag tag;
    }

    private static ReadOptions withReader(JsonClassReader<Pt> reader) {
        return new ReadOptionsBuilder().addCustomReaderClass(Pt.class, reader).build();
    }

    private static Bag bag() {
        Bag bag = new Bag();
        bag.p = new Pt(1);
        bag.any = new Pt(2);
        bag.arr = new Pt[] {new Pt(3)};
        bag.list = new ArrayList<>(Collections.singletonList(new Pt(4)));
        return bag;
    }

    @Test
    void inAFieldAsAtTheRootAndInACollection() {
        ReadOptions options = withReader(new PlusThousand());
        Bag back = JsonIo.toJava(JsonIo.toJson(bag(), null), options).asClass(Bag.class);
        assertEquals(1001, back.p.x);
        assertEquals(1002, ((Pt) back.any).x);
        assertEquals(1003, back.arr[0].x);
        assertEquals(1004, back.list.get(0).x);
        assertEquals(1005, JsonIo.toJava("{\"x\":5}", options).asClass(Pt.class).x);

        back = JsonIo.fromToon(JsonIo.toToon(bag(), null), options).asClass(Bag.class);
        assertEquals(1001, back.p.x);
        assertEquals(1004, back.list.get(0).x);
    }

    /** Two fields holding one object hold the reader's one result, whichever field defines it. */
    @Test
    void oneObjectInTwoFields() {
        ReadOptions options = withReader(new PlusThousand());
        String type = Bag.class.getName();
        Bag back = JsonIo.toJava("{\"@type\":\"" + type + "\",\"p\":{\"@id\":1,\"x\":7},\"q\":{\"@ref\":1}}", options)
                .asClass(Bag.class);
        assertEquals(1007, back.p.x);
        assertSame(back.p, back.q);
        back = JsonIo.toJava("{\"@type\":\"" + type + "\",\"p\":{\"@ref\":1},\"q\":{\"@id\":1,\"x\":8}}", options)
                .asClass(Bag.class);
        assertEquals(1008, back.q.x);
        assertSame(back.p, back.q);
    }

    @Test
    void aReaderThatDeclinesOrIsTurnedOff() {
        // A reader returning null leaves the object to be read as any other
        JsonClassReader<Pt> declines = new JsonClassReader<Pt>() {
            @Override
            public Pt read(Object jsonObj, Resolver resolver) {
                return null;
            }
        };
        Bag back = JsonIo.toJava(JsonIo.toJson(bag(), null), withReader(declines)).asClass(Bag.class);
        assertEquals(1, back.p.x);
        assertEquals(2, ((Pt) back.any).x);

        ReadOptions off = new ReadOptionsBuilder().addCustomReaderClass(Pt.class, new PlusThousand())
                .addNotCustomReaderClass(Pt.class).build();
        back = JsonIo.toJava(JsonIo.toJson(bag(), null), off).asClass(Bag.class);
        assertEquals(1, back.p.x);
        assertEquals(4, back.list.get(0).x);
    }

    /** A class factory that finishes the object reads it, as at the root: the reader is not asked. */
    @Test
    void aFactoryAndAReader() {
        ClassFactory factory = new ClassFactory() {
            @Override
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
                return new Pt(2000 + ((Number) jObj.get("x")).intValue());
            }

            @Override
            public boolean isObjectFinal() {
                return true;
            }
        };
        ReadOptions options = new ReadOptionsBuilder().addCustomReaderClass(Pt.class, new PlusThousand())
                .addClassFactory(Pt.class, factory).build();
        Bag back = JsonIo.toJava(JsonIo.toJson(bag(), null), options).asClass(Bag.class);
        assertEquals(2001, back.p.x);
        assertEquals(2002, ((Pt) back.any).x);
        assertEquals(2005, JsonIo.toJava("{\"x\":5}", options).asClass(Pt.class).x);
    }

    @Test
    void aReaderNamedByAnnotation() {
        Tagged tagged = new Tagged();
        tagged.tag = new Tag();
        tagged.tag.name = "t";
        Tagged back = JsonIo.toJava(JsonIo.toJson(tagged, null), null).asClass(Tagged.class);
        assertEquals("read:t", back.tag.name);
    }
}
