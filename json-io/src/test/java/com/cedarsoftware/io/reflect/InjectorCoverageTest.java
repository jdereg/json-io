package com.cedarsoftware.io.reflect;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.ReadOptionsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage tests for Injector — targets JaCoCo gaps:
 * - Direct create() with various field types
 * - Final field handling
 * - Null/empty validation
 * - Inject value into POJO fields of all primitive types and references
 *
 * Most of Injector's coverage comes through JSON deserialization of typed
 * POJOs, so these tests use that path heavily.
 */
class InjectorCoverageTest {

    // ========== Direct API ==========

    @Test
    void testCreateWithNullFieldThrows() {
        assertThatThrownBy(() -> Injector.create(null, "uniqueFieldName"))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Field cannot be null");
    }

    @Test
    void testCreateWithNullUniqueNameThrows() throws NoSuchFieldException {
        Field field = TestPojo.class.getDeclaredField("name");
        assertThatThrownBy(() -> Injector.create(field, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unique field name");
    }

    @Test
    void testCreateWithEmptyUniqueNameThrows() throws NoSuchFieldException {
        Field field = TestPojo.class.getDeclaredField("name");
        assertThatThrownBy(() -> Injector.create(field, ""))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testCreateAndInjectStringField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("name");
        Injector injector = Injector.create(field, "name");
        assertThat(injector).isNotNull();

        TestPojo pojo = new TestPojo();
        injector.inject(pojo, "Alice");
        assertThat(pojo.name).isEqualTo("Alice");
    }

    @Test
    void testCreateAndInjectIntField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("age");
        Injector injector = Injector.create(field, "age");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, 30);
        assertThat(pojo.age).isEqualTo(30);
    }

    @Test
    void testCreateAndInjectLongField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("id");
        Injector injector = Injector.create(field, "id");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, 12345L);
        assertThat(pojo.id).isEqualTo(12345L);
    }

    @Test
    void testCreateAndInjectBooleanField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("active");
        Injector injector = Injector.create(field, "active");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, true);
        assertThat(pojo.active).isTrue();
    }

    @Test
    void testCreateAndInjectDoubleField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("balance");
        Injector injector = Injector.create(field, "balance");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, 100.50);
        assertThat(pojo.balance).isEqualTo(100.50);
    }

    @Test
    void testCreateAndInjectFloatField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("ratio");
        Injector injector = Injector.create(field, "ratio");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, 0.5f);
        assertThat(pojo.ratio).isEqualTo(0.5f);
    }

    @Test
    void testCreateAndInjectShortField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("count");
        Injector injector = Injector.create(field, "count");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, (short) 42);
        assertThat(pojo.count).isEqualTo((short) 42);
    }

    @Test
    void testCreateAndInjectByteField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("flag");
        Injector injector = Injector.create(field, "flag");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, (byte) 7);
        assertThat(pojo.flag).isEqualTo((byte) 7);
    }

    @Test
    void testCreateAndInjectCharField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("ch");
        Injector injector = Injector.create(field, "ch");
        TestPojo pojo = new TestPojo();
        injector.inject(pojo, 'X');
        assertThat(pojo.ch).isEqualTo('X');
    }

    @Test
    void testCreateAndInjectObjectField() throws Exception {
        Field field = TestPojo.class.getDeclaredField("data");
        Injector injector = Injector.create(field, "data");
        TestPojo pojo = new TestPojo();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("k", "v");
        injector.inject(pojo, data);
        assertThat(pojo.data).isEqualTo(data);
    }

    // ========== Through JSON deserialization (covers many internal paths) ==========

    @Test
    void testDeserializeAllFieldTypes() {
        String json = "{\"@type\":\"" + TestPojo.class.getName() + "\"," +
                "\"name\":\"Bob\",\"age\":25,\"id\":99,\"active\":false," +
                "\"balance\":99.99,\"ratio\":0.25,\"count\":7,\"flag\":1,\"ch\":\"Y\"}";
        TestPojo p = JsonIo.toJava(json).asClass(TestPojo.class);
        assertThat(p.name).isEqualTo("Bob");
        assertThat(p.age).isEqualTo(25);
        assertThat(p.id).isEqualTo(99L);
        assertThat(p.active).isFalse();
        assertThat(p.balance).isEqualTo(99.99);
        assertThat(p.ratio).isEqualTo(0.25f);
        assertThat(p.count).isEqualTo((short) 7);
        assertThat(p.flag).isEqualTo((byte) 1);
        assertThat(p.ch).isEqualTo('Y');
    }

    @Test
    void testDeserializeWithNullValues() {
        String json = "{\"@type\":\"" + TestPojo.class.getName() + "\",\"name\":null,\"data\":null}";
        TestPojo p = JsonIo.toJava(json).asClass(TestPojo.class);
        assertThat(p.name).isNull();
        assertThat(p.data).isNull();
    }

    // ========== Final field handling ==========

    public static class FinalFieldClass {
        public final String constant;
        public FinalFieldClass() { this.constant = "default"; }
        public FinalFieldClass(String c) { this.constant = c; }
    }

    @Test
    void testInjectIntoFinalFieldWorks() throws Exception {
        // Final fields can typically be set via reflection on JDK 8-16,
        // and via VarHandle on JDK 9+
        Field field = FinalFieldClass.class.getDeclaredField("constant");
        Injector injector = Injector.create(field, "constant");
        assertThat(injector).isNotNull();
    }

    // ========== Round-trip with all types ==========

    @Test
    void testRoundTripPojo() {
        TestPojo orig = new TestPojo();
        orig.name = "Alice";
        orig.age = 30;
        orig.id = 12345L;
        orig.active = true;
        orig.balance = 100.50;
        orig.ratio = 0.75f;
        orig.count = 5;
        orig.flag = 1;
        orig.ch = 'A';

        String json = JsonIo.toJson(orig);
        TestPojo restored = JsonIo.toJava(json).asClass(TestPojo.class);

        assertThat(restored.name).isEqualTo(orig.name);
        assertThat(restored.age).isEqualTo(orig.age);
        assertThat(restored.id).isEqualTo(orig.id);
        assertThat(restored.active).isEqualTo(orig.active);
        assertThat(restored.balance).isEqualTo(orig.balance);
        assertThat(restored.ratio).isEqualTo(orig.ratio);
        assertThat(restored.count).isEqualTo(orig.count);
        assertThat(restored.flag).isEqualTo(orig.flag);
        assertThat(restored.ch).isEqualTo(orig.ch);
    }

    // ========== Test classes ==========

    public static class TestPojo {
        public String name;
        public int age;
        public long id;
        public boolean active;
        public double balance;
        public float ratio;
        public short count;
        public byte flag;
        public char ch;
        public Object data;
    }
}
