package com.cedarsoftware.io;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecordDeserializationTest {
    private static boolean recordsSupported() {
        try {
            Class.forName("java.lang.Record");
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    @Test
    public void testRecordRoundTrip() throws Exception {
        Assumptions.assumeTrue(recordsSupported(), "Records not supported");

        Path dir = Files.createTempDirectory("recordTest");
        Path recordFile = dir.resolve("RecordPojo.java");
        Files.write(recordFile,
                ("public record RecordPojo(String subString, int iSubNumber) {}" + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8));
        Path testFile = dir.resolve("TestPojo.java");
        Files.write(testFile,
                ("public class TestPojo {" +
                        "  public int number;" +
                        "  public String string;" +
                        "  public RecordPojo subPojo;" +
                        "  public TestPojo(int number, String string, RecordPojo subPojo) {" +
                        "    this.number = number;" +
                        "    this.string = string;" +
                        "    this.subPojo = subPojo;" +
                        "  }" +
                        "}" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                recordFile.toString(), testFile.toString(), "-d", dir.toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed");
        }

        URLClassLoader loader = URLClassLoader.newInstance(new URL[]{dir.toUri().toURL()});
        Class<?> recordClass = Class.forName("RecordPojo", true, loader);
        Class<?> testClass = Class.forName("TestPojo", true, loader);

        Constructor<?> recordCtor = recordClass.getDeclaredConstructor(String.class, int.class);
        Object record = recordCtor.newInstance("subString", 42);
        Constructor<?> testCtor = testClass.getDeclaredConstructor(int.class, String.class, recordClass);
        Object testPojo = testCtor.newInstance(3, "myString", record);

        String json = JsonIo.toJson(testPojo, null);
        ReadOptions options = new ReadOptionsBuilder().classLoader(loader).build();
        Object clone = JsonIo.toJava(json, options).asClass(testClass);

        Field numberField = testClass.getField("number");
        Field stringField = testClass.getField("string");
        Field subPojoField = testClass.getField("subPojo");

        assertEquals(3, numberField.getInt(clone));
        assertEquals("myString", stringField.get(clone));
        Object cloneSub = subPojoField.get(clone);
        Method subString = recordClass.getMethod("subString");
        Method iSubNumber = recordClass.getMethod("iSubNumber");
        assertEquals("subString", subString.invoke(cloneSub));
        assertEquals(42, iSubNumber.invoke(cloneSub));
    }
}

