package com.cedarsoftware.io;

import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link CharSegmentWriter} — the segmented char[] Writer behind
 * JsonIo.toJson / JsonIo.toToon String materialization. Exercises every write
 * variant, segment-rollover boundaries (first segment is 8K chars), and
 * non-Latin-1 content (the case where the prior StringBuilder-backed writer
 * paid a full-buffer UTF-16 inflation mid-write).
 */
class CharSegmentWriterTest {

    @Test
    void singleCharWrites() {
        CharSegmentWriter w = new CharSegmentWriter();
        w.write('a');
        w.write('b');
        w.write('"');
        assertEquals("ab\"", w.toString());
    }

    @Test
    void stringWrites() {
        CharSegmentWriter w = new CharSegmentWriter();
        w.write("hello");
        w.write(", ");
        w.write("world");
        assertEquals("hello, world", w.toString());
    }

    @Test
    void stringSliceWrites() {
        CharSegmentWriter w = new CharSegmentWriter();
        w.write("XXhelloYY", 2, 5);
        assertEquals("hello", w.toString());
    }

    @Test
    void charArrayWrites() {
        CharSegmentWriter w = new CharSegmentWriter();
        char[] chars = "abcdef".toCharArray();
        w.write(chars, 0, 6);
        w.write(chars, 2, 3);
        assertEquals("abcdefcde", w.toString());
    }

    @Test
    void appendVariants() {
        CharSegmentWriter w = new CharSegmentWriter();
        assertSame(w, w.append("abc"));
        assertSame(w, w.append('d'));
        assertSame(w, w.append("XefY", 1, 3));
        assertSame(w, w.append(null));
        assertEquals("abcdefnull", w.toString());
    }

    @Test
    void emptyWriterYieldsEmptyString() {
        CharSegmentWriter w = new CharSegmentWriter();
        assertEquals("", w.toString());
        w.write("");
        w.write(new char[0], 0, 0);
        assertEquals("", w.toString());
    }

    @Test
    void nonLatin1ContentRoundTrips() {
        CharSegmentWriter w = new CharSegmentWriter();
        String mixed = "ascii-prefix éñ 中文 😀 ascii-suffix";
        w.write(mixed);
        w.write(" more ascii");
        assertEquals(mixed + " more ascii", w.toString());
    }

    @Test
    void exactSegmentBoundarySingleChars() {
        // First segment is 8192 chars — fill it exactly, then one more char.
        CharSegmentWriter w = new CharSegmentWriter();
        StringBuilder expected = new StringBuilder(8193);
        for (int i = 0; i < 8192; i++) {
            char c = (char) ('a' + (i % 26));
            w.write(c);
            expected.append(c);
        }
        assertEquals(expected.toString(), w.toString());   // exactly full, no rollover output loss
        w.write('!');
        expected.append('!');
        assertEquals(expected.toString(), w.toString());   // first char of second segment
    }

    @Test
    void largeStringWriteSpansSegments() {
        // One single write() larger than several segments (8K + 16K + 32K = 56K chars in
        // the first three) forces the split path repeatedly within a single call.
        CharSegmentWriter w = new CharSegmentWriter();
        StringBuilder big = new StringBuilder(100_000);
        for (int i = 0; i < 10_000; i++) {
            big.append("0123456789");
        }
        String bigStr = big.toString();
        w.write(bigStr);
        assertEquals(bigStr, w.toString());
    }

    @Test
    void largeCharArrayWriteSpansSegments() {
        CharSegmentWriter w = new CharSegmentWriter();
        char[] big = new char[70_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (char) ('0' + (i % 10));
        }
        w.write(big, 0, big.length);
        assertEquals(new String(big), w.toString());
    }

    @Test
    void mixedWritesAcrossManySegmentsMatchStringBuilder() {
        // Randomized differential test: identical operation sequence applied to a
        // StringBuilder reference and the segmented writer must produce identical output.
        Random r = new Random(42);
        CharSegmentWriter w = new CharSegmentWriter();
        StringBuilder expected = new StringBuilder(300_000);
        String[] samples = {"x", "key\":", "{\"a\":1,\"b\":[2,3]}", "中文", "0123456789abcdef"};
        char[] arr = "segmented-writer-differential".toCharArray();
        for (int i = 0; i < 50_000; i++) {
            switch (r.nextInt(4)) {
                case 0:
                    char c = (char) (32 + r.nextInt(90));
                    w.write(c);
                    expected.append(c);
                    break;
                case 1:
                    String s = samples[r.nextInt(samples.length)];
                    w.write(s);
                    expected.append(s);
                    break;
                case 2:
                    int off = r.nextInt(arr.length);
                    int len = r.nextInt(arr.length - off);
                    w.write(arr, off, len);
                    expected.append(arr, off, len);
                    break;
                default:
                    String s2 = samples[r.nextInt(samples.length)];
                    int start = r.nextInt(s2.length());
                    int sliceLen = r.nextInt(s2.length() - start);
                    w.write(s2, start, sliceLen);
                    expected.append(s2, start, start + sliceLen);
                    break;
            }
        }
        assertEquals(expected.toString(), w.toString());
    }

    @Test
    void toJsonStillCorrectThroughSegmentedWriter() {
        // End-to-end sanity: a payload large enough to span segments, with non-ASCII
        // content, round-trips through JsonIo.toJson -> toJava unchanged.
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        StringBuilder filler = new StringBuilder();
        for (int i = 0; i < 2_000; i++) {
            filler.append("value-").append(i).append(';');
        }
        map.put("big", filler.toString());
        map.put("unicode", "café 中文 😀");
        map.put("count", 42L);

        String json = JsonIo.toJson(map, null);
        java.util.Map<String, Object> back = JsonIo.toJava(json, null).asType(new TypeHolder<java.util.Map<String, Object>>() {});
        assertEquals(map.get("big"), back.get("big"));
        assertEquals(map.get("unicode"), back.get("unicode"));
        assertEquals(map.get("count"), back.get("count"));
    }
}
