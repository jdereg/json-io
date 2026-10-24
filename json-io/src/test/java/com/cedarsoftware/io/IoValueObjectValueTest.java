package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A class whose {@code @IoValue} method returns an object, a list or a map is written as that value, and reads back
 * through the creator that takes it: an object without the creator parameter's key is the value itself, given to the
 * creator whole; one with that key is the class's fields, as TOON wrote such a class through 4.117.0. Jackson's
 * {@code @JsonCreator(mode = DELEGATING)} and {@code PROPERTIES} say which, when given. Up to now the value read back
 * as null inside the class, or threw. The value keeps its identity: an {@code @id} written on it names the value, and
 * a reference elsewhere reads back as the value, not as the class holding it.
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
class IoValueObjectValueTest {
    public static class Address {
        public String street;
        public int zip;

        public Address() {
        }

        public Address(String street, int zip) {
            this.street = street;
            this.zip = zip;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Address && Objects.equals(((Address) o).street, street) && ((Address) o).zip == zip;
        }

        @Override
        public int hashCode() {
            return Objects.hash(street, zip);
        }

        @Override
        public String toString() {
            return "Address(" + street + ", " + zip + ")";
        }
    }

    public static class Site {
        private final Address addr;

        @IoCreator
        public Site(Address addr) {
            this.addr = addr;
        }

        @IoValue
        public Address addr() {
            return addr;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Site && Objects.equals(((Site) o).addr, addr);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(addr);
        }

        @Override
        public String toString() {
            return "Site(" + addr + ")";
        }
    }

    /** Its value is a list. */
    public static class Tags {
        private final List<String> names;

        @IoCreator
        public Tags(List<String> names) {
            this.names = names;
        }

        @IoValue
        public List<String> names() {
            return names;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Tags && Objects.equals(((Tags) o).names, names);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(names);
        }
    }

    /** Its value is a map. */
    public static class Counts {
        private final Map<String, Integer> counts;

        @IoCreator
        public Counts(Map<String, Integer> counts) {
            this.counts = counts;
        }

        @IoValue
        public Map<String, Integer> counts() {
            return counts;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Counts && Objects.equals(((Counts) o).counts, counts);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(counts);
        }
    }

    /** A value with a field named value, beside others. */
    public static class Measure {
        public String unit;
        public double value;

        public Measure() {
        }

        public Measure(String unit, double value) {
            this.unit = unit;
            this.value = value;
        }
    }

    public static class Reading {
        private final Measure measure;

        @IoCreator
        public Reading(Measure measure) {
            this.measure = measure;
        }

        @IoValue
        public Measure measure() {
            return measure;
        }
    }

    public static class Readings {
        public Reading reading;
        public Object any;
    }

    /** Delegating, though the value has a key named as the parameter. */
    public static class Street {
        private final Address address;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Street(@JsonProperty("street") Address address) {
            this.address = address;
        }

        @JsonValue
        public Address address() {
            return address;
        }
    }

    /** Properties, though the object lacks the parameter's key: its fields, the parameter missing. */
    public static class Zip {
        private final Address where;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Zip(@JsonProperty("where") Address where) {
            this.where = where;
        }

        @JsonValue
        public Address where() {
            return where;
        }
    }

    public static class Holder {
        public Site site;
        public Object any;
        public List<Site> sites;
        public Map<String, Site> byName;
        public Site[] siteArray;
        public Tags tags;
        public Counts counts;
    }

    /** An Address held by a Site and by a field, in each order. */
    public static class Shared {
        public Address before;
        public Site site;
        public Site again;
        public Address after;
    }

    /** Sites sharing one Address, in a list, an array and a map, and the Address in a field after them. */
    public static class Many {
        public List<Site> sites;
        public Site[] array;
        public Map<String, Site> map;
        public Address address;
    }

    /** A creator given a Site, and a field holding a Site, after it in the document. */
    public static class Owner {
        private final Site site;

        @IoCreator
        public Owner(@com.cedarsoftware.io.annotation.IoProperty("site") Site site) {
            this.site = site;
        }
    }

