package com.cedarsoftware.io;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

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
class YearMonthTests extends SerializationDeserializationMinimumTests<YearMonth> {

    @Override
    protected YearMonth provideT1() {
        return YearMonth.of(1970, 6);
    }

    @Override
    protected YearMonth provideT2() {
        return YearMonth.of(1971, 7);
    }

    @Override
    protected YearMonth provideT3() {
        return YearMonth.of(1973, 12);
    }

    @Override
    protected YearMonth provideT4() {
        return YearMonth.of(1950, 1);
    }

    @Override
    protected Class<YearMonth> getTestClass() {
        return YearMonth.class;
    }


    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedYearMonth(
                provideT1(),
                provideT2());
    }

    @Override
    protected YearMonth[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedYearMonth nested = (NestedYearMonth) o;

        return new YearMonth[]{
                nested.one,
                nested.two
        };
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedYearMonth(provideT1());
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(YearMonth expected, Object actual) {
        assertThat(actual).isEqualTo("1950-01");
    }

    @Test
    void testOldFormat_objectType() {
        String json = "{\"@type\":\"java.time.YearMonth\",\"yearMonth\":\"1970-06\"}";
        YearMonth date = TestUtil.toObjects(json, null);
        assertThat(date.getYear()).isEqualTo(1970);
        assertThat(date.getMonthValue()).isEqualTo(6);
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.io.YearMonthTests$NestedYearMonth\",\"one\":{\"@id\":1,\"yearMonth\":\"1970-06\"},\"two\":{\"@ref\":1}}";
        NestedYearMonth date = TestUtil.toObjects(json, null);
        assertThat(date.one.getYear()).isEqualTo(1970);
        assertThat(date.one.getMonthValue()).isEqualTo(6);
        assertThat(date.one).isSameAs(date.two);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        YearMonth date = YearMonth.of(2014, 10);
        String json = TestUtil.toJson(date);
        YearMonth result = TestUtil.toObjects(json, null);
        assert result.equals(date);
    }

    @Test
    void testYearMonth_inArray() {
        YearMonth[] initial = new YearMonth[]{
                YearMonth.of(2014, 10),
                YearMonth.of(2023, 6)
        };

        YearMonth[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private static class NestedYearMonth {
        public YearMonth one;
        public YearMonth two;

        public NestedYearMonth(YearMonth one, YearMonth two) {
            this.one = one;
            this.two = two;
        }

        public NestedYearMonth(YearMonth date) {
            this(date, date);
        }
    }
}
