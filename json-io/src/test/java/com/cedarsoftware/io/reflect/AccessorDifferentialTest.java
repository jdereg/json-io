package com.cedarsoftware.io.reflect;

import java.io.StreamTokenizer;
import java.io.StringReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.cedarsoftware.io.JsonIoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@link Accessor} must return exactly what plain reflection returns: {@code Field.get} for a field accessor,
 * {@code Method.invoke} for a getter accessor. The oracle here is reflection alone, on its own {@code Field} and
 * {@code Method} objects, so it shares nothing with the method handles the accessor reads through. Objects are
 * filled from a fixed seed, with the edge values of every primitive, NaN and -0.0, and nulls.
 *
 * <p>The accessor is always built from a {@code Field} that nobody has made accessible: it has to open the field
 * itself.</p>
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
class AccessorDifferentialTest {

    private static final long SEED = 20261012L;
    private static final int OBJECTS_PER_CLASS = 300;

    /** Every kind of field, private, each with a public getter that returns the field. */
    public static class Open {
        private boolean z;
        private byte b;
        private char c;
        private short s;
        private int i;
        private long j;
        private float f;
        private double d;
        private Boolean boxedZ;
        private Byte boxedB;
        private Character boxedC;
        private Short boxedS;
        private Integer boxedI;
        private Long boxedJ;
        private Float boxedF;
        private Double boxedD;
        private String str;
        private Object any;
        private int[] ints;
        private List<String> list;
        private TimeUnit unit;
        private final int finalInt = new Random().nextInt();   // not a compile-time constant
        private final String finalStr = "final" + System.nanoTime();
        public int publicInt;
        public String publicStr;
        protected long protectedLong;
        double packageDouble;
        private transient int transientInt;
        private volatile long volatileLong;

        public boolean isZ() { return z; }
        public byte getB() { return b; }
        public char getC() { return c; }
        public short getS() { return s; }
        public int getI() { return i; }
        public long getJ() { return j; }
        public float getF() { return f; }
        public double getD() { return d; }
        public Boolean getBoxedZ() { return boxedZ; }
        public Byte getBoxedB() { return boxedB; }
        public Character getBoxedC() { return boxedC; }
        public Short getBoxedS() { return boxedS; }
        public Integer getBoxedI() { return boxedI; }
        public Long getBoxedJ() { return boxedJ; }
        public Float getBoxedF() { return boxedF; }
        public Double getBoxedD() { return boxedD; }
        public String getStr() { return str; }
        public Object getAny() { return any; }
        public int[] getInts() { return ints; }
        public List<String> getList() { return list; }
        public TimeUnit getUnit() { return unit; }
        public int getFinalInt() { return finalInt; }
        public String getFinalStr() { return finalStr; }
        public int getPublicInt() { return publicInt; }
        public String getPublicStr() { return publicStr; }
        public long getProtectedLong() { return protectedLong; }
        public double getPackageDouble() { return packageDouble; }
        public int getTransientInt() { return transientInt; }
        public long getVolatileLong() { return volatileLong; }
    }

    /** A subclass: a field of its own that hides the parent's, and a getter it overrides. */
    public static class Child extends Open {
        private int i;
        private String extra;

        @Override
        public String getStr() { return "child:" + super.getStr(); }
        public String getExtra() { return extra; }
    }

    /** Getters that do not return the field: a method accessor returns what the getter returns. */
    public static class Twisted {
        private int n;
        private long big;
        private String s;
        private boolean on;

        public int getN() { return n + 1000; }
        public long getBig() { return ~big; }
        public String getS() { return "got:" + s; }
        public boolean isOn() { return !on; }
    }

    /** Not a public class: its fields are read all the same; its public getters cannot be reached by name. */
    static class Hidden {
        private int i;
        private double d;
        private String str;
        private final long finalLong = System.nanoTime();

        public int getI() { return i; }
        public double getD() { return d; }
        public String getStr() { return str; }
        public long getFinalLong() { return finalLong; }
    }

    public static class Throwing {
        private int n = 7;
        private String s = "the field";
        int nCalls;
        int sCalls;

        public int getN() {
            nCalls++;
            throw new IllegalStateException("getN");
        }

        public String getS() {
            sCalls++;
            throw new IllegalStateException("getS");
        }
    }

    public static class Statics {
        public static int count = 41;
        public static String label = "static";
        private int own = 1;
    }

    public static class Mismatched {
        private int n;
        private String s;

