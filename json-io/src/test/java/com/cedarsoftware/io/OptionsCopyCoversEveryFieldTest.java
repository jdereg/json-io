package com.cedarsoftware.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code new WriteOptionsBuilder(WriteOptions)} and {@code new ReadOptionsBuilder(ReadOptions)} are documented as
 * "a copy as a starting point", and they copy field by field, by hand. A field added to the options and not added to
 * the copy is copied as its default, silently: {@code enumWriter} was left out in 2024, so options copied from one
 * that wrote enums as objects wrote them as bare strings, and nothing said so.
 * <p>
 * This test finds such a field the day it is added, by reflection. For every field of the options class it changes
 * the field on an options object to a value that cannot be the default, copies that options object, and asks whether
 * the copy holds the value. A field that is not meant to be copied must be named in the exemption list below with the
 * reason; so a new field is either copied, or someone has said why not.
 */
class OptionsCopyCoversEveryFieldTest {

    /** Fields the copy deliberately does not carry over as they stand, with the reason. */
    private static final Map<String, String> WRITE_EXEMPT = new LinkedHashMap<>();
    private static final Map<String, String> READ_EXEMPT = new LinkedHashMap<>();

    static {
        String cache = "a per-instance cache, rebuilt empty for the copy";
        WRITE_EXEMPT.put("customWriterGateCache", cache);
        WRITE_EXEMPT.put("accessorsCache", cache);
        WRITE_EXEMPT.put("writeFieldPlanCache", cache);
        WRITE_EXEMPT.put("classMetaCache", cache);
        WRITE_EXEMPT.put("toonWriteTypeCache", cache);
        WRITE_EXEMPT.put("aliasesShared", "set by the copy itself: the alias map is shared until the copy changes it");

        READ_EXEMPT.put("returningJsonObjects", cache);
        READ_EXEMPT.put("readingTreeText", cache);
        READ_EXEMPT.put("converter", cache);
        READ_EXEMPT.put("aliasesShared", "set by the copy itself: the alias map is shared until the copy changes it");
    }

    @Test
    void writeOptionsCopyCarriesEveryField() throws Exception {
        List<String> missed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Field builderOptions = WriteOptionsBuilder.class.getDeclaredField("options");
        builderOptions.setAccessible(true);

        for (Field field : instanceFields(WriteOptionsBuilder.DefaultWriteOptions.class)) {
            seen.add(field.getName());
            if (WRITE_EXEMPT.containsKey(field.getName()) || isCache(field)) {
                continue;
            }
            WriteOptions base = new WriteOptionsBuilder().build();
            Object changed = changeTo(field, base);
            WriteOptions copy = (WriteOptions) builderOptions.get(new WriteOptionsBuilder(base));
            if (!sameValue(changed, field.get(copy))) {
                missed.add(field.getName() + " (" + field.getGenericType() + ")");
            }
        }
        assertThat(WRITE_EXEMPT.keySet()).as("exemptions naming a field that is gone").isSubsetOf(seen);
        assertThat(missed).as("fields of WriteOptions that new WriteOptionsBuilder(WriteOptions) does not copy").isEmpty();
    }

    @Test
    void readOptionsCopyCarriesEveryField() throws Exception {
        List<String> missed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Field builderOptions = ReadOptionsBuilder.class.getDeclaredField("options");
        builderOptions.setAccessible(true);

        for (Field field : instanceFields(ReadOptionsBuilder.DefaultReadOptions.class)) {
            seen.add(field.getName());
            if (READ_EXEMPT.containsKey(field.getName()) || isCache(field)) {
                continue;
            }
            ReadOptions base = new ReadOptionsBuilder().build();
            Object changed = changeTo(field, base);
            ReadOptions copy = (ReadOptions) builderOptions.get(new ReadOptionsBuilder(base));
            if (!sameValue(changed, field.get(copy))) {
                missed.add(field.getName() + " (" + field.getGenericType() + ")");
            }
        }
        assertThat(READ_EXEMPT.keySet()).as("exemptions naming a field that is gone").isSubsetOf(seen);
        assertThat(missed).as("fields of ReadOptions that new ReadOptionsBuilder(ReadOptions) does not copy").isEmpty();
    }

