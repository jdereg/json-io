package com.cedarsoftware.io;

import java.util.BitSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field's value that cannot be read names the class and the field: "com.acme.Holder.count: Value 'abc' not
 * parseable as an int value ...". The message was only the conversion's, so in a large document nothing said which
 * of its values was wrong.
 */
class FieldErrorContextTest {

    public static class Holder {
        public int count;
        public BitSet flags;
        public UUID id;
    }

    public static class Outer {
        public String name;
        public Holder inner;
    }

    private static final String HOLDER = Holder.class.getName();

    private static JsonIoException readFails(String json, Class<?> type) {
        return assertThrows(JsonIoException.class, () -> JsonIo.toJava(json, null).asClass(type));
    }

    @Test
    void theMessageNamesTheClassAndField() {
        JsonIoException count = readFails("{\"count\":\"abc\"}", Holder.class);
        assertTrue(count.getMessage().startsWith(HOLDER + ".count: Value 'abc'"), count.getMessage());
        assertTrue(count.getCause() instanceof IllegalArgumentException, String.valueOf(count.getCause()));

        JsonIoException flags = readFails("{\"flags\":\"10x1\"}", Holder.class);
        assertTrue(flags.getMessage().startsWith(HOLDER + ".flags: Unable to convert '10x1' to BitSet"),
                flags.getMessage());

        JsonIoException typed = readFails("{\"flags\":{\"@type\":\"BitSet\",\"value\":\"10x1\"}}", Holder.class);
        assertTrue(typed.getMessage().startsWith(HOLDER + ".flags: "), typed.getMessage());

        JsonIoException id = readFails("{\"id\":\"not-a-uuid\"}", Holder.class);
        assertTrue(id.getMessage().startsWith(HOLDER + ".id: "), id.getMessage());
    }

    /** In a nested object, the message names the object's own class and field, once. */
    @Test
    void aNestedObjectNamesItsOwnField() {
        JsonIoException e = readFails("{\"name\":\"x\",\"inner\":{\"count\":\"abc\"}}", Outer.class);
        assertTrue(e.getMessage().startsWith(HOLDER + ".count: Value 'abc'"), e.getMessage());
        assertEquals(e.getMessage().indexOf(HOLDER), e.getMessage().lastIndexOf(HOLDER), e.getMessage());
    }

    /** Values that read are unaffected. */
    @Test
    void goodValuesRead() {
        Holder holder = JsonIo.toJava("{\"count\":\"42\",\"flags\":\"101\"}", null).asClass(Holder.class);
        assertEquals(42, holder.count);
        BitSet expected = new BitSet();
        expected.set(0);
        expected.set(2);
        assertEquals(expected, holder.flags);
    }
}
