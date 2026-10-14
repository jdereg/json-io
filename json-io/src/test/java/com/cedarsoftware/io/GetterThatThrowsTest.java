package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.cedarsoftware.io.annotation.IoGetter;
import com.cedarsoftware.io.reflect.Accessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A getter that throws says something about the object it was called on, in the state that object is in. The field
 * is written in its place for that object, and the getter is called again for the next one.
 * <p>
 * Through 4.114.0 one throw switched the getter off for good: every object written afterwards with the same
 * {@code WriteOptions} got the field's value where the objects before it had got the getter's. Nothing said so. The
 * same object, written before and after an unrelated one whose getter threw, came out as two different documents.
 */
class GetterThatThrowsTest {

    /** The getter gives the price with tax, and refuses an item that has no price yet. */
    public static class Item {
        private String name;
        private int price;

        Item(String name, int price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public int getPrice() {
            if (price < 0) {
                throw new IllegalStateException("not priced yet");
            }
            return price + 10;
        }
    }

    /** A getter named for the field: used with or without useGetters. */
    public static class Labelled {
        private String label;

        Labelled(String label) {
            this.label = label;
        }

        @IoGetter("label")
        public String shownLabel() {
            if (label.isEmpty()) {
                throw new IllegalStateException("no label");
            }
            return label.toUpperCase();
        }
    }

    /**
     * One field of each kind an Accessor reads in its own way, each with a getter that returns something other than
     * the field and throws while the object is moody. The counters say how often each getter was called.
     */
    public static class Moody {
        boolean moody;
        int calls;

        private boolean z = false;
        private byte b = 1;
        private char c = 'a';
        private short s = 2;
        private int i = 3;
        private long j = 4;
        private float f = 5f;
        private double d = 6d;
        private String str = "field";

        private void called() {
            calls++;
            if (moody) {
                throw new IllegalStateException("moody");
            }
        }

        public boolean getZ() { called(); return true; }
        public byte getB() { called(); return 101; }
        public char getC() { called(); return 'G'; }
        public short getS() { called(); return 102; }
        public int getI() { called(); return 103; }
        public long getJ() { called(); return 104; }
        public float getF() { called(); return 105f; }
        public double getD() { called(); return 106d; }
        public String getStr() { called(); return "getter"; }
    }

    private static final Object[][] KINDS = {
            // field, getter, the field's value, the getter's value
            {"z", "getZ", false, true},
            {"b", "getB", (byte) 1, (byte) 101},
            {"c", "getC", 'a', 'G'},
            {"s", "getS", (short) 2, (short) 102},
            {"i", "getI", 3, 103},
            {"j", "getJ", 4L, 104L},
            {"f", "getF", 5f, 105f},
            {"d", "getD", 6d, 106d},
            {"str", "getStr", "field", "getter"}};

    /** The read an Accessor's caller makes for a field of this kind: the typed one for a primitive. */
    private static Object typedRead(Accessor accessor, Object target) {
        Class<?> type = accessor.getFieldType();
        if (type == boolean.class) {
            return accessor.getBoolean(target);
        } else if (type == byte.class) {
            return accessor.getByte(target);
        } else if (type == char.class) {
            return accessor.getChar(target);
        } else if (type == short.class) {
            return accessor.getShort(target);
        } else if (type == int.class) {
            return accessor.getInt(target);
        } else if (type == long.class) {
            return accessor.getLong(target);
        } else if (type == float.class) {
            return accessor.getFloat(target);
        } else if (type == double.class) {
            return accessor.getDouble(target);
        }
        return accessor.retrieve(target);
    }

    @Test
    void theSameObjectIsWrittenTheSameBeforeAndAfterAnotherObjectsGetterThrew() {
        WriteOptions getters = new WriteOptionsBuilder().showTypeInfoNever().useGetters(true).build();
        Item pen = new Item("pen", 5);

        assertEquals("{\"name\":\"pen\",\"price\":15}", JsonIo.toJson(pen, getters));
        // Its getter throws: the field is written for this object
        assertEquals("{\"name\":\"draft\",\"price\":-1}", JsonIo.toJson(new Item("draft", -1), getters));
        // ... and the getter is still the way the next object is read (it was {"name":"pen","price":5})
        assertEquals("{\"name\":\"pen\",\"price\":15}", JsonIo.toJson(pen, getters));

        List<Item> items = new ArrayList<>();
        items.add(new Item("ink", 1));
        items.add(new Item("draft", -1));
        items.add(new Item("nib", 2));
        assertEquals("[{\"name\":\"ink\",\"price\":11},{\"name\":\"draft\",\"price\":-1},{\"name\":\"nib\",\"price\":12}]",
                JsonIo.toJson(items, getters));
        assertEquals("[3]{name,price}:\n  ink,11\n  draft,-1\n  nib,12", JsonIo.toToon(items, getters).trim());
    }

    @Test
    void aGetterNamedForAFieldIsAskedAgainToo() {
        WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().build();
        assertEquals("{\"label\":\"A\"}", JsonIo.toJson(new Labelled("a"), options));
        assertEquals("{\"label\":\"\"}", JsonIo.toJson(new Labelled(""), options));
        assertEquals("{\"label\":\"B\"}", JsonIo.toJson(new Labelled("b"), options));
    }

    @Test
    void everyKindOfReadAsksTheGetterAgainAfterItThrew() throws Exception {
        for (Object[] kind : KINDS) {
            String name = (String) kind[0];
            Field field = Moody.class.getDeclaredField(name);
            field.setAccessible(true);
            Accessor accessor = Accessor.createMethodAccessor(field, (String) kind[1], name);
            Moody calm = new Moody();
            Moody moody = new Moody();
            moody.moody = true;

            // Typed read and retrieve(), in both orders around the throw
            assertEquals(kind[3], typedRead(accessor, calm), name);
            assertEquals(kind[2], typedRead(accessor, moody), name + ": the field, while the getter throws");
            assertEquals(kind[3], typedRead(accessor, calm), name + ": the getter again, after it threw for another");
            assertEquals(kind[2], accessor.retrieve(moody), name);
            assertEquals(kind[3], accessor.retrieve(calm), name + ": retrieve(), after the getter threw for another");

            // The object it threw for is asked again as well, and answers once its state allows
            moody.moody = false;
            assertEquals(kind[3], typedRead(accessor, moody), name + ": the same object, no longer moody");

            // One call of the getter per read, never two: 3 reads of calm, 3 of the other
            assertEquals(3, calm.calls, name);
            assertEquals(3, moody.calls, name);
        }
    }

    @Test
    void aGetterThatAlwaysThrowsIsCalledOncePerReadAndTheFieldIsWritten() throws Exception {
        Field field = Moody.class.getDeclaredField("i");
        field.setAccessible(true);
        Accessor accessor = Accessor.createMethodAccessor(field, "getI", "i");
        Moody moody = new Moody();
        moody.moody = true;
        for (int read = 1; read <= 5; read++) {
            assertEquals(3, accessor.getInt(moody));
            assertEquals(read, moody.calls);
        }
    }
}
