package com.cedarsoftware.io;

import java.util.BitSet;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BitSet is read from its binary text, 0s and 1s only, as java-util's Converter reads it: in its typed form
 * {"@type":"BitSet","value":"101010"} and as a bare string in a BitSet field.
 */
class BitSetReadTest {

    public static class Holder {
        public BitSet flags;
    }

    private static BitSet bits(int... indexes) {
        BitSet bitSet = new BitSet();
        for (int index : indexes) {
            bitSet.set(index);
        }
        return bitSet;
    }

    /**
     * Any character but 1 counted as 0: "10x1" read as {0, 3} without a word, typed or bare. It is now an error that
     * names the character and its index, wherever the BitSet sits (java-util 4.113.0's String to BitSet).
     */
    @Test
    void onlyZerosAndOnesAreRead() {
        JsonIoException typed = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"@type\":\"BitSet\",\"value\":\"10x1\"}", null).asClass(BitSet.class));
        assertTrue(typed.getMessage().contains("index 2"), typed.getMessage());

        JsonIoException field = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"flags\":\"10x1\"}", null).asClass(Holder.class));
        assertTrue(field.getMessage().contains("index 2"), field.getMessage());
    }

    @Test
    void theTypedFormAndABareStringRead() {
        assertEquals(bits(1, 3, 5), JsonIo.toJava("{\"@type\":\"BitSet\",\"value\":\"101010\"}", null)
                .asClass(BitSet.class));
        assertEquals(bits(), JsonIo.toJava("{\"@type\":\"BitSet\",\"value\":\"\"}", null).asClass(BitSet.class));
        assertEquals(bits(1, 3, 5), JsonIo.toJava("{\"flags\":\"101010\"}", null).asClass(Holder.class).flags);
    }

    /** Random BitSets, in a field and at the root, come back equal. */
    @Test
    void bitSetsRoundTrip() {
        Random random = new Random(3);
        for (int k = 0; k < 200; k++) {
            BitSet bitSet = new BitSet();
            int count = random.nextInt(20);
            for (int i = 0; i < count; i++) {
                bitSet.set(random.nextInt(200));
            }
            Holder holder = new Holder();
            holder.flags = bitSet;
            assertEquals(bitSet, JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class).flags);
            assertEquals(bitSet, JsonIo.toJava(JsonIo.toJson(bitSet, null), null).asClass(BitSet.class));
        }
    }
}
