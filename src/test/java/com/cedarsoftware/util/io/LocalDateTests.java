package com.cedarsoftware.util.io;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.DeepEquals;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void testOldFormat_topLevel_withType() {
        String json = "{ \"@type\" : \"java.time.LocalDate\", \"year\" : 2023, \"month\": 4, \"day\": 5 }";
        LocalDate localDate = TestUtil.toObjects(json, null);

        assertThat(localDate)
                .hasYear(2023)
                .hasMonthValue(4)
                .hasDayOfMonth(5);
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
    void testLocalDateAsTimeStamp_withAsia()
    {
        LocalDate ld = LocalDate.of(2023, 12, 25);
        String json = TestUtil.toJson(ld, new WriteOptionsBuilder()
                .addCustomWrittenClass(LocalDate.class, new Writers.LocalDateAsLong()).build());
        ReadOptions options = new ReadOptionsBuilder().setZoneId(ZoneId.of("Asia/Saigon")).build();
        LocalDate ld2 = TestUtil.toObjects(json, options, null);
        assert ld.equals(ld2);
    }

    @Test
    void testLocalDateAsTimeStamp_withNewYork() {
        LocalDate ld = LocalDate.of(2023, 12, 25);
        String json = TestUtil.toJson(ld, new WriteOptionsBuilder()
                .addCustomWrittenClass(LocalDate.class, new Writers.LocalDateAsLong()).build());
        ReadOptions options = new ReadOptionsBuilder().setZoneId(ZoneId.of("America/New_York")).build();
        LocalDate ld2 = TestUtil.toObjects(json, options, null);
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
