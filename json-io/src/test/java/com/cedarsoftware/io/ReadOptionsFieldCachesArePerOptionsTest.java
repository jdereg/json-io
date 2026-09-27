package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A ReadOptions' field settings apply to that ReadOptions only. The fields and injectors of a class were cached in
 * static caches shared by every ReadOptions, though they are worked out from one ReadOptions' settings (fields not
 * imported, excluded fields, field filters, injector factories): whichever ReadOptions read a class first decided its
 * fields for all of them. A field not imported by one ReadOptions was then missing when any other ReadOptions read the
 * class, including ReadOptions built later (build() cleared the injectors, never the fields).
 */
class ReadOptionsFieldCachesArePerOptionsTest {

    public static class ReadFirstBySkipper {
        public String a;
        public String b;
    }

    public static class ReadFirstByPlain {
        public String a;
        public String b;
    }

    private static String json(Class<?> c) {
        return "{\"@type\":\"" + c.getName() + "\",\"a\":\"A\",\"b\":\"B\"}";
    }

    @Test
    void aFieldOneReadOptionsDoesNotImportIsStillReadByOthers() {
        ReadOptions skipsB = new ReadOptionsBuilder().addNotImportedField(ReadFirstBySkipper.class, "b").build();
        ReadOptions plain = new ReadOptionsBuilder().build();
        String json = json(ReadFirstBySkipper.class);

        ReadFirstBySkipper skipped = JsonIo.toJava(json, skipsB).asClass(ReadFirstBySkipper.class);
        assertEquals("A", skipped.a);
        assertNull(skipped.b);

        assertEquals("B", JsonIo.toJava(json, plain).asClass(ReadFirstBySkipper.class).b, "plain options, read after");
        assertEquals("B", JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(ReadFirstBySkipper.class).b,
                "options built afterwards");
        assertNull(JsonIo.toJava(json, skipsB).asClass(ReadFirstBySkipper.class).b, "the skipping options, again");
    }

    @Test
    void aFieldIsSkippedByTheReadOptionsThatSaySoEvenWhenAnotherReadTheClassFirst() {
        ReadOptions plain = new ReadOptionsBuilder().build();
        ReadOptions skipsB = new ReadOptionsBuilder().addNotImportedField(ReadFirstByPlain.class, "b").build();
        String json = json(ReadFirstByPlain.class);

        assertEquals("B", JsonIo.toJava(json, plain).asClass(ReadFirstByPlain.class).b);
        ReadFirstByPlain skipped = JsonIo.toJava(json, skipsB).asClass(ReadFirstByPlain.class);
        assertEquals("A", skipped.a);
        assertNull(skipped.b, "the skipping options, read after");
        assertEquals("B", JsonIo.toJava(json, plain).asClass(ReadFirstByPlain.class).b, "plain options, again");
    }
}
