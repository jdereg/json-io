package com.cedarsoftware.io;

import java.util.Date;

import com.cedarsoftware.io.Writers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateAsLongWriterTest
{
    private static class DateHolder {
        private Date util;
        private java.sql.Date sql;
    }

    @Test
    void testDateAsLongWriter()
    {
        DateHolder holder = new DateHolder();
        holder.util = new Date(1700000000000L);
        holder.sql = java.sql.Date.valueOf("2024-02-02");

        WriteOptions options = new WriteOptionsBuilder().build();
        assertTrue(options.getCustomWriter(Date.class) instanceof Writers.DateAsLongWriter);
        String json = TestUtil.toJson(holder, options);

        assertTrue(json.contains("\"util\":" + holder.util.getTime()));
        assertTrue(json.contains("\"sql\":\"" + holder.sql.toLocalDate().toString() + "\""));

        DateHolder result = TestUtil.toObjects(json, DateHolder.class);
        assertEquals(holder.util.getTime(), result.util.getTime());
        assertEquals(holder.sql, result.sql);
    }
}

