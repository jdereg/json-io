package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.reflect.Accessor;

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

    // Test for isNonReferenceableClass method
    @Test
    void testIsNonReferenceableType() {
        assertTrue(options.isNonReferenceableClass(Date.class)); // Assuming Date is a logical primitive
        assertFalse(options.isNonReferenceableClass(Object.class)); // Assuming Object is not
    }

    // Test for getNonReferenceable method
    @Test
    void testGetNotReferenceableTypes() {
        Collection<Class<?>> nonReferenceableClasses = options.getNonReferenceableClasses();
        assertNotNull(nonReferenceableClasses);
        assertTrue(nonReferenceableClasses instanceof LinkedHashSet);
    }

    // Test for addNonReferenceable method
    @Test
    void testAddNonReferenceableClass() {
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
        assertThrows(JsonIoException.class, () -> options.aliasTypeName("foo", "foobar"));
        options.aliasTypeNames(new HashMap<>());
        options.aliasTypeName("int", "properInt");
        options.aliasTypeName("java.lang.Integer", "Int");
        options.build();

        assertEquals("properInt", options.aliasTypeNames().get("int"));
        assertEquals("Int", options.aliasTypeNames().get("java.lang.Integer"));

        // Asserting that we had at least one alias already loaded (non-empty resources/aliases.txt)
        assert options.aliasTypeNames().size() > 2;
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
    void testExcludedFields_containsBaseExclusions() {
        Collection<String> fieldNames = new WriteOptions().getExcludedFieldsPerClass(Throwable.class);

        assertThat(fieldNames)
                .hasSize(4);
    }

    @Test
    void testExcludedFields_subclassPicksUpSuperclassExclusions() {
        Collection<String> fieldNames = new WriteOptions().getExcludedFieldsPerClass(Exception.class);

        assertThat(fieldNames)
                .hasSize(4);
    }

    @Test
    void testExcludeFields_containsUniqueFieldNames()
    {
        options.addExcludedField(String.class, "dog");
        options.addExcludedFields(String.class, MetaUtils.setOf("dog", "cat"));

        assertThat(options.getExcludedFieldsPerClass(String.class))
                .hasSize(2);
    }

    @Test
    void testExcludeFields_addsNewExclusionsToBase() {
        options.addExcludedField(String.class, "dog");
        options.addExcludedFields(String.class, MetaUtils.setOf("dog", "cat"));
        options.addExcludedFields(Integer.class, MetaUtils.setOf("fly"));

        Collection<String> set = options.getExcludedFieldsPerClass(String.class);
        assertThat(set)
                .hasSize(2)
                .contains("dog", "cat");

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