    /** The test must be able to see the fields, or it would pass on an options class it can no longer read. */
    @Test
    void theTestSeesTheFieldsItChecks() {
        assertThat(names(instanceFields(WriteOptionsBuilder.DefaultWriteOptions.class)))
                .contains("enumWriter", "useGetters", "showTypeInfo", "maxObjectGraphDepth", "customOptions");
        assertThat(names(instanceFields(ReadOptionsBuilder.DefaultReadOptions.class)))
                .contains("useSetters", "returnType", "maxDepth", "customOptions");
    }

    /** A final field, or a ClassValue, is a per-instance cache that is built with the options, never a setting. */
    private static boolean isCache(Field field) {
        return Modifier.isFinal(field.getModifiers()) || ClassValue.class.isAssignableFrom(field.getType());
    }

    private static Set<String> names(List<Field> fields) {
        Set<String> names = new TreeSet<>();
        for (Field field : fields) {
            names.add(field.getName());
        }
        return names;
    }

    private static List<Field> instanceFields(Class<?> c) {
        List<Field> fields = new ArrayList<>();
        for (Field field : c.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    /** Set the field of {@code options} to a value that is not the one it has now, and return that value. */
    private static Object changeTo(Field field, Object options) throws Exception {
        Object current = field.get(options);
        Object changed = other(field.getGenericType(), current);
        field.set(options, changed);
        return changed;
    }

    private static boolean sameValue(Object expected, Object actual) {
        return expected == actual || (expected != null && expected.equals(actual));
    }

    /** A value of {@code type} that is not {@code current}; a collection comes with one element so it is not empty. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object other(Type type, Object current) throws Exception {
        Class<?> raw = type instanceof Class ? (Class<?>) type
                : type instanceof ParameterizedType ? (Class<?>) ((ParameterizedType) type).getRawType()
                : type instanceof WildcardType ? Object.class : Object.class;

        if (raw == boolean.class || raw == Boolean.class) {
            return current == null ? Boolean.TRUE : !((Boolean) current);
        }
        if (raw == int.class || raw == Integer.class) {
            return current == null ? 7 : ((Integer) current) + 7;
        }
        if (raw == long.class || raw == Long.class) {
            return current == null ? 7L : ((Long) current) + 7L;
        }
        if (raw == float.class) {
            return ((Float) current) + 7f;
        }
        if (raw == double.class) {
            return ((Double) current) + 7d;
        }
        if (raw == char.class || raw == Character.class) {
            return current != null && ((Character) current) == 'Z' ? 'Y' : 'Z';
        }
        if (raw == String.class) {
            return "sentinel".equals(current) ? "sentinel2" : "sentinel";
        }
        if (raw.isEnum()) {
            for (Object constant : raw.getEnumConstants()) {
                if (!constant.equals(current)) {
                    return constant;
                }
            }
            throw new AssertionError("an enum with one constant cannot be changed: " + raw);
        }
        if (raw == Class.class) {
            return current == Thread.class ? Runnable.class : Thread.class;
        }
        if (raw == ClassLoader.class) {
            return new URLClassLoader(new URL[0]);
        }
        if (Map.class.isAssignableFrom(raw)) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            Map map = new LinkedHashMap();
            map.put(other(args[0], null), other(args[1], null));
            return map;
        }
        if (Set.class.isAssignableFrom(raw)) {
            Set set = new LinkedHashSet();
            set.add(other(((ParameterizedType) type).getActualTypeArguments()[0], null));
            return set;
        }
        if (List.class.isAssignableFrom(raw)) {
            List list = new ArrayList();
            list.add(other(((ParameterizedType) type).getActualTypeArguments()[0], null));
            return list;
        }
        if (raw.isInterface()) {
            InvocationHandler handler = (proxy, method, args) -> {
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(method.getName())) {
                    return "sentinel " + raw.getSimpleName();
                }
                throw new UnsupportedOperationException(method.toString());
            };
            return Proxy.newProxyInstance(raw.getClassLoader(), new Class<?>[]{raw}, handler);
        }
        for (Constructor<?> constructor : raw.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                constructor.setAccessible(true);
                return constructor.newInstance();
            }
        }
        throw new AssertionError("teach other() to make a value of " + type + " (the field is new, or its type is)"
                + " -- " + Arrays.toString(raw.getDeclaredConstructors()));
    }
}
