package com.cedarsoftware.util.io;

import com.cedarsoftware.util.PrintStyle;

public class JsonUtilities {

    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        Object object = JsonReader.toObjects(json, readOptions.ensureUsingMaps());
        return JsonWriter.toJson(object, writeOptions.ensurePrettyPrint());
    }

    public static String formatJson(String json) {
        return formatJson(json,
                new ReadOptionsBuilder().returnAsMaps().build(),
                new WriteOptionsBuilder().withPrintStyle(PrintStyle.PRETTY_PRINT).build());
    }

    public static <T> T deepCopy(Object o) {
        return deepCopy(o, new ReadOptionsBuilder().build(), new WriteOptionsBuilder().build());
    }

    public static <T> T deepCopy(Object o, ReadOptions readOptions, WriteOptions writeOptions) {
        String json = JsonWriter.toJson(o, writeOptions);
        return JsonReader.toObjects(json, readOptions);
    }
}