        public long getN() { return n; }                 // wider than the field
        public Object getS() { return s; }               // wider than the field
        String getHidden() { return s; }                 // not public
    }

    private static final Class<?>[] SHAPES = {Open.class, Child.class, Twisted.class, Hidden.class};

    // ---------------------------------------------------------------- fields

    @Test
    void aFieldAccessorReturnsWhatFieldGetReturns() throws Exception {
        Random random = new Random(SEED);
        int fieldsChecked = 0;

        for (Class<?> shape : SHAPES) {
            List<Field> fields = instanceFields(shape);
            Accessor[] accessors = new Accessor[fields.size()];
            Field[] oracles = new Field[fields.size()];
            for (int k = 0; k < accessors.length; k++) {
                Field field = fields.get(k);
                assertFalse(isAccessible(field), "the accessor must open the field itself: " + field);
                accessors[k] = Accessor.createFieldAccessor(field, "unique." + field.getName());
                oracles[k] = opened(field.getDeclaringClass().getDeclaredField(field.getName()));
                assertDescribes(accessors[k], field, field.getName(), false,
                        Modifier.isPublic(field.getModifiers()));
            }

            for (int n = 0; n < OBJECTS_PER_CLASS; n++) {
                Object target = filled(shape, random);
                for (int k = 0; k < accessors.length; k++) {
                    Object expected = oracles[k].get(target);
                    assertReads(accessors[k], target, expected, true,
                            shape.getSimpleName() + "." + oracles[k].getName());
                }
            }
            fieldsChecked += accessors.length;
        }
        assertEquals(29 + 31 + 4 + 4, fieldsChecked, "every field of every shape was read");
    }

    /** A parent's field is read from an instance of the subclass, and the subclass's own field of the same name too. */
    @Test
    void aHiddenParentFieldAndTheSubclassFieldAreDifferentAccessors() throws Exception {
        Accessor parentI = Accessor.createFieldAccessor(Open.class.getDeclaredField("i"), "Open.i");
        Accessor childI = Accessor.createFieldAccessor(Child.class.getDeclaredField("i"), "i");
        Child child = new Child();
        opened(Open.class.getDeclaredField("i")).setInt(child, 11);
        opened(Child.class.getDeclaredField("i")).setInt(child, 22);

        assertEquals(11, parentI.getInt(child));
        assertEquals(11, parentI.retrieve(child));
        assertEquals(22, childI.getInt(child));
        assertEquals(22, childI.retrieve(child));
        assertEquals("Open.i", parentI.getUniqueFieldName());
        assertEquals(Open.class, parentI.getDeclaringClass());
        assertEquals(Child.class, childI.getDeclaringClass());
    }

    // ---------------------------------------------------------------- getters

    @Test
    void aMethodAccessorReturnsWhatTheGetterReturns() throws Exception {
        Random random = new Random(SEED + 1);
        int gettersChecked = 0;

        for (Class<?> shape : new Class<?>[] {Open.class, Child.class, Twisted.class}) {
            List<Field> fields = instanceFields(shape);
            List<Accessor> accessors = new ArrayList<>();
            List<Method> oracles = new ArrayList<>();
            for (Field field : fields) {
                String name = getterName(field);
                Method getter = field.getDeclaringClass().getMethod(name);
                Accessor accessor = Accessor.createMethodAccessor(field, name, "unique." + field.getName());

                assertNotNull(accessor, shape.getSimpleName() + "." + name + "()");
                assertDescribes(accessor, field, name, true, true);
                accessors.add(accessor);
                oracles.add(getter);
            }

            for (int n = 0; n < OBJECTS_PER_CLASS; n++) {
                Object target = filled(shape, random);
                for (int k = 0; k < accessors.size(); k++) {
                    Object expected = oracles.get(k).invoke(target);
                    assertReads(accessors.get(k), target, expected, false,
                            shape.getSimpleName() + "." + oracles.get(k).getName() + "()");
                }
            }
            gettersChecked += accessors.size();
        }
        assertEquals(29 + 31 + 4, gettersChecked, "every getter of every public shape was called");
    }

