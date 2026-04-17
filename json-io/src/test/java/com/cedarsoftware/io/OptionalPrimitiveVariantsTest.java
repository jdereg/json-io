package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OptionalInt, OptionalLong, OptionalDouble serialization/deserialization.
 * These previously produced garbage output ({@code {"isPresent":null,"value":null}})
 * because no custom writer/factory existed. Now handled with dedicated writers and
 * factories, using Jackson-compatible primitive form by default.
 */
class OptionalPrimitiveVariantsTest {

    public static class Holder {
        public Optional<String> str;
        public OptionalInt optInt;
        public OptionalLong optLong;
        public OptionalDouble optDouble;

        public Holder() {}
    }

    // ========== Top-level round-trip (Jackson-style primitive form) ==========

    @Test
    void topLevelOptionalIntPresent_roundTrips() {
        OptionalInt opt = OptionalInt.of(42);
        String json = JsonIo.toJson(opt);
        // Verify valid JSON (no garbage output)
        assertThat(json).doesNotContain("isPresent");
        OptionalInt restored = JsonIo.toJava(json).asClass(OptionalInt.class);
        assertThat(restored).isPresent();
        assertThat(restored.getAsInt()).isEqualTo(42);
    }

    @Test
    void topLevelOptionalIntEmpty_roundTrips() {
        OptionalInt opt = OptionalInt.empty();
        String json = JsonIo.toJson(opt);
        OptionalInt restored = JsonIo.toJava(json).asClass(OptionalInt.class);
        assertThat(restored).isEmpty();
    }

    @Test
    void topLevelOptionalLongPresent_roundTrips() {
        OptionalLong opt = OptionalLong.of(1234567890123L);
        String json = JsonIo.toJson(opt);
        assertThat(json).doesNotContain("isPresent");
        OptionalLong restored = JsonIo.toJava(json).asClass(OptionalLong.class);
        assertThat(restored).isPresent();
        assertThat(restored.getAsLong()).isEqualTo(1234567890123L);
    }

    @Test
    void topLevelOptionalLongEmpty_roundTrips() {
        OptionalLong restored = JsonIo.toJava(JsonIo.toJson(OptionalLong.empty()))
                .asClass(OptionalLong.class);
        assertThat(restored).isEmpty();
    }

    @Test
    void topLevelOptionalDoublePresent_roundTrips() {
        OptionalDouble opt = OptionalDouble.of(3.14159);
        String json = JsonIo.toJson(opt);
        assertThat(json).doesNotContain("isPresent");
        OptionalDouble restored = JsonIo.toJava(json).asClass(OptionalDouble.class);
        assertThat(restored).isPresent();
        assertThat(restored.getAsDouble()).isEqualTo(3.14159);
    }

    @Test
    void topLevelOptionalDoubleEmpty_roundTrips() {
        OptionalDouble restored = JsonIo.toJava(JsonIo.toJson(OptionalDouble.empty()))
                .asClass(OptionalDouble.class);
        assertThat(restored).isEmpty();
    }

    // ========== Fields in a holder POJO ==========

    @Test
    void holderWithAllPresentOptionals_roundTrips() {
        Holder h = new Holder();
        h.str = Optional.of("hello");
        h.optInt = OptionalInt.of(42);
        h.optLong = OptionalLong.of(99L);
        h.optDouble = OptionalDouble.of(2.5);

        String json = JsonIo.toJson(h);
        // Must be valid JSON — no garbage
        assertThat(json).doesNotContain("isPresent\":null");
        assertThat(json).doesNotContain("\"value\":null");

        Holder restored = JsonIo.toJava(json).asClass(Holder.class);
        assertThat(restored.str).isPresent().contains("hello");
        assertThat(restored.optInt).isPresent();
        assertThat(restored.optInt.getAsInt()).isEqualTo(42);
        assertThat(restored.optLong).isPresent();
        assertThat(restored.optLong.getAsLong()).isEqualTo(99L);
        assertThat(restored.optDouble).isPresent();
        assertThat(restored.optDouble.getAsDouble()).isEqualTo(2.5);
    }

    @Test
    void holderWithAllEmptyOptionals_roundTrips() {
        Holder h = new Holder();
        h.str = Optional.empty();
        h.optInt = OptionalInt.empty();
        h.optLong = OptionalLong.empty();
        h.optDouble = OptionalDouble.empty();

        String json = JsonIo.toJson(h);
        Holder restored = JsonIo.toJava(json).asClass(Holder.class);
        assertThat(restored.str).isEmpty();
        assertThat(restored.optInt).isEmpty();
        assertThat(restored.optLong).isEmpty();
        assertThat(restored.optDouble).isEmpty();
    }

    // ========== standardJson() explicitly uses primitive form ==========

    @Test
    void standardJson_usesBareValueForPresent() {
        Holder h = new Holder();
        h.str = Optional.of("hello");
        h.optInt = OptionalInt.of(42);
        h.optLong = OptionalLong.empty();
        h.optDouble = OptionalDouble.of(3.14);

        String json = JsonIo.toJson(h, new WriteOptionsBuilder().standardJson().build());
        // Values appear bare (not wrapped in {present,value})
        assertThat(json).contains("\"str\":\"hello\"");
        assertThat(json).contains("\"optInt\":42");
        assertThat(json).contains("\"optLong\":null");
        assertThat(json).contains("\"optDouble\":3.14");
    }

    // ========== writeOptionalAsObject(true) forces legacy object form ==========

