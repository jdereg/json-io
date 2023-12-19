package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.models.NestedLocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates() {
        return new NestedLocalDate(
                provideT1(),
                provideT2());
    }

    @Override
    protected LocalDate[] extractNestedInObject(Object o) {
        NestedLocalDate nested = (NestedLocalDate) o;

        return new LocalDate[]{
                nested.getDate1(),
                nested.getDate2()
        };
    }

    @Override
    protected Object provideNestedInObject_withDuplicates() {
        return new NestedLocalDate(provideT1());
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

        assertThat(nested.getDate1())
                .hasYear(2014)
                .hasMonthValue(6)
                .hasDayOfMonth(13);

        assertThat(nested.getDate2())
                .hasYear(2024)
                .hasMonthValue(9)
                .hasDayOfMonth(12);
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
}
