package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A list element or array element that TOON writes as a nested list ({@code - [2]: u,v}) is read as the list or array
 * type declared for it: a {@code List<Tags>}'s element as a {@code Tags} (a list subclass), a {@code Tags[]}'s as a
 * {@code Tags}, a {@code List<LinkedList<String>>}'s as a {@code LinkedList}, a {@code List<Set<String>>}'s as a set.
 * TOON reads a nested list as a java.util.List, where JSON's parser gives an Object[] that is built as declared; the
 * list stayed an {@code ArrayList} (and an array threw ArrayStoreException), in 4.117.0 too.
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
class ToonNestedListElementTest {
    public static class Tags extends ArrayList<String> {
    }

    public static class Holder {
        public List<Tags> tags;
        public Tags[] tagArray;
        public List<LinkedList<String>> linked;
        public List<Set<String>> sets;
        public List<List<Tags>> nested;
        public List<List<String>> plainLists;
        public List<Object> objects;
    }

    private static Tags tags(String... s) {
        Tags t = new Tags();
        t.addAll(Arrays.asList(s));
        return t;
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.tags = new ArrayList<>(Arrays.asList(tags("u", "v"), tags("w")));
        h.tagArray = new Tags[] {tags("a", "b")};
        h.linked = new ArrayList<>(Collections.singletonList(new LinkedList<>(Arrays.asList("x", "y"))));
        h.sets = new ArrayList<>(Collections.singletonList(new LinkedHashSet<>(Arrays.asList("p", "q"))));
        h.nested = new ArrayList<>(Collections.singletonList(new ArrayList<>(Collections.singletonList(tags("n")))));
        h.plainLists = new ArrayList<>(Collections.singletonList(new ArrayList<>(Arrays.asList("s", "t"))));
        h.objects = new ArrayList<>(Collections.singletonList(new ArrayList<>(Arrays.asList("o"))));
        return h;
    }

    private static void assertRead(Holder h, Holder back, String text) {
        assertEquals(Tags.class, back.tags.get(0).getClass(), text);
        assertEquals(Tags.class, back.tags.get(1).getClass(), text);
        assertEquals(Tags.class, back.tagArray[0].getClass(), text);
        assertEquals(LinkedList.class, back.linked.get(0).getClass(), text);
        assertTrue(back.sets.get(0) instanceof Set, text);
        assertEquals(Tags.class, back.nested.get(0).get(0).getClass(), text);
        assertEquals(ArrayList.class, back.plainLists.get(0).getClass(), text);
        if (h.objects != null) {
            assertEquals(ArrayList.class, back.objects.get(0).getClass(), text);
        }
        Map<String, Object> options = new HashMap<>();
        assertTrue(DeepEquals.deepEquals(h, back, options), () -> text + "\n" + options.get("diff"));
    }

    @Test
    void asTheTypeDeclared() {
        Holder h = holder();
        for (WriteOptions options : new WriteOptions[] {null,
                new WriteOptionsBuilder().showTypeInfoMinimal().build()}) {
            String toon = JsonIo.toToon(h, options);
            assertRead(h, JsonIo.fromToon(toon, null).asClass(Holder.class), toon);
        }
    }

    public static class Point {
        public int x;
    }

    public static class Points {
        public List<Point> points;
    }

    /** A list where an object is declared is not a list type: it is left as it was read, as before. */
    @Test
    void notAListTypeIsLeftAsItWas() {
        Points p = JsonIo.fromToon("points[1]:\n  - [1]: 5\n", null).asClass(Points.class);
        assertEquals(1, p.points.size());
        assertEquals(ArrayList.class, ((Object) p.points.get(0)).getClass());
    }

    /** Hand-written, and as JSON reads the same structure. */
    @Test
    void handWrittenAndAsJson() {
        String toon = "tags[2]:\n  - [2]: u,v\n  - [1]: w\ntagArray[1]:\n  - [2]: a,b\nlinked[1]:\n  - [2]: x,y\n"
                + "sets[1]:\n  - [2]: p,q\nnested[1]:\n  - [1]:\n    - [1]: n\nplainLists[1]:\n  - [2]: s,t\n"
                + "objects[1]:\n  - [1]: o\n";
        Holder h = holder();
        assertRead(h, JsonIo.fromToon(toon, null).asClass(Holder.class), toon);
        h.objects = null;       // JSON reads a list in a List<Object> as an Object[]; TOON, as a list
        String json = JsonIo.toJson(h, new WriteOptionsBuilder().showTypeInfoNever().build());
        assertRead(h, JsonIo.toJava(json, null).asClass(Holder.class), json);
    }
}
