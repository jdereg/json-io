package com.cedarsoftware.io.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.util.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@link Injector} must leave in the field exactly what plain reflection would: the value itself, the value the
 * Converter makes of it when it is of another type, or the narrowed number for {@code injectLong} and
 * {@code injectDouble}. What the field holds afterwards is read with {@code Field.get} on a {@code Field} of the
 * test's own, so the check shares nothing with the method handles the injector sets through. Values come from a
 * fixed seed, with the edge values of every primitive, NaN and -0.0, and nulls.
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
class InjectorDifferentialTest {

    private static final long SEED = 20261012L;
    private static final int ROUNDS = 200;
    private static final Object NOT_INJECTED = new Object();
    private static final Object REFUSED = new Object();

    /** Every kind of field, private, each with a public setter that assigns the field. */
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
        public int publicInt;
        public String publicStr;
        protected long protectedLong;
        double packageDouble;
        private transient int transientInt;
        private volatile long volatileLong;

        public void setZ(boolean v) { z = v; }
        public void setB(byte v) { b = v; }
        public void setC(char v) { c = v; }
        public void setS(short v) { s = v; }
        public void setI(int v) { i = v; }
        public void setJ(long v) { j = v; }
        public void setF(float v) { f = v; }
        public void setD(double v) { d = v; }
        public void setBoxedZ(Boolean v) { boxedZ = v; }
        public void setBoxedB(Byte v) { boxedB = v; }
        public void setBoxedC(Character v) { boxedC = v; }
        public void setBoxedS(Short v) { boxedS = v; }
        public void setBoxedI(Integer v) { boxedI = v; }
        public void setBoxedJ(Long v) { boxedJ = v; }
        public void setBoxedF(Float v) { boxedF = v; }
        public void setBoxedD(Double v) { boxedD = v; }
        public void setStr(String v) { str = v; }
        public void setAny(Object v) { any = v; }
        public void setInts(int[] v) { ints = v; }
        public void setList(List<String> v) { list = v; }
        public void setUnit(TimeUnit v) { unit = v; }
        public void setPublicInt(int v) { publicInt = v; }
        public void setPublicStr(String v) { publicStr = v; }
        public void setProtectedLong(long v) { protectedLong = v; }
        public void setPackageDouble(double v) { packageDouble = v; }
        public void setTransientInt(int v) { transientInt = v; }
        public void setVolatileLong(long v) { volatileLong = v; }
    }

    /** A subclass: a field of its own that hides the parent's, and a setter it overrides. */
    public static class Child extends Open {
        private int i;
        private String extra;

        @Override
        public void setStr(String v) { super.setStr("child:" + v); }
        public void setExtra(String v) { extra = v; }
    }

    /** Final fields, assigned in the constructor from values the compiler cannot fold. */
    public static class Finals {
        private final int finalInt;
        private final long finalLong;
        private final double finalDouble;
        private final short finalShort;
        private final String finalStr;
        private final Integer finalBoxed;

        public Finals() {
            long now = System.nanoTime();
            finalInt = (int) now;
            finalLong = now;
            finalDouble = now / 3.0;
            finalShort = (short) now;
            finalStr = "final" + now;
            finalBoxed = (int) (now >> 8);
        }
    }

    /** Not a public class: its fields are set all the same. */
    static class Hidden {
        private int i;
        private double d;
        private String str;
        private Long boxedJ;
    }

    /** Setters that do not store their argument: a method injector leaves in the field what the setter stores. */
    public static class Twisted {
        private int n;
        private long big;
        private double ratio;
        private String s;
        private Integer boxed;
        int calls;

        public void setN(int v) { calls++; n = v + 1000; }
        public void setBig(long v) { calls++; big = ~v; }
        public void setRatio(double v) { calls++; ratio = v * 2; }
        public void setS(String v) { calls++; s = "set:" + v; }
        public void setBoxed(Integer v) { calls++; boxed = v == null ? -1 : v + 1; }
    }

    public static class Refusing {
        private int n = 7;
        private String s = "untouched";

        public void setN(int v) { throw new IllegalStateException("setN refuses " + v); }
        public void setS(String v) { throw new IllegalStateException("setS refuses " + v); }
    }

    public static class Mismatched {
        private int n;
        private String s;

        public void setN(long v) { n = (int) v; }                       // wider than the field
        public Mismatched setS(String v) { s = v; return this; }        // not void
        private void setHidden(String v) { s = v; }                     // not public
    }

    public static class Statics {
        public static int count = 41;
    }

    private static final Class<?>[] SHAPES = {Open.class, Child.class, Finals.class, Hidden.class};

    // ---------------------------------------------------------------- fields

    @Test
    void aFieldInjectorLeavesInTheFieldWhatFieldSetWould() throws Exception {
        Random random = new Random(SEED);
        int fieldsChecked = 0;

        for (Class<?> shape : SHAPES) {
            Object target = shape.getDeclaredConstructor().newInstance();
            for (Field field : instanceFields(shape)) {
                // json-io hands the injector fields it has already made accessible
                Injector injector = Injector.create(opened(field), "unique." + field.getName());
                Field oracle = opened(field.getDeclaringClass().getDeclaredField(field.getName()));
                String what = shape.getSimpleName() + "." + field.getName();

                assertDescribes(injector, field, field.getName());
                for (int n = 0; n < ROUNDS; n++) {
                    assertInjects(injector, oracle, target, random, true, what);
                }
                fieldsChecked++;
            }
        }
        assertEquals(27 + 29 + 6 + 4, fieldsChecked, "every field of every shape was set");
    }

    /** The parent's field and the subclass's field of the same name are set separately. */
    @Test
    void aHiddenParentFieldAndTheSubclassFieldAreDifferentInjectors() throws Exception {
        Injector parentI = Injector.create(Open.class.getDeclaredField("i"), "Open.i");
        Injector childI = Injector.create(Child.class.getDeclaredField("i"), "i");
        Child child = new Child();

        parentI.inject(child, 11);
        assertTrue(childI.injectLong(child, 22L));

        assertEquals(11, opened(Open.class.getDeclaredField("i")).getInt(child));
        assertEquals(22, opened(Child.class.getDeclaredField("i")).getInt(child));
        assertEquals("Open.i", parentI.getUniqueFieldName());
    }

    // ---------------------------------------------------------------- setters

    @Test
    void aMethodInjectorLeavesInTheFieldWhatTheSetterStores() throws Exception {
        Random random = new Random(SEED + 1);
        int settersChecked = 0;

        for (Class<?> shape : new Class<?>[] {Open.class, Child.class}) {
            Object target = shape.getDeclaredConstructor().newInstance();
            for (Field field : instanceFields(shape)) {
                String setter = setterName(field);
                Injector injector = Injector.create(field, setter, "unique." + field.getName());
                Field oracle = opened(field.getDeclaringClass().getDeclaredField(field.getName()));
                String what = shape.getSimpleName() + "." + setter + "()";
                // Child.setStr stores something else, and setI sets the parent's i, not the one that hides it:
                // each has a test of its own
                boolean plain = !(shape == Child.class && field.getName().equals("str"))
                        && !(field.getDeclaringClass() == Child.class && field.getName().equals("i"));

                assertDescribes(injector, field, setter);
                for (int n = 0; plain && n < ROUNDS; n++) {
                    assertInjects(injector, oracle, target, random, false, what);
                }
                settersChecked++;
            }
        }
        assertEquals(27 + 29, settersChecked, "every setter of every public shape was called");
    }

    /** Stated without the oracle: what the setter stores, through inject, injectLong and injectDouble alike. */
    @Test
    void aSetterThatDoesNotStoreItsArgumentDecidesWhatTheFieldHolds() throws Exception {
        Twisted twisted = new Twisted();
        Injector n = Injector.create(Twisted.class.getDeclaredField("n"), "setN", "n");
        Injector big = Injector.create(Twisted.class.getDeclaredField("big"), "setBig", "big");
        Injector ratio = Injector.create(Twisted.class.getDeclaredField("ratio"), "setRatio", "ratio");
        Injector s = Injector.create(Twisted.class.getDeclaredField("s"), "setS", "s");
        Injector boxed = Injector.create(Twisted.class.getDeclaredField("boxed"), "setBoxed", "boxed");

        n.inject(twisted, 5);
        assertEquals(1005, twisted.n);
        assertTrue(n.injectLong(twisted, 6L));
        assertEquals(1006, twisted.n);
        assertTrue(n.injectDouble(twisted, 7.9));
        assertEquals(1007, twisted.n);
        n.inject(twisted, "8");                         // converted, then handed to the setter
        assertEquals(1008, twisted.n);

        assertTrue(big.injectLong(twisted, 0L));
        assertEquals(-1L, twisted.big);
        big.inject(twisted, 1L);
        assertEquals(-2L, twisted.big);

        assertTrue(ratio.injectDouble(twisted, 1.5));
        assertEquals(3.0, twisted.ratio);
        assertTrue(ratio.injectLong(twisted, 4L));
        assertEquals(8.0, twisted.ratio);

        s.inject(twisted, "x");
        assertEquals("set:x", twisted.s);
        s.inject(twisted, null);
        assertEquals("set:null", twisted.s);
        assertFalse(s.injectLong(twisted, 1L));
        assertEquals("set:null", twisted.s);

        boxed.inject(twisted, 9);
        assertEquals(Integer.valueOf(10), twisted.boxed);
        boxed.inject(twisted, null);
        assertEquals(Integer.valueOf(-1), twisted.boxed);
        assertTrue(boxed.injectLong(twisted, 20L));
        assertEquals(Integer.valueOf(21), twisted.boxed);

        assertEquals(13, twisted.calls, "one call of the setter per injection, none for the refused long");
    }

    /** The setter is found on the class that declares the field and dispatched like any virtual call. */
    @Test
    void anOverriddenSetterIsCalledOnTheSubclass() throws Exception {
        Injector str = Injector.create(Open.class.getDeclaredField("str"), "setStr", "str");
        Field oracle = opened(Open.class.getDeclaredField("str"));
        Open open = new Open();
        Child child = new Child();

        str.inject(open, "a");
        str.inject(child, "a");

        assertEquals("a", oracle.get(open));
        assertEquals("child:a", oracle.get(child));
    }

    @Test
    void thereIsNoMethodInjectorWithoutAVoidSetterOfTheFieldsTypeThatJsonIoMayCall() throws Exception {
        Field n = Mismatched.class.getDeclaredField("n");
        Field s = Mismatched.class.getDeclaredField("s");

        JsonIoException e = assertThrows(JsonIoException.class, () -> Injector.create(n, "setN", "n"));
        assertEquals("Method not found: setN in class: " + Mismatched.class.getName(), e.getMessage());
        e = assertThrows(JsonIoException.class, () -> Injector.create(s, "setS", "s"));
        assertEquals("Method not found: setS in class: " + Mismatched.class.getName(), e.getMessage());
        e = assertThrows(JsonIoException.class, () -> Injector.create(s, "setNothing", "s"));
        assertEquals("Method not found: setNothing in class: " + Mismatched.class.getName(), e.getMessage());
        e = assertThrows(JsonIoException.class, () -> Injector.create(s, "setHidden", "s"));
        assertEquals("Access denied to method: setHidden in class: " + Mismatched.class.getName(), e.getMessage());
    }

    // ---------------------------------------------------------------- the edges

    /** A setter that throws fails the injection, with the setter's own exception as the cause. */
    @Test
    void aSetterThatThrowsFailsTheInjection() throws Exception {
        Injector n = Injector.create(Refusing.class.getDeclaredField("n"), "setN", "n");
        Injector s = Injector.create(Refusing.class.getDeclaredField("s"), "setS", "s");
        Refusing refusing = new Refusing();

        JsonIoException e = assertThrows(JsonIoException.class, () -> n.inject(refusing, 1));
        assertEquals("Unable to set field: n using setN", e.getMessage());
        assertEquals("setN refuses 1", e.getCause().getMessage());
        e = assertThrows(JsonIoException.class, () -> n.injectLong(refusing, 2L));
        assertEquals("Unable to set field: n using setN", e.getMessage());
        assertEquals("setN refuses 2", e.getCause().getMessage());
        e = assertThrows(JsonIoException.class, () -> s.inject(refusing, "x"));
        assertEquals("Unable to set field: s using setS", e.getMessage());
        assertEquals("setS refuses x", e.getCause().getMessage());

        assertEquals(7, refusing.n);
        assertEquals("untouched", refusing.s);
    }

    @Test
    void aNullObjectOrAnObjectOfAnotherClassIsAnError() throws Exception {
        Injector field = Injector.create(Twisted.class.getDeclaredField("n"), "n");
        Injector method = Injector.create(Twisted.class.getDeclaredField("n"), "setN", "n");

        for (Injector injector : new Injector[] {field, method}) {
            JsonIoException e = assertThrows(JsonIoException.class, () -> injector.inject(null, 1));
            assertEquals("Attempting to set field: n on null object.", e.getMessage());
            e = assertThrows(JsonIoException.class, () -> injector.injectLong(null, 1L));
            assertEquals("Attempting to set field: n on null object.", e.getMessage());
            e = assertThrows(JsonIoException.class, () -> injector.injectDouble(null, 1.0));
            assertEquals("Attempting to set field: n on null object.", e.getMessage());

            e = assertThrows(JsonIoException.class, () -> injector.inject("not a Twisted", 1));
            assertTrue(e.getMessage().startsWith("Unable to set field: n using "), e.getMessage());
            e = assertThrows(JsonIoException.class, () -> injector.injectLong("not a Twisted", 1L));
            assertTrue(e.getMessage().startsWith("Unable to set field: n using "), e.getMessage());
        }

        // and both still work
        Twisted twisted = new Twisted();
        field.inject(twisted, 3);
        assertEquals(3, twisted.n);
        method.inject(twisted, 3);
        assertEquals(1003, twisted.n);
    }

    /** null cannot go into a primitive; the field keeps its value. */
    @Test
    void nullIntoAPrimitiveFieldIsAnError() throws Exception {
        Open open = new Open();
        for (String name : new String[] {"i", "j", "d", "z", "c"}) {
            Injector field = Injector.create(opened(Open.class.getDeclaredField(name)), name);
            Injector method = Injector.create(Open.class.getDeclaredField(name), setterName(
                    Open.class.getDeclaredField(name)), name);
            field.inject(open, "1");
            Object before = opened(Open.class.getDeclaredField(name)).get(open);

            JsonIoException e = assertThrows(JsonIoException.class, () -> field.inject(open, null));
            assertEquals("Unable to set field: " + name + " using " + name, e.getMessage());
            e = assertThrows(JsonIoException.class, () -> method.inject(open, null));
            assertEquals("Unable to set field: " + name + " using " + method.getDisplayName(), e.getMessage());
            assertEquals(before, opened(Open.class.getDeclaredField(name)).get(open), name);
        }
    }

    /** A static field has no handle that takes the object: injecting into it is refused, as it always was. */
    @Test
    void aStaticFieldIsNotSet() throws Exception {
        Injector count = Injector.create(Statics.class.getDeclaredField("count"), "count");

        assertThrows(JsonIoException.class, () -> count.inject(new Statics(), 5));
        assertThrows(JsonIoException.class, () -> count.injectLong(new Statics(), 5L));
        assertEquals(41, Statics.count);
    }

    /**
     * A final field handed over without having been made accessible is set all the same. (Through 4.113.0, on
     * JDK 9 and later, the injector made for it could not set anything: it held a VarHandle, and a VarHandle of a
     * final field has no set.)
     */
    @Test
    void aFinalFieldThatWasNotMadeAccessibleIsSet() throws Exception {
        Finals finals = new Finals();
        Field finalStr = Finals.class.getDeclaredField("finalStr");
        Field finalInt = Finals.class.getDeclaredField("finalInt");
        assertFalse(isAccessible(finalStr));

        Injector.create(finalStr, "finalStr").inject(finals, "set");
        assertTrue(Injector.create(finalInt, "finalInt").injectLong(finals, 42L));

        assertEquals("set", opened(Finals.class.getDeclaredField("finalStr")).get(finals));
        assertEquals(42, opened(Finals.class.getDeclaredField("finalInt")).get(finals));
    }

    // ---------------------------------------------------------------- the mechanism

    /**
     * The reader's speed rests on this, and no comparison of values would notice its loss: every ordinary field and
     * setter is set through a handle adapted for {@code invokeExact}, a primitive number through one of its own
     * type as well. Only a final field on JDK 8 through 16 is set with {@code Field.set}.
     */
    @Test
    void anOrdinaryFieldOrSetterIsSetThroughItsExactHandle() throws Exception {
        int checked = 0;
        for (Class<?> shape : new Class<?>[] {Open.class, Child.class, Hidden.class}) {
            for (Field field : instanceFields(shape)) {
                String what = shape.getSimpleName() + "." + field.getName();
                assertExactHandle(Injector.create(opened(field), field.getName()), field.getType(), what);
                checked++;
                if (Modifier.isPublic(shape.getModifiers())) {
                    Field fresh = field.getDeclaringClass().getDeclaredField(field.getName());
                    assertExactHandle(Injector.create(fresh, setterName(fresh), fresh.getName()), fresh.getType(),
                            what + " through " + setterName(fresh) + "()");
                    checked++;
                }
            }
        }
        assertEquals(2 * (27 + 29) + 4, checked);
    }

    private static void assertExactHandle(Injector injector, Class<?> type, String what) throws Exception {
        MethodHandle boxed = (MethodHandle) peek(injector, "boxedSetter");
        MethodHandle primitive = (MethodHandle) peek(injector, "primitiveSetter");

        assertEquals(false, peek(injector, "useFieldSet"), what);
        assertNotNull(boxed, what);
        assertEquals(MethodType.methodType(void.class, Object.class, Object.class), boxed.type(), what);
        if (type.isPrimitive() && type != boolean.class && type != char.class) {
            assertNotNull(primitive, what);
            assertEquals(MethodType.methodType(void.class, Object.class, type), primitive.type(), what);
        } else {
            assertNull(primitive, what);
        }
    }

    private static Object peek(Injector injector, String privateField) throws Exception {
        return opened(Injector.class.getDeclaredField(privateField)).get(injector);
    }

    // ---------------------------------------------------------------- the comparison

    /**
     * One round on one field: inject a value of the field's own type, a value of another type, a long and a
     * double, and after each compare what the field holds with what reflection says it should.
     *
     * @param sameReference a field injector stores the very object it was given; a setter may store another
     */
    private static void assertInjects(Injector injector, Field oracle, Object target, Random random,
                                      boolean sameReference, String what) throws Exception {
        Class<?> type = oracle.getType();

        // 1. a value of the field's own type: stored as it is
        Object own = randomValue(type, random);
        injector.inject(target, own);
        if (type.isPrimitive() || !sameReference) {
            assertEquals(own, oracle.get(target), what);        // boxed: Float and Double compare by bits
        } else {
            assertSame(own, oracle.get(target), what);
        }

        // 2. a value of another type: what the Converter makes of it, or refused and the field left alone
        Object foreign = foreignValue(random);
        Object before = oracle.get(target);
        Object expected;
        Class<?> refusal = null;
        if (wrapper(type).isInstance(foreign)) {
            expected = foreign;
        } else {
            try {
                expected = Converter.convert(foreign, type);
            } catch (RuntimeException refused) {
                expected = REFUSED;
                refusal = refused.getClass();
            }
        }
        if (expected == REFUSED) {
            // the Converter's own exception, not one of json-io's
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> injector.inject(target, foreign),
                    what + " <- " + foreign);
            assertEquals(refusal, thrown.getClass(), what + " <- " + foreign);
            assertEquals(before, oracle.get(target), what + " <- " + foreign);
        } else {
            injector.inject(target, foreign);
            assertTrue(Objects.deepEquals(expected, oracle.get(target)),
                    what + " <- " + foreign + ": expected " + expected + " but the field holds " + oracle.get(target));
        }

        // 3. a long
        long longValue = (Long) randomValue(long.class, random);
        before = oracle.get(target);
        expected = fromLong(type, longValue);
        assertEquals(expected != NOT_INJECTED, injector.injectLong(target, longValue), what + " <- " + longValue);
        assertEquals(expected == NOT_INJECTED ? before : expected, oracle.get(target), what + " <- " + longValue);

        // 4. a double
        double doubleValue = (Double) randomValue(double.class, random);
        before = oracle.get(target);
        expected = fromDouble(type, doubleValue);
        assertEquals(expected != NOT_INJECTED, injector.injectDouble(target, doubleValue),
                what + " <- " + doubleValue);
        assertEquals(expected == NOT_INJECTED ? before : expected, oracle.get(target), what + " <- " + doubleValue);
    }

    /** What injectLong leaves in a field of this type: the long narrowed or widened as a Java cast does it. */
    private static Object fromLong(Class<?> type, long v) {
        if (type == long.class || type == Long.class) {
            return v;
        }
        if (type == int.class || type == Integer.class) {
            return (int) v;
        }
        if (type == short.class || type == Short.class) {
            return (short) v;
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) v;
        }
        if (type == float.class || type == Float.class) {
            return (float) v;
        }
        if (type == double.class || type == Double.class) {
            return (double) v;
        }
        return NOT_INJECTED;
    }

    /** What injectDouble leaves in a field of this type; a short or byte field is left to the general path. */
    private static Object fromDouble(Class<?> type, double v) {
        if (type == double.class || type == Double.class) {
            return v;
        }
        if (type == float.class || type == Float.class) {
            return (float) v;
        }
        if (type == long.class || type == Long.class) {
            return (long) v;
        }
        if (type == int.class || type == Integer.class) {
            return (int) v;
        }
        return NOT_INJECTED;
    }

    private static void assertDescribes(Injector injector, Field field, String displayName) {
        String what = field.toString();
        Class<?> type = field.getType();
        assertEquals(field.getName(), injector.getName(), what);
        assertEquals(displayName, injector.getDisplayName(), what);
        assertEquals("unique." + field.getName(), injector.getUniqueFieldName(), what);
        assertEquals(type, injector.getType(), what);
        assertEquals(field.getGenericType(), injector.getGenericType(), what);

        byte fastPath = Injector.FAST_PATH_GENERIC;
        if (type == boolean.class) {
            fastPath = Injector.FAST_PATH_BOOLEAN;
        } else if (type == String.class) {
            fastPath = Injector.FAST_PATH_STRING;
        } else if (type.isPrimitive() && type != char.class) {
            fastPath = Injector.FAST_PATH_PRIMITIVE_NUMERIC;
        }
        assertEquals(fastPath, injector.getFastPath(), what);
    }

    // ---------------------------------------------------------------- the values

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

    private static String setterName(Field field) {
        String name = field.getName();
        return "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Field opened(Field field) {
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("deprecation")    // Field.isAccessible: the replacement, canAccess, is not in Java 8
    private static boolean isAccessible(Field field) {
        return field.isAccessible();
    }

    private static Class<?> wrapper(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        return type == float.class ? Float.class : Double.class;
    }

    /** A value that is not of most fields' type: the Converter turns it into the field's type, or refuses. */
    private static Object foreignValue(Random random) {
        switch (random.nextInt(6)) {
            case 0:
                return (long) random.nextInt(100);
            case 1:
                return String.valueOf(random.nextInt(100));
            case 2:
                return random.nextInt(100);
            case 3:
                return (double) random.nextInt(100);
            case 4:
                return random.nextBoolean();
            default:
                return "not a number";
        }
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
