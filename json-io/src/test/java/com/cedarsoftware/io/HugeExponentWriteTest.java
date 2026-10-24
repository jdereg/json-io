package com.cedarsoftware.io;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BigDecimal is written in plain digits while that takes at most 1,000 digits (ConverterOptions.getMaxPlainDigits())
 * or no more than its own; past that, with its exponent. 1E+999999999 written out is a billion digits: json-io wrote
 * every BigDecimal with toPlainString(), so re-writing one read from untrusted input exhausted the heap.
 */
class HugeExponentWriteTest {

    public static class Holder {
        public BigDecimal amount;
    }

    private static final BigDecimal HUGE = new BigDecimal("1E999999999");

    @Test
    void aHugeExponentIsWrittenAsAnExponentAndReadsBack() {
        assertTimeout(Duration.ofMillis(1000), () -> {
            String json = JsonIo.toJson(HUGE, null);
            assertTrue(json.contains("1E+999999999") && json.length() < 200, json);
            assertEquals(HUGE, JsonIo.toJava(json, null).asClass(BigDecimal.class));

            Holder holder = new Holder();
            holder.amount = new BigDecimal("-1E-999999999");
            String fieldJson = JsonIo.toJson(holder, null);
            assertTrue(fieldJson.length() < 200, fieldJson);
            assertEquals(holder.amount, JsonIo.toJava(fieldJson, null).asClass(Holder.class).amount);

            StringWriter out = new StringWriter();
            try (JsonGenerator gen = JsonIo.createGenerator(out)) {
                gen.writeNumber(HUGE);
            }
            assertEquals("1E+999999999", out.toString());

            String toon = JsonIo.toToon(holder, null);
            assertTrue(toon.length() < 200, toon);
            assertEquals(holder.amount, JsonIo.fromToon(toon, null).asClass(Holder.class).amount);
        });
    }

    @Test
    void moneyAndOrdinaryNumbersStayPlain() throws Exception {
        Holder holder = new Holder();
        holder.amount = new BigDecimal("999999999999999.99");
        assertTrue(JsonIo.toJson(holder, null).contains("999999999999999.99"));
        holder.amount = new BigDecimal("999000000000000");
        assertTrue(JsonIo.toJson(holder, null).contains("\"999000000000000\""));
        // A negative scale is written in its exponent form, which keeps it; a TOON number has no scale
        holder.amount = new BigDecimal("9.99E14");
        assertTrue(JsonIo.toJson(holder, null).contains("\"9.99E+14\""));
        assertTrue(JsonIo.toToon(holder, null).contains("999000000000000"));
        StringWriter out = new StringWriter();
        try (JsonGenerator gen = JsonIo.createGenerator(out)) {
            gen.writeNumber(new BigDecimal("1E18"));
        }
        assertEquals("1000000000000000000", out.toString());
    }
}
