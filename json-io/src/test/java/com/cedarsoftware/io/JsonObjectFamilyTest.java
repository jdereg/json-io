package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Family-level coverage for the JsonObject hierarchy: lite {@link JsonObject},
 * {@link JsonObjectArray}, and {@link JsonObjectMap}. Pins the contracts that
 * span the family — cross-shape equality, hash consistency, and metadata
 * preservation across the {@code promoteToArray} / {@code promoteToMap} helpers.
 */
class JsonObjectFamilyTest {

    // ========== Cross-shape equality returns false ==========

    @Test
    void liteNotEqualToArray_evenWhenContentMatches() {
        JsonObject lite = new JsonObject();
        JsonObjectArray array = new JsonObjectArray();
        // No state on either side — equality must still be false because the shapes differ.
        assertFalse(lite.equals(array));
        assertFalse(array.equals(lite));
    }

    @Test
    void liteNotEqualToMap_evenWhenContentMatches() {
        JsonObject lite = new JsonObject();
        JsonObjectMap map = new JsonObjectMap();
        assertFalse(lite.equals(map));
        assertFalse(map.equals(lite));
    }

    @Test
    void arrayNotEqualToMap() {
        JsonObjectArray array = new JsonObjectArray();
        array.setItems(new Object[]{"a", "b"});
        JsonObjectMap map = new JsonObjectMap();
        map.setKeys(new Object[]{"a", "b"});
        map.setItems(new Object[]{1, 2});
        assertFalse(array.equals(map));
        assertFalse(map.equals(array));
    }

    // ========== Within-shape equality and hash consistency ==========

