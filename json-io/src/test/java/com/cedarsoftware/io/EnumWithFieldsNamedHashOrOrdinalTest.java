package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What json-io writes and reads for an enum that declares fields of its own named {@code hash} or {@code ordinal}
 * is what 4.118.0 wrote and read.
 * <p>
 * java-util 4.119.0 lists such fields again in {@code ReflectionUtils.getDeclaredFields()} (it had left them out,
 * meaning to leave out only {@code java.lang.Enum}'s). json-io asks java-util for a class's fields and then applies
 * its own {@code EnumFieldFilter}, which leaves those two names out of every enum, so nothing changes here: this
 * test holds that still. The expected text and values below were produced by the released 4.118.0 jars.
 */
class EnumWithFieldsNamedHashOrOrdinalTest {
    public enum Digest {
        SHA256("e3b0c442", 3), MD5("d41d8cd9", 7);

        private String hash;
        private int ordinal;
        public final String label = "sha";

        Digest(String hash, int ordinal) {
            this.hash = hash;
            this.ordinal = ordinal;
        }
    }

    public static class Holder {
        public Digest digest = Digest.SHA256;
        public Digest[] all = Digest.values();
    }

    private static final String DIGEST = Digest.class.getName();
    private static final String HOLDER = Holder.class.getName();

    @Test
    void writtenAsBefore() {
        assertEquals("{\"@type\":\"" + HOLDER + "\",\"digest\":\"SHA256\",\"all\":[\"SHA256\",\"MD5\"]}",
                JsonIo.toJson(new Holder(), new WriteOptionsBuilder().build()));
        assertEquals("{\"@type\":\"" + DIGEST + "\",\"label\":\"sha\",\"name\":\"SHA256\"}",
                JsonIo.toJson(Digest.SHA256, new WriteOptionsBuilder().writeEnumAsJsonObject(false).build()));
    }

    @Test
    void readAsBefore() {
        ReadOptions options = new ReadOptionsBuilder().build();
        Object one = JsonIo.toJava("{\"@type\":\"" + DIGEST + "\",\"name\":\"MD5\",\"hash\":\"CHANGED\",\"ordinal\":99}",
                options).asClass(Object.class);
        assertSame(Digest.MD5, one);

        Holder holder = JsonIo.toJava("{\"@type\":\"" + HOLDER
                + "\",\"digest\":{\"name\":\"SHA256\",\"hash\":\"CHANGED\",\"ordinal\":5},\"all\":[\"MD5\"]}",
                options).asClass(Holder.class);
        assertSame(Digest.SHA256, holder.digest);
        assertEquals(1, holder.all.length);
        assertSame(Digest.MD5, holder.all[0]);

        // the JSON's "hash" and "ordinal" reached neither constant
        assertEquals("e3b0c442", Digest.SHA256.hash);
        assertEquals(3, Digest.SHA256.ordinal);
        assertEquals("d41d8cd9", Digest.MD5.hash);
        assertEquals(7, Digest.MD5.ordinal);
        assertEquals(0, Digest.SHA256.ordinal());
        assertEquals(1, Digest.MD5.ordinal());
    }
}