    /** Stated without the oracle: the getter's answer, not the field's. */
    @Test
    void aGetterThatDoesNotReturnItsFieldIsStillWhatAMethodAccessorReturns() throws Exception {
        Twisted twisted = new Twisted();
        twisted.n = 5;
        twisted.big = 0L;
        twisted.s = "x";
        twisted.on = false;

        Accessor n = Accessor.createMethodAccessor(Twisted.class.getDeclaredField("n"), "getN", "n");
        Accessor big = Accessor.createMethodAccessor(Twisted.class.getDeclaredField("big"), "getBig", "big");
        Accessor s = Accessor.createMethodAccessor(Twisted.class.getDeclaredField("s"), "getS", "s");
        Accessor on = Accessor.createMethodAccessor(Twisted.class.getDeclaredField("on"), "isOn", "on");

        assertEquals(1005, n.retrieve(twisted));
        assertEquals(1005, n.getInt(twisted));
        assertEquals(-1L, big.retrieve(twisted));
        assertEquals(-1L, big.getLong(twisted));
        assertEquals("got:x", s.retrieve(twisted));
        assertEquals(true, on.retrieve(twisted));
        assertTrue(on.getBoolean(twisted));

        // and the field accessors of the same fields return the fields
        assertEquals(5, Accessor.createFieldAccessor(Twisted.class.getDeclaredField("n"), "n").getInt(twisted));
        assertEquals("x", Accessor.createFieldAccessor(Twisted.class.getDeclaredField("s"), "s").retrieve(twisted));
        assertFalse(Accessor.createFieldAccessor(Twisted.class.getDeclaredField("on"), "on").getBoolean(twisted));
    }

    /** The getter is found on the class that declares the field and dispatched like any virtual call. */
    @Test
    void anOverriddenGetterIsCalledOnTheSubclass() throws Exception {
        Accessor str = Accessor.createMethodAccessor(Open.class.getDeclaredField("str"), "getStr", "str");
        Open open = new Open();
        Child child = new Child();
        opened(Open.class.getDeclaredField("str")).set(open, "a");
        opened(Open.class.getDeclaredField("str")).set(child, "a");

        assertEquals("a", str.retrieve(open));
        assertEquals("child:a", str.retrieve(child));
    }

    /** No accessor when the getter is missing, not public, on a class that is not public, or of another type. */
    @Test
    void thereIsNoMethodAccessorWithoutAPublicGetterOfTheFieldsType() throws Exception {
        Field n = Mismatched.class.getDeclaredField("n");
        Field s = Mismatched.class.getDeclaredField("s");

        assertNull(Accessor.createMethodAccessor(n, "getN", "n"), "getter returns long, field is int");
        assertNull(Accessor.createMethodAccessor(s, "getS", "s"), "getter returns Object, field is String");
        assertNull(Accessor.createMethodAccessor(s, "getHidden", "s"), "getter is not public");
        assertNull(Accessor.createMethodAccessor(s, "getNothing", "s"), "no such method");
        for (Field field : instanceFields(Hidden.class)) {
            assertNull(Accessor.createMethodAccessor(field, getterName(field), field.getName()),
                    "the class is not public: " + field.getName());
        }
    }

    // ---------------------------------------------------------------- the edges

    /**
     * A field of a JDK class reads as reflection allows on this JDK: its value where the field can be opened
     * (JDK 8, or a public field), and null -- zero from a primitive getter -- where the module system refuses.
     */
    @Test
    void aFieldOfAJdkClassReadsAsThisJdkAllows() throws Exception {
        ParsePosition position = new ParsePosition(17);
        position.setErrorIndex(3);
        for (String name : new String[] {"index", "errorIndex"}) {
            Accessor accessor = Accessor.createFieldAccessor(ParsePosition.class.getDeclaredField(name), name);
            Field oracle = ParsePosition.class.getDeclaredField(name);
            Object expected;
            try {
                oracle.setAccessible(true);
                expected = oracle.get(position);
            } catch (RuntimeException closed) {         // InaccessibleObjectException on JDK 9+
                expected = null;
            }

            assertEquals(expected, accessor.retrieve(position), name);
            assertEquals(expected == null ? 0 : (Integer) expected, accessor.getInt(position), name);
            assertEquals(expected == null ? 0L : (Integer) expected, accessor.getLong(position), name);
        }

        // public fields of a public JDK class need no opening on any JDK
        StreamTokenizer tokenizer = new StreamTokenizer(new StringReader("42.5 word"));
        tokenizer.nextToken();
        Accessor nval = Accessor.createFieldAccessor(StreamTokenizer.class.getDeclaredField("nval"), "nval");
        Accessor ttype = Accessor.createFieldAccessor(StreamTokenizer.class.getDeclaredField("ttype"), "ttype");
        Accessor sval = Accessor.createFieldAccessor(StreamTokenizer.class.getDeclaredField("sval"), "sval");
        assertEquals(42.5, nval.getDouble(tokenizer));
        assertEquals(42.5, nval.retrieve(tokenizer));
        assertEquals(StreamTokenizer.TT_NUMBER, ttype.getInt(tokenizer));
        assertNull(sval.retrieve(tokenizer));
        tokenizer.nextToken();
        assertEquals("word", sval.retrieve(tokenizer));
        assertSame(tokenizer.sval, sval.retrieve(tokenizer));
    }

