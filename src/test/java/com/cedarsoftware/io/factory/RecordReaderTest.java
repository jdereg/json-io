package com.cedarsoftware.io.factory;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.ObjectResolver;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.io.ReferenceTracker;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordReaderTest {
    private static boolean recordsSupported() {
        try {
            Class.forName("java.lang.Record");
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    @Test
    void readSimpleRecord() throws Exception {
        Assumptions.assumeTrue(recordsSupported(), "Records not supported");

        Path dir = Files.createTempDirectory("recordReaderTest");
        Path recordFile = dir.resolve("SimpleRecord.java");
        Files.write(recordFile,
                ("public record SimpleRecord(String name, int age) {}" + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, recordFile.toString(), "-d", dir.toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed");
        }

        URLClassLoader loader = URLClassLoader.newInstance(new URL[]{dir.toUri().toURL()});
        Class<?> recordClass = Class.forName("SimpleRecord", true, loader);

        JsonObject jObj = new JsonObject();
        jObj.setType(recordClass);
        jObj.put("name", "Bob");
        jObj.put("age", 25);

        ReadOptions options = new ReadOptionsBuilder().classLoader(loader).build();
        ReferenceTracker references = new Resolver.DefaultReferenceTracker(options);
        Converter converter = new Converter(options.getConverterOptions());
        Resolver resolver = new ObjectResolver(options, references, converter);

        RecordFactory.RecordReader recordReader = new RecordFactory.RecordReader();
        Object record = recordReader.read(jObj, resolver);

        Method name = recordClass.getMethod("name");
        Method age = recordClass.getMethod("age");
        assertEquals("Bob", name.invoke(record));
        assertEquals(25, age.invoke(record));
    }
}
