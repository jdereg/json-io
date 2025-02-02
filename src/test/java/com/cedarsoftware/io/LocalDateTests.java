package com.cedarsoftware.io;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

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
        assertEquals("1950-01-27", actual);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        LocalDate date = LocalDate.of(2014, 10, 17);
        String json = TestUtil.toJson(date);
        LocalDate result = TestUtil.toObjects(json, null);
        assertEquals(date, result);
    }

    @Test
    void testOldFormat_topLevel_withType() {
        String json = "{ \"@type\" : \"java.time.LocalDate\", \"localDate\" : \"2023-4-5\" }";
        LocalDate localDate = TestUtil.toObjects(json, null);

        assert localDate.getYear() == 2023;
        assert localDate.getMonthValue() == 4;
        assert localDate.getDayOfMonth() == 5;
    }

    @Test
    void testOldFormat_nestedLevel() {

        String json = loadJsonForTest("old-format-nested-level.json");
        NestedLocalDate nested = TestUtil.toObjects(json, null);

        LocalDate d1 = nested.getOne();
        assert d1.getYear() == 2014;
        assert d1.getMonthValue() == 6;
        assert d1.getDayOfMonth() == 13;

        LocalDate d2 = nested.getTwo();
        assert d2.getYear() == 2024;
        assert d2.getMonthValue() == 9;
        assert d2.getDayOfMonth() == 12;
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
        return ClassUtilities.loadResourceAsString("localdate/" + fileName);
    }


    public static class NestedLocalDate {
        private final LocalDate one;
        private final LocalDate two;

        public NestedLocalDate(LocalDate one, LocalDate two) {
            this.one = one;
            this.two = two;
        }

        public LocalDate getOne() {
            return this.one;
        }

        public LocalDate getTwo() {
            return this.two;
        }
    }
}
