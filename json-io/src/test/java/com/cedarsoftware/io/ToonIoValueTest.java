package com.cedarsoftware.io;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOON writes an object with an {@code @IoValue} method as that method's value, in its place, as JSON does:
 * {@code email: a@x.com}, a list of them as {@code emails[2]: c@x.com,d@x.com}, and with type info on, where nothing
 * declares the class, {@code $type} and {@code value}. Up to 4.117.0 TOON used the method only for the root (which did
 * not read back) and wrote the object's fields everywhere else. As in JSON, such an object is never given a $id of its
 * own: each place holds its value, and a value that is itself an object keeps its identity.
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
class ToonIoValueTest {
    private static final Pattern ID = Pattern.compile("\\$id: (\\d+)");
    private static final Pattern REF = Pattern.compile("\\$ref: (\\d+)");

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

        @Override
        public String toString() {
            return "Email(" + address + ")";
        }
    }

    public static class Cents {
        private final long amount;

        @IoCreator
        public Cents(@IoProperty("amount") long amount) {
            this.amount = amount;
        }

        @IoValue
        public long toValue() {
            return amount;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cents && ((Cents) o).amount == amount;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(amount);
        }
    }

    public static class Row {
        public String name;
        public Email email;
        public Cents cents;
    }

    public static class Holder {
        public Email email;
        public Cents cents;
        public Object any;
        public List<Email> emails = new ArrayList<>();
        public Map<String, Email> byName = new LinkedHashMap<>();
        public Map<Email, String> byEmail = new LinkedHashMap<>();
        public Email[] array;
        public Optional<Email> optional;
        public List<Row> rows = new ArrayList<>();
        public Map<String, Row> keyed = new LinkedHashMap<>();
    }

    public static class Address {
        public String street;
        public Object back;
    }

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

    public static class SiteHolder {
        public Site site;
        public Site site2;
    }

    /** Two objects, each written as the other. */
    public static class Mirror {
        Mirror other;

        @IoValue
        public Mirror toValue() {
            return other;
        }
    }

    public static class Endless {
        @IoValue
        public Endless next() {
            return new Endless();
        }
    }

    /** An @IoValue class whose custom writer gives its data: the writer wins, as in JSON. */
    public static class Tag {
        private final String name;

        public Tag(String name) {
            this.name = name;
        }

        @IoValue
        public String toValue() {
            return "v-" + name;
        }
    }

    public static class TagDataWriter implements JsonClassWriter<Tag> {
        @Override
        public void write(Tag tag, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            context.writeObjectField("name", tag.name, String.class);
        }

        @Override
        public Object toData(Tag tag) {
            return "data-" + tag.name;
        }
    }

    /** A custom writer that gives TOON no data: TOON writes the fields, as before, not the @IoValue value. */
    public static class TagPlainWriter implements JsonClassWriter<Tag> {
        @Override
        public void write(Tag tag, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            context.writeObjectField("name", tag.name, String.class);
        }
    }

    public static class TagHolder {
        public Tag tag;
    }

    /** Written as its fields: its custom writer gives no data for it. */
    public static class Pair {
        public Object left;
        public Object right;
    }

    public static class NoDataWriter implements JsonClassWriter<Pair> {
        @Override
        public void write(Pair pair, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            context.writeObjectField("left", pair.left, Object.class);
            context.writeObjectField("right", pair.right, Object.class);
        }

        @Override
        public Object toData(Pair pair) {
            return NO_DATA;
        }
    }

    public static class Optionals {
        public Optional<Address> first;
        public Optional<Address> second;
    }

    private static List<WriteOptions> modes() {
        return Arrays.asList(new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build());
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.email = new Email("a@x.com");
        h.cents = new Cents(150);
        h.any = new Email("b@x.com");
        h.emails.add(new Email("c@x.com"));
        h.emails.add(new Email("d@x.com"));
        h.byName.put("k", new Email("e@x.com"));
        h.byEmail.put(new Email("key@x.com"), "v");
        h.array = new Email[] {new Email("f@x.com")};
        h.optional = Optional.of(new Email("o@x.com"));
        for (int i = 0; i < 2; i++) {
            Row row = new Row();
            row.name = "n" + i;
            row.email = new Email("r" + i + "@x.com");
            row.cents = new Cents(i);
            h.rows.add(row);
            Row keyedRow = new Row();
            keyedRow.name = "m" + i;
            keyedRow.email = new Email("m" + i + "@x.com");
            keyedRow.cents = new Cents(10 + i);
            h.keyed.put("k" + i, keyedRow);
        }
        return h;
    }

    @Test
    void theValueIsWrittenWhereTheClassIsDeclared() {
        for (WriteOptions options : modes()) {
            String toon = "\n" + JsonIo.toToon(holder(), options) + "\n";
            if (options.isAlwaysShowingType()) {
                assertTrue(toon.contains("email:\n  $type: " + Email.class.getName() + "\n  value: a@x.com"), toon);
                continue;
            }
            assertTrue(toon.contains("\nemail: a@x.com\n"), toon);
            assertTrue(toon.contains("\ncents: 150\n"), toon);
            assertTrue(toon.contains("\nemails[2]: c@x.com,d@x.com\n"), toon);
            assertTrue(toon.contains("\n  k: e@x.com\n"), toon);
            assertTrue(toon.contains("\narray[1]: f@x.com\n"), toon);
            assertTrue(toon.contains("\noptional: o@x.com\n"), toon);
            assertTrue(toon.contains("email: r0@x.com"), toon);
            assertFalse(toon.contains("address:"), toon);
            assertFalse(toon.contains("amount:"), toon);
        }
    }

    @Test
    void everyPlaceReadsBackInEachTypeInfoMode() {
        for (WriteOptions options : modes()) {
            Holder h = holder();
            String toon = JsonIo.toToon(h, options);
            Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
            assertEquals(h.email, back.email, toon);
            assertEquals(h.cents, back.cents, toon);
            assertEquals(h.emails, back.emails, toon);
            assertInstanceOf(Email.class, back.emails.get(1), toon);
            assertEquals(h.byName, back.byName, toon);
            assertInstanceOf(Email.class, ((Map<?, ?>) back.byName).get("k"), toon);
            assertEquals(h.byEmail, back.byEmail, toon);
            assertInstanceOf(Email.class, ((Map<?, ?>) back.byEmail).keySet().iterator().next(), toon);
            assertEquals(h.array[0], back.array[0], toon);
            assertEquals(h.optional, back.optional, toon);
            assertInstanceOf(Email.class, ((Optional<?>) back.optional).get(), toon);
            for (int i = 0; i < 2; i++) {
                assertEquals(h.rows.get(i).email, back.rows.get(i).email, toon);
                assertEquals(h.rows.get(i).cents, back.rows.get(i).cents, toon);
                assertEquals(h.keyed.get("k" + i).email, back.keyed.get("k" + i).email, toon);
            }
            if (toon.startsWith("$type")) {
                assertEquals(h.any, back.any, toon);      // with type info, where nothing declares it, its type
            }
        }
    }

    @Test
    void whereNothingDeclaresTheClassTypeInfoGivesItsTypeAndValue() {
        Object[] values = {new Email("r@x.com"), new Cents(7)};
        String toon = JsonIo.toToon(values, new WriteOptionsBuilder().showTypeInfoMinimal().build());
        assertTrue(toon.contains("$type: " + Email.class.getName() + "\n    value: r@x.com"), toon);
        Object[] back = JsonIo.fromToon(toon, null).asClass(Object[].class);
        assertEquals(values[0], back[0], toon);
        assertEquals(values[1], back[1], toon);

        assertEquals("r@x.com", JsonIo.toToon(new Email("r@x.com"), null));
        assertEquals(new Email("r@x.com"), JsonIo.fromToon("r@x.com", null).asClass(Email.class));
    }

    @Test
    void anObjectHeldTwiceIsWrittenAsItsValueInEachPlace() {
        for (WriteOptions options : modes()) {
            Email shared = new Email("same@x.com");
            Holder h = new Holder();
            h.email = shared;
            h.any = shared;
            h.emails.add(shared);
            h.emails.add(shared);
            WriteOptions cycles = new WriteOptionsBuilder(options).cycleSupport(true).build();
            String toon = JsonIo.toToon(h, cycles);
            assertFalse(toon.contains("$id"), toon);
            assertFalse(toon.contains("$ref"), toon);
            Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
            assertEquals(shared, back.email, toon);
            assertEquals(Arrays.asList(shared, shared), back.emails, toon);
        }
    }

    @Test
    void anObjectValueKeepsItsIdentity() {
        for (WriteOptions options : modes()) {
            WriteOptions cycles = new WriteOptionsBuilder(options).cycleSupport(true).build();
            Address address = new Address();
            address.street = "1 Main";
            Site site = new Site(address);
            SiteHolder h = new SiteHolder();
            h.site = site;
            h.site2 = site;
            String toon = JsonIo.toToon(h, cycles);
            assertEveryRefIsDefined(toon);
            assertEquals(1, count(Pattern.compile("1 Main"), toon), toon);

            address.back = site;            // a cycle through the holder
            h.site2 = null;
            toon = JsonIo.toToon(h, cycles);
            assertEveryRefIsDefined(toon);
            assertEquals(1, count(REF, toon), toon);
        }
    }

    @Test
    void aValueMethodThatNeverEndsIsRefusedAtTheDepthLimit() {
        WriteOptions options = new WriteOptionsBuilder().cycleSupport(true).maxObjectGraphDepth(50).build();
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toToon(new Endless(), options));
        assertTrue(e.getMessage().contains("too deep"), e.getMessage());

        Mirror a = new Mirror();
        Mirror b = new Mirror();
        a.other = b;
        b.other = a;
        e = assertThrows(JsonIoException.class, () -> JsonIo.toToon(new Object[] {a, b}, options));
        assertTrue(e.getMessage().contains("too deep"), e.getMessage());
    }

    /** TOON written by 4.117.0, with the object's fields, still reads. */
    @Test
    void theFieldsFormWrittenBeforeStillReads() {
        Holder back = JsonIo.fromToon("email:\n  address: old@x.com\nemails[1]{address}:\n  c@x.com\n", null)
                .asClass(Holder.class);
        assertEquals(new Email("old@x.com"), back.email);
        assertEquals(new Email("c@x.com"), back.emails.get(0));
    }

    @Test
    void aCustomWriterStillDecides() {
        TagHolder h = new TagHolder();
        h.tag = new Tag("t");
        String toon = JsonIo.toToon(h, new WriteOptionsBuilder().addCustomWrittenClass(Tag.class, new TagDataWriter())
                .build());
        assertEquals("tag: data-t", toon);
        toon = JsonIo.toToon(h, new WriteOptionsBuilder().addCustomWrittenClass(Tag.class, new TagPlainWriter())
                .build());
        assertEquals("tag:\n  name: t", toon);
        toon = JsonIo.toToon(h, null);
        assertEquals("tag: v-t", toon);
    }

    /** Where the reference pass looks: an object's fields when its custom writer gives no data, else its data. */
    @Test
    void theReferencePassFollowsWhatIsWritten() {
        Address shared = new Address();
        shared.street = "1 Main";
        Pair pair = new Pair();
        pair.left = shared;
        pair.right = shared;
        WriteOptions options = new WriteOptionsBuilder().cycleSupport(true).showTypeInfoMinimal()
                .addCustomWrittenClass(Pair.class, new NoDataWriter()).build();
        String toon = JsonIo.toToon(pair, options);
        assertEquals(1, count(Pattern.compile("1 Main"), toon), toon);
        assertEveryRefIsDefined(toon);

        Optionals optionals = new Optionals();            // an Optional's content is in a field the JDK keeps to itself
        optionals.first = Optional.of(shared);
        optionals.second = optionals.first;
        Optionals two = new Optionals();
        two.first = Optional.of(shared);
        two.second = Optional.of(shared);
        for (Optionals o : Arrays.asList(optionals, two)) {
            toon = JsonIo.toToon(o, new WriteOptionsBuilder().cycleSupport(true).showTypeInfoMinimal().build());
            assertEquals(1, count(Pattern.compile("1 Main"), toon), toon);
            assertEveryRefIsDefined(toon);
        }
    }

    /** Through WriteOptions other than json-io's own, which keep no write plans: the same. */
    @Test
    void anotherWriteOptionsImplementationWritesTheValue() {
        WriteOptions real = new WriteOptionsBuilder().cycleSupport(true).build();
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
        h.email = shared;
        h.emails.add(shared);
        String toon = JsonIo.toToon(h, other);
        assertTrue(toon.contains("\nemail: same@x.com\n"), toon);
        assertTrue(toon.contains("\nemails[1]: same@x.com\n"), toon);

        WriteOptions typed = new WriteOptionsBuilder().cycleSupport(true).showTypeInfoAlways().build();
        WriteOptions otherTyped = (WriteOptions) Proxy.newProxyInstance(WriteOptions.class.getClassLoader(),
                new Class<?>[] {WriteOptions.class}, (proxy, method, args) -> {
                    try {
                        return method.invoke(typed, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        h.any = shared;
        toon = JsonIo.toToon(h, otherTyped);
        assertFalse(toon.contains("$id"), toon);
        assertFalse(toon.contains("$ref"), toon);
    }

    /** An Optional of such a class, with its content written as the value alone, reads back, in JSON too. */
    @Test
    void anOptionalHoldsTheClassBuiltFromTheValue() {
        Holder back = JsonIo.toJava("{\"optional\":\"o@x.com\"}", null).asClass(Holder.class);
        assertEquals(Optional.of(new Email("o@x.com")), back.optional);
        Optional<Email> root = JsonIo.toJava("\"o@x.com\"", null).asType(new TypeHolder<Optional<Email>>() { });
        assertEquals(Optional.of(new Email("o@x.com")), root);
    }

    private static void assertEveryRefIsDefined(String toon) {
        Set<String> ids = new HashSet<>();
        Matcher m = ID.matcher(toon);
        while (m.find()) {
            ids.add(m.group(1));
        }
        m = REF.matcher(toon);
        while (m.find()) {
            assertTrue(ids.contains(m.group(1)), "$ref " + m.group(1) + " with no $id in\n" + toon);
        }
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
