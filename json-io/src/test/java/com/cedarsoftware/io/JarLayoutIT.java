package com.cedarsoftware.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packaged jar (run by failsafe, after package) serves JDK 8 and the module system both: every class a JDK 8 tool
 * reads is a JDK 8 class file, and module-info lives under META-INF/versions/9 of a multi-release jar, where only
 * JDK 9+ looks. It sat at the root as a Java 9 class file, which JDK 8 tools that read every class in a jar can fail
 * on. The OSGi headers stay as they were.
 */
class JarLayoutIT {

    private static JarFile jar() throws IOException {
        String path = System.getProperty("json-io.jar");
        assertNotNull(path, "failsafe sets json-io.jar to the packaged jar");
        return new JarFile(new File(path));
    }

    private static int majorVersion(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            byte[] head = new byte[8];
            int read = 0;
            while (read < 8) {
                int n = in.read(head, read, 8 - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return ((head[6] & 0xff) << 8) | (head[7] & 0xff);
        }
    }

    @Test
    void everyClassOutsideTheVersionedTreeIsAJdk8ClassFile() throws IOException {
        try (JarFile jar = jar()) {
            List<String> newer = new ArrayList<>();
            int classes = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                    classes++;
                    if (majorVersion(jar, entry) > 52) {
                        newer.add(name);
                    }
                }
            }
            assertTrue(classes > 100, classes + " classes");
            assertTrue(newer.isEmpty(), "newer than JDK 8: " + newer);
        }
    }

    @Test
    void moduleInfoIsUnderVersions9OfAMultiReleaseJar() throws IOException {
        try (JarFile jar = jar()) {
            assertNull(jar.getEntry("module-info.class"), "module-info.class at the root");
            JarEntry moduleInfo = jar.getJarEntry("META-INF/versions/9/module-info.class");
            assertNotNull(moduleInfo, "META-INF/versions/9/module-info.class");
            assertEquals(53, majorVersion(jar, moduleInfo));
            Attributes main = jar.getManifest().getMainAttributes();
            assertEquals("true", main.getValue("Multi-Release"));
        }
    }

    /**
     * On JDK 9+, the module the jar declares can be read: every package it exports is in the jar. It exported
     * com.cedarsoftware.io.prettyprint and com.cedarsoftware.io.reflect.injectors, which no longer exist, so the JVM
     * refused the module ("Package ... not found in module") and json-io could not be used on the module path.
     * Through reflection, as the tests compile for JDK 8.
     */
    @Test
    void theModuleDescriptorIsValid() throws Exception {
        Class<?> finderClass;
        try {
            finderClass = Class.forName("java.lang.module.ModuleFinder");
        } catch (ClassNotFoundException jdk8) {
            return;
        }
        java.nio.file.Path path = new File(System.getProperty("json-io.jar")).toPath();
        Object finder = finderClass.getMethod("of", java.nio.file.Path[].class)
                .invoke(null, (Object) new java.nio.file.Path[] {path});
        java.util.Set<?> modules;
        try {
            modules = (java.util.Set<?>) finderClass.getMethod("findAll").invoke(finder);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("the module cannot be read: " + e.getCause(), e.getCause());
        }
        assertEquals(1, modules.size());
        Object reference = modules.iterator().next();
        Object descriptor = reference.getClass().getMethod("descriptor").invoke(reference);
        assertEquals("com.cedarsoftware.io", descriptor.getClass().getMethod("name").invoke(descriptor));
    }

    /**
     * On JDK 9+, json-io runs as a named module, with java-util as one: a Map is written and read back. Its config
     * files (config/*.txt) are in a package of the module that is not opened, which no class loader can see from
     * outside the module, so json-io failed on first use ("Resource not found: config/classFactory.txt"). The two
     * jars are loaded into a module layer of their own; the context class loader is set to the platform loader, so
     * nothing is found through the json-io jar that is also on this test's class path.
     */
    @Test
    void jsonIoRunsAsANamedModule() throws Exception {
        Class<?> finderClass;
        try {
            finderClass = Class.forName("java.lang.module.ModuleFinder");
        } catch (ClassNotFoundException jdk8) {
            return;
        }
        java.nio.file.Path jsonIo = new File(System.getProperty("json-io.jar")).toPath();
        java.nio.file.Path javaUtil = new File(com.cedarsoftware.util.Converter.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toPath();
        Object finder = finderClass.getMethod("of", java.nio.file.Path[].class)
                .invoke(null, (Object) new java.nio.file.Path[] {jsonIo, javaUtil});
        Object none = finderClass.getMethod("of", java.nio.file.Path[].class)
                .invoke(null, (Object) new java.nio.file.Path[0]);
        Class<?> layerClass = Class.forName("java.lang.ModuleLayer");
        Object boot = layerClass.getMethod("boot").invoke(null);
        Object bootConfiguration = layerClass.getMethod("configuration").invoke(boot);
        Object configuration = bootConfiguration.getClass()
                .getMethod("resolve", finderClass, finderClass, java.util.Collection.class)
                .invoke(bootConfiguration, finder, none, java.util.Collections.singleton("com.cedarsoftware.io"));
        ClassLoader platform = (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        Object layer = layerClass.getMethod("defineModulesWithOneLoader", configuration.getClass(), ClassLoader.class)
                .invoke(boot, configuration, platform);
        ClassLoader loader = (ClassLoader) layerClass.getMethod("findLoader", String.class)
                .invoke(layer, "com.cedarsoftware.io");
        Class<?> jsonIoClass = loader.loadClass("com.cedarsoftware.io.JsonIo");
        assertEquals("com.cedarsoftware.io", moduleName(jsonIoClass));

        Thread thread = Thread.currentThread();
        ClassLoader saved = thread.getContextClassLoader();
        thread.setContextClassLoader(platform);
        try {
            Class<?> writeOptions = loader.loadClass("com.cedarsoftware.io.WriteOptions");
            Class<?> readOptions = loader.loadClass("com.cedarsoftware.io.ReadOptions");
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("a", java.util.Arrays.asList(1L, 2L));
            String json = (String) jsonIoClass.getMethod("toJson", Object.class, writeOptions)
                    .invoke(null, map, null);
            Object builder = jsonIoClass.getMethod("toJava", String.class, readOptions).invoke(null, json, null);
            Object back = builder.getClass().getMethod("asClass", Class.class).invoke(builder, java.util.Map.class);
            assertEquals(map.toString(), back.toString(), json);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("json-io failed as a named module: " + rootCause(e), e.getCause());
        } finally {
            thread.setContextClassLoader(saved);
        }
    }

    private static Object moduleName(Class<?> c) throws Exception {
        Object module = Class.class.getMethod("getModule").invoke(c);
        return module.getClass().getMethod("getName").invoke(module);
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    @Test
    void theOsgiHeadersAreKept() throws IOException {
        try (JarFile jar = jar()) {
            Attributes main = jar.getManifest().getMainAttributes();
            assertNotNull(main.getValue("Bundle-SymbolicName"));
            String exports = main.getValue("Export-Package");
            for (String pkg : new String[] {"com.cedarsoftware.io;", "com.cedarsoftware.io.annotation;",
                    "com.cedarsoftware.io.factory;", "com.cedarsoftware.io.writers;"}) {
                assertTrue(exports.contains(pkg), pkg + " in " + exports);
            }
            assertTrue(!exports.contains("META-INF"), exports);
            assertTrue(main.getValue("Import-Package").contains("com.cedarsoftware.util"),
                    main.getValue("Import-Package"));
        }
    }
}