    /** A static field has no handle that takes the object; it is read with Field.get, whatever object is passed. */
    @Test
    void aStaticFieldIsReadThroughTheField() throws Exception {
        Accessor count = Accessor.createFieldAccessor(Statics.class.getDeclaredField("count"), "count");
        Accessor label = Accessor.createFieldAccessor(Statics.class.getDeclaredField("label"), "label");
        Statics target = new Statics();

        assertEquals(41, count.retrieve(target));
        assertEquals(41, count.getInt(target));
        assertEquals(41L, count.getLong(target));
        assertEquals("static", label.retrieve(target));
        Statics.count = 42;
        try {
            assertEquals(42, count.getInt(target));
            assertEquals(42, count.retrieve(target));
        } finally {
            Statics.count = 41;
        }
    }

    /**
     * A getter that throws is called once for each read, and the field is read in its place that time. It is asked
     * again for the next read: through 4.114.0 one throw switched it off for good (GetterThatThrowsTest). (Through
     * 4.113.0 it was also called twice before the field was read, on the JDKs where a lambda could be made for it.)
     */
    @Test
    void aGetterThatThrowsIsCalledOncePerReadAndTheFieldIsReadThatTime() throws Exception {
        Accessor n = Accessor.createMethodAccessor(opened(Throwing.class.getDeclaredField("n")), "getN", "n");
        Accessor s = Accessor.createMethodAccessor(opened(Throwing.class.getDeclaredField("s")), "getS", "s");
        Throwing first = new Throwing();
        Throwing second = new Throwing();

        assertEquals(7, n.getInt(first));
        assertEquals(1, first.nCalls);
        assertEquals(7, n.getInt(first));
        assertEquals(7, n.retrieve(first));
        assertEquals(7, n.getInt(second));
        assertEquals(3, first.nCalls);
        assertEquals(1, second.nCalls);

        assertEquals("the field", s.retrieve(first));
        assertEquals(1, first.sCalls);
        assertEquals("the field", s.retrieve(first));
        assertEquals("the field", s.retrieve(second));
        assertEquals(2, first.sCalls);
        assertEquals(1, second.sCalls);
    }

    @Test
    void aNullObjectOrAnObjectOfAnotherClassIsAnError() throws Exception {
        Accessor field = Accessor.createFieldAccessor(Twisted.class.getDeclaredField("n"), "n");
        Accessor method = Accessor.createMethodAccessor(Twisted.class.getDeclaredField("n"), "getN", "n");

        for (Accessor accessor : new Accessor[] {field, method}) {
            JsonIoException e = assertThrows(JsonIoException.class, () -> accessor.retrieve(null));
            assertEquals("Cannot retrieve field value from null object for field: n", e.getMessage());
            e = assertThrows(JsonIoException.class, () -> accessor.getInt(null));
            assertEquals("Cannot retrieve field value from null object for field: n", e.getMessage());

            e = assertThrows(JsonIoException.class, () -> accessor.retrieve("not a Twisted"));
            assertTrue(e.getMessage().startsWith("Failed to retrieve field value: n in class: "), e.getMessage());
            assertThrows(JsonIoException.class, () -> accessor.getInt("not a Twisted"));
        }
    }

    /** A boolean or char value is not a number, and a number is not a boolean or a char. */
    @Test
    void aTypedGetterOfTheWrongFamilyIsAnError() throws Exception {
        Open open = new Open();
        Accessor z = Accessor.createFieldAccessor(Open.class.getDeclaredField("z"), "z");
        Accessor c = Accessor.createFieldAccessor(Open.class.getDeclaredField("c"), "c");
        Accessor i = Accessor.createFieldAccessor(Open.class.getDeclaredField("i"), "i");

        assertThrows(JsonIoException.class, () -> z.getInt(open));
        assertThrows(JsonIoException.class, () -> c.getLong(open));
        assertThrows(JsonIoException.class, () -> i.getBoolean(open));
        assertThrows(JsonIoException.class, () -> i.getChar(open));
        // ... and the accessors still read
        assertEquals(false, z.retrieve(open));
        assertEquals(0, i.getInt(open));
    }

