package com.cedarsoftware.io;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application that puts its own config/aliases.txt ahead of json-io's -- n-cube does, so that it keeps writing the
 * names its older clients read -- decides the names json-io writes; json-io still reads the names every copy
 * defines, its own included. The application's copy took json-io's place for reading too: "ArrayList", which json-io
 * writes, was "Unknown type (class) 'ArrayList' not defined", so n-cube could not read the pull requests it stored in
 * the months it wrote json-io's names. json-io is loaded here in a class loader of its own, the application's copy
 * first, as on such an application's class path.
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
class AliasesOfEveryCopyAreReadTest {

    /** The application's copy: n-cube's names for Integer and String; no alias for ArrayList. */
    private static final String APPLICATIONS_COPY = "java.lang.Integer = int\njava.lang.String = string\n";

    /** The JDK's classes and none of the class path's: the platform class loader on JDK 9+, the bootstrap on 8. */
    private static ClassLoader platformLoader() throws Exception {
        try {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (NoSuchMethodException jdk8) {
            return null;
        }
    }

    @Test
    void theFirstCopyNamesWhatIsWrittenAndEveryCopyIsRead() throws Exception {
        File dir = Files.createTempDirectory("aliases").toFile();
        File config = new File(dir, "config");
        assertTrue(config.mkdirs());
        File copy = new File(config, "aliases.txt");
        Files.write(copy.toPath(), APPLICATIONS_COPY.getBytes(StandardCharsets.UTF_8));
        URL jsonIo = JsonIo.class.getProtectionDomain().getCodeSource().getLocation();
        URL javaUtil = ClassUtilities.class.getProtectionDomain().getCodeSource().getLocation();

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(new URL[] {dir.toURI().toURL(), jsonIo, javaUtil},
                platformLoader())) {
            Thread.currentThread().setContextClassLoader(isolated);
            Class<?> io = isolated.loadClass("com.cedarsoftware.io.JsonIo");
            Method toJson = io.getMethod("toJson", Object.class, isolated.loadClass("com.cedarsoftware.io.WriteOptions"));
            Method toJava = io.getMethod("toJava", String.class, isolated.loadClass("com.cedarsoftware.io.ReadOptions"));
            Method asClass = toJava.getReturnType().getMethod("asClass", Class.class);

            // Written: the application's names
            String json = (String) toJson.invoke(null, new ArrayList<Object>(Arrays.asList(1, "s")), null);
            assertEquals("{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@type\":\"int\",\"value\":1},\"s\"]}",
                    json);

            // Read: the application's names, json-io's, and full class names
            String names = "{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"int\",\"value\":1},"
                    + "{\"@type\":\"Integer\",\"value\":2},{\"@type\":\"string\",\"value\":\"s\"},"
                    + "{\"@type\":\"java.lang.Integer\",\"value\":3},{\"@type\":\"LinkedHashMap\",\"k\":\"v\"}]}";
            List<?> back = (List<?>) asClass.invoke(toJava.invoke(null, names, null), (Object) null);
            assertEquals(ArrayList.class, back.getClass());
            assertEquals(Arrays.asList(1, 2, "s", 3), back.subList(0, 4));
            assertEquals(Integer.class, back.get(1).getClass());
            assertEquals("java.util.LinkedHashMap", back.get(4).getClass().getName());

            // A name removed for reading is removed whichever copy defines it
            Class<?> builder = isolated.loadClass("com.cedarsoftware.io.ReadOptionsBuilder");
            builder.getMethod("removePermanentAliasTypeNamesMatching", String.class).invoke(null, "ArrayList");
            Object afterward = builder.getMethod("build").invoke(builder.getConstructor().newInstance());
            Throwable removed = assertThrows(InvocationTargetException.class,
                    () -> asClass.invoke(toJava.invoke(null, names, afterward), (Object) null)).getCause();
            assertTrue(removed.getMessage().contains("Unknown type (class) 'ArrayList'"), removed.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            assertTrue(copy.delete() && config.delete() && dir.delete());
        }
    }
}
