package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link CustomWriterDispatch} machinery added in json-io 4.103.0:
 * when a {@link JsonClassWriter} overrides one of the new
 * {@link JsonGenerator}-based methods, the framework constructs a bridge
 * generator and dispatches to it; when only the deprecated
 * {@link java.io.Writer}-based methods are overridden, the framework
 * dispatches via the legacy path.
 *
 * <p>Both paths must produce identical JSON output for the same logical
 * emission sequence — the test pairs a new-API writer against an old-API
 * writer for the same model class and compares.
 */
public class JsonGeneratorCustomWriterDispatchTest {

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    static final class Point {
        final int x;
        final int y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    static final class Tag {
        final String name;
        Tag(String name) { this.name = name; }
    }

    // ------------------------------------------------------------------
    // Custom writers — NEW API (uses JsonGenerator)
    // ------------------------------------------------------------------

    static final class PointWriterNew implements JsonClassWriter<Point> {
        @Override
        public void write(Point p, boolean showType, JsonGenerator gen, WriterContext ctx) throws IOException {
            gen.writeFieldName("x");
            gen.writeNumber(p.x);
            gen.writeFieldName("y");
            gen.writeNumber(p.y);
        }
    }

    static final class TagWriterNewPrimitive implements JsonClassWriter<Tag> {
        @Override
        public boolean hasPrimitiveForm(WriterContext ctx) { return true; }

        @Override
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext ctx) throws IOException {
            gen.writeString(((Tag) o).name);
        }
    }

    // ------------------------------------------------------------------
    // Custom writers — OLD API (uses Writer directly)
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    static final class PointWriterOld implements JsonClassWriter<Point> {
        @Override
        public void write(Point p, boolean showType, Writer output, WriterContext ctx) throws IOException {
            output.write("\"x\":");
            output.write(Integer.toString(p.x));
            output.write(",\"y\":");
            output.write(Integer.toString(p.y));
        }
    }

    @SuppressWarnings("deprecation")
    static final class TagWriterOldPrimitive implements JsonClassWriter<Tag> {
        @Override
        public boolean hasPrimitiveForm(WriterContext ctx) { return true; }

        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext ctx) throws IOException {
            output.write('"');
            output.write(((Tag) o).name);
            output.write('"');
        }
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void dispatchInfo_newApiWriter_reportsNewMethods() {
        CustomWriterDispatch.Info info = CustomWriterDispatch.forWriter(new PointWriterNew());
        assertTrue(info.useNewWrite, "PointWriterNew overrides the new write method");
        // PointWriterNew does NOT override writePrimitiveForm; both forms are the interface default;
        // resolver defaults to "use new" when neither is overridden (no observable difference).
        assertTrue(info.useNewPrimitive);
    }

    @Test
    void dispatchInfo_oldApiWriter_reportsOldMethods() {
        CustomWriterDispatch.Info info = CustomWriterDispatch.forWriter(new PointWriterOld());
        assertFalse(info.useNewWrite, "PointWriterOld overrides only the deprecated write method");
    }

    @Test
    void dispatchInfo_newPrimitiveWriter() {
        CustomWriterDispatch.Info info = CustomWriterDispatch.forWriter(new TagWriterNewPrimitive());
        assertTrue(info.useNewPrimitive);
    }

    @Test
    void dispatchInfo_oldPrimitiveWriter() {
        CustomWriterDispatch.Info info = CustomWriterDispatch.forWriter(new TagWriterOldPrimitive());
        assertFalse(info.useNewPrimitive);
    }

    @Test
    void newApi_objectBodyWriter_producesSameOutputAsOldApi() {
        Point p = new Point(3, 4);

        WriteOptions optsNew = new WriteOptionsBuilder()
                .addCustomWrittenClass(Point.class, new PointWriterNew())
                .showTypeInfoNever()
                .build();
        WriteOptions optsOld = new WriteOptionsBuilder()
                .addCustomWrittenClass(Point.class, new PointWriterOld())
                .showTypeInfoNever()
                .build();

        String jsonNew = JsonIo.toJson(p, optsNew);
        String jsonOld = JsonIo.toJson(p, optsOld);

        assertEquals(jsonOld, jsonNew,
                "New-API custom writer must produce byte-identical JSON to the old-API equivalent");
        assertEquals("{\"x\":3,\"y\":4}", jsonNew);
    }

    @Test
    void newApi_primitiveFormWriter_producesSameOutputAsOldApi() {
        Tag t = new Tag("admin");

        WriteOptions optsNew = new WriteOptionsBuilder()
                .addCustomWrittenClass(Tag.class, new TagWriterNewPrimitive())
                .showTypeInfoNever()
                .build();
        WriteOptions optsOld = new WriteOptionsBuilder()
                .addCustomWrittenClass(Tag.class, new TagWriterOldPrimitive())
                .showTypeInfoNever()
                .build();

        String jsonNew = JsonIo.toJson(t, optsNew);
        String jsonOld = JsonIo.toJson(t, optsOld);

        assertEquals(jsonOld, jsonNew,
                "New-API primitive-form custom writer must produce byte-identical JSON to the old-API equivalent");
        assertEquals("\"admin\"", jsonNew);
    }

    @Test
    void newApi_objectBodyWriter_prettyPrinted_indentMatchesJsonWriter() {
        Point p = new Point(7, 11);

        WriteOptions opts = new WriteOptionsBuilder()
                .addCustomWrittenClass(Point.class, new PointWriterNew())
                .showTypeInfoNever()
                .prettyPrint(true)
                .build();

        String json = JsonIo.toJson(p, opts);

        // The bridge generator inherits CharStreamGenerator's pretty-print
        // semantics (already covered by JsonGeneratorTest). What this test
        // pins down for the dispatch path:
        //   1. No duplicated leading newline+indent on the first field (the
        //      bridge correctly suppresses its first indent because JsonWriter
        //      already emitted one).
        //   2. The opening brace and the first field are separated by exactly
        //      one newline+indent (from JsonWriter's tabIn).
        //   3. The output is multi-line and contains both fields.
        // Exact whitespace placement around the value-after-colon is a
        // CharStreamGenerator pretty-print question separate from dispatch.
        assertTrue(json.startsWith("{\n  \"x\""),
                "first field should follow the opening brace with exactly one newline+indent: " + json);
        assertTrue(json.contains("7"));
        assertTrue(json.contains("11"));
        assertTrue(json.endsWith("}"));
        // Two newlines between opening brace and the comma after the first field
        // would mean duplicated indent — we want to NOT see that.
        assertFalse(json.contains("{\n  \n"),
                "bridge must not double-emit the leading newline+indent on the first field: " + json);
    }
}