    // ---------------------------------------------------------------- the mechanism

    /**
     * The writer's speed rests on this, and no comparison of values would notice its loss, because a read that
     * cannot go through the handle quietly goes through {@code Field.get}: every ordinary field and getter has its
     * handle adapted for {@code invokeExact}, a primitive one in its own type as well, and reading through any
     * typed getter never trips the fallback. (Through 4.113.0 the lambda meant for a primitive field could never be
     * made, and nothing reported it.)
     */
    @Test
    void anOrdinaryFieldOrGetterIsReadThroughItsExactHandle() throws Exception {
        Random random = new Random(SEED + 2);
        int checked = 0;

        for (Class<?> shape : SHAPES) {
            Object target = filled(shape, random);
            for (Field field : instanceFields(shape)) {
                String what = shape.getSimpleName() + "." + field.getName();
                assertExactHandle(Accessor.createFieldAccessor(field, field.getName()), target, what);
                checked++;
                if (Modifier.isPublic(shape.getModifiers())) {
                    String getter = getterName(field);
                    assertExactHandle(Accessor.createMethodAccessor(field, getter, field.getName()), target,
                            what + " through " + getter + "()");
                    checked++;
                }
            }
        }
        assertEquals(2 * (29 + 31 + 4) + 4, checked);
    }

    private static void assertExactHandle(Accessor accessor, Object target, String what) throws Exception {
        Class<?> type = accessor.getFieldType();
        MethodHandle boxed = (MethodHandle) peek(accessor, "boxedGetter");
        MethodHandle primitive = (MethodHandle) peek(accessor, "primitiveGetter");

        assertNotNull(boxed, what);
        assertEquals(MethodType.methodType(Object.class, Object.class), boxed.type(), what);
        if (type.isPrimitive()) {
            assertNotNull(primitive, what);
            assertEquals(MethodType.methodType(type, Object.class), primitive.type(), what);
        } else {
            assertNull(primitive, what);
        }

        Object value = accessor.retrieve(target);
        if (value instanceof Boolean) {
            accessor.getBoolean(target);
        } else if (value instanceof Character) {
            accessor.getChar(target);
        } else if (value instanceof Number) {
            accessor.getByte(target);
            accessor.getShort(target);
            accessor.getInt(target);
            accessor.getLong(target);
            accessor.getFloat(target);
            accessor.getDouble(target);
        }
        assertEquals(false, peek(accessor, "handleFailed"), what + ": a read fell back to Field.get");
    }

    private static Object peek(Accessor accessor, String privateField) throws Exception {
        return opened(Accessor.class.getDeclaredField(privateField)).get(accessor);
    }

    // ---------------------------------------------------------------- the comparison

    /**
     * retrieve() and every typed getter that applies, against the value reflection returned.
     *
     * @param sameReference a field accessor hands back the very object the field holds; a getter may build one
     */
    private static void assertReads(Accessor accessor, Object target, Object expected, boolean sameReference,
                                    String what) {
        Class<?> type = accessor.getFieldType();
        Object actual = accessor.retrieve(target);
        if (sameReference && !type.isPrimitive()) {
            assertSame(expected, actual, what);
        } else {
            assertEquals(expected, actual, what);       // boxed: Float and Double compare by bits (NaN, -0.0)
        }

        if (expected == null) {
            // null reads as zero through every typed getter
            assertFalse(accessor.getBoolean(target), what);
            assertEquals((byte) 0, accessor.getByte(target), what);
            assertEquals((char) 0, accessor.getChar(target), what);
            assertEquals((short) 0, accessor.getShort(target), what);
            assertEquals(0, accessor.getInt(target), what);
            assertEquals(0L, accessor.getLong(target), what);
            assertEquals(0, Float.floatToIntBits(accessor.getFloat(target)), what);
            assertEquals(0L, Double.doubleToLongBits(accessor.getDouble(target)), what);
        } else if (expected instanceof Boolean) {
            assertEquals(expected, accessor.getBoolean(target), what);
        } else if (expected instanceof Character) {
            assertEquals(expected, accessor.getChar(target), what);
        } else if (expected instanceof Number) {
            // the getter of the field's own type, and the others as Number converts
            Number number = (Number) expected;
            assertEquals(number.byteValue(), accessor.getByte(target), what);
            assertEquals(number.shortValue(), accessor.getShort(target), what);
            assertEquals(number.intValue(), accessor.getInt(target), what);
            assertEquals(number.longValue(), accessor.getLong(target), what);
            assertEquals(Float.floatToIntBits(number.floatValue()),
                    Float.floatToIntBits(accessor.getFloat(target)), what);
            assertEquals(Double.doubleToLongBits(number.doubleValue()),
                    Double.doubleToLongBits(accessor.getDouble(target)), what);
        }
    }

