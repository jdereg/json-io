package com.cedarsoftware.io;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify that {@code WriteOptionsBuilder.standardJson()} produces Jackson-compatible
 * date/time output — specifically ISO-8601 strings rather than epoch-millis longs.
 * <p>
 * This matches the Spring Boot default Jackson configuration
 * ({@code WRITE_DATES_AS_TIMESTAMPS=false}), which is what the vast majority of real
 * Java web applications actually emit. {@code java.time.*} types are already ISO-8601
 * by json-io's default; {@code standardJson()} flips {@code java.util.Date} /
 * {@code java.sql.Date} from the library's legacy long form to ISO-8601 as well.
 */
class StandardJsonDateTest {

    public static class DateHolder {
        public Date utilDate;
        public java.sql.Date sqlDate;
        public Timestamp timestamp;
        public Instant instant;
        public LocalDate localDate;
        public LocalDateTime localDateTime;
        public ZonedDateTime zonedDateTime;

        public DateHolder() {}
    }

    @Test
    void standardJson_writesJavaUtilDateAsIsoString() {
        DateHolder h = new DateHolder();
        h.utilDate = new Date(1_700_000_000_000L);   // 2023-11-14T22:13:20Z

        String json = JsonIo.toJson(h, new WriteOptionsBuilder().standardJson().build());

        // Should NOT be the epoch-millis long form
        assertThat(json).doesNotContain("1700000000000");
        // Should be a quoted ISO-8601 string
        assertThat(json).contains("\"utilDate\":\"");
        // Matches a date-like pattern (the exact format comes from Converter; just verify it's a string, not a number)
        assertThat(json).contains("2023");
    }

    @Test
    void defaultWriteOptions_stillWritesJavaUtilDateAsEpochMillis() {
        // The library default (without standardJson()) keeps the legacy epoch-millis form
        // for backward compatibility. Only standardJson() / json5() flip to ISO.
        DateHolder h = new DateHolder();
        h.utilDate = new Date(1_700_000_000_000L);

        String json = JsonIo.toJson(h);  // default options

        assertThat(json).contains("1700000000000");
    }

    @Test
    void standardJson_javaTimeTypesAreIsoStrings() {
        // java.time.* types were already ISO-8601 by default. Verify standardJson() doesn't break them.
        DateHolder h = new DateHolder();
        h.instant = Instant.parse("2023-11-14T22:13:20Z");
        h.localDate = LocalDate.of(2023, 11, 14);
        h.localDateTime = LocalDateTime.of(2023, 11, 14, 22, 13, 20);
        h.zonedDateTime = ZonedDateTime.of(h.localDateTime, ZoneId.of("UTC"));

        String json = JsonIo.toJson(h, new WriteOptionsBuilder().standardJson().build());

        assertThat(json).contains("\"instant\":\"2023-11-14T22:13:20Z\"");
        assertThat(json).contains("\"localDate\":\"2023-11-14\"");
        assertThat(json).contains("\"localDateTime\":\"2023-11-14T22:13:20\"");
        assertThat(json).contains("2023-11-14T22:13:20");
    }

    @Test
    void standardJson_roundTripsJavaUtilDate() {
        DateHolder h = new DateHolder();
        h.utilDate = new Date(1_700_000_000_000L);

        String json = JsonIo.toJson(h, new WriteOptionsBuilder().standardJson().build());
        DateHolder back = JsonIo.toJava(json).asClass(DateHolder.class);

        assertThat(back.utilDate).isEqualTo(h.utilDate);
    }

    @Test
    void standardJson_override_longDateFormat_flipsBackToLongs() {
        // Verify chainability: standardJson() then longDateFormat() picks the long form
        DateHolder h = new DateHolder();
        h.utilDate = new Date(1_700_000_000_000L);

        String json = JsonIo.toJson(h, new WriteOptionsBuilder()
                .standardJson()
                .longDateFormat()
                .build());

        assertThat(json).contains("1700000000000");
    }

    @Test
    void json5_writesJavaUtilDateAsIsoString() {
        // json5() should also flip to ISO
        DateHolder h = new DateHolder();
        h.utilDate = new Date(1_700_000_000_000L);

        String json = JsonIo.toJson(h, new WriteOptionsBuilder().json5().build());

        assertThat(json).doesNotContain("1700000000000");
        assertThat(json).contains("2023");
    }
}
