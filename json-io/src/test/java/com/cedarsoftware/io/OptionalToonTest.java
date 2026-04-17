package com.cedarsoftware.io;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify TOON round-trip for all Optional types. Prior to the fix, TOON
 * serialized Optional/OptionalInt/Long/Double as broken POJOs with null
 * values. The custom writers (which ToonWriter consults through the same
 * WriteOptions) now provide primitive-form output which TOON respects.
 */
class OptionalToonTest {

    public static class Holder {
        public Optional<String> str;
        public OptionalInt optInt;
        public OptionalLong optLong;
        public OptionalDouble optDouble;

        public Holder() {}
    }

    @Test
    void toonHolderWithAllPresentOptionals_roundTrips() {
        Holder h = new Holder();
        h.str = Optional.of("hello");
        h.optInt = OptionalInt.of(42);
        h.optLong = OptionalLong.of(99L);
        h.optDouble = OptionalDouble.of(2.5);

        String toon = JsonIo.toToon(h);
        // Must not contain garbage output
        assertThat(toon).doesNotContain("isPresent: null");
        assertThat(toon).doesNotContain("value: null");

        Holder restored = JsonIo.fromToon(toon).asClass(Holder.class);
        assertThat(restored.str).isPresent().contains("hello");
        assertThat(restored.optInt).isPresent();
        assertThat(restored.optInt.getAsInt()).isEqualTo(42);
        assertThat(restored.optLong).isPresent();
        assertThat(restored.optLong.getAsLong()).isEqualTo(99L);
        assertThat(restored.optDouble).isPresent();
        assertThat(restored.optDouble.getAsDouble()).isEqualTo(2.5);
    }

    @Test
    void toonHolderWithAllEmptyOptionals_roundTrips() {
        Holder h = new Holder();
        h.str = Optional.empty();
        h.optInt = OptionalInt.empty();
        h.optLong = OptionalLong.empty();
        h.optDouble = OptionalDouble.empty();

        String toon = JsonIo.toToon(h);
        Holder restored = JsonIo.fromToon(toon).asClass(Holder.class);
        assertThat(restored.str).isEmpty();
        assertThat(restored.optInt).isEmpty();
        assertThat(restored.optLong).isEmpty();
        assertThat(restored.optDouble).isEmpty();
    }

    @Test
    void toonTopLevelOptionalStringPresent() {
        String toon = JsonIo.toToon(Optional.of("hello"));
        @SuppressWarnings("unchecked")
        Optional<String> restored = JsonIo.fromToon(toon).asClass(Optional.class);
        assertThat(restored).isPresent().contains("hello");
    }

    @Test
    void toonTopLevelOptionalIntPresent() {
        String toon = JsonIo.toToon(OptionalInt.of(42));
        OptionalInt restored = JsonIo.fromToon(toon).asClass(OptionalInt.class);
        assertThat(restored).isPresent();
        assertThat(restored.getAsInt()).isEqualTo(42);
    }

    @Test
    void toonTopLevelOptionalLongPresent() {
        String toon = JsonIo.toToon(OptionalLong.of(123456789L));
        OptionalLong restored = JsonIo.fromToon(toon).asClass(OptionalLong.class);
        assertThat(restored).isPresent();
        assertThat(restored.getAsLong()).isEqualTo(123456789L);
    }

    @Test
    void toonTopLevelOptionalDoublePresent() {
        String toon = JsonIo.toToon(OptionalDouble.of(3.14));
        OptionalDouble restored = JsonIo.fromToon(toon).asClass(OptionalDouble.class);
        assertThat(restored).isPresent();
        assertThat(restored.getAsDouble()).isEqualTo(3.14);
    }

    @Test
    void toonMixedOptionals() {
        Holder h = new Holder();
        h.str = Optional.of("present");
        h.optInt = OptionalInt.empty();
        h.optLong = OptionalLong.of(500L);
        h.optDouble = OptionalDouble.empty();

        String toon = JsonIo.toToon(h);
        Holder restored = JsonIo.fromToon(toon).asClass(Holder.class);

        assertThat(restored.str).isPresent().contains("present");
        assertThat(restored.optInt).isEmpty();
        assertThat(restored.optLong).isPresent();
        assertThat(restored.optLong.getAsLong()).isEqualTo(500L);
        assertThat(restored.optDouble).isEmpty();
    }
}