    private static void assertDescribes(Accessor accessor, Field field, String fieldOrMethodName, boolean isMethod,
                                        boolean isPublic) {
        String what = field.toString();
        assertEquals(isMethod, accessor.isMethod(), what);
        assertEquals(isPublic, accessor.isPublic(), what);
        assertEquals(fieldOrMethodName, accessor.getFieldOrMethodName(), what);
        assertEquals(field.getName(), accessor.getActualFieldName(), what);
        assertEquals("unique." + field.getName(), accessor.getUniqueFieldName(), what);
        assertEquals(field.getType(), accessor.getFieldType(), what);
        assertEquals(field.getGenericType(), accessor.getGenericType(), what);
        assertEquals(field.getDeclaringClass(), accessor.getDeclaringClass(), what);
    }

    // ---------------------------------------------------------------- the objects

    /** The instance fields of the class and its parents, each a fresh Field that has not been made accessible. */
    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static String getterName(Field field) {
        String name = field.getName();
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return (field.getType() == boolean.class ? "is" : "get") + capitalized;
    }

    private static Field opened(Field field) {
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("deprecation")    // Field.isAccessible: the replacement, canAccess, is not in Java 8
    private static boolean isAccessible(Field field) {
        return field.isAccessible();
    }

    private static Object filled(Class<?> type, Random random) throws Exception {
        Object target = type.getDeclaredConstructor().newInstance();
        for (Field field : instanceFields(type)) {
            if (Modifier.isFinal(field.getModifiers())) {
                continue;       // set by the constructor
            }
            opened(field).set(target, randomValue(field.getType(), random));
        }
        return target;
    }

    private static Object randomValue(Class<?> type, Random random) {
        if (!type.isPrimitive() && random.nextInt(5) == 0) {
            return null;
        }
        boolean edge = random.nextInt(4) == 0;
        int pick = random.nextInt(4);

        if (type == boolean.class || type == Boolean.class) {
            return random.nextBoolean();
        }
        if (type == byte.class || type == Byte.class) {
            return edge ? new byte[] {Byte.MIN_VALUE, Byte.MAX_VALUE, 0, -1}[pick] : (byte) random.nextInt();
        }
        if (type == char.class || type == Character.class) {
            return edge ? new char[] {Character.MIN_VALUE, Character.MAX_VALUE, 'a', '\uD83D'}[pick]
                    : (char) random.nextInt(0x10000);
        }
        if (type == short.class || type == Short.class) {
            return edge ? new short[] {Short.MIN_VALUE, Short.MAX_VALUE, 0, -1}[pick] : (short) random.nextInt();
        }
        if (type == int.class || type == Integer.class) {
            return edge ? new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE, 0, -1}[pick] : random.nextInt();
        }
        if (type == long.class || type == Long.class) {
            return edge ? new long[] {Long.MIN_VALUE, Long.MAX_VALUE, 0L, 1L << 40}[pick] : random.nextLong();
        }
        if (type == float.class || type == Float.class) {
            return edge ? new float[] {Float.NaN, -0.0f, Float.MAX_VALUE, Float.NEGATIVE_INFINITY}[pick]
                    : random.nextFloat() * random.nextInt();
        }
        if (type == double.class || type == Double.class) {
            return edge ? new double[] {Double.NaN, -0.0d, Double.MIN_VALUE, Double.POSITIVE_INFINITY}[pick]
                    : random.nextDouble() * random.nextLong();
        }
        if (type == String.class) {
            return "s" + random.nextInt();
        }
        if (type == int[].class) {
            return new int[] {random.nextInt(), random.nextInt()};
        }
        if (type == List.class) {
            return new ArrayList<>(Arrays.asList("a" + random.nextInt(), "b"));
        }
        if (type == TimeUnit.class) {
            return TimeUnit.values()[random.nextInt(TimeUnit.values().length)];
        }
        // Object: anything
        Object[] any = {random.nextInt(), "text" + random.nextInt(), random.nextDouble(), new int[] {1},
                TimeUnit.DAYS, new ArrayList<>(Arrays.asList(1, 2))};
        return any[random.nextInt(any.length)];
    }
}
