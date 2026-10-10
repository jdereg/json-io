package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

import com.cedarsoftware.util.LRUCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upgrade note at the top of 4.113.0 in changelog.md -- "what can change a result" -- one test per row, so the
 * note says what the code does. Each row's example is the one the note gives; 4.112.0 answered every one of them
 * differently (the note's "4.112.0" column).
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
class UpgradeTo4113NotesTest {

    public static class Item {
        public String name;
    }

    public static class Team {
        public List<Item> members;
    }

    public static class Bytes {
        public byte[] data;
    }

    public static class Fields {
        public long count;
        public UUID id;
        public TimeUnit unit;
        public Number amount;
    }

    private static Team team() {
        Team team = new Team();
        Item item = new Item();
        item.name = "a";
        team.members = new ArrayList<>(Arrays.asList(item));
        return team;
    }

    private static <T> T jsonRoundTrip(T value) {
        @SuppressWarnings("unchecked")
        T back = (T) JsonIo.toJava(JsonIo.toJson(value, null), null).asClass(value.getClass());
        return back;
    }

    @Test
    void theDefaultTypeInfoLeavesOutTheClassTheReaderBuilds() {
        String json = JsonIo.toJson(team(), null);
        assertFalse(json.contains("ArrayList"), json);
        assertTrue(JsonIo.toJson(team(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build())
                .contains("ArrayList"));
        assertEquals("a", JsonIo.toJava(json, null).asClass(Team.class).members.get(0).name);
    }

    @Test
    void aJsonStringInAByteArrayIsBase64OnlyWhenPaddedCorrectly() {
        assertEquals(6, JsonIo.toJava("{\"data\":\"CAFEBABE\"}", null).asClass(Bytes.class).data.length);
        assertArrayEquals("AF".getBytes(StandardCharsets.UTF_8),
                JsonIo.toJava("{\"data\":\"AF\"}", null).asClass(Bytes.class).data);
    }

    @Test
    void theGenericTypeAskedForAtTheRootIsBuilt() {
        List<Item> items = JsonIo.toJava("[{\"name\":\"a\"}]", null).asType(new TypeHolder<List<Item>>() { });
        assertEquals(Item.class, items.get(0).getClass());
    }

    @Test
    void toonWritesAWholeDoubleAsTheDecimalItPrints() {
        String toon = JsonIo.toToon(new double[] {9.223372036854776E18}, null);
        assertFalse(toon.contains("9223372036854775807"), toon);     // 2^63 - 1, a long's limit
        assertTrue(toon.contains("9223372036854776000"), toon);      // 9.223372036854776E18, as the double prints
    }

    @Test
    void aPatternKeepsItsFlags() {
        Pattern back = jsonRoundTrip(Pattern.compile("abc", Pattern.CASE_INSENSITIVE));
        assertTrue(back.matcher("ABC").matches());
    }

    @Test
    void aBigDecimalWithAHugeExponentIsWrittenWithItsExponent() {
        assertTrue(JsonIo.toJson(new BigDecimal("1E+100000"), null).length() < 50);
    }

    @Test
    void aJsonNumberTooLargeForItsFieldIsRefused() {
        assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"count\":99999999999999999999}", null).asClass(Fields.class));
    }

    @Test
    void blankTextIsNullForAUuidOrAnEnum() {
        Fields back = JsonIo.toJava("{\"id\":\"\",\"unit\":\"\"}", null).asClass(Fields.class);
        assertNull(back.id);
        assertNull(back.unit);
    }

    @Test
    void aNumberFieldTakesNumberText() {
        assertEquals(42L, JsonIo.toJava("{\"amount\":\"42\"}", null).asClass(Fields.class).amount);
    }

    @Test
    void moreJdkTypesReadBack() {
        AtomicReferenceArray<String> atomic = new AtomicReferenceArray<>(new String[] {"x", "y"});
        assertEquals("y", jsonRoundTrip(atomic).get(1));
        EnumMap<TimeUnit, String> enumMap = new EnumMap<>(TimeUnit.class);
        enumMap.put(TimeUnit.SECONDS, "s");
        assertEquals(enumMap, jsonRoundTrip(enumMap));
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(4);
        queue.add("q");
        assertEquals("q", jsonRoundTrip(queue).peek());
        LongAdder adder = new LongAdder();
        adder.add(7);
        assertEquals(7, jsonRoundTrip(adder).sum());
    }

    @Test
    void anInetAddressReadsBack() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        assertEquals(loopback, jsonRoundTrip(loopback));
    }

    @Test
    void anLruCacheReadsBackWithItsEntries() {
        LRUCache<String, Integer> cache = new LRUCache<>(10);
        cache.put("a", 1);
        LRUCache<?, ?> back = jsonRoundTrip(cache);
        assertEquals(1, back.get("a"));
    }

    @Test
    void anEmptyOptionalAtTheRootOfToonReadsBackEmpty() {
        assertEquals(Optional.empty(), JsonIo.fromToon(JsonIo.toToon(Optional.empty(), null), null)
                .asClass(Optional.class));
    }

    @Test
    void aSharedOptionalInPlainToonReadsBackShared() {
        Optional<List<String>> shared = Optional.of(new ArrayList<>(Arrays.asList("a")));
        // The builder's defaults: no type info, cycle support on (toToon(x, null) writes no references at all)
        List<?> back = JsonIo.fromToon(JsonIo.toToon(Arrays.asList(shared, shared),
                new WriteOptionsBuilder().build()), null).asClass(List.class);
        assertSame(back.get(0), back.get(1));
    }

    public static class SharedMap {
        public java.util.Map<String, String> a;
        public Object b;
    }

    @Test
    void aMapSharedWithALeafDeclaredFieldIsWrittenAgainInToon() {
        SharedMap shared = new SharedMap();
        shared.a = new java.util.LinkedHashMap<>();
        shared.a.put("k", "v");
        shared.b = shared.a;
        String toon = JsonIo.toToon(shared, new WriteOptionsBuilder().build());
        assertFalse(toon.contains("$ref"), toon);
        assertEquals(shared.a, JsonIo.fromToon(toon, null).asClass(SharedMap.class).b);
    }

    /** The class factory nothing reached is gone; a BitSet still reads, as do the older index form's lists. */
    @Test
    void bitSetFactoryIsRemovedAndABitSetStillReads() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.cedarsoftware.io.factory.BitSetFactory"));
        BitSet bits = new BitSet();
        bits.set(1);
        bits.set(3);
        assertEquals(bits, jsonRoundTrip(bits));
        assertEquals(bits, JsonIo.toJava("{\"@type\":\"java.util.BitSet\",\"value\":[1,3]}", null)
                .asClass(BitSet.class));
    }
}
