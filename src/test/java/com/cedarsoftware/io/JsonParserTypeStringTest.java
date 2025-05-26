package com.cedarsoftware.io;

import java.io.StringReader;

import com.cedarsoftware.io.JsonReader.DefaultReferenceTracker;
import com.cedarsoftware.util.FastReader;
import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Test that JsonParser keeps @type values as Strings during parsing. */
public class JsonParserTypeStringTest {
    @Test
    public void testParserKeepsTypeName() throws Exception {
        String json = "{\"@type\":\"some.missing.Type\",\"value\":1}";
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().failOnUnknownType(false).build();
        Converter converter = new Converter(options.getConverterOptions());
        MapResolver resolver = new MapResolver(options, new DefaultReferenceTracker(), converter);
        JsonParser parser = new JsonParser(new FastReader(new StringReader(json)), resolver);
        Object obj = parser.readValue(Object.class);
        assertTrue(obj instanceof JsonObject);
        JsonObject jObj = (JsonObject) obj;
        assertNull(jObj.getType());
        assertEquals("some.missing.Type", jObj.getRawTypeName());
    }
}
