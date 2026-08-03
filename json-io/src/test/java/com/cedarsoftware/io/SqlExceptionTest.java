package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * java.sql.SQLException and friends must serialize, and must carry their real vendor code.
 * <p>
 * They used to throw outright: every field on SQLException is private and java.sql is exported but not opened, so
 * no reflective path reaches them. Accessor.retrieve() returns null for such a field on purpose -- "skip it
 * safely" -- but the primitive getters unboxed that null unchecked, and the int field vendorCode therefore raised
 * a NullPointerException. Worse than the throw itself was where it landed: mid-write, after bytes were already on
 * the wire, so a caller received a TRUNCATED JSON body with no indication of what went wrong. That is how it
 * surfaced -- an n-cube commitBranch returning HTTP 200 and 381 bytes ending at {@code "vendorCode":}.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 */
class SqlExceptionTest
{
    private static String json(Object o) {
        return JsonIo.toJson(o, new WriteOptionsBuilder().build());
    }

    @Test
    void testSqlExceptionSerializes() {
        String j = json(new SQLException("boom", "23000", 1));

        assertThat(j).contains("java.sql.SQLException");
        assertThat(j).contains("\"detailMessage\":\"boom\"");
        assertThat(j).contains("\"SQLState\":\"23000\"");
    }

    /**
     * The vendor code must be the real one. Its field is unreadable, and its getter is getErrorCode() rather than
     * the getVendorCode() a name-based lookup would search for, so without an explicit mapping it silently wrote 0
     * -- a fabricated value, which is worse than an omission because nothing tells the reader it is wrong.
     */
    @Test
    void testVendorCodeIsTheRealValueNotZero() {
        assertThat(json(new SQLException("boom", "23000", 42))).contains("\"vendorCode\":42");
        assertThat(json(new SQLException("boom", "23000", -1))).contains("\"vendorCode\":-1");
    }

    @Test
    void testSubclassesSerialize() {
        assertThat(json(new BatchUpdateException("b", "23000", 7, new int[]{1}, null)))
                .contains("\"vendorCode\":7");
        assertThat(json(new SQLIntegrityConstraintViolationException("c", "23000", 9)))
                .contains("\"vendorCode\":9");
    }

    /** The shape that actually broke n-cube: a SQL exception nested as the cause of something else. */
    @Test
    void testSqlExceptionAsACause() {
        String j = json(new RuntimeException("Database operation failed", new SQLException("x", "23000", 1)));

        assertThat(j).contains("java.sql.SQLException");
        assertThat(j).contains("Database operation failed");
    }

    /**
     * The property that matters most, independent of any field: a failure while writing must never leave a partial
     * document. Braces balance, so what the caller receives is parseable.
     */
    @Test
    void testOutputIsCompleteNotTruncated() {
        for (Object o : new Object[]{
                new SQLException("boom", "23000", 1),
                new BatchUpdateException("b", "23000", 7, new int[]{1}, null),
                new RuntimeException("wrap", new SQLException("x", "23000", 1))}) {
            String j = json(o);
            assertThat(j.chars().filter(c -> c == '{').count())
                    .as("unbalanced braces means a truncated document: " + j)
                    .isEqualTo(j.chars().filter(c -> c == '}').count());
            assertThat(j).endsWith("}");
        }
    }

    /** The chained-exception link is also unreadable by field; getNextException() supplies it. */
    @Test
    void testNextExceptionIsCarried() {
        SQLException head = new SQLException("first", "23000", 1);
        head.setNextException(new SQLException("second", "23001", 2));

        assertThat(json(head)).contains("second");
    }
}
