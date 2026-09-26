package com.cedarsoftware.io;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonObject's views follow the Map contract, as any Map's do:
 * <ul>
 *   <li>{@code values()} is a Collection, not a Set -- values repeat -- so, like {@code HashMap.values()}, it is not
 *       equal to a Set that holds the same values. (It extended AbstractSet, and was.)</li>
 *   <li>An entry of a complex-keyed map ({@code @keys}/{@code @items}) writes through: its {@code setValue()} changed
 *       a detached copy and left the map as it was.</li>
 * </ul>
 */
class JsonObjectViewsTest {

    private static JsonObject lite() {
        JsonObject jo = new JsonObject();
        jo.put("a", 1);
        jo.put("b", 2);
        jo.put("c", 1);
        return jo;
    }

    /** A complex-keyed map: keys that are not Strings, stored as @keys/@items. */
    private static JsonObject complexKeyed() {
        JsonObjectMap m = new JsonObjectMap();
        m.setKeys(new Object[]{Arrays.asList(1, 2), Arrays.asList(3)});
        m.setItems(new Object[]{"x", "y"});
        return m;
    }

    @Test
    void valuesIsACollectionThatMayRepeat() {
        for (JsonObject jo : new JsonObject[]{lite(), complexKeyed()}) {
            Collection<Object> values = jo.values();
            assertFalse(values instanceof Set, jo.getClass().getSimpleName() + ": values repeat, so they are not a Set");
        }
        Collection<Object> values = lite().values();
        assertEquals(3, values.size());
        assertEquals(Arrays.asList(1, 2, 1), new ArrayList<>(values));
        assertTrue(values.contains(2));
    }

    @Test
    void valuesCompareAsAHashMapsValuesDo() {
        JsonObject jo = new JsonObject();
        jo.put("a", 1);
        jo.put("b", 2);
        Map<String, Object> hm = new HashMap<>();
        hm.put("a", 1);
        hm.put("b", 2);
        Set<Object> sameValues = new HashSet<>(Arrays.asList(1, 2));
        assertEquals(sameValues.equals(hm.values()), sameValues.equals(jo.values()));
        assertNotEquals(sameValues, jo.values());
    }

    @Test
    void aComplexKeyedEntryWritesThrough() {
        JsonObject m = complexKeyed();
        m.hashCode();                               // cached
        Iterator<Map.Entry<Object, Object>> it = m.entrySet().iterator();
        Map.Entry<Object, Object> first = it.next();
        it.next();
        assertEquals("x", first.setValue("changed"));
        assertEquals("changed", m.get(Arrays.asList(1, 2)));
        assertEquals("y", m.get(Arrays.asList(3)));
        assertEquals("changed", first.getValue());

        JsonObject same = complexKeyed();
        for (Map.Entry<Object, Object> e : same.entrySet()) {
            if (e.getKey().equals(Arrays.asList(1, 2))) {
                e.setValue("changed");
            }
        }
        assertEquals(same.hashCode(), m.hashCode(), "the cached hash must follow a write through an entry");
    }

    @Test
    void complexKeyedEntriesFollowTheMapEntryContract() {
        JsonObject m = complexKeyed();
        List<Map.Entry<Object, Object>> kept = new ArrayList<>(m.entrySet());
        assertNotSame(kept.get(0), kept.get(1));
        Map.Entry<Object, Object> expected = new AbstractMap.SimpleEntry<>(Arrays.asList(1, 2), "x");
        assertEquals(expected, kept.get(0));
        assertEquals(kept.get(0), expected);
        assertEquals(expected.hashCode(), kept.get(0).hashCode());
        assertEquals("[1, 2]=x", kept.get(0).toString());
    }
}