    public static class Parent {
        public Owner owner;
        public Site site;
        public List<Site> sites;
        public Site[] array;
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.site = new Site(new Address("1 Main", 1));
        h.any = new Site(new Address("2 Any", 2));
        h.sites = new ArrayList<>(Arrays.asList(new Site(new Address("3 List", 3)), new Site(new Address("3 More", 33))));
        h.byName = new LinkedHashMap<>();
        h.byName.put("home", new Site(new Address("4 Map", 4)));
        h.siteArray = new Site[] {new Site(new Address("5 Array", 5))};
        h.tags = new Tags(new ArrayList<>(Arrays.asList("a", "b")));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("x", 1);
        h.counts = new Counts(counts);
        return h;
    }

    private static void assertSameHolder(Holder expected, Holder actual, boolean typed, String text) {
        assertEquals(expected.site, actual.site, text);
        if (typed) {
            assertEquals(expected.any, actual.any, text);
        }
        assertEquals(expected.sites, actual.sites, text);
        for (Object s : actual.sites) {
            assertInstanceOf(Site.class, s, text);
        }
        assertEquals(expected.byName, actual.byName, text);
        for (Object s : ((Map<?, ?>) actual.byName).values()) {
            assertInstanceOf(Site.class, s, text);
        }
        assertArrayEquals(expected.siteArray, actual.siteArray, text);
        assertEquals(expected.tags, actual.tags, text);
        assertEquals(expected.counts, actual.counts, text);
    }

