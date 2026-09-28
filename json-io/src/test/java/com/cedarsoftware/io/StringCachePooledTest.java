package com.cedarsoftware.io;

import java.lang.management.ManagementFactory;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The JSON tokenizer's string cache (2,048 slots, 8 KB) is lent per thread by JsonIo's buffer recycler instead of
 * allocated for every parse: allocating and zeroing it was most of the cost of reading a small document (a two-field
 * map took ~600 ns and 10.7 KB; now ~270 ns and 2.4 KB). A lent cache still holds strings from earlier documents, and
 * a hit is always checked against the text, so they never stand in for a different string; a read within a read
 * gets a cache of its own.
 */
class StringCachePooledTest {

    public static class Named {
        public String name;
    }

    @Test
    void aSmallReadNoLongerAllocatesAStringCache() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean, "per-thread allocation counts unavailable");
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());

        ReadOptions options = new ReadOptionsBuilder().build();
        String json = "{\"a\":1,\"b\":\"x\"}";
        for (int i = 0; i < 500; i++) {
            JsonIo.toJava(json, options).asClass(Map.class);
        }
        long thread = Thread.currentThread().getId();
        int reads = 2000;
        long before = threads.getThreadAllocatedBytes(thread);
        for (int i = 0; i < reads; i++) {
            JsonIo.toJava(json, options).asClass(Map.class);
        }
        long perRead = (threads.getThreadAllocatedBytes(thread) - before) / reads;
        // 10.7 KB per read when each parse made its own 8 KB cache; about 2.4 KB now
        assertThat(perRead).as("bytes allocated per small read").isLessThan(6000);
    }

    @Test
    void stringsFromEarlierDocumentsNeverStandInForOthers() {
        // Same length, first, middle and last characters: the same cache slot
        String first = "abcde";
        String second = "azcye";
        for (int i = 0; i < 3; i++) {
            assertThat(JsonIo.toJava("{\"name\":\"" + first + "\"}", null).asClass(Named.class).name).isEqualTo(first);
            assertThat(JsonIo.toJava("{\"name\":\"" + second + "\"}", null).asClass(Named.class).name).isEqualTo(second);
            Map<?, ?> map = JsonIo.toJava("{\"" + second + "\":\"" + first + "\"}", null).asClass(Map.class);
            assertThat(map.get(second)).isEqualTo(first);
        }
    }

    @Test
    void aReadWithinAReadHasItsOwnCache() {
        ClassFactory readsAgain = new ClassFactory() {
            @Override
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
                Named inner = JsonIo.toJava("{\"name\":\"inner-abcde\"}", null).asClass(Named.class);
                Named made = new Named();
                made.name = inner.name + "+";
                return made;
            }

            @Override
            public boolean isObjectFinal() {
                return true;
            }
        };
        ReadOptions options = new ReadOptionsBuilder().addClassFactory(Named.class, readsAgain).build();
        Map<?, ?> outer = JsonIo.toJava("{\"before\":\"azcye\",\"it\":{\"@type\":\"" + Named.class.getName()
                + "\",\"name\":\"x\"},\"after\":\"abcde\"}", options).asClass(Map.class);
        assertThat(outer.get("before")).isEqualTo("azcye");
        assertThat(((Named) outer.get("it")).name).isEqualTo("inner-abcde+");
        assertThat(outer.get("after")).isEqualTo("abcde");

        // Both caches were returned: the next read on this thread is ordinary
        assertThat(JsonIo.toJava("{\"name\":\"abcde\"}", null).asClass(Named.class).name).isEqualTo("abcde");
    }
}
