package com.cedarsoftware.io;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link JsonGenerator}-based overloads of {@link Writers#writeWithStringFormat},
 * {@link Writers#writeNumericWithFieldFormat}, and {@link Writers#writeWithFieldFormat}
 * added in json-io 4.103.0 produce output equivalent to the deprecated
 * {@link java.io.Writer}-based forms.
 */
public class WritersJsonGeneratorOverloadTest {

    /**
     * Minimal WriterContext stub that returns a fixed {@link #getFieldFormatPattern} value.
     * Only the pattern accessor is exercised by the helpers under test; the rest throw
     * to surface accidental dependence on unstubbed methods in future test churn.
     */
    private static WriterContext ctxWithPattern(final String pattern) {
        return new WriterContext() {
            @Override public WriteOptions getWriteOptions() { throw new UnsupportedOperationException(); }
            @Override public String getFieldFormatPattern() { return pattern; }
            @Override public void writeObject(Object obj, boolean showType, boolean bodyOnly) { throw new UnsupportedOperationException(); }
            @Override public void writeImpl(Object obj, boolean showType) { throw new UnsupportedOperationException(); }
            @Override public IdentityIntMap getObjsReferenced() { throw new UnsupportedOperationException(); }
            @Override public void writeFieldName(String name) { throw new UnsupportedOperationException(); }
            @Override public void writeStringField(String name, String value) { throw new UnsupportedOperationException(); }
            @Override public void writeObjectField(String name, Object value) { throw new UnsupportedOperationException(); }
            @Override public void writeStartObject() { throw new UnsupportedOperationException(); }
            @Override public void writeEndObject() { throw new UnsupportedOperationException(); }
            @Override public void writeStartArray() { throw new UnsupportedOperationException(); }
            @Override public void writeEndArray() { throw new UnsupportedOperationException(); }
            @Override public void writeValue(String value) { throw new UnsupportedOperationException(); }
            @Override public void writeValue(Object value) { throw new UnsupportedOperationException(); }
            @Override public void writeArrayFieldStart(String name) { throw new UnsupportedOperationException(); }
            @Override public void writeObjectFieldStart(String name) { throw new UnsupportedOperationException(); }
            @Override public void writeNumberField(String name, Number value) { throw new UnsupportedOperationException(); }
            @Override public void writeBooleanField(String name, boolean value) { throw new UnsupportedOperationException(); }
        };
    }

    private static JsonGenerator newGenerator(StringWriter sink) {
        return JsonIo.createGenerator(sink, new WriteOptionsBuilder().build());
    }

    @Test
    void writeWithStringFormat_emitsQuotedFormattedString() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonGenerator gen = newGenerator(sink)) {
            boolean handled = Writers.writeWithStringFormat(42, gen, ctxWithPattern("value=%d"));
            assertTrue(handled);
        }
        assertEquals("\"value=42\"", sink.toString());
    }

    @Test
    void writeWithStringFormat_noPercent_returnsFalse() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonGenerator gen = newGenerator(sink)) {
            boolean handled = Writers.writeWithStringFormat(42, gen, ctxWithPattern("plain text"));
            assertFalse(handled, "patterns without '%' should not trigger string-format handling");
        }
        assertEquals("", sink.toString(), "should not write anything when not handled");
    }

    @Test
    void writeWithStringFormat_nullPattern_returnsFalse() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonGenerator gen = newGenerator(sink)) {
            assertFalse(Writers.writeWithStringFormat(42, gen, ctxWithPattern(null)));
        }
        assertEquals("", sink.toString());
    }

    @Test
    void writeNumericWithFieldFormat_emitsQuotedDecimalFormatted() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonGenerator gen = newGenerator(sink)) {
            boolean handled = Writers.writeNumericWithFieldFormat(1234.5678, gen, ctxWithPattern("#,##0.00"));
            assertTrue(handled);
        }
        assertEquals("\"1,234.57\"", sink.toString());
    }

    @Test
    void writeNumericWithFieldFormat_nonNumber_returnsFalse() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonGenerator gen = newGenerator(sink)) {
            assertFalse(Writers.writeNumericWithFieldFormat("not a number", gen, ctxWithPattern("#,##0.00")));
        }
        assertEquals("", sink.toString());
    }

    @Test
    void writeWithFieldFormat_temporalEmitsQuotedFormattedDate() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonGenerator gen = newGenerator(sink)) {
            boolean handled = Writers.writeWithFieldFormat(LocalDate.of(2026, 5, 17), gen, ctxWithPattern("yyyy-MM-dd"));
            assertTrue(handled);
        }
        assertEquals("\"2026-05-17\"", sink.toString());
    }

    @Test
    void writeWithFieldFormat_nonTemporal_returnsFalse() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonGenerator gen = newGenerator(sink)) {
            assertFalse(Writers.writeWithFieldFormat("not a temporal", gen, ctxWithPattern("yyyy-MM-dd")));
        }
        assertEquals("", sink.toString());
    }
}
