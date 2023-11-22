package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Externalizable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
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
                Arguments.of(Set.class, List.of(LinkedHashSet.class, Set.class)),
                Arguments.of(SortedSet.class, List.of(SortedSet.class, Set.class, TreeSet.class)),
                Arguments.of(SortedMap.class, List.of(SortedMap.class, TreeMap.class)),
                Arguments.of(Collection.class, List.of(Collection.class, ArrayList.class)),
                Arguments.of(Calendar.class, List.of(Calendar.class)),
                Arguments.of(TimeZone.class, List.of(TimeZone.class)),
                Arguments.of(BigInteger.class, List.of(BigInteger.class)),
                Arguments.of(BigDecimal.class, List.of(BigDecimal.class, BigDecimal.class)),
                Arguments.of(StringBuilder.class, List.of(StringBuilder.class)),
                Arguments.of(StringBuffer.class, List.of(StringBuffer.class)),
                Arguments.of(Locale.class, List.of(Locale.class)),
                Arguments.of(Timestamp.class, List.of(Timestamp.class)),
                Arguments.of(Date.class, List.of(Date.class)),
                Arguments.of(Class.class, List.of(Class.class)),
                Arguments.of(Object.class, List.of(Object.class)));

    }

    @ParameterizedTest
    @MethodSource("ensureFillArgsInstanceOf")
    void testFillArgs_ensureInstanceOf(final Class<?> input, final List<Class<?>> expected) {

        final Object[] actual = MetaUtilsMap.fillArgs(new Class[]{input}, false);
        assertThat(actual).hasSize(1);

        expected.forEach(e -> assertThat(actual[0]).isInstanceOf(e));
    }

    @Test
    public void testNewPrimitiveWrapper() {
        try {
            MetaUtils.convert(TimeZone.class, "");
        } catch (JsonIoException e) {
            assert e.getMessage().toLowerCase().contains("not have primitive wrapper");
        }


        try {
            MetaUtils.convert(Float.class, "float");
        } catch (JsonIoException e) {
            assert e.getMessage().toLowerCase().contains("error creating primitive wrapper");
        }

        assertThat((Character) MetaUtils.convert(Character.class, '0')).isEqualTo(Character.valueOf('0'));
    }

    @Test
    public void tryOtherConstructors() {
        try {
            MetaUtils.tryOtherConstruction(Byte.TYPE);
        } catch (JsonIoException e) {
            assert e.getMessage().toLowerCase().contains("cannot instantiate");
            assert e.getMessage().toLowerCase().contains("byte");
        }

    }

    @Test
    public void testGetDistance() {
        int x = MetaUtils.getDistance(Serializable.class, Externalizable.class);
        assert x == 1;

        x = MetaUtils.getDistance(Externalizable.class, Serializable.class);
        assert x == Integer.MAX_VALUE;
    }

    @Test
    public void testLoggingMessage() {
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<String, Serializable>(4);
        map.put("a", "Alpha");
        map.put("b", "Bravo");
        map.put("car", "McLaren 675LT");
        map.put("pi", 3.1415926535897932384);

        String methodName = "blame";
        Object[] args = new Object[]{17, 34.5, map};
        String msg = MetaUtilsMap.getLogMessage(methodName, args);
        assert msg.equals("blame({\"value\":17}  {\"value\":34.5}  {\"a\":\"Alpha\",\"b\":\"Bravo\",\"car\":\"McLaren 675LT\",\"pi\":3.1415926535...)");

        msg = MetaUtilsMap.getLogMessage(methodName, args, 500);
        assert msg.equals("blame({\"value\":17}  {\"value\":34.5}  {\"a\":\"Alpha\",\"b\":\"Bravo\",\"car\":\"McLaren 675LT\",\"pi\":3.141592653589793})");
    }

    @Test
    void getWithDefault_whenObjectIsFound_returnsObject() {
        var map = Map.of("foo", "bar");

        String actual = MetaUtilsMap.getValueWithDefaultForMissing(map, "foo", "qux");
        assertThat(actual).isEqualTo("bar");
    }

    @Test
    void getWithDefault_whenObjectIsNotFound_returnsDefaultObject() {
        var map = Map.of("foo", "bar");

        String actual = MetaUtilsMap.getValueWithDefaultForMissing(map, "blah", "qux");
        assertThat(actual).isEqualTo("qux");
    }

    @Test
    void getWithDefaultForNull_whenObjectIsNotFound_returnsDefaultObject() {
        var map = Map.of("foo", "bar");

        String actual = MetaUtilsMap.getValueWithDefaultForNull(map, "blah", "qux");
        assertThat(actual).isEqualTo("qux");
    }

    @Test
    void getWithDefaultForNull_whenObjectIsEqualToNull_returnsDefaultObject() {
        var map = new HashMap<>();
        map.put("foo", null);

        String actual = MetaUtilsMap.getValueWithDefaultForNull(map, "foo", "bar");
        assertThat(actual).isEqualTo("bar");
    }
}
