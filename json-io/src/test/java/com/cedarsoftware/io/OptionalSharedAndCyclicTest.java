package com.cedarsoftware.io;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An Optional is written as its content, so the writer must trace that content for cycles and shared objects, and the
 * reader must wrap an {@code @ref} it finds in an Optional field. The writer traced an Optional through its fields,
 * which java.util keeps hidden on JDK 9+: a cycle through an Optional recursed until StackOverflowError, and an object
 * shared through two Optionals was written twice and read back as two objects. On JDK 8, where the fields are
 * visible, the second one was written as {@code @ref}, and the read failed: the reader put the bare content into the
 * Optional field ("Unsupported conversion ... target type 'Optional'").
 */
class OptionalSharedAndCyclicTest {

    public static class Parent {
        public String name;
        public Optional<Child> child;
    }

    public static class Child {
        public String name;
        public Parent parent;
    }

    public static class Pojo {
        public String id;
    }

    public static class TwoOptionals {
        public Optional<Pojo> a;
        public Optional<Pojo> b;
    }

    private static final WriteOptions CYCLES = new WriteOptionsBuilder().cycleSupport(true).build();

    private static Parent parentWithChildPointingBack() {
        Parent parent = new Parent();
        parent.name = "p";
        Child child = new Child();
        child.name = "c";
        child.parent = parent;
        parent.child = Optional.of(child);
        return parent;
    }

    @Test
    void aCycleThroughAnOptionalRoundTrips() {
        String json = JsonIo.toJson(parentWithChildPointingBack(), CYCLES);
        assertThat(json).contains("\"@id\":1").contains("\"@ref\":1");

        Parent read = JsonIo.toJava(json, null).asClass(Parent.class);
        assertThat(read.child).isPresent();
        assertThat(read.child.get().name).isEqualTo("c");
        assertThat(read.child.get().parent).isSameAs(read);
    }

    @Test
    void aCycleThroughAnOptionalRoundTripsInToon() {
        String toon = JsonIo.toToon(parentWithChildPointingBack(), CYCLES);
        Parent read = JsonIo.fromToon(toon, null).asClass(Parent.class);
        assertThat(read.child).isPresent();
        assertThat(read.child.get().parent).isSameAs(read);
    }

    @Test
    void anObjectSharedThroughTwoOptionalsIsReadBackAsOneObject() {
        Pojo shared = new Pojo();
        shared.id = "shared";
        TwoOptionals holder = new TwoOptionals();
        holder.a = Optional.of(shared);
        holder.b = Optional.of(shared);

        String json = JsonIo.toJson(holder, CYCLES);
        assertThat(json).contains("\"@ref\":1");

        TwoOptionals read = JsonIo.toJava(json, null).asClass(TwoOptionals.class);
        assertThat(read.a.get().id).isEqualTo("shared");
        assertThat(read.b.get()).isSameAs(read.a.get());
    }

    @Test
    void aForwardReferenceInAnOptionalFieldIsWrapped() {
        // b refers to the object a defines, before a has been read
        String json = "{\"@type\":\"" + TwoOptionals.class.getName() + "\","
                + "\"b\":{\"@ref\":1},"
                + "\"a\":{\"@id\":1,\"@type\":\"" + Pojo.class.getName() + "\",\"id\":\"x\"}}";

        TwoOptionals read = JsonIo.toJava(json, null).asClass(TwoOptionals.class);
        assertThat(read.a.get().id).isEqualTo("x");
        assertThat(read.b).isPresent();
        assertThat(read.b.get()).isSameAs(read.a.get());
    }
}
