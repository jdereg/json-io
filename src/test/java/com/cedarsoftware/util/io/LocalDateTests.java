package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Date;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.DeepEquals;

import lombok.AllArgsConstructor;
import lombok.Getter;

class LocalDateTests extends SerializationDeserializationMinimumTests<LocalDate> {

    @Override
    protected LocalDate provideT1() {
        return LocalDate.of(1970, 6, 24);
    }

    @Override
    protected LocalDate provideT2() {
        return LocalDate.of(1971, 7, 14);
    }

    @Override
    protected LocalDate provideT3() {
        return LocalDate.of(1973, 12, 23);
    }

    @Override
    protected LocalDate provideT4() {
        return LocalDate.of(1950, 1, 27);
    }

    @Override
    protected Class<LocalDate> getTestClass() {
        return LocalDate.class;
    }

    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected LocalDate[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedLocalDate date = (NestedLocalDate) o;
        return new LocalDate[]{date.one, date.two};
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedLocalDate(
                provideT1(),
                provideT2());
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        LocalDate now = LocalDate.now();
        return new NestedLocalDate(now, now);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(LocalDate expected, Object actual) {
        assertThat(actual).isEqualTo("1950-01-27");
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        LocalDate date = LocalDate.of(2014, 10, 17);
        String json = TestUtil.toJson(date);
        LocalDate result = TestUtil.toObjects(json, null);
        assertThat(result).isEqualTo(date);
    }

    private static Stream<Arguments> checkDifferentFormatsByFile() {
        return Stream.of(
                Arguments.of("old-format-top-level.json", 2023, 4, 5),
                Arguments.of("old-format-long.json", 2023, 4, 5)
        );
    }

    @ParameterizedTest
    @MethodSource("checkDifferentFormatsByFile")
    void testOldFormat_topLevel_withType(String fileName, int year, int month, int day) {
        String json = loadJsonForTest(fileName);
        LocalDate localDate = TestUtil.toObjects(json, null);

        assertThat(localDate)
                .hasYear(year)
                .hasMonthValue(month)
                .hasDayOfMonth(day);
    }

    @Test
    void testOldFormat_nestedLevel() {

        String json = loadJsonForTest("old-format-nested-level.json");
        NestedLocalDate nested = TestUtil.toObjects(json, null);

        assertThat(nested.getOne())
                .hasYear(2014)
                .hasMonthValue(6)
                .hasDayOfMonth(13);

        assertThat(nested.getTwo())
                .hasYear(2024)
                .hasMonthValue(9)
                .hasDayOfMonth(12);
    }

    static class LocalDateArray
    {
        LocalDate[] localDates;
        Object[] otherDates;
    }

    @Test
    void testLocalDateArray()
    {
        LocalDateArray lda = new LocalDateArray();
        LocalDate now = LocalDate.now();
        lda.localDates = new LocalDate[] {now, now};
        lda.otherDates = new Object[] {now, new Date(System.currentTimeMillis()), now};
        String json = TestUtil.toJson(lda);
        LocalDateArray lda2 = TestUtil.toObjects(json, null);
        assert lda.localDates.length == 2;
        assert lda.otherDates.length == 3;

        assert DeepEquals.deepEquals(lda, lda2);
    }

    @Test
    public void testLocalDateArrayAtRoot()
    {
        LocalDate now = LocalDate.now();
        LocalDate[] dates = new LocalDate[]{now, now};
        String json = TestUtil.toJson(dates);
        LocalDate[] dates2 = TestUtil.toObjects(json, null);
        assertEquals(2, dates2.length);
        assertEquals(dates2[0], now);
        assertEquals(dates2[0], dates2[1]);
    }

    @Test
    public void testLocalDateAsTimeStamp()
    {
        LocalDate ld = LocalDate.of(2023, 12, 25);
        String json = TestUtil.toJson(ld, new WriteOptionsBuilder().addCustomWrittenClass(LocalDate.class, new Writers.LocalDateAsLong()).build());
        LocalDate ld2 = TestUtil.toObjects(json, null);
        assert ld.equals(ld2);
    }

    private String loadJsonForTest(String fileName) {
        return MetaUtils.loadResourceAsString("localdate/" + fileName);
    }


    @Getter
    @AllArgsConstructor
    public static class NestedLocalDate {
        private final LocalDate one;
        private final LocalDate two;
    }
}
