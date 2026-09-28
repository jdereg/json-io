package com.cedarsoftware.io;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An EnumMap reads back as an EnumMap. It had no factory: json-io writes its keys in {@code @keys} as enum objects, and
 * the generic instantiation path failed on them ("JsonObject cannot be cast to String"), at the root and in a field,
 * on every JDK -- where Jackson reads it. The enum class comes from the declared type or from the first key.
 */
class EnumMapRoundTripTest {

    public enum Color { RED, GREEN }

    public static class Holder {
        public EnumMap<Color, String> byColor;
        public Map<Color, Integer> declaredAsMap;
    }

    private static EnumMap<Color, String> redAndGreen() {
        EnumMap<Color, String> map = new EnumMap<>(Color.class);
        map.put(Color.RED, "r");
        map.put(Color.GREEN, "g");
        return map;
    }

    @Test
    void atTheRoot() {
        EnumMap<Color, String> map = redAndGreen();
        Object read = JsonIo.toJava(JsonIo.toJson(map, null), null).asClass(EnumMap.class);
        assertThat(read).isInstanceOf(EnumMap.class).isEqualTo(map);

        EnumMap<TimeUnit, String> units = new EnumMap<>(TimeUnit.class);
        units.put(TimeUnit.SECONDS, "s");
        assertThat(JsonIo.toJava(JsonIo.toJson(units, null), null).asClass(EnumMap.class)).isEqualTo(units);
    }

    @Test
    void inFields() {
        Holder holder = new Holder();
        holder.byColor = redAndGreen();
        EnumMap<Color, Integer> counts = new EnumMap<>(Color.class);
        counts.put(Color.GREEN, 7);
        holder.declaredAsMap = counts;

        Holder read = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
        assertThat(read.byColor).isInstanceOf(EnumMap.class).isEqualTo(holder.byColor);
        assertThat(read.declaredAsMap).isInstanceOf(EnumMap.class).isEqualTo(counts);

        Holder empty = new Holder();
        empty.byColor = new EnumMap<>(Color.class);
        assertThat(JsonIo.toJava(JsonIo.toJson(empty, null), null).asClass(Holder.class).byColor)
                .isInstanceOf(EnumMap.class).isEmpty();
    }

    @Test
    void plainStringKeysIntoADeclaredField() {
        // As Jackson writes it, and as json-io writes it with stringifyMapKeys(true)
        String json = "{\"byColor\":{\"RED\":\"r\",\"GREEN\":\"g\"}}";
        Holder read = JsonIo.toJava(json, null).asClass(Holder.class);
        assertThat(read.byColor).isInstanceOf(EnumMap.class).isEqualTo(redAndGreen());

        Holder holder = new Holder();
        holder.byColor = redAndGreen();
        String stringKeys = JsonIo.toJson(holder, new WriteOptionsBuilder().stringifyMapKeys(true).build());
        assertThat(JsonIo.toJava(stringKeys, null).asClass(Holder.class).byColor).isEqualTo(redAndGreen());
    }

    @Test
    void anEmptyEnumMapWithNoDeclaredTypeSaysWhatIsMissing() {
        String json = JsonIo.toJson(new EnumMap<>(Color.class), null);
        assertThatThrownBy(() -> JsonIo.toJava(json, null).asClass(EnumMap.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("its enum class is not known");
    }
}