    @Test
    void twoArraysWithSameItems_areEqualAndHashEqual() {
        JsonObjectArray a = new JsonObjectArray();
        a.setItems(new Object[]{1, 2, 3});
        JsonObjectArray b = new JsonObjectArray();
        b.setItems(new Object[]{1, 2, 3});
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void twoArraysWithDifferentItems_areNotEqual() {
        JsonObjectArray a = new JsonObjectArray();
        a.setItems(new Object[]{1, 2, 3});
        JsonObjectArray b = new JsonObjectArray();
        b.setItems(new Object[]{1, 2, 4});
        assertNotEquals(a, b);
    }

    @Test
    void twoMapsWithSameKeysAndItems_areEqualAndHashEqual() {
        JsonObjectMap a = new JsonObjectMap();
        a.setKeys(new Object[]{"x", "y"});
        a.setItems(new Object[]{1, 2});
        JsonObjectMap b = new JsonObjectMap();
        b.setKeys(new Object[]{"x", "y"});
        b.setItems(new Object[]{1, 2});
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void twoMapsWithDifferentValues_areNotEqual() {
        JsonObjectMap a = new JsonObjectMap();
        a.setKeys(new Object[]{"x", "y"});
        a.setItems(new Object[]{1, 2});
        JsonObjectMap b = new JsonObjectMap();
        b.setKeys(new Object[]{"x", "y"});
        b.setItems(new Object[]{1, 99});
        assertNotEquals(a, b);
    }

    @Test
    void hashCodeIsCachedAndStableAcrossCalls() {
        JsonObjectArray a = new JsonObjectArray();
        a.setItems(new Object[]{"k", "v"});
        int first = a.hashCode();
        int second = a.hashCode();
        assertEquals(first, second);
    }

    @Test
    void hashCodeInvalidatesAfterMutation() {
        JsonObjectArray a = new JsonObjectArray();
        a.setItems(new Object[]{1, 2});
        int before = a.hashCode();
        a.setItems(new Object[]{1, 2, 3});
        int after = a.hashCode();
        assertNotEquals(before, after);
    }

    // ========== Lite shape: setItems/setKeys must throw, not silently accept ==========

    @Test
    void liteSetItemsThrows() {
        JsonObject lite = new JsonObject();
        JsonIoException ex = org.junit.jupiter.api.Assertions.assertThrows(
                JsonIoException.class,
                () -> lite.setItems(new Object[]{1, 2}));
        assertTrue(ex.getMessage().contains("not supported"));
    }

    @Test
    void liteSetKeysThrows() {
        JsonObject lite = new JsonObject();
        JsonIoException ex = org.junit.jupiter.api.Assertions.assertThrows(
                JsonIoException.class,
                () -> lite.setKeys(new Object[]{"a"}));
        assertTrue(ex.getMessage().contains("not supported"));
    }

    @Test
    void liteGetItemsAndGetKeysReturnNull() {
        JsonObject lite = new JsonObject();
        assertNull(lite.getItems());
        assertNull(lite.getKeys());
    }

    // ========== Promotion helpers preserve metadata + update references ==========

    @Test
    void promoteToArray_copiesMetadataFromLite() {
        JsonObject lite = new JsonObject();
        lite.setId(42L);
        lite.setType(java.util.ArrayList.class);
        lite.setItemElementType(String.class);

        ReferenceTracker refs = new Resolver.DefaultReferenceTracker(new ReadOptionsBuilder().build());
        refs.put(42L, lite);

        JsonObject promoted = JsonObject.promoteToArray(lite, refs);

        assertTrue(promoted instanceof JsonObjectArray);
        assertEquals(42L, promoted.getId());
        assertEquals(java.util.ArrayList.class, promoted.getType());
        assertEquals(String.class, promoted.getItemElementType());
        // Reference table now points at the promoted instance, not the discarded lite shell.
        assertSame(promoted, refs.get(42L));
    }

    @Test
    void promoteToArray_isNoOpWhenAlreadyArray() {
        JsonObjectArray array = new JsonObjectArray();
        JsonObject result = JsonObject.promoteToArray(array, null);
        assertSame(array, result);
    }

    @Test
    void promoteToArray_isNoOpWhenAlreadyMap() {
        // JsonObjectMap stores @items natively (as the values half of the pair).
        // Promoting must not clobber its keysRef/valuesRef state.
        JsonObjectMap map = new JsonObjectMap();
        map.setKeys(new Object[]{"k"});
        map.setItems(new Object[]{"v"});
        JsonObject result = JsonObject.promoteToArray(map, null);
        assertSame(map, result);
        assertEquals(1, result.size());
    }

    @Test
    void promoteToMap_copiesMetadataFromLite() {
        JsonObject lite = new JsonObject();
        lite.setId(7L);
        lite.setType(java.util.HashMap.class);
        lite.setMapKeyType(Long.class);

        ReferenceTracker refs = new Resolver.DefaultReferenceTracker(new ReadOptionsBuilder().build());
        refs.put(7L, lite);

        JsonObject promoted = JsonObject.promoteToMap(lite, refs);

        assertTrue(promoted instanceof JsonObjectMap);
        assertEquals(7L, promoted.getId());
        assertEquals(java.util.HashMap.class, promoted.getType());
        assertEquals(Long.class, promoted.getMapKeyType());
        assertSame(promoted, refs.get(7L));
    }

    @Test
    void promoteToMap_isNoOpWhenAlreadyMap() {
        JsonObjectMap map = new JsonObjectMap();
        JsonObject result = JsonObject.promoteToMap(map, null);
        assertSame(map, result);
    }

    @Test
    void promoteWithoutReferences_doesNotThrow() {
        JsonObject lite = new JsonObject();
        lite.setId(99L);
        // Null references map is the in-flight parser case for objects with no @id seen yet.
        JsonObject promoted = JsonObject.promoteToArray(lite, null);
        assertTrue(promoted instanceof JsonObjectArray);
        assertEquals(99L, promoted.getId());
    }

    // ========== Equality contract: reflexive / null-safe / type-safe ==========

    @Test
    void equalsIsReflexive() {
        JsonObject lite = new JsonObject();
        lite.put("k", "v");
        JsonObjectArray array = new JsonObjectArray();
        array.setItems(new Object[]{"a"});
        JsonObjectMap map = new JsonObjectMap();
        map.setKeys(new Object[]{"k"});
        map.setItems(new Object[]{"v"});

        assertEquals(lite, lite);
        assertEquals(array, array);
        assertEquals(map, map);
    }

    @Test
    void equalsRejectsNullAndForeignTypes() {
        JsonObjectArray array = new JsonObjectArray();
        array.setItems(new Object[]{"a"});
        assertFalse(array.equals(null));
        assertFalse(array.equals("not a JsonObject"));
    }
}
