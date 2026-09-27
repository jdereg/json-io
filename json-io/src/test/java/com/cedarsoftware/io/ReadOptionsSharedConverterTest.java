package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every read with one ReadOptions uses one Converter, made from the ReadOptions' conversion settings the first time it
 * is needed. A Converter was made for every read -- an allocation and a copy of the converter overrides each time,
 * though ReadOptions are fixed once built and are meant to be built once and reused.
 */
class ReadOptionsSharedConverterTest {

    public static class Widget {
        public String name;
        public Long count;
    }

    /** Records the Converter each read hands to it, and leaves the fields to the resolver. */
    private static final class RecordingFactory implements ClassFactory {
        final List<Converter> seen = new ArrayList<>();

        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            synchronized (seen) {
                seen.add(resolver.getConverter());
            }
            return new Widget();
        }
    }

    @Test
    void readsWithOneReadOptionsShareAConverterAndOtherReadOptionsHaveTheirOwn() {
        RecordingFactory factory = new RecordingFactory();
        ReadOptions first = new ReadOptionsBuilder().addClassFactory(Widget.class, factory).build();
        ReadOptions second = new ReadOptionsBuilder().addClassFactory(Widget.class, factory).build();

        for (ReadOptions options : new ReadOptions[]{first, first, second, second}) {
            Widget widget = JsonIo.toJava("{\"name\":\"w\",\"count\":\"5\"}", options).asClass(Widget.class);
            assertEquals("w", widget.name);
            assertEquals(Long.valueOf(5), widget.count);
        }
        assertEquals(4, factory.seen.size());
        assertSame(factory.seen.get(0), factory.seen.get(1), "two reads with one ReadOptions");
        assertSame(factory.seen.get(2), factory.seen.get(3), "two reads with one ReadOptions");
        assertNotSame(factory.seen.get(0), factory.seen.get(2), "two ReadOptions");
        assertSame(first.getConverterOptions(), factory.seen.get(0).getOptions());
    }

    @Test
    void readsOnManyThreadsShareItAndConvertCorrectly() throws Exception {
        RecordingFactory factory = new RecordingFactory();
        ReadOptions options = new ReadOptionsBuilder()
                .addClassFactory(Widget.class, factory)
                .addConverterOverride(String.class, Long.class, (from, converter) -> Long.parseLong((String) from) * 10)
                .build();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> reads = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                final int thread = t;
                reads.add(pool.submit(() -> {
                    for (int i = 0; i < 200; i++) {
                        int n = thread * 1000 + i;
                        String json = "{\"name\":\"w" + n + "\",\"count\":\"" + n + "\"}";
                        Widget widget = JsonIo.toJava(json, options).asClass(Widget.class);
                        assertEquals("w" + n, widget.name);
                        assertEquals(Long.valueOf(n * 10L), widget.count, "the override, on every thread");
                    }
                    return null;
                }));
            }
            for (Future<?> read : reads) {
                read.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1600, factory.seen.size());
        Converter shared = factory.seen.get(0);
        assertTrue(factory.seen.stream().allMatch(c -> c == shared), "one Converter for every read");
    }
}
