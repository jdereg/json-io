package com.cedarsoftware.io;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.cedarsoftware.io.annotation.IoNaming;
import com.cedarsoftware.io.reflect.filters.field.StaticFieldFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A permanent setting ({@code WriteOptionsBuilder.addPermanent...}, {@code ReadOptionsBuilder.addPermanent...})
 * reaches the calls that pass no options, such as {@code JsonIo.toJson(obj, null)}. Through 4.113.0 it did not:
 * the options used for those calls were built when the builder class was loaded, before any permanent setting
 * could have been made, and never again. Only options built with a new builder had the setting.
 *
 * <p>Every test here restores what it changed; the settings are JVM-wide.</p>
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
class PermanentSettingsReachNullOptionsTest {

    public static class Reading {
        private int value = 1;
        private String label = "raw";
        private Long big = 5L;
        private Object nothing;

        public int getValue() {
            return value + 98;
        }

        public String getLabel() {
            return label.toUpperCase();
        }

        public void setValue(int value) {
            this.value = value + 1000;
        }

        public void setLabel(String label) {
            this.label = "set:" + label;
        }
    }

    private static final String FIELDS = "{\"@type\":\"" + Reading.class.getName()
            + "\",\"value\":1,\"label\":\"raw\",\"big\":5,\"nothing\":null}";

    // ---------------------------------------------------------------- writing

    @Test
    void aPermanentWriteSettingReachesACallWithNoOptions() {
        assertEquals(FIELDS, JsonIo.toJson(new Reading(), null));
        try {
            WriteOptionsBuilder.addPermanentUseGetters(true);
            assertEquals(FIELDS.replace("\"value\":1,\"label\":\"raw\"", "\"value\":99,\"label\":\"RAW\""),
                    JsonIo.toJson(new Reading(), null));

            WriteOptionsBuilder.addPermanentUseGetters(false);
            WriteOptionsBuilder.addPermanentWriteLongsAsStrings(true);
            WriteOptionsBuilder.addPermanentSkipNullFields(true);
            assertEquals(FIELDS.replace("\"big\":5,\"nothing\":null", "\"big\":\"5\""),
                    JsonIo.toJson(new Reading(), null));
            // ... and is what a new builder starts from, as it always was
            assertEquals(JsonIo.toJson(new Reading(), new WriteOptionsBuilder().build()),
                    JsonIo.toJson(new Reading(), null));
        } finally {
            WriteOptionsBuilder.addPermanentUseGetters(false);
            WriteOptionsBuilder.addPermanentWriteLongsAsStrings(false);
            WriteOptionsBuilder.addPermanentSkipNullFields(false);
        }
        assertEquals(FIELDS, JsonIo.toJson(new Reading(), null));
    }

    @Test
    void aPermanentAliasReachesACallWithNoOptions() {
        String alias = "permanentReading";
        // the options for calls with none are in use before the setting is made
        assertEquals(FIELDS, JsonIo.toJson(new Reading(), null));
        assertEquals(Reading.class, JsonIo.toJava(FIELDS, null).asClass(Object.class).getClass());
        try {
            WriteOptionsBuilder.addPermanentAlias(Reading.class, alias);
            ReadOptionsBuilder.addPermanentAlias(Reading.class, alias);

            String json = JsonIo.toJson(new Reading(), null);
            assertEquals(FIELDS.replace(Reading.class.getName(), alias), json);
            assertEquals(Reading.class, JsonIo.toJava(json, null).asClass(Object.class).getClass());
        } finally {
            WriteOptionsBuilder.removePermanentAliasTypeNamesMatching(Reading.class.getName());
            ReadOptionsBuilder.removePermanentAliasTypeNamesMatching(alias);
        }
        assertEquals(FIELDS, JsonIo.toJson(new Reading(), null));
    }

    /** TOON's own defaults for a call with no options (no type info, no references) come with the settings too. */
    @Test
    void aPermanentSettingReachesAToonCallWithNoOptions() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("at", new LinkedHashMap<>(java.util.Collections.singletonMap("x", 2L)));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("rows", new ArrayList<>(Arrays.asList(row, new LinkedHashMap<>(row))));
        doc.put("tags", new ArrayList<>(Arrays.asList("a", "b")));

