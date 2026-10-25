package com.cedarsoftware.io;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-step read -- Maps mode, then {@code JsonIo.toJava(tree, ..).asClass(X)} -- gives what Java mode reads from
 * the tree's text: the tree as read, or as changed, or a part of it. It was broken in every release: a root with no
 * {@code @type} failed ("Return type mismatch .. found JsonObject"), and a map or list in the tree held Maps mode's own
 * copy of its items, so a map field came back empty and an array held a null. A JsonObject built by hand (never read
 * in Maps mode) is read directly, as before.
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
class MapsModeTwoStepTest {
    public static class Member {
        public String name;
        public int age;
        public LocalDate born;
    }

    public static class Team {
        public String name = "t";
        public int size = 2;
        public LocalDate formed = LocalDate.of(2020, 5, 1);
        public List<Member> members = new ArrayList<>();
        public Map<String, Member> byName = new LinkedHashMap<>();
        public Member[] bench;
        public Member lead;
        public Object any;

        static Team sample() {
            Team team = new Team();
            Member a = member("a", 30);
            Member b = member("b", 25);
            team.members.add(a);
            team.members.add(b);
            team.byName.put("a", a);
            team.bench = new Member[] {b};
            team.lead = member("lead", 40);
            team.any = a;
            return team;
        }
    }

    private static Member member(String name, int age) {
        Member member = new Member();
        member.name = name;
        member.age = age;
        member.born = LocalDate.of(2000, 1, 1).plusYears(age % 7);
        return member;
    }

    private static final WriteOptions[] FORMS = {null, new WriteOptionsBuilder().showTypeInfoAlways().build(),
            new WriteOptionsBuilder().showTypeInfoNever().build()};

    private static JsonObject tree(String json) {
        return JsonIo.toMaps(json, null).asClass(JsonObject.class);
    }

    @Test
    void theTreeReadsAsJavaModeReadsItsText() {
        for (WriteOptions form : FORMS) {
            String json = JsonIo.toJson(Team.sample(), form);
            Team direct = JsonIo.toJava(json, null).asClass(Team.class);
            Team twoStep = JsonIo.toJava(tree(json), null).asClass(Team.class);
            assertTrue(DeepEquals.deepEquals(direct, twoStep), json);
            if (form != FORMS[2]) {     // without type info, no @id names what is shared
                assertSame(twoStep.members.get(0), twoStep.byName.get("a"), json);
                assertSame(twoStep.members.get(0), twoStep.any, json);
            }
        }
    }

    @Test
    void aChangedTree() {
        JsonObject tree = tree(JsonIo.toJson(Team.sample(), null));
        tree.put("size", 3L);
        ((Map<String, Object>) ((Object[]) tree.get("members"))[1]).put("name", "bee");
        Team team = JsonIo.toJava(tree, null).asClass(Team.class);
        assertEquals(3, team.size);
        assertEquals("bee", team.members.get(1).name);
        assertSame(team.members.get(1), team.bench[0]);
    }

    @Test
    void aPartOfTheTree() {
        JsonObject tree = tree(JsonIo.toJson(Team.sample(), null));
        Member lead = JsonIo.toJava((JsonObject) tree.get("lead"), null).asClass(Member.class);
        assertEquals("lead", lead.name);
        assertEquals(40, lead.age);
        // a typed list and a typed array, as json-io writes them under showTypeInfoAlways()
        JsonObject typed = tree(JsonIo.toJson(Team.sample(), FORMS[1]));
        List<Member> members = JsonIo.toJava((JsonObject) typed.get("members"), null)
                .asType(new TypeHolder<List<Member>>() {});
        assertEquals("b", members.get(1).name);
        Member[] bench = JsonIo.toJava((JsonObject) typed.get("bench"), null).asClass(Member[].class);
        assertEquals("b", bench[0].name);
        Map<Long, Member> byAge = new LinkedHashMap<>();
        byAge.put(30L, member("a", 30));
        JsonObject keyed = tree(JsonIo.toJson(byAge, null));
        Map<Long, Member> back = JsonIo.toJava(keyed, null).asType(new TypeHolder<Map<Long, Member>>() {});
        assertEquals("a", back.get(30L).name);
        // a map with keys that are not text, of typed lists
        Map<Long, List<Member>> byYear = new LinkedHashMap<>();
        byYear.put(2020L, new ArrayList<>(java.util.Arrays.asList(member("c", 9))));
        JsonObject years = tree(JsonIo.toJson(byYear, FORMS[1]));
        Map<Long, List<Member>> yearsBack = JsonIo.toJava(years, null).asType(new TypeHolder<Map<Long, List<Member>>>() {});
        assertEquals("c", yearsBack.get(2020L).get(0).name);
    }

    /** asClass(JsonObject.class) is the tree's root: it was a new, empty one where the root named an unknown class. */
    @Test
    void jsonObjectIsTheTreesRoot() {
        JsonObject root = tree("{\"@id\":1,\"@type\":\"com.example.NoSuchClass\",\"name\":\"Joe\",\"self\":{\"@ref\":1}}");
        assertEquals("Joe", root.get("name"));
        assertSame(root, root.get("self"));
        // a typed collection at the root is a JsonObject too (it was a mismatch), and reads in two steps
        JsonObject list = tree(JsonIo.toJson(new ArrayList<>(java.util.Arrays.asList(member("d", 2))), null));
        List<Member> members = JsonIo.toJava(list, null).asType(new TypeHolder<List<Member>>() {});
        assertEquals("d", members.get(0).name);
    }

    /**
     * A key named as a field of JsonObject is a key like any other: the root's own target, a JsonObject, typed it as
     * that field ("size" an Integer, "typeString" 5 the text "5").
     */
    @Test
    void keysNamedLikeAJsonObjectsFields() {
        for (String json : new String[] {"{\"size\":2,\"hash\":1,\"typeString\":5}",
                "{\"@type\":\"com.cedarsoftware.io.JsonObject\",\"size\":2,\"hash\":1,\"typeString\":5}"}) {
            JsonObject root = tree(json);
            assertEquals(2L, root.get("size"), json);
            assertEquals(1L, root.get("hash"), json);
            assertEquals(5L, root.get("typeString"), json);
        }
    }

    /** A class the tree names that is not on the classpath stays a map, as the Maps-mode read found it. */
    @Test
    void aClassNotOnTheClasspath() {
        JsonObject tree = tree("{\"@type\":\"com.example.NoSuchClass\",\"name\":\"Joe\"}");
        Object read = JsonIo.toJava(tree, null).asClass(null);
        assertInstanceOf(Map.class, read);
        assertEquals("Joe", ((Map<?, ?>) read).get("name"));
    }

    /** n-cube's ConfigurationProvider builds one this way: read directly, its own values kept. */
    @Test
    void aJsonObjectBuiltByHand() {
        LocalDate born = LocalDate.of(1990, 2, 3);
        JsonObject built = new JsonObject();
        built.put("name", "m");
        built.put("born", born);
        built.setType(Member.class);
        Member member = JsonIo.toJava(built, null).asClass(Member.class);
        assertEquals("m", member.name);
        assertSame(born, member.born);
    }
}
