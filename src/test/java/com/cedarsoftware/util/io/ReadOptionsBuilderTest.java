package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.factory.LocalDateFactory;
import com.cedarsoftware.util.io.factory.LocalTimeFactory;
import com.cedarsoftware.util.io.factory.PersonFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOptionsBuilderTest {

    @Test
    void failOnUnknownType() {
        ReadOptions options = new ReadOptionsBuilder()
                .failOnUnknownType()
                .build();

        assertThat(options.isFailOnUnknownType()).isTrue();
    }

    @Test
    void setUnknownTypeClass() {
        ReadOptions options = new ReadOptionsBuilder()
                .setUnknownTypeClass(LinkedHashMap.class)
                .build();
    }

    @Test
    void returnAsMaps() {
        ReadOptions options = new ReadOptionsBuilder()
                .returnAsMaps()
                .build();

        assertThat(options.isUsingMaps()).isTrue();
    }

    @Test
    void withCustomTypeName_usingClassAsKey() {
        String value = "foobar";

        ReadOptions options = new ReadOptionsBuilder()
                .withCustomTypeName(Date.class, value)
                .build();

        assertThat(options.getTypeName(value)).isEqualTo(Date.class.getName());
    }

    @Test
    void test() {
        ReadOptions readOptions = new ReadOptionsBuilder().withMaxDepth(2).build();


    }

    @Test
    void withCustomTypeName_usingStringAsKey() {
        String key = "javax.sql.Date";
        String value = "foobar";

        ReadOptions options = new ReadOptionsBuilder()
                .withCustomTypeName(key, value)
                .build();

        assertThat(options.getTypeName("foobar")).isEqualTo("javax.sql.Date");
    }

    @Test
    void withCustomTypeName_whenAddingNames_addsUniqueNames() {
        ReadOptions options = new ReadOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(Date.class.getName(), "foo1")
                .withCustomTypeName(String.class, "bar2")
                .withCustomTypeName(Date.class.getName(), "foo2")
                .build();

        assertThat(options.getTypeName("bar2")).isEqualTo(String.class.getName());
        assertThat(options.getTypeName("foo2")).isEqualTo(Date.class.getName());

        assertThat(options.getTypeName("bar1")).isNull();
        assertThat(options.getTypeName("foo1")).isNull();
    }

    @Test
    void withCustomTypeName_withMixedCustomTypeNameInitialization_accumulates() {
        Map<String, String> map = MetaUtils.mapOf(
                String.class.getName(), "char1",
                "foo", "bar");

        ReadOptions options = new ReadOptionsBuilder()
                .withCustomTypeNames(map)
                .withCustomTypeName(String.class, "char2")
                .withCustomTypeName(Date.class, "dt")
                .withCustomTypeName(TimeZone.class.getName(), "tz")
                .build();

        assertThat(options.getTypeName("char2")).isEqualTo(String.class.getName());
        assertThat(options.getTypeName("dt")).isEqualTo(Date.class.getName());
        assertThat(options.getTypeName("bar")).isEqualTo("foo");
        assertThat(options.getTypeName("tz")).isEqualTo(TimeZone.class.getName());
    }

    @Test
    void withCustomTypeNameMap_whenAddingNames_addsUniqueNames() {
        ReadOptions options = new ReadOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(String.class, "bar2")
                .build();

        assertThat(options.getTypeName("bar2")).isEqualTo(String.class.getName());
        assertThat(options.getTypeName("bar1")).isNull();
    }

    @Test
    void withCustomReader() {
        JsonReader.JsonClassReader reader = new Readers.DateReader();
        ReadOptions options = new ReadOptionsBuilder()
                .withCustomReader(Date.class, reader)
                .build();

        assertThat(options.getReader(Date.class)).isSameAs(reader);
    }

    @Test
    void withCustomReaderMap_withNoAdditionalReaders_containsBaseReaders() {
        Map<Class<?>, JsonReader.JsonClassReader> map = new HashMap<>();

        ReadOptions options = new ReadOptionsBuilder()
                .withCustomReaders(map)
                .build();

        assertThat(options.getReader(String.class)).isNotNull();
        assertThat(options.getReader(Date.class)).isNotNull();
        assertThat(options.getReader(AtomicBoolean.class)).isNotNull();
        assertThat(options.getReader(AtomicInteger.class)).isNotNull();
        assertThat(options.getReader(AtomicLong.class)).isNotNull();
        assertThat(options.getReader(BigInteger.class)).isNotNull();
        assertThat(options.getReader(BigDecimal.class)).isNotNull();
        assertThat(options.getReader(java.sql.Date.class)).isNotNull();
        assertThat(options.getReader(Timestamp.class)).isNotNull();
        assertThat(options.getReader(Calendar.class)).isNotNull();
    }

    @Test
    void withClassLoader() {
        ClassLoader classLoader = this.getClass().getClassLoader();

        ReadOptions options = new ReadOptionsBuilder()
                .withClassLoader(classLoader)
                .build();

        assertThat(options.getClassLoader()).isNotNull();
    }

    @Test
    void withNonCustomizableClass() {
        ReadOptions options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .build();

        assertThat(options.isNonCustomizable(String.class)).isTrue();
        assertThat(options.isNonCustomizable(Map.class)).isFalse();
    }

    @Test
    void withNonCustomizableClass_addsAdditionalUniqueClasses() {
        Collection<Class<?>> list = MetaUtils.listOf(HashMap.class, String.class);

        ReadOptions options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Map.class)
                .withNonCustomizableClasses(list)
                .build();

        assertThat(options.isNonCustomizable(String.class)).isTrue();
        assertThat(options.isNonCustomizable(HashMap.class)).isTrue();
        assertThat(options.isNonCustomizable(Map.class)).isTrue();
    }

    @Test
    void withNonCustomizableClasses_whenNonCustomizableClassesExist_addsUniqueItemsToTheCollection() {
        ReadOptions options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Date.class)
                .withNonCustomizableClasses(MetaUtils.listOf(String.class, List.class))
                .build();

        assertThat(options.isNonCustomizable(String.class)).isTrue();
        assertThat(options.isNonCustomizable(Date.class)).isTrue();
        assertThat(options.isNonCustomizable(List.class)).isTrue();
    }

    @Test
    void withClassFactory() {
        LocalDateFactory localDateFactory = new LocalDateFactory();
        ReadOptions options = new ReadOptionsBuilder()
                .withClassFactory(LocalDate.class, localDateFactory)
                .build();

        assertThat(options.getClassFactory(LocalDate.class)).isSameAs(localDateFactory);
    }

    private Map<String, JsonReader.ClassFactory> getClassFactoryMap() {
        return MetaUtils.mapOf(
                LocalDate.class.getName(), new LocalDateFactory(),
                LocalTime.class.getName(), new LocalTimeFactory());
    }

    @Test
    void testConstructor_whenNoArgsArePassed_classFactoriesIsInstantiatedWithGlobalFactories() {
        ReadOptions options = new ReadOptionsBuilder().build();
        assertThat(options.getClassFactory(Map.class)).isNotNull();
        assertThat(options.getClassFactory(SortedMap.class)).isNotNull();
        assertThat(options.getClassFactory(Collection.class)).isNotNull();
        assertThat(options.getClassFactory(List.class)).isNotNull();
        assertThat(options.getClassFactory(Set.class)).isNotNull();
        assertThat(options.getClassFactory(SortedSet.class)).isNotNull();
        assertThat(options.getClassFactory(LocalDate.class)).isNotNull();
        assertThat(options.getClassFactory(LocalTime.class)).isNotNull();
        assertThat(options.getClassFactory(LocalDateTime.class)).isNotNull();
        assertThat(options.getClassFactory(ZonedDateTime.class)).isNotNull();
    }

    @Test
    void testConstructor_whenOptionsContainsClassFactories_thoseAreAppendedToBaseClassFactories() {
        ReadOptions options = new ReadOptionsBuilder()
                .withClassFactory(CustomWriterTest.Person.class, new PersonFactory())
                .build();

        assertThat(options.getClassFactory(CustomWriterTest.Person.class)).isNotNull();
    }

    @Test
    void testConstructor_whenNoArgs_readersAreInstantiatedWithBaseReaders() {
        ReadOptions options = new ReadOptionsBuilder().build();

        assertThat(options.getReader(AtomicInteger.class)).isNotNull();
    }

    @Test
    void testConstructor_whenOptionsContainsReaders_thoseAreAppendedToBaseReaders() {
        JsonReader.JsonClassReader reader = new CustomWriterTest.CustomPersonReader();
        ReadOptions options = new ReadOptionsBuilder()
                .withCustomReader(CustomWriterTest.Person.class, reader)
                .build();

        assertThat(options.getReader(CustomWriterTest.Person.class)).isSameAs(reader);
    }


    private Map<String, String> expectedTypeNameMap() {
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put(Date.class.getName(), "foo2");
        expectedMap.put(String.class.getName(), "bar2");
        return expectedMap;
    }
}