        assertEquals("rows[2]{id,at{x}}:\n  1,2\n  1,2\ntags[2]: a,b", JsonIo.toToon(doc, null));
        try {
            WriteOptionsBuilder.addPermanentToonDelimiter('|');
            assertEquals("rows[2|]{id|at{x}}:\n  1|2\n  1|2\ntags[2|]: a|b", JsonIo.toToon(doc, null));

            WriteOptionsBuilder.addPermanentToonDelimiter(',');
            WriteOptionsBuilder.addPermanentToonLegacyForms(true);
            assertEquals("rows[2]:\n  - id: 1\n    at:\n      x: 2\n  - id: 1\n    at:\n      x: 2\ntags[2]: a,b",
                    JsonIo.toToon(doc, null));
        } finally {
            WriteOptionsBuilder.addPermanentToonDelimiter(',');
            WriteOptionsBuilder.addPermanentToonLegacyForms(false);
        }
        assertEquals("rows[2]{id,at{x}}:\n  1,2\n  1,2\ntags[2]: a,b", JsonIo.toToon(doc, null));
    }

    /** The streaming generator made with no options follows the permanent settings as well. */
    @Test
    void aPermanentSettingReachesAGeneratorMadeWithNoOptions() throws Exception {
        int original = new WriteOptionsBuilder().build().getMaxStringLength();
        JsonIo.createGenerator(new StringWriter(), null).close();      // in use before the setting is made
        try {
            WriteOptionsBuilder.addPermanentMaxStringLength(8);
            StringWriter out = new StringWriter();
            JsonGenerator generator = JsonIo.createGenerator(out, null);
            assertThrows(Exception.class, () -> generator.writeString("longer than eight characters"));
        } finally {
            WriteOptionsBuilder.addPermanentMaxStringLength(original);
        }
        StringWriter out = new StringWriter();
        try (JsonGenerator generator = JsonIo.createGenerator(out, null)) {
            generator.writeString("longer than eight characters");
        }
        assertEquals("\"longer than eight characters\"", out.toString());
    }

    /** deepCopy with no options writes and reads with the permanent settings too. */
    @Test
    void aPermanentSettingReachesADeepCopyMadeWithNoOptions() {
        assertEquals(1, JsonIo.deepCopy(new Reading(), null, null).value);
        try {
            WriteOptionsBuilder.addPermanentUseGetters(true);
            assertEquals(99, JsonIo.deepCopy(new Reading(), null, null).value, "the getter's answer was copied");
        } finally {
            WriteOptionsBuilder.addPermanentUseGetters(false);
        }
        assertEquals(1, JsonIo.deepCopy(new Reading(), null, null).value);
    }

    // ---------------------------------------------------------------- reading

    @Test
    void aPermanentReadSettingReachesACallWithNoOptions() {
        String json = "{\"value\":5,\"label\":\"a\"}";
        Reading assigned = JsonIo.toJava(json, null).asClass(Reading.class);
        assertEquals(5, assigned.value);
        assertEquals("a", assigned.label);
        try {
            ReadOptionsBuilder.addPermanentUseSetters(true);
            Reading set = JsonIo.toJava(json, null).asClass(Reading.class);
            assertEquals(1005, set.value);
            assertEquals("set:a", set.label);
            assertEquals(1005, JsonIo.fromToon("value: 5\nlabel: a", null).asClass(Reading.class).value);
        } finally {
            ReadOptionsBuilder.addPermanentUseSetters(false);
        }
        assertEquals(5, JsonIo.toJava(json, null).asClass(Reading.class).value);
        assertEquals(5, JsonIo.fromToon("value: 5\nlabel: a", null).asClass(Reading.class).value);
    }

    @Test
    void aPermanentReadLimitReachesACallWithNoOptions() {
        String nested = "[[[[[[1]]]]]]";
        int original = new ReadOptionsBuilder().build().getMaxDepth();
        assertEquals(1, ((Object[]) JsonIo.toJava(nested, null).asClass(Object[].class)).length);
        try {
            ReadOptionsBuilder.addPermanentMaxDepth(3);
            assertThrows(JsonIoException.class, () -> JsonIo.toJava(nested, null).asClass(Object[].class));
        } finally {
            ReadOptionsBuilder.addPermanentMaxDepth(original);
        }
        assertEquals(1, ((Object[]) JsonIo.toJava(nested, null).asClass(Object[].class)).length);
    }

    /** The streaming tokenizer made with no options follows the permanent settings as well. */
    @Test
    void aPermanentSettingReachesATokenizerMadeWithNoOptions() throws Exception {
        boolean original = new ReadOptionsBuilder().build().isAllowNanAndInfinity();
        try {
            ReadOptionsBuilder.addPermanentAllowNanAndInfinity(false);
            assertThrows(Exception.class, () -> tokensOf("[NaN]"));

            ReadOptionsBuilder.addPermanentAllowNanAndInfinity(true);
            assertEquals(3, tokensOf("[NaN]"));
        } finally {
            ReadOptionsBuilder.addPermanentAllowNanAndInfinity(original);
        }
    }

    private static int tokensOf(String json) throws Exception {
        int tokens = 0;
        try (JsonTokenizer tokenizer = JsonIo.createTokenizer(json, null)) {
            while (tokenizer.nextToken() != null) {
                tokens++;
            }
        }
        return tokens;
    }

    // ---------------------------------------------------------------- the default options themselves

    /** One instance while nothing changes, as before; a new one after a permanent setting. */
    @Test
    void theDefaultOptionsAreBuiltAgainOnlyAfterAPermanentSetting() {
        WriteOptions write = WriteOptionsBuilder.getDefaultWriteOptions();
        ReadOptions read = ReadOptionsBuilder.getDefaultReadOptions();
        assertSame(write, WriteOptionsBuilder.getDefaultWriteOptions());
        assertSame(read, ReadOptionsBuilder.getDefaultReadOptions());
        assertFalse(write.isUseGetters());
        assertFalse(read.isUseSetters());
        try {
            WriteOptionsBuilder.addPermanentUseGetters(true);
            WriteOptions changed = WriteOptionsBuilder.getDefaultWriteOptions();
            assertNotSame(write, changed);
            assertTrue(changed.isUseGetters());
            assertSame(changed, WriteOptionsBuilder.getDefaultWriteOptions());
            assertSame(read, ReadOptionsBuilder.getDefaultReadOptions(), "a write setting leaves the read options");

            ReadOptionsBuilder.addPermanentUseSetters(true);
            assertTrue(ReadOptionsBuilder.getDefaultReadOptions().isUseSetters());
            assertSame(changed, WriteOptionsBuilder.getDefaultWriteOptions(),
                    "a read setting leaves the write options");
        } finally {
            WriteOptionsBuilder.addPermanentUseGetters(false);
            ReadOptionsBuilder.addPermanentUseSetters(false);
        }
        assertFalse(WriteOptionsBuilder.getDefaultWriteOptions().isUseGetters());
        assertFalse(ReadOptionsBuilder.getDefaultReadOptions().isUseSetters());
    }

    /**
     * Every permanent setting that takes one number, boolean or character is counted: each is called here with the
     * value it already has, which changes nothing, and the count the cached options follow must move on. A setting
     * added later that forgets to count would be the old defect again, for that setting.
     */
    @Test
    void everyScalarPermanentSettingIsCounted() throws Exception {
        TreeSet<String> counted = new TreeSet<>();
        List<String> unmatched = new ArrayList<>();
        for (Class<?> builder : new Class<?>[] {WriteOptionsBuilder.class, ReadOptionsBuilder.class}) {
            Method version = builder.getDeclaredMethod("permanentVersion");
            version.setAccessible(true);
            for (Method method : builder.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())
                        || !method.getName().startsWith("addPermanent") || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isPrimitive()) {
                    continue;
                }
                String name = builder.getSimpleName() + "." + method.getName();
                Field base = settingFieldOf(builder, method.getName());
                if (base == null || base.getType() != method.getParameterTypes()[0]) {
                    unmatched.add(name);
                    continue;
                }
                Object current = base.get(null);
                int before = (Integer) version.invoke(null);
                method.invoke(null, current);
                assertTrue((Integer) version.invoke(null) > before, name + " is not counted");
                assertEquals(current, base.get(null), name);
                counted.add(name);
            }
        }
        assertTrue(unmatched.isEmpty(), "no field found for " + unmatched);
        assertTrue(counted.size() >= 45, counted.size() + " settings checked: " + counted);
        assertTrue(counted.contains("WriteOptionsBuilder.addPermanentToonLegacyForms"), counted.toString());
        assertTrue(counted.contains("ReadOptionsBuilder.addPermanentUseSetters"), counted.toString());
    }

    /** Nothing else uses this class, so what the sweep below leaves behind for it touches no other test. */
    public static class Probe {
        private int x;

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }
    }

    /**
     * The permanent settings that take a class, a name or an object: each is called with something harmless (the
     * probe class, a name only used here, or the value already in force) and must move the count on. The last
     * check is the point: a permanent setting added to either builder later fails here until it is in a sweep.
     */
    @Test
    void everyOtherPermanentSettingIsCounted() throws Exception {
        Map<String, Runnable> write = new LinkedHashMap<>();
        String probeName = Probe.class.getName();
        Class<?> wob = WriteOptionsBuilder.class;
        WriteOptions.ShowType showType = (WriteOptions.ShowType) staticValue(wob, "BASE_SHOW_TYPE_INFO");
        IoNaming.Strategy naming = (IoNaming.Strategy) staticValue(wob, "BASE_NAMING_STRATEGY");
        ClassLoader loader = (ClassLoader) staticValue(wob, "BASE_CLASS_LOADER");
        write.put("addPermanentAlias", () -> WriteOptionsBuilder.addPermanentAlias(Probe.class, "permanentProbe"));
        write.put("removePermanentAliasTypeNamesMatching",
                () -> WriteOptionsBuilder.removePermanentAliasTypeNamesMatching(probeName));
        write.put("addPermanentExcludedField", () -> WriteOptionsBuilder.addPermanentExcludedField(Probe.class, "x"));
        write.put("addPermanentWriteOnlyField", () -> WriteOptionsBuilder.addPermanentWriteOnlyField(Probe.class, "x"));
        write.put("addPermanentDeserializeOnlyField",
                () -> WriteOptionsBuilder.addPermanentDeserializeOnlyField(Probe.class, "x"));
        write.put("addPermanentNonRef", () -> WriteOptionsBuilder.addPermanentNonRef(Probe.class));
        write.put("addPermanentNotCustomWrittenClass",
                () -> WriteOptionsBuilder.addPermanentNotCustomWrittenClass(Probe.class));
        write.put("addPermanentWriter",
                () -> WriteOptionsBuilder.addPermanentWriter(Probe.class, new JsonClassWriter<Probe>() { }));
        write.put("addPermanentNonStandardGetter",
                () -> WriteOptionsBuilder.addPermanentNonStandardGetter(Probe.class, "x", "getX"));
        write.put("addPermanentFieldFilter",
                () -> WriteOptionsBuilder.addPermanentFieldFilter("static", new StaticFieldFilter()));
        write.put("addPermanentMethodFilter",
                () -> WriteOptionsBuilder.addPermanentMethodFilter("permanentProbe", (clazz, method) -> false));
        write.put("addPermanentNamedMethodFilter",        // under the same name, so the next line removes both
                () -> WriteOptionsBuilder.addPermanentNamedMethodFilter("permanentProbe", Probe.class, "getX"));
        write.put("removePermanentMethodFilter",
                () -> WriteOptionsBuilder.removePermanentMethodFilter("permanentProbe"));
        write.put("addPermanentAccessorFactory", () -> WriteOptionsBuilder.addPermanentAccessorFactory(
                "permanentProbe", (field, nonStandardGetters, uniqueFieldName) -> null));
        write.put("removePermanentAccessorFactory",
                () -> WriteOptionsBuilder.removePermanentAccessorFactory("permanentProbe"));
        write.put("addPermanentClassLoader", () -> WriteOptionsBuilder.addPermanentClassLoader(loader));
        write.put("addPermanentShowTypeInfoAlways", WriteOptionsBuilder::addPermanentShowTypeInfoAlways);
        write.put("addPermanentShowTypeInfoNever", WriteOptionsBuilder::addPermanentShowTypeInfoNever);
        write.put("addPermanentShowTypeInfoMinimalPlus", WriteOptionsBuilder::addPermanentShowTypeInfoMinimalPlus);
        write.put("addPermanentShowTypeInfoMinimalLegacy", WriteOptionsBuilder::addPermanentShowTypeInfoMinimalLegacy);
        write.put("addPermanentShowTypeInfoMinimal", WriteOptionsBuilder::addPermanentShowTypeInfoMinimal);
        write.put("addPermanentNamingStrategy", () -> WriteOptionsBuilder.addPermanentNamingStrategy(naming));

        Map<String, Runnable> read = new LinkedHashMap<>();
        ClassFactory factory = new ClassFactory() { };
        read.put("addPermanentClassFactory", () -> ReadOptionsBuilder.addPermanentClassFactory(Probe.class, factory));
        read.put("assignInstantiator", () -> ReadOptionsBuilder.assignInstantiator(Probe.class, factory));
        read.put("addPermanentCoercedType", () -> ReadOptionsBuilder.addPermanentCoercedType(Probe.class, Probe.class));
        read.put("addPermanentAlias", () -> ReadOptionsBuilder.addPermanentAlias(Probe.class, "permanentProbe"));
        read.put("removePermanentAliasTypeNamesMatching",
                () -> ReadOptionsBuilder.removePermanentAliasTypeNamesMatching(probeName));
        read.put("addPermanentReader",
                () -> ReadOptionsBuilder.addPermanentReader(Probe.class, new JsonClassReader<Probe>() { }));
        read.put("addPermanentNonReferenceableClass",
                () -> ReadOptionsBuilder.addPermanentNonReferenceableClass(Probe.class));
        read.put("addPermanentNotCustomReadClass",
                () -> ReadOptionsBuilder.addPermanentNotCustomReadClass(Probe.class));
        read.put("addPermanentNotImportedField",
                () -> ReadOptionsBuilder.addPermanentNotImportedField(Probe.class, "x"));
        read.put("addPermanentReadOnlyField", () -> ReadOptionsBuilder.addPermanentReadOnlyField(Probe.class, "x"));
        read.put("addPermanentSerializeOnlyField",
                () -> ReadOptionsBuilder.addPermanentSerializeOnlyField(Probe.class, "x"));
        read.put("addPermanentNonStandardSetter",
                () -> ReadOptionsBuilder.addPermanentNonStandardSetter(Probe.class, "x", "setX"));

        try {
            assertEachIsCounted(WriteOptionsBuilder.class, write);
            assertEachIsCounted(ReadOptionsBuilder.class, read);
        } finally {
            WriteOptionsBuilder.class.getMethod("addPermanentShowTypeInfo" + camel(showType.name())).invoke(null);
        }
        assertEquals(showType, staticValue(wob, "BASE_SHOW_TYPE_INFO"));
        assertEquals(otherPermanentSettingsOf(WriteOptionsBuilder.class), new TreeSet<>(write.keySet()));
        assertEquals(otherPermanentSettingsOf(ReadOptionsBuilder.class), new TreeSet<>(read.keySet()));
    }

    private static void assertEachIsCounted(Class<?> builder, Map<String, Runnable> settings) throws Exception {
        Method version = builder.getDeclaredMethod("permanentVersion");
        version.setAccessible(true);
        for (Map.Entry<String, Runnable> setting : settings.entrySet()) {
            int before = (Integer) version.invoke(null);
            setting.getValue().run();
            assertTrue((Integer) version.invoke(null) > before,
                    builder.getSimpleName() + "." + setting.getKey() + " is not counted");
        }
    }

    /** The public permanent settings of a builder that the sweep of one-primitive settings does not reach. */
    private static TreeSet<String> otherPermanentSettingsOf(Class<?> builder) {
        TreeSet<String> names = new TreeSet<>();
        for (Method method : builder.getDeclaredMethods()) {
            String name = method.getName();
            boolean permanent = name.startsWith("addPermanent") || name.startsWith("removePermanent")
                    || name.equals("assignInstantiator");
            boolean onePrimitive = method.getParameterCount() == 1 && method.getParameterTypes()[0].isPrimitive();
            if (permanent && !onePrimitive && Modifier.isStatic(method.getModifiers())
                    && Modifier.isPublic(method.getModifiers())) {
                names.add(name);
            }
        }
        return names;
    }

    private static Object staticValue(Class<?> owner, String fieldName) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    /** MINIMAL_PLUS -> MinimalPlus */
    private static String camel(String upperSnake) {
        StringBuilder out = new StringBuilder();
        for (String word : upperSnake.split("_")) {
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return out.toString();
    }

    /** The field a setting is kept in: addPermanentMaxDepth -> BASE_MAX_DEPTH, or permanentMaxDepth. */
    private static Field settingFieldOf(Class<?> builder, String methodName) {
        String setting = methodName.substring("addPermanent".length());
        StringBuilder base = new StringBuilder("BASE");
        for (char c : setting.toCharArray()) {
            if (Character.isUpperCase(c)) {
                base.append('_');
            }
            base.append(Character.toUpperCase(c));
        }
        for (String name : new String[] {base.toString(), "permanent" + setting}) {
            try {
                Field field = builder.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // the other spelling
            }
        }
        return null;
    }
}
