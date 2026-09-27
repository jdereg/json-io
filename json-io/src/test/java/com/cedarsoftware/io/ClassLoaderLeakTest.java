package com.cedarsoftware.io;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing and reading an object must not keep its class -- or the class's loader -- alive, not even through the
 * default ReadOptions and WriteOptions, which live for the life of the JVM. What json-io works out about a class
 * (writers, readers, class factories, field plans, injectors, annotation metadata) is kept on the class itself (a
 * ClassValue) and goes away with it. A class from a discarded loader -- a Groovy script n-cube recompiled, a plugin, a
 * redeployed webapp -- would otherwise stay in memory, with its loader and every class that loader loaded. (The
 * default ReadOptions' class-factory cache was a ConcurrentHashMap, which held every class it saw.)
 */
class ClassLoaderLeakTest {

    private static final String FIXTURE = "com.cedarsoftware.io.ClassLoaderLeakTest$Fixture";

    /** Loaded in a throwaway ClassLoader whose parent is the bootstrap loader: may depend only on java.base. */
    public static class Fixture {
        public String name;
        public int count;
        public java.util.List<String> tags;

        public Fixture() {
        }
    }

    @Test
    void writingAndReadingAClassDoesNotKeepItsLoaderAlive() throws Exception {
        WeakReference<ClassLoader> loader = writeAndReadAFixture();
        for (int i = 0; i < 100 && loader.get() != null; i++) {
            System.gc();
            byte[][] pressure = new byte[64][];
            for (int j = 0; j < pressure.length; j++) {
                pressure[j] = new byte[64 * 1024];
            }
            Thread.sleep(10);
        }
        assertNull(loader.get(), "writing or reading kept the class loader alive");
    }

    private static WeakReference<ClassLoader> writeAndReadAFixture() throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{new File("target/test-classes").toURI().toURL()}, null);
        Class<?> fixture = Class.forName(FIXTURE, true, loader);
        assertNotSame(Fixture.class, fixture, "the fixture must be defined by the throwaway loader");

        Object written = fixture.getConstructor().newInstance();
        fixture.getField("name").set(written, "n");
        fixture.getField("count").set(written, 3);
        fixture.getField("tags").set(written, new java.util.ArrayList<>(java.util.Arrays.asList("a", "b")));

        String json = JsonIo.toJson(written, null);                     // the default WriteOptions
        assertTrue(json.contains("\"name\":\"n\""), json);
        String untyped = JsonIo.toJson(written, new WriteOptionsBuilder().showTypeInfoNever().build());
        Object read = JsonIo.toJava(untyped, null).asClass(fixture);     // the default ReadOptions
        assertEquals(fixture, read.getClass());
        assertEquals("n", fixture.getField("name").get(read));
        assertEquals(3, fixture.getField("count").get(read));

        loader.close();
        return new WeakReference<>(loader);
    }
}
