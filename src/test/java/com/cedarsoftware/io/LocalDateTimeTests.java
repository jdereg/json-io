package com.cedarsoftware.io;

import java.time.LocalDateTime;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class LocalDateTimeTests extends SerializationDeserializationMinimumTests<LocalDateTime> {

    @Override
    protected LocalDateTime provideT1() {
        return LocalDateTime.of(1970, 6, 24, 0, 19, 15, 999);
    }

    @Override
    protected LocalDateTime provideT2() {
        return LocalDateTime.of(1971, 7, 14, 23, 59, 59, 999999999);
    }

    @Override
    protected LocalDateTime provideT3() {
        return LocalDateTime.of(1973, 12, 23, 5, 9, 5, 0);
    }

    @Override
    protected LocalDateTime provideT4() {
        return LocalDateTime.of(1950, 1, 27, 11, 11, 11, 999999999);
    }

    @Override
    protected Class<LocalDateTime> getTestClass() {
        return LocalDateTime.class;
    }

    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected LocalDateTime[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedLocalDateTime date = (NestedLocalDateTime) o;
        return new LocalDateTime[]{date.dateTime1, date.dateTime2};
    }


    @Override
    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedLocalDateTime(
                provideT1(),
                provideT2());
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        LocalDateTime date = provideT1();
        return new NestedLocalDateTime(date, date);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(LocalDateTime expected, Object actual) {
        assertThat(actual).isEqualTo("1950-01-27T11:11:11.999999999");
    }

    @Test
    void testOldFormat_topLevel_withType() {
        String json = "{ \"@type\": \"java.time.LocalDateTime\", \"localDateTime\": \"2014-10-17T09:15:16\" }";
        LocalDateTime localDate = TestUtil.toObjects(json, LocalDateTime.class);
        assert localDate.getYear() == 2014;
        assert localDate.getMonthValue() == 10;
        assert localDate.getDayOfMonth() == 17;
        assert localDate.getHour() == 9;
        assert localDate.getMinute() == 15;
        assert localDate.getSecond() == 16;
        assert localDate.getNano() == 0;
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        LocalDateTime date = LocalDateTime.of(2014, 10, 17, 9, 15, 16, 99999);
        String json = TestUtil.toJson(date);
        LocalDateTime result = TestUtil.toObjects(json, null);
        assertThat(result).isEqualTo(date);
    }

    @Test
    void testLocalDateTime_inArray() {
        LocalDateTime[] initial = new LocalDateTime[]{
                LocalDateTime.of(2014, 10, 9, 11, 11, 11, 11),
                LocalDateTime.of(2023, 6, 24, 11, 11, 11, 1000)
        };

        LocalDateTime[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private void assertLocalDateTime(LocalDateTime date, int year, int month, int dayOfMonth) {
        assertThat(date.getYear()).isEqualTo(year);
        assertThat(date.getMonthValue()).isEqualTo(month);
        assertThat(date.getDayOfMonth()).isEqualTo(dayOfMonth);
    }

    private String loadJsonForTest(String fileName) {
        return ClassUtilities.loadResourceAsString("localdatetime/" + fileName);
    }

    private static class NestedLocalDateTime {
        public LocalDateTime dateTime1;
        public LocalDateTime dateTime2;

        public NestedLocalDateTime(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        public LocalDateTime getDateTime1() {
            return this.dateTime1;
        }

        public LocalDateTime getDateTime2() {
            return this.dateTime2;
        }
    }
}
