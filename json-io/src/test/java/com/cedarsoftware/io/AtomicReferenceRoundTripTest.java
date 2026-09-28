package com.cedarsoftware.io;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An AtomicReference keeps its content in a private field that java.util.concurrent.atomic does not open on JDK 9+,
 * and json-io writes an unreachable JDK field as null. Its content was therefore written as {@code "value":null},
 * and reading it back failed (the field cannot be set either). It is written through get() and read through set()
 * now, as on JDK 8, where the field was reachable -- the content, shared objects and cycles through it included.
 */
class AtomicReferenceRoundTripTest {

    public static class Holder {
        public String name;
        public AtomicReference<Object> ref;
    }

    public static class Item {
        public String id;
    }

    @Test
    void itsContentIsWrittenAndReadBack() {
        Holder holder = new Holder();
        holder.name = "h";
        holder.ref = new AtomicReference<>("hello");

        String json = JsonIo.toJson(holder, null);
        assertThat(json).contains("\"value\":\"hello\"");

        Holder read = JsonIo.toJava(json, null).asClass(Holder.class);
        assertThat(read.ref.get()).isEqualTo("hello");
    }

    @Test
    void anObjectInsideIsReadBackAndACycleThroughItHolds() {
        Item item = new Item();
        item.id = "i";
        Holder holder = new Holder();
        holder.ref = new AtomicReference<>(item);
        Holder read = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
        assertThat(((Item) read.ref.get()).id).isEqualTo("i");

        Holder self = new Holder();
        self.name = "self";
        self.ref = new AtomicReference<>(self);
        WriteOptions cycles = new WriteOptionsBuilder().cycleSupport(true).build();
        Holder selfRead = JsonIo.toJava(JsonIo.toJson(self, cycles), null).asClass(Holder.class);
        assertThat(selfRead.ref.get()).isSameAs(selfRead);
    }

    @Test
    void itsContentSurvivesToon() {
        Holder holder = new Holder();
        holder.ref = new AtomicReference<>("hello");
        String toon = JsonIo.toToon(holder, null);
        assertThat(toon).contains("value: hello");
        Holder read = JsonIo.fromToon(toon, null).asClass(Holder.class);
        assertThat(read.ref.get()).isEqualTo("hello");
    }
}
