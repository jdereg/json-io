package com.cedarsoftware.io;


import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AccessorFactory;
import com.cedarsoftware.io.reflect.filters.FieldFilter;
import com.cedarsoftware.io.reflect.filters.MethodFilter;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.junit.jupiter.api.Assertions.*;


public class WriteOptionsBuilderTest {

    static class Example {
        public int value;
        public int getValue() { return value; }
    }

    static class DummyWriter implements JsonWriter.JsonClassWriter {
        @Override
        public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException {
            output.write("\"dummy\"");
        }
    }

    static class TestAccessorFactory implements AccessorFactory {
        boolean called;
        @Override
        public Accessor buildAccessor(Field field, Map<Class<?>, Map<String, String>> nonStandardGetters, String uniqueFieldName) {
            called = true;
            return null;
        }
    }

    static class NamedFilter implements MethodFilter {
        private final String name;
        NamedFilter(String name) { this.name = name; }
        public boolean filter(Class<?> clazz, String methodName) {
            return Example.class.equals(clazz) && name.equals(methodName);
        }
    }

    static class SimpleFieldFilter implements FieldFilter {
        private final String field;
        SimpleFieldFilter(String field) { this.field = field; }
        public boolean filter(Field f) { return f.getName().equals(field); }
    }

    @Test
    void testAddPermanentNamedMethodFilter() {
        WriteOptions before = new WriteOptionsBuilder().build();
        List<Accessor> pre = before.getAccessorsForClass(Example.class);
        boolean preMethod = pre.stream().filter(a -> "value".equals(a.getActualFieldName())).findFirst().get().isMethod();

        WriteOptionsBuilder.addPermanentNamedMethodFilter("test", Example.class, "getValue");
        WriteOptions after = new WriteOptionsBuilder().build();
        List<Accessor> post = after.getAccessorsForClass(Example.class);
        boolean postMethod = post.stream().filter(a -> "value".equals(a.getActualFieldName())).findFirst().get().isMethod();

        assertTrue(preMethod);
        assertFalse(postMethod);
    }

    @Test
    void testWriteEnumsAsString() {
        WriteOptions opts = new WriteOptionsBuilder()
                .writeEnumAsJsonObject(false)
                .writeEnumsAsString()
                .build();
        String json = JsonIo.toJson(TestEnum.A, opts);
        assertEquals("\"A\"", json);
    }

    enum TestEnum { A, B }

    @Test
    void testSetCustomWrittenClasses() {
        Map<Class<?>, JsonWriter.JsonClassWriter> map = mapOf(Integer.class, new DummyWriter());
        WriteOptions opts = new WriteOptionsBuilder().setCustomWrittenClasses(map).build();
        assertTrue(opts.isCustomWrittenClass(Integer.class));
        assertFalse(opts.isCustomWrittenClass(String.class));
    }

    @Test
    void testAddIncludedFieldAndGetIncludedFields() {
        WriteOptions opts = new WriteOptionsBuilder()
                .addIncludedField(Example.class, "value")
                .build();
        Set<String> fields = opts.getIncludedFields(Example.class);
        assertTrue(fields.contains("value"));
    }

    @Test
    void testAddExcludedFieldAndGetExcludedFields() {
        WriteOptions opts = new WriteOptionsBuilder()
                .addExcludedField(Example.class, "value")
                .build();
        Set<String> fields = opts.getExcludedFields(Example.class);
        assertTrue(fields.contains("value"));
    }

    @Test
    void testAddCustomOptionAndGetCustomOption() {
        WriteOptions opts = new WriteOptionsBuilder()
                .addCustomOption("foo", 42)
                .build();
        assertEquals(42, opts.getCustomOption("foo"));
    }

    @Test
    void testAddAndRemoveFieldFilter() {
        FieldFilter filter = new SimpleFieldFilter("value");
        WriteOptions opts = new WriteOptionsBuilder()
                .addFieldFilter("f", filter)
                .build();
        Map<String, Field> map = opts.getDeepDeclaredFields(Example.class);
        assertFalse(map.containsKey("value"));

        WriteOptions opts2 = new WriteOptionsBuilder()
                .addFieldFilter("f", filter)
                .removeFieldFilter("f")
                .build();
        Map<String, Field> map2 = opts2.getDeepDeclaredFields(Example.class);
        assertTrue(map2.containsKey("value"));
    }

    @Test
    void testAddAndRemoveMethodFilter() {
        MethodFilter mf = new NamedFilter("getValue");
        WriteOptions opts = new WriteOptionsBuilder()
                .addMethodFilter("f", mf)
                .build();
        List<Accessor> a = opts.getAccessorsForClass(Example.class);
        boolean method = a.stream().filter(x -> "value".equals(x.getActualFieldName())).findFirst().get().isMethod();
        assertFalse(method);

        WriteOptions opts2 = new WriteOptionsBuilder()
                .addMethodFilter("f", mf)
                .removeMethodFilter("f")
                .build();
        List<Accessor> b = opts2.getAccessorsForClass(Example.class);
        boolean method2 = b.stream().filter(x -> "value".equals(x.getActualFieldName())).findFirst().get().isMethod();
        assertTrue(method2);
    }

    @Test
    void testAddAndRemoveAccessorFactory() {
        TestAccessorFactory factory = new TestAccessorFactory();
        WriteOptions opts = new WriteOptionsBuilder()
                .addAccessorFactory("t", factory)
                .build();
        opts.getAccessorsForClass(Example.class);
        assertTrue(factory.called);

        TestAccessorFactory factory2 = new TestAccessorFactory();
        WriteOptions opts2 = new WriteOptionsBuilder()
                .addAccessorFactory("t", factory2)
                .removeAccessorFactory("t")
                .build();
        opts2.getAccessorsForClass(Example.class);
        assertFalse(factory2.called);
    }
}

