package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

public class DebugArrayConversion {
    @Test
    void testDebug() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        char[] chars = new char[]{'a', 'b', 'c'};
        String json = JsonIo.toJson(chars, writeOptions);
        System.out.println("JSON: " + json);
        
        // Enable debug
        System.setProperty("json-io.debug", "true");
        
        byte[] result = JsonIo.toObjects(json, readOptions, byte[].class);
        System.out.println("Result: " + (result != null ? java.util.Arrays.toString(result) : "null"));
    }
}