    @Test
    void jsonReadsTheValueBackInEveryPlace() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoNever().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (WriteOptions options : all) {
            Holder h = holder();
            String json = JsonIo.toJson(h, options);
            assertSameHolder(h, JsonIo.toJava(json, null).asClass(Holder.class), options != all[1], json);
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
            assertSameHolder(h, JsonIo.fromToon(toon, null).asClass(Holder.class), options != null, toon);
        }
    }

    @Test
    void theClassAskedForAtTheRootIsBuiltFromTheValue() {
        Site site = new Site(new Address("6 Root", 6));
        assertEquals(site, JsonIo.toJava("{\"street\":\"6 Root\",\"zip\":6}", null).asClass(Site.class));
        assertEquals(site, JsonIo.toJava(JsonIo.toJson(site, null), null).asClass(Site.class));
        assertEquals(site, JsonIo.toJava(JsonIo.toJson(site, null), null).asClass(null));
        assertEquals(site, JsonIo.fromToon(JsonIo.toToon(site, null), null).asClass(Site.class));
        assertEquals(new Tags(Arrays.asList("r")), JsonIo.toJava("[\"r\"]", null).asClass(Tags.class));
    }

    /** A null value is written as null, so it reads back as null, as a String-valued class's does. */
    @Test
    void aNullValueReadsBackAsNull() {
        Holder h = new Holder();
        h.site = new Site(null);
        h.sites = new ArrayList<>(Arrays.asList(new Site(null)));
        String json = JsonIo.toJson(h, null);
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertNull(back.site, json);
        assertEquals(Arrays.asList((Site) null), back.sites, json);
    }

    /** What TOON wrote through 4.117.0: the class's fields. */
    @Test
    void theFieldsStillReadBack() {
        Holder back = JsonIo.fromToon("site:\n  addr:\n    street: 7 Old\n    zip: 7\n", null).asClass(Holder.class);
        assertEquals(new Site(new Address("7 Old", 7)), back.site);
        back = JsonIo.toJava("{\"site\":{\"addr\":{\"street\":\"7 Old\",\"zip\":7}}}", null).asClass(Holder.class);
        assertEquals(new Site(new Address("7 Old", 7)), back.site);
    }

    /** The value keeps its identity; the class holding it has none of its own (each place holds the value). */
    @Test
    void theValueKeepsItsIdentity() {
        Address address = new Address("8 Same", 8);
        Shared shared = new Shared();
        shared.before = address;
        shared.site = new Site(address);
        shared.again = shared.site;
        shared.after = address;
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            String json = JsonIo.toJson(shared, options);
            Shared back = JsonIo.toJava(json, null).asClass(Shared.class);
            assertEquals(address, back.site.addr, json);
            assertSame(back.before, back.site.addr, json);
            assertSame(back.before, back.again.addr, json);
            assertSame(back.before, back.after, json);
            assertNotSame(back.site, back.again, json);
            String toon = JsonIo.toToon(shared, options);
            back = JsonIo.fromToon(toon, null).asClass(Shared.class);
            assertEquals(address, back.site.addr, toon);
            if (toon.contains("$id")) {     // TOON without type info writes each place as it is, with no $id
                assertSame(back.before, back.site.addr, toon);
                assertSame(back.before, back.after, toon);
            }
        }

        Shared first = new Shared();
        first.site = new Site(address);
        first.after = address;
        String json = JsonIo.toJson(first, null);
        Shared back = JsonIo.toJava(json, null).asClass(Shared.class);
        assertEquals(address, back.after, json);
        assertSame(back.site.addr, back.after, json);
    }

    @Test
    void aValueSharedInsideCollectionsKeepsItsIdentity() {
        Address address = new Address("9 Many", 9);
        Many many = new Many();
        many.sites = new ArrayList<>(Arrays.asList(new Site(address), new Site(address)));
        many.array = new Site[] {new Site(address)};
        many.map = new LinkedHashMap<>();
        many.map.put("k", new Site(address));
        many.address = address;
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            String json = JsonIo.toJson(many, options);
            Many back = JsonIo.toJava(json, null).asClass(Many.class);
            Address first = back.sites.get(0).addr;
            assertEquals(address, first, json);
            assertSame(first, back.sites.get(1).addr, json);
            assertSame(first, back.array[0].addr, json);
            assertSame(first, back.map.get("k").addr, json);
            assertSame(first, back.address, json);
        }

        // Each place refers to the Address before it is defined
        String forward = "{\"sites\":[{\"@ref\":1}],\"array\":[{\"@ref\":1}],\"map\":{\"k\":{\"@ref\":1}},"
                + "\"address\":{\"@id\":1,\"street\":\"10 Later\",\"zip\":10}}";
        Many back = JsonIo.toJava(forward, null).asClass(Many.class);
        assertEquals(new Address("10 Later", 10), back.address);
        assertSame(back.address, back.sites.get(0).addr);
        assertSame(back.address, back.array[0].addr);
        assertSame(back.address, back.map.get("k").addr);
        Shared shared = JsonIo.toJava("{\"site\":{\"@ref\":1},\"after\":{\"@id\":1,\"street\":\"11 Field\",\"zip\":11}}",
                null).asClass(Shared.class);
        assertSame(shared.after, shared.site.addr);
    }

    /** A "value" key beside others is the value's field; alone with a type it is json-io's typed form. */
    @Test
    void aValueWithAFieldNamedValueReadsBack() {
        Readings readings = new Readings();
        readings.reading = new Reading(new Measure("kg", 2.5));
        readings.any = new Reading(new Measure("lb", 5.5));
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            String json = JsonIo.toJson(readings, options);
            Readings back = JsonIo.toJava(json, null).asClass(Readings.class);
            assertEquals("kg", back.reading.measure.unit, json);
            assertEquals(2.5, back.reading.measure.value, 0.0, json);
            assertEquals("lb", ((Reading) back.any).measure.unit, json);
        }
    }

    /** A creator's argument names a value defined later, in a field, a list and an array of the class. */
    @Test
    void aValueBuiltForACreatorFirstIsStillTheClassWhereItIsDefined() {
        String site = Site.class.getName();
        for (String place : new String[] {"\"site\":{\"@id\":1,\"street\":\"12 Late\",\"zip\":12}",
                "\"sites\":[{\"@id\":1,\"street\":\"12 Late\",\"zip\":12}]",
                "\"array\":[{\"@id\":1,\"street\":\"12 Late\",\"zip\":12}]"}) {
            String json = "{\"owner\":{\"site\":{\"@type\":\"" + site + "\",\"value\":{\"@ref\":1}}}," + place + "}";
            Parent back = JsonIo.toJava(json, null).asClass(Parent.class);
            Site defined = back.site != null ? back.site : back.sites != null ? back.sites.get(0) : back.array[0];
            assertEquals(new Address("12 Late", 12), defined.addr, json);
            assertSame(defined.addr, back.owner.site.addr, json);
        }
    }

    @Test
    void jacksonsCreatorModeSaysWhichFormItIs() {
        Street street = JsonIo.toJava("{\"street\":\"9 Deleg\",\"zip\":9}", null).asClass(Street.class);
        assertEquals(new Address("9 Deleg", 9), street.address);
        Zip zip = JsonIo.toJava("{\"street\":\"z\",\"zip\":10}", null).asClass(Zip.class);
        assertNull(zip.where);
        zip = JsonIo.toJava("{\"where\":{\"street\":\"z\",\"zip\":10}}", null).asClass(Zip.class);
        assertEquals(new Address("z", 10), zip.where);
    }
}