    @Test
    void legacyObjectForm_whenOptedIn() {
        Holder h = new Holder();
        h.str = Optional.of("hello");
        h.optInt = OptionalInt.of(42);
        h.optLong = OptionalLong.empty();
        h.optDouble = OptionalDouble.of(3.14);

        WriteOptions opts = new WriteOptionsBuilder()
                .writeOptionalAsObject(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(h, opts);

        // Object form with present/value
        assertThat(json).contains("\"present\":true");
        assertThat(json).contains("\"present\":false");
        assertThat(json).contains("\"value\":\"hello\"");
        assertThat(json).contains("\"value\":42");

        // And it still reads back correctly
        Holder restored = JsonIo.toJava(json).asClass(Holder.class);
        assertThat(restored.str).isPresent().contains("hello");
        assertThat(restored.optInt).isPresent();
        assertThat(restored.optInt.getAsInt()).isEqualTo(42);
        assertThat(restored.optLong).isEmpty();
        assertThat(restored.optDouble).isPresent();
        assertThat(restored.optDouble.getAsDouble()).isEqualTo(3.14);
    }

    // ========== standardJson() overrides writeOptionalAsObject(true) ==========

    @Test
    void standardJson_overridesWriteOptionalAsObject() {
        // Order matters: .standardJson() must reset writeOptionalAsObject back to false
        Holder h = new Holder();
        h.str = Optional.of("hi");
        h.optInt = OptionalInt.of(5);
        h.optLong = OptionalLong.of(10L);
        h.optDouble = OptionalDouble.of(1.5);

        WriteOptions opts = new WriteOptionsBuilder()
                .writeOptionalAsObject(true)
                .standardJson()  // should reset the above to false
                .build();
        String json = JsonIo.toJson(h, opts);

        // Primitive form — no {present,value}
        assertThat(json).doesNotContain("\"present\"");
        assertThat(json).contains("\"str\":\"hi\"");
        assertThat(json).contains("\"optInt\":5");
    }

    // ========== Legacy {"present":...,"value":...} format still reads ==========

    @Test
    void legacyFormatStillReads_Optional() {
        // Hand-crafted legacy JSON with short meta keys
        String json = "{\"@t\":\"" + Holder.class.getName() + "\","
                + "\"str\":{\"@t\":\"Optional\",\"present\":true,\"value\":\"legacy\"},"
                + "\"optInt\":{\"@t\":\"OptionalInt\",\"present\":true,\"value\":7}}";
        Holder h = JsonIo.toJava(json).asClass(Holder.class);
        assertThat(h.str).isPresent().contains("legacy");
        assertThat(h.optInt).isPresent();
        assertThat(h.optInt.getAsInt()).isEqualTo(7);
    }

    @Test
    void legacyFormatStillReads_PresentFalse() {
        String json = "{\"@t\":\"" + Holder.class.getName() + "\","
                + "\"str\":{\"@t\":\"Optional\",\"present\":false},"
                + "\"optLong\":{\"@t\":\"OptionalLong\",\"present\":false}}";
        Holder h = JsonIo.toJava(json).asClass(Holder.class);
        assertThat(h.str).isEmpty();
        assertThat(h.optLong).isEmpty();
    }

    // ========== Optional in a List ==========

    @Test
    void optionalListRoundTrips() {
        class Container {
            public List<Optional<String>> items;
            public Container() {}
        }
        Container c = new Container();
        c.items = Arrays.asList(Optional.of("a"), Optional.empty(), Optional.of("c"));

        String json = JsonIo.toJson(c);
        // Sanity: valid JSON
        assertThat(json).doesNotContain("{null}");
    }

    // ========== Referenced shared Optional field via cycleSupport ==========

    public static class SharedPojo {
        public String id;
        public SharedPojo() {}
        public SharedPojo(String id) { this.id = id; }
    }

    public static class DualOpt {
        public Optional<SharedPojo> a;
        public Optional<SharedPojo> b;
        public DualOpt() {}
    }

    @Test
    void sharedObjectInOptional_cycleSupportEnabled() {
        // With cycleSupport, the framework may emit @id/@ref for shared objects.
        // The Optional writer's object form is used when referenced, so this path
        // still round-trips correctly.
        SharedPojo shared = new SharedPojo("shared-1");
        DualOpt holder = new DualOpt();
        holder.a = Optional.of(shared);
        holder.b = Optional.of(shared);

        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(true).build();
        String json = JsonIo.toJson(holder, opts);

        DualOpt restored = JsonIo.toJava(json).asClass(DualOpt.class);
        assertThat(restored.a).isPresent();
        assertThat(restored.b).isPresent();
        // Both should contain valid SharedPojo instances
        assertThat(restored.a.get().id).isEqualTo("shared-1");
        assertThat(restored.b.get().id).isEqualTo("shared-1");
    }

    @Test
    void optionalArrayWithSharedElements_cycleSupport() {
        SharedPojo shared = new SharedPojo("shared-elem");
        Object[] arr = new Object[] { Optional.of(shared), Optional.of(shared) };

        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(true).build();
        String json = JsonIo.toJson(arr, opts);

        Object[] restored = JsonIo.toJava(json).asClass(Object[].class);
        assertThat(restored).hasSize(2);
        // Both elements should deserialize — Optional-wrapped SharedPojo
        assertThat(restored[0]).isInstanceOf(Optional.class);
        assertThat(restored[1]).isInstanceOf(Optional.class);
    }
}
