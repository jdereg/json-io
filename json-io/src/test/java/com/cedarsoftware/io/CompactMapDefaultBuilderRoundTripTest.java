package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.CompactMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A builder CompactMap in the default configuration -- {@code CompactMap.builder().build()} -- was written as an
 * ordinary object of its generated class, {@code com.cedarsoftware.util.CompactMap$d1b04c6f91811170}, which no reader
 * can load, so the document could not be read back. java-util 4.113.0 no longer calls such a map a default
 * CompactMap, and json-io writes it with its configuration, as it writes every other builder map.
 */
class CompactMapDefaultBuilderRoundTripTest {

    static class Holder {
        Map<String, Object> map;
        Object any;
        List<Object> list;
    }

    private static CompactMap<String, Object> defaultBuilderMap(int entries) {
        CompactMap<String, Object> map = CompactMap.<String, Object>builder().build();
        for (int i = 0; i < entries; i++) {
            map.put("k" + i, "v" + i);
        }
        return map;
    }

    @Test
    void atTheTopLevel() {
        for (int entries : new int[]{0, 1, 3, 60}) {
            CompactMap<String, Object> map = defaultBuilderMap(entries);
            String json = JsonIo.toJson(map, null);
            Object back = JsonIo.toJava(json, null).asClass(Object.class);
            assertTrue(back instanceof CompactMap, json);
            assertEquals(map, back, json);
            assertEquals(map.getConfig(), ((CompactMap<?, ?>) back).getConfig(), json);
        }
    }

    @Test
    void inAFieldAnObjectFieldAndAList() {
        Holder holder = new Holder();
        holder.map = defaultBuilderMap(2);
        holder.any = defaultBuilderMap(1);
        holder.list = new ArrayList<>(Arrays.asList(defaultBuilderMap(3), "text"));
        String json = JsonIo.toJson(holder, null);
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertEquals(holder.map, back.map, json);
        assertEquals(holder.any, back.any, json);
        assertEquals(holder.list, back.list, json);
    }
}
