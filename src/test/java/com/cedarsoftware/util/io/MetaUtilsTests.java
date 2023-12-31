package com.cedarsoftware.util.io;

import java.io.Externalizable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class MetaUtilsTests {

    private static Stream<Arguments> ensureFillArgsInstanceOf() {
        return Stream.of(
                Arguments.of(Set.class, MetaUtils.listOf(LinkedHashSet.class, Set.class)),
                Arguments.of(SortedSet.class, MetaUtils.listOf(SortedSet.class, Set.class, TreeSet.class)),
                Arguments.of(SortedMap.class, MetaUtils.listOf(SortedMap.class, TreeMap.class)),
                Arguments.of(Collection.class, MetaUtils.listOf(Collection.class, ArrayList.class)),
                Arguments.of(Calendar.class, MetaUtils.listOf(Calendar.class)),
                Arguments.of(TimeZone.class, MetaUtils.listOf(TimeZone.class)),
                Arguments.of(BigInteger.class, MetaUtils.listOf(BigInteger.class)),
                Arguments.of(BigDecimal.class, MetaUtils.listOf(BigDecimal.class, BigDecimal.class)),
                Arguments.of(StringBuilder.class, MetaUtils.listOf(StringBuilder.class)),
                Arguments.of(StringBuffer.class, MetaUtils.listOf(StringBuffer.class)),
                Arguments.of(Locale.class, MetaUtils.listOf(Locale.class)),
                Arguments.of(Timestamp.class, MetaUtils.listOf(Timestamp.class)),
                Arguments.of(Date.class, MetaUtils.listOf(Date.class)),
                Arguments.of(Class.class, MetaUtils.listOf(Class.class)),
                Arguments.of(LocalDate.class, MetaUtils.listOf(LocalDate.class)),
                Arguments.of(LocalDateTime.class, MetaUtils.listOf(LocalDateTime.class)),
                Arguments.of(ZonedDateTime.class, MetaUtils.listOf(ZonedDateTime.class)),
                Arguments.of(ZoneId.class, MetaUtils.listOf(ZoneId.class)),
                Arguments.of(AtomicBoolean.class, MetaUtils.listOf(AtomicBoolean.class)),
                Arguments.of(AtomicInteger.class, MetaUtils.listOf(AtomicInteger.class)),
                Arguments.of(AtomicLong.class, MetaUtils.listOf(AtomicLong.class)),
                Arguments.of(Object.class, MetaUtils.listOf(Object.class)));

    }

    @ParameterizedTest
    @MethodSource("ensureFillArgsInstanceOf")
    void testFillArgs_ensureInstanceOf(final Class<?> input, final List<Class<?>> expected) {

        Object obj = MetaUtils.getArgForType(input);
        assertThat(obj).isInstanceOf(input);
    }

    @Test
    public void testNewPrimitiveWrapper() {
        try {
            Converter.convert("", TimeZone.class);
            fail();
        } catch (IllegalArgumentException e) {
            TestUtil.assertContainsIgnoreCase(e.getMessage(), "unsupported target type", "timezone");
        }
        
        try {
            Converter.convert("float", Float.class);
            fail();
        } catch (IllegalArgumentException e) {
            TestUtil.assertContainsIgnoreCase(e.getMessage(), "value: float not parseable as a float value");
        }

        assertThat(Converter.convert('0', Character.class)).isEqualTo(Character.valueOf('0'));
    }

    @Test
    public void testGetDistance() {
        int x = MetaUtils.computeInheritanceDistance(Serializable.class, Externalizable.class);
        assert x == -1;

        x = MetaUtils.computeInheritanceDistance(Externalizable.class, Serializable.class);
        assert x == 1;
    }

    @Test
    public void testLoggingMessage() {
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(4);
        map.put("a", "Alpha");
        map.put("b", "Bravo");
        map.put("car", "McLaren 675LT");
        map.put("pi", 3.1415926535897932384);

        String methodName = "blame";
        Object[] args = new Object[]{17, 34.5, map};
        String msg = MetaUtils.getLogMessage(methodName, args);
        assertThat(msg).isEqualTo("blame(17  34.5  {\"a\":\"Alpha\",\"b\":\"Bravo\",\"car\":\"McLaren 675LT\",\"pi\":3.1415926535...)");

        msg = MetaUtils.getLogMessage(methodName, args, 500);
        assertThat(msg).isEqualTo("blame(17  34.5  {\"a\":\"Alpha\",\"b\":\"Bravo\",\"car\":\"McLaren 675LT\",\"pi\":3.141592653589793})");
    }

    @Test
    void getWithDefault_whenObjectIsFound_returnsObject() {
        Map map = MetaUtils.mapOf("foo", "bar");
        String actual = MetaUtils.getValueWithDefaultForMissing(map, "foo", "qux");
        assertThat(actual).isEqualTo("bar");
    }

    @Test
    void getWithDefault_whenObjectIsNotFound_returnsDefaultObject() {
        Map map = MetaUtils.mapOf("foo", "bar");
        String actual = MetaUtils.getValueWithDefaultForMissing(map, "blah", "qux");
        assertThat(actual).isEqualTo("qux");
    }

    @Test
    void getWithDefaultForNull_whenObjectIsNotFound_returnsDefaultObject() {
        Map map = MetaUtils.mapOf("foo", "bar");
        String actual = MetaUtils.getValueWithDefaultForNull(map, "blah", "qux");
        assertThat(actual).isEqualTo("qux");
    }

    @Test
    void getWithDefaultForNull_whenObjectIsEqualToNull_returnsDefaultObject() {
        Map map = new HashMap<>();
        map.put("foo", null);
        String actual = MetaUtils.getValueWithDefaultForNull(map, "foo", "bar");
        assertThat(actual).isEqualTo("bar");
    }

    @Test
    void convertTrimQuotes() {
        String s = "\"\"\"This is \"really\" weird.\"\"\"";
        String x = MetaUtils.removeLeadingAndTrailingQuotes(s);
        assert "This is \"really\" weird.".equals(x);
    }

    private static Stream<Arguments> commaSeparatedStringArguments() {
        return Stream.of(
                Arguments.of(" foo, bar, qux, quy "),
                Arguments.of("foo,bar,qux,quy"));
    }

    @ParameterizedTest
    @MethodSource("commaSeparatedStringArguments")
    void testCommaSeparatedStringToSet(String commaSeparatedString) {
        Set<String> set = MetaUtils.commaSeparatedStringToSet(commaSeparatedString);
        assertThat(set).contains("foo", "bar", "qux", "quy");
    }

    private static Stream<Arguments> createGetterNameTests() {
        return Stream.of(
                Arguments.of("field", "getField"),
                Arguments.of("foo", "getFoo"),
                Arguments.of("treeMap", "getTreeMap"));
    }
}
