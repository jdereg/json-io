package com.cedarsoftware.io;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TimeZone;

import com.cedarsoftware.util.CaseInsensitiveMap;
import com.cedarsoftware.util.ClassUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packaged jar works as a bundle in an OSGi framework (Apache Felix), beside java-util's bundle, run by failsafe
 * after package. Writing a double, the TOON writer and the tokenizer reach java-util's internal package, which
 * java-util exports to json-io alone; JSON naming a JDK-internal class (the class of every TimeZone) is read through
 * json-io's loader, which sees only java.* and its imports; and a type named in the JSON is the bundle's, not the copy
 * on the launcher's class path that the context class loader sees.
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
class OsgiBundleIT {

    private static Framework framework;
    private static Bundle javaUtil;
    private static Bundle jsonIo;
    private static Class<?> jsonIoClass;

    @BeforeAll
    static void startFramework() throws Exception {
        String jar = System.getProperty("json-io.jar");
        assertNotNull(jar, "failsafe sets json-io.jar to the packaged jar");
        File javaUtilJar = new File(ClassUtilities.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(javaUtilJar.isFile(), "java-util is a jar on the class path: " + javaUtilJar);

        Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE, Files.createTempDirectory("felix-json-io").toString());
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        framework = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config);
        framework.start();
        BundleContext context = framework.getBundleContext();
        javaUtil = context.installBundle(javaUtilJar.toURI().toString());
        jsonIo = context.installBundle(new File(jar).toURI().toString());
        javaUtil.start();
        jsonIo.start();
        jsonIoClass = jsonIo.loadClass("com.cedarsoftware.io.JsonIo");
    }

    @AfterAll
    static void stopFramework() throws Exception {
        if (framework != null) {
            framework.stop();
            framework.waitForStop(10_000);
        }
    }

    private static Object call(Class<?> type, String method, Object target, Class<?>[] parameters, Object... args)
            throws Exception {
        Method m = type.getMethod(method, parameters);
        return m.invoke(target, args);
    }

    private static String toJson(Object value) throws Exception {
        return (String) call(jsonIoClass, "toJson", null, new Class<?>[] {Object.class}, value);
    }

    private static Object toJava(String json, Class<?> type) throws Exception {
        Object builder = call(jsonIoClass, "toJava", null, new Class<?>[] {String.class}, json);
        return call(builder.getClass(), "asClass", builder, new Class<?>[] {Class.class}, type);
    }

    @Test
    void theBundlesResolveAndStart() {
        assertEquals(Bundle.ACTIVE, javaUtil.getState());
        assertEquals(Bundle.ACTIVE, jsonIo.getState());
    }

    @Test
    void itWritesAndReadsDoubles() throws Exception {
        double[] values = {1e23, 0.1, -2.5};
        String json = toJson(values);
        assertTrue(json.contains("[1.0E23,0.1,-2.5]"), json);
        assertArrayEquals(values, (double[]) toJava(json, double[].class));
    }

    @Test
    void itWritesToon() throws Exception {
        String toon = (String) call(jsonIoClass, "toToon", null, new Class<?>[] {Object.class},
                Arrays.asList(0x1p60, 1.5));
        assertEquals("[2]: 1152921504606847000,1.5", toon.trim());
    }

    /** 9.999999999999999E22 is the double 1e23: the token's text is the shortest decimal that reads back as it. */
    @Test
    void itTokenizesANumberToItsShortestText() throws Exception {
        Object tokenizer = call(jsonIoClass, "createTokenizer", null, new Class<?>[] {String.class},
                "[9.999999999999999E22]");
        Class<?> tokenizerClass = jsonIo.loadClass("com.cedarsoftware.io.JsonTokenizer");
        call(tokenizerClass, "nextToken", tokenizer, new Class<?>[0]);
        call(tokenizerClass, "nextToken", tokenizer, new Class<?>[0]);
        assertEquals("1.0E23", call(tokenizerClass, "getText", tokenizer, new Class<?>[0]));
    }

    /**
     * A TimeZone is written as its class, sun.util.calendar.ZoneInfo (by its alias), a JDK internal json-io's bundle
     * does not import; read back, it is that class, by its full name too.
     */
    @Test
    void itReadsAJdkInternalClassNamedInTheJson() throws Exception {
        TimeZone zone = TimeZone.getTimeZone("America/New_York");
        String json = toJson(zone);
        String named = "{\"@type\":\"" + zone.getClass().getName() + "\",\"value\":\"America/New_York\"}";
        for (String text : new String[] {json, named}) {
            Object back = toJava(text, null);
            assertEquals(zone.getClass(), back.getClass(), text);
            assertEquals("America/New_York", ((TimeZone) back).getID(), text);
        }
    }

    /** The launcher's class path holds its own java-util; the map read back is the bundle's class, not that copy. */
    @Test
    void aTypeNamedInTheJsonIsTheBundlesClass() throws Exception {
        Class<?> caseInsensitiveMap = javaUtil.loadClass("com.cedarsoftware.util.CaseInsensitiveMap");
        assertNotSame(CaseInsensitiveMap.class, caseInsensitiveMap);
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) caseInsensitiveMap.getConstructor().newInstance();
        map.put("Key", "value");
        Object back = toJava(toJson(map), null);
        assertSame(caseInsensitiveMap, back.getClass());
        assertEquals("value", ((Map<?, ?>) back).get("KEY"));
    }

    /** A CompactMap from the builder: its generated class is defined on java-util's bundle loader, and reads back. */
    @Test
    void itRoundTripsABuiltCompactMap() throws Exception {
        Class<?> compactMap = javaUtil.loadClass("com.cedarsoftware.util.CompactMap");
        Object builder = call(compactMap, "builder", null, new Class<?>[0]);
        builder = call(builder.getClass(), "caseSensitive", builder, new Class<?>[] {boolean.class}, false);
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) call(builder.getClass(), "build", builder, new Class<?>[0]);
        map.put("Key", "value");
        Object back = toJava(toJson(map), null);
        assertSame(map.getClass(), back.getClass());
        assertEquals("value", ((Map<?, ?>) back).get("KEY"));
    }
}
