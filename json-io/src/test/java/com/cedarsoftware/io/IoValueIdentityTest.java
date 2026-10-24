package com.cedarsoftware.io;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object with an {@code @IoValue} method is written as that method's value, in its place, where no @id can go. Up
 * to 4.117.0 the reference pass counted it as any object, so where one was held twice the second place held
 * {"@ref":1} to an @id never written, and the JSON did not read back ("Forward reference @ref: 1, but no object
 * defined"). Now it is never referenced itself: each place holds its value, as a String or a LocalDate is written in
 * each place it is held. A value that is itself an object keeps its identity, as any object does: held twice, it is
 * written once with an @id and then as {"@ref":n}.
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
class IoValueIdentityTest {
    private static final Pattern ID = Pattern.compile("\"@id\":(\\d+)");
    private static final Pattern REF = Pattern.compile("\"@ref\":(\\d+)");

    public static class Email {
        private final String address;

        @IoCreator
        public Email(@IoProperty("address") String address) {
            this.address = address;
        }

        @IoValue
        public String toValue() {
            return address;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Email && Objects.equals(((Email) o).address, address);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(address);
        }
    }

    public static class Address {
        public String street;
        public Object back;
    }

    /** Written as an object, its address: the value is itself an object, with an identity of its own. */
    public static class Site {
        private final Address address;

        public Site(Address address) {
            this.address = address;
        }

        @IoValue
        public Address toValue() {
            return address;
        }
    }

    /** An @IoValue class that a custom writer writes: the writer wins, as before, and the object keeps its @id. */
    public static class Tag {
        private final String name;

        public Tag(String name) {
            this.name = name;
        }

        @IoValue
        public String toValue() {
            return "v:" + name;
        }
    }

    public static class TagWriter implements JsonClassWriter<Tag> {
        @Override
        public void write(Tag tag, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            context.writeObjectField("name", tag.name, String.class);
        }
    }

    public static class Endless {
        @IoValue
        public Endless next() {
            return new Endless();
        }
    }

    /** Two objects, each written as the other. */
    public static class Mirror {
        Mirror other;

        @IoValue
        public Mirror toValue() {
            return other;
        }
    }

    public static class TagHolder {
        public Tag a;
        public Tag b;
    }

    public static class Holder {
        public Email a;
        public Email b;
        public Object any;
        public List<Object> raw = new ArrayList<>();
        public List<Email> emails = new ArrayList<>();
    }

    public static class SiteHolder {
        public Site site;
        public Site site2;
        public Address address;
    }

    private static List<WriteOptions> modes() {
        List<WriteOptions> modes = new ArrayList<>();
        modes.add(new WriteOptionsBuilder().build());
        modes.add(new WriteOptionsBuilder().showTypeInfoAlways().build());
        modes.add(new WriteOptionsBuilder().showTypeInfoNever().build());
        return modes;
    }

    /** Every @ref in {@code json} names an @id written in it. */
    private static void assertEveryRefIsDefined(String json) {
        Set<String> ids = new HashSet<>();
        Matcher m = ID.matcher(json);
        while (m.find()) {
            ids.add(m.group(1));
        }
        m = REF.matcher(json);
        while (m.find()) {
            assertTrue(ids.contains(m.group(1)), "@ref " + m.group(1) + " with no @id in " + json);
        }
    }

    @Test
    void aValueObjectHeldTwiceIsWrittenAsItsValueInEachPlace() {
        for (WriteOptions options : modes()) {
            Email shared = new Email("same@x.com");
            Holder h = new Holder();
            h.a = shared;
            h.b = shared;
            h.any = shared;
            h.raw.add(shared);
            h.raw.add(shared);
            h.emails.add(shared);
            h.emails.add(shared);
            String json = JsonIo.toJson(h, options);
            assertFalse(json.contains("@ref"), json);
            assertFalse(json.contains("@id"), json);
            if (options.isNeverShowingType()) {
                continue;     // nothing says what the Object field and the raw list hold
            }
            Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
            assertEquals(shared, back.a, json);
            assertEquals(shared, back.b, json);
            assertNotSame(back.a, back.b, json);      // written by value: two places, two objects
            assertEquals(shared, back.any, json);
            assertEquals(shared, back.raw.get(0), json);
            assertEquals(shared, back.raw.get(1), json);
            assertEquals(shared, back.emails.get(0), json);
            assertEquals(shared, back.emails.get(1), json);
        }
    }

    @Test
    void anObjectValueHeldTwiceIsWrittenOnceAndReferenced() {
        for (WriteOptions options : modes()) {
            Address address = new Address();
            address.street = "1 Main";
            Site site = new Site(address);
            SiteHolder h = new SiteHolder();
            h.site = site;
            h.site2 = site;
            String json = JsonIo.toJson(h, options);
            assertEveryRefIsDefined(json);
            assertEquals(1, count(ID, json), json);
            assertEquals(1, count(REF, json), json);
            assertEquals(1, count(Pattern.compile("1 Main"), json), json);

            h.site2 = null;
            h.address = address;          // the value held elsewhere too
            json = JsonIo.toJson(h, options);
            assertEveryRefIsDefined(json);
            assertEquals(1, count(Pattern.compile("1 Main"), json), json);
        }
    }

    @Test
    void aCycleThroughAValueObjectIsWrittenWithEveryRefDefined() {
        for (WriteOptions options : modes()) {
            Address address = new Address();
            address.street = "1 Main";
            Site site = new Site(address);
            address.back = site;
            SiteHolder h = new SiteHolder();
            h.site = site;
            String json = JsonIo.toJson(h, options);
            assertEveryRefIsDefined(json);
            assertEquals(1, count(REF, json), json);
        }
    }

    @Test
    void aValueObjectThatACustomWriterWritesKeepsItsIdentity() {
        Tag tag = new Tag("t");
        TagHolder h = new TagHolder();
        h.a = tag;
        h.b = tag;
        WriteOptions custom = new WriteOptionsBuilder().addCustomWrittenClass(Tag.class, new TagWriter()).build();
        String json = JsonIo.toJson(h, custom);
        assertTrue(json.contains("\"a\":{\"@id\":1,\"name\":\"t\"}"), json);
        assertTrue(json.contains("\"b\":{\"@ref\":1}"), json);

        // Its custom writer turned off, it is written by its @IoValue method, so as its value in each place
        json = JsonIo.toJson(h, new WriteOptionsBuilder().addCustomWrittenClass(Tag.class, new TagWriter())
                .addNotCustomWrittenClass(Tag.class).build());
        assertEquals("{\"@type\":\"" + TagHolder.class.getName() + "\",\"a\":\"v:t\",\"b\":\"v:t\"}", json);
    }

    /**
     * A value method that makes a new object of its own class each time never ends. The reference pass counts each
     * value one level deeper than its holder, so it stops at the depth limit, rather than at the object limit (or, up
     * to 4.117.0, at the end of the stack, from the writer).
     */
    @Test
    void aValueMethodThatNeverEndsIsRefusedAtTheDepthLimit() {
        WriteOptions options = new WriteOptionsBuilder().maxObjectGraphDepth(50).maxObjectCount(100000).build();
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toJson(new Endless(), options));
        assertTrue(e.getMessage().contains("too deep"), e.getMessage());

        Mirror a = new Mirror();
        Mirror b = new Mirror();
        a.other = b;
        b.other = a;
        e = assertThrows(JsonIoException.class, () -> JsonIo.toJson(new Object[] {a, b}, options));
        assertTrue(e.getMessage().contains("too deep"), e.getMessage());
    }

    /** Through WriteOptions other than json-io's own, which keep no write plans: the same. */
    @Test
    void anotherWriteOptionsImplementationWritesTheValueInEachPlace() {
        WriteOptions real = new WriteOptionsBuilder().build();
        WriteOptions other = (WriteOptions) Proxy.newProxyInstance(WriteOptions.class.getClassLoader(),
                new Class<?>[] {WriteOptions.class}, (proxy, method, args) -> {
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        Email shared = new Email("same@x.com");
        Holder h = new Holder();
        h.a = shared;
        h.b = shared;
        String json = JsonIo.toJson(h, other);
        assertTrue(json.contains("\"a\":\"same@x.com\",\"b\":\"same@x.com\""), json);
        assertFalse(json.contains("@ref"), json);
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
