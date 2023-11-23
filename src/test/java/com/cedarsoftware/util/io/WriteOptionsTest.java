package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.Accessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteOptionsTest {

    private WriteOptions options;

    @BeforeEach
    void setUp() {
        options = new WriteOptions();
    }

    @Test
    void testDefaultSkipNullFields() {
        assertFalse(options.isSkipNullFields());
    }

    // Example test for a setter method (skipNullFields)
    @Test
    void testSetSkipNullFields() {
        options.skipNullFields(true);
        assertTrue(options.isSkipNullFields());
        options.skipNullFields(false);
        assertFalse(options.isSkipNullFields());
    }

    @Test
    void testClassLoader() {
        options.classLoader(ClassLoader.getSystemClassLoader());
        assertSame(options.getClassLoader(), ClassLoader.getSystemClassLoader());
        options.classLoader(WriteOptionsTest.class.getClassLoader());
        assertSame(options.getClassLoader(), WriteOptionsTest.class.getClassLoader());
    }

    // Test for forceMapOutputAsTwoArrays method
    @Test
    void testForceMapOutputAsTwoArrays() {
        options.forceMapOutputAsTwoArrays(true);
        assertTrue(options.isForceMapOutputAsTwoArrays());
    }

    // Test for isLogicalPrimitive method
    @Test
    void testIsLogicalPrimitive() {
        assertTrue(options.isNonReferenceableClass(Date.class)); // Assuming Date is a logical primitive
        assertFalse(options.isNonReferenceableClass(Object.class)); // Assuming Object is not
    }

    // Test for getLogicalPrimitives method
    @Test
    void testGetLogicalPrimitives() {
        Collection<Class<?>> logicalPrimitives = options.getNonReferenceableClasses();
        assertNotNull(logicalPrimitives);
        assertTrue(logicalPrimitives instanceof LinkedHashSet);
    }

    // Test for addLogicalPrimitive method
    @Test
    void testAddLogicalPrimitive() {
        options.addNonReferenceableClass(String.class);
        assertTrue(options.isNonReferenceableClass(String.class));
    }

    // Test for sealing the options
    @Test
    void testSeal() {
        assertFalse(options.isBuilt());
        options.build();
        assertTrue(options.isBuilt());
    }

    // Test for mutating methods after sealing
    @Test
    void testModificationAfterSealing() {
        options.build();
        assertThrows(JsonIoException.class, () -> options.skipNullFields(true));
        // Repeat for other setters
    }

    @Test
    void testAliases() {
        options.aliasTypeName("foo", "foobar");
        options.aliasTypeName("bar", "barback");
        options.aliasTypeName("baz", "bazqux");
        options.aliasTypeName("qux", "garply");
        options.aliasTypeName("bar", "barbaz");
        options.build();

        assertEquals("foobar", options.aliasTypeNames().get("foo"));
        assertEquals("barbaz", options.aliasTypeNames().get("bar"));
        assertEquals("bazqux", options.aliasTypeNames().get("baz"));
        assertEquals("garply", options.aliasTypeNames().get("qux"));

        assert options.aliasTypeNames().size() > 4;    // Asserting that we have at least one "pre-installed" alias.
        assertThrows(JsonIoException.class, () -> options.aliasTypeName("x", "y"));
    }

    @Test
    void testAlias2() {
        int i = 0;
        while (options.aliasTypeNames().size() < 4) {
            options.aliasTypeName("" + i, "i=" + i);
            i++;
        }
        Map<String, String> newAliases = new LinkedHashMap<>();
        newAliases.put("foo", "foot");
        newAliases.put("bar", "barkly");
        newAliases.put("baz", "bazooka");
        options.aliasTypeNames(newAliases);
        assert options.aliasTypeNames().size() == 3;
    }

    @Test
    void testShowTypeInfoMinimal() {
        assert options.isMinimalShowingType();     // default on
        options.showTypeInfoAlways();               // turn off
        assert !options.isMinimalShowingType();     // stays off
        options.showTypeInfoMinimal();              // turn on
        assert options.isMinimalShowingType();     // back on
        options.build();
        assertThrows(JsonIoException.class, () -> options.showTypeInfoMinimal());
    }

    @Test
    void testEnums() {
        assert options.isWriteEnumAsString();
        options.writeEnumAsJsonObject(true);
        assert !options.isWriteEnumAsString();
        options.writeEnumAsJsonObject(false);
        assert !options.isWriteEnumAsString();
        options.writeEnumsAsString();
        assert options.isWriteEnumAsString();
    }

    @Test
    void testCustomWrittenClasses() {
        Map<Class<?>, JsonWriter.JsonClassWriter> custom = options.getCustomWrittenClasses();
        assert !custom.isEmpty();
        options.setCustomWrittenClasses(new HashMap<>());
        custom = options.getCustomWrittenClasses();
        assert custom.isEmpty();
        options.build();
        custom = options.getCustomWrittenClasses();
        assert custom.isEmpty();
        assertThrows(JsonIoException.class, () -> options.addCustomWrittenClass(String.class, new Writers.JsonStringWriter()));
    }

    @Test
    void testCustomWrittenClasses2() {
        Map<Class<?>, JsonWriter.JsonClassWriter> custom = options.getCustomWrittenClasses();
        int x = custom.size();
        options.setCustomWrittenClasses(new HashMap<>());
        custom = options.getCustomWrittenClasses();
        assert custom.isEmpty();
        options.build();
        assertThrows(JsonIoException.class, () -> options.addCustomWrittenClass(String.class, new Writers.JsonStringWriter()));
    }

    @Test
    void testCustomWrittenClasses3() {
        options.addCustomWrittenClass(ConcurrentHashMap.class, new Writers.JsonStringWriter());
        assert options.isCustomWrittenClass(ConcurrentHashMap.class);
    }

    @Test
    void testNotCustomWrittenClass() {
        Set<Class<?>> notCustom = options.getNotCustomWrittenClasses();
        assert notCustom.isEmpty();   // lock in default of 0
        options.addNotCustomWrittenClass(LocalDate.class);
        options.setNotCustomWrittenClasses(MetaUtils.setOf(LocalDateTime.class, ZonedDateTime.class));
        assert options.getNotCustomWrittenClasses().size() == 2;
        options.addNotCustomWrittenClass(LocalDate.class);
        assert options.getNotCustomWrittenClasses().size() == 3;
        assert options.getNotCustomWrittenClasses().contains(LocalDate.class);
        assert options.getNotCustomWrittenClasses().contains(LocalDateTime.class);
        assert options.getNotCustomWrittenClasses().contains(ZonedDateTime.class);
    }

    @Test
    void testIncludedFields()
    {
        Map<Class<?>, Set<String>> map = options.getIncludedFieldsPerAllClasses();
        assert map.isEmpty();
        options.addIncludedField(String.class, "dog");
        options.addIncludedFields(String.class, MetaUtils.setOf("cat", "bird"));
        options.addIncludedField(Integer.class, "really?");
        assert options.getIncludedFields(String.class).size() == 3;
        map = options.getIncludedFieldsPerAllClasses();
        assert map.size() == 2;
        assert map.containsKey(String.class);
        assert map.containsKey(Integer.class);

        Set<Accessor> accessors = options.getIncludedAccessors(String.class);
        assert accessors.isEmpty();
        
        options.build();

        accessors = options.getIncludedAccessors(String.class);
        assert accessors.isEmpty();
        
        assert options.getIncludedFields(String.class).containsAll(MetaUtils.setOf("dog", "cat", "bird"));
        assert options.getIncludedFields(Integer.class).containsAll(MetaUtils.setOf("really?"));
        assertThrows(JsonIoException.class, () -> options.addIncludedField(String.class, "dog"));
        assertThrows(JsonIoException.class, () -> options.addIncludedFields(String.class, MetaUtils.setOf("cat", "bird")));
        map = options.getIncludedFieldsPerAllClasses();
        assert map.size() == 2;
        assert map.containsKey(String.class);
        assert map.containsKey(Integer.class);

        WriteOptions copy = new WriteOptions(options);
        assert !copy.isBuilt();
        map = copy.getIncludedFieldsPerAllClasses();
        assert map.size() == 2;
        assert copy.getIncludedFields(String.class).containsAll(MetaUtils.setOf("dog", "cat", "bird"));
        assert copy.getIncludedFields(Integer.class).containsAll(MetaUtils.setOf("really?"));
    }

    @Test
    void testExcludedFields()
    {
        Map<Class<?>, Set<String>> map = options.getExcludedFieldsPerAllClasses();
        assert map.isEmpty();
        options.addExcludedField(String.class, "dog");
        options.addExcludedFields(String.class, MetaUtils.setOf("cat", "bird"));
        options.addExcludedField(Integer.class, "really?");
        assert options.getExcludedFields(String.class).size() == 3;
        map = options.getExcludedFieldsPerAllClasses();
        assert map.size() == 2;
        assert map.containsKey(String.class);
        assert map.containsKey(Integer.class);

        Set<Accessor> accessors = options.getExcludedAccessors(String.class);
        assert accessors.isEmpty();

        options.build();

        accessors = options.getExcludedAccessors(String.class);
        assert accessors.isEmpty();

        assert options.getExcludedFields(String.class).containsAll(MetaUtils.setOf("dog", "cat", "bird"));
        assert options.getExcludedFields(Integer.class).containsAll(MetaUtils.setOf("really?"));
        assertThrows(JsonIoException.class, () -> options.addExcludedField(String.class, "dog"));
        assertThrows(JsonIoException.class, () -> options.addExcludedFields(String.class, MetaUtils.setOf("cat", "bird")));
        map = options.getExcludedFieldsPerAllClasses();
        assert map.size() == 2;
        assert map.containsKey(String.class);
        assert map.containsKey(Integer.class);

        WriteOptions copy = new WriteOptions(options);
        assert !copy.isBuilt();
        map = copy.getExcludedFieldsPerAllClasses();
        assert map.size() == 2;
        assert copy.getExcludedFields(String.class).containsAll(MetaUtils.setOf("dog", "cat", "bird"));
        assert copy.getExcludedFields(Integer.class).containsAll(MetaUtils.setOf("really?"));
    }

    @Test
    void testLongDateFormat()
    {
        assert options.isLongDateFormat();  // assert default setting
        Date now = new Date();
        Date[] dates = new Date[] {now};
        String json = TestUtil.toJson(dates, options);
        Date[] dates2 = TestUtil.toObjects(json, null);
        assertEquals(dates2[0], dates[0]);
        assertEquals(dates2[0], now);

        WriteOptions writeOptions = new WriteOptions(options);
        writeOptions.isoDateTimeFormat();
        assert !writeOptions.isLongDateFormat();
        json = TestUtil.toJson(dates, writeOptions);
        dates2 = TestUtil.toObjects(json, null);
        assertEquals(dates2[0].toString(), dates[0].toString());    // differ in millis
        assertEquals(dates2[0].toString(), now.toString());         // differ in millis

        writeOptions = new WriteOptions(writeOptions);
        assert !writeOptions.isLongDateFormat();
        writeOptions.longDateFormat();
        assert writeOptions.isLongDateFormat();

        json = TestUtil.toJson(dates, writeOptions);
        assert json.contains("" + now.getTime());
    }
}
