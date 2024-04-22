package com.cedarsoftware.io;

import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class ZoneOffsetTests extends SerializationDeserializationMinimumTests<ZoneOffset> {

    @Override
    protected ZoneOffset provideT1() {
        return ZoneOffset.of("+09:08");
    }

    @Override
    protected ZoneOffset provideT2() {
        return ZoneOffset.of("-06");
    }

    @Override
    protected ZoneOffset provideT3() {
        return ZoneOffset.of("+5");
    }

    @Override
    protected ZoneOffset provideT4() {
        return ZoneOffset.of("-04:25:33");
    }

    @Override
    protected Class<ZoneOffset> getTestClass() {
        return ZoneOffset.class;
    }


    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedZoneOffset(
                provideT1(),
                provideT2());
    }

    @Override
    protected ZoneOffset[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedZoneOffset nested = (NestedZoneOffset) o;

        return new ZoneOffset[]{
                nested.one,
                nested.two
        };
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedZoneOffset(provideT1());
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(ZoneOffset expected, Object actual) {
        assertThat(actual).isEqualTo(expected.toString());
    }

    private static Stream<Arguments> argumentsForOldFormat() {
        return Stream.of(
                Arguments.of("{\"@type\":\"java.time.ZoneOffset\",\"value\":\"+9\"}"),
                Arguments.of("{\"@type\":\"java.time.ZoneOffset\",\"value\":\"+09\"}")
        );
    }

    @ParameterizedTest
    @MethodSource("argumentsForOldFormat")
    void testOldFormat_objectType(String json) {
        ZoneOffset zone = TestUtil.toObjects(json, null);
        assertThat(zone).isEqualTo(ZoneOffset.of("+9"));
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.io.ZoneOffsetTests$NestedZoneOffset\",\"one\":{\"@id\":1,\"value\":\"+05:30\"},\"two\":{\"@ref\":1}}";
        NestedZoneOffset date = TestUtil.toObjects(json, null);
        assertThat(date.one)
                .isEqualTo(ZoneOffset.of("+05:30"))
                .isSameAs(date.two);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        ZoneOffset initial = ZoneOffset.of("-0908");
        ZoneOffset result = TestUtil.serializeDeserialize(initial);
        assertThat(result).isEqualTo(initial);
    }

    @Test
    void testZoneOffset_inArray() {
        ZoneOffset[] initial = new ZoneOffset[]{
                ZoneOffset.of("+06"),
                ZoneOffset.of("+06:02")
        };

        ZoneOffset[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private static class NestedZoneOffset {
        public ZoneOffset one;
        public ZoneOffset two;

        public NestedZoneOffset(ZoneOffset one, ZoneOffset two) {
            this.one = one;
            this.two = two;
        }

        public NestedZoneOffset(ZoneOffset date) {
            this(date, date);
        }
    }
}
