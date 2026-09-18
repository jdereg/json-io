package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A field declared {@code Map<String, Object>} may legally hold a null key -- {@code map.put(null, v)}
 * compiles and runs.  The declared generic type is therefore not proof that every runtime key is a
 * non-null String, and the writer must not treat it as proof.
 */
class NullMapKeyDeclaredTypeTest {
    static class PartyHolder {
        Map<String, Object> parties = new LinkedHashMap<>();
    }

    static class IntKeyHolder {
        Map<Integer, Object> byId = new LinkedHashMap<>();
    }

    enum Role { INSURED, BROKER }

    @Test
    void nullKeyInFieldDeclaredWithStringKeysRoundTrips() {
        PartyHolder holder = new PartyHolder();
        holder.parties.put("insured", "P1");
        holder.parties.put(null, "P2");          // an unmapped role -- legal Java

        String json = TestUtil.toJson(holder);

        PartyHolder back = TestUtil.toJava(json, null).asClass(PartyHolder.class);
        assertThat(back.parties).hasSize(2);
        assertThat(back.parties.get("insured")).isEqualTo("P1");
        assertThat(back.parties.get(null)).isEqualTo("P2");
    }

    @Test
    void bareMapWithNullKeyRoundTrips() {
        // Control: no declaring field, so the writer has always scanned the real keys here.
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("insured", "P1");
        map.put(null, "P2");

        String json = TestUtil.toJson(map);

        Map<String, Object> back = TestUtil.toJava(json, null).asClass(Map.class);
        assertThat(back.get(null)).isEqualTo("P2");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void nonStringKeyInFieldDeclaredWithStringKeysRoundTrips() {
        // The other half of the same unsoundness, and the one that forced Dynamis's RpmUtil.clone()
        // to pass forceMapOutputAsTwoArrays: a raw reference can put a key of any type into a
        // Map<String, Object>, so the declared type is no proof of the key's runtime class either.
        PartyHolder holder = new PartyHolder();
        ((Map) holder.parties).put(Role.INSURED, "P1");

        String json = TestUtil.toJson(holder);

        PartyHolder back = TestUtil.toJava(json, null).asClass(PartyHolder.class);
        assertThat(back.parties).hasSize(1);
        assertThat(back.parties.get(Role.INSURED)).isEqualTo("P1");
    }

    @Test
    void nullKeyInFieldDeclaredWithStringifiableKeysRoundTrips() {
        // Same unsound shortcut in canStringifyMapKeys: a declared key type that Converter can
        // stringify skips the per-key null check its own javadoc promises.
        IntKeyHolder holder = new IntKeyHolder();
        holder.byId.put(7, "seven");
        holder.byId.put(null, "unknown");

        String json = TestUtil.toJson(holder, new WriteOptionsBuilder().stringifyMapKeys(true).build());

        IntKeyHolder back = TestUtil.toJava(json, null).asClass(IntKeyHolder.class);
        assertThat(back.byId).hasSize(2);
        assertThat(back.byId.get(7)).isEqualTo("seven");
        assertThat(back.byId.get(null)).isEqualTo("unknown");
    }
}
