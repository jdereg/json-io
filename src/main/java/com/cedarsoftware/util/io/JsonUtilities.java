package com.cedarsoftware.util.io;

public class JsonUtilities {

    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        if (writeOptions.isSealed())
        {
            writeOptions = new WriteOptions(writeOptions).prettyPrint(true);
        }
        Object object = JsonReader.toObjects(json, readOptions.ensureUsingMaps(), null);
        return JsonWriter.toJson(object, writeOptions.prettyPrint(true));
    }

    public static String formatJson(String json) {
        return formatJson(json,
                new ReadOptionsBuilder().returnAsMaps().build(),
                new WriteOptions().prettyPrint(true));
    }

    public static <T> T deepCopy(Object o) {
        return deepCopy(o, new ReadOptionsBuilder().build(), new WriteOptions());
    }

    public static <T> T deepCopy(Object o, ReadOptions readOptions, WriteOptions writeOptions) {
        String json = JsonWriter.toJson(o, writeOptions);
        return JsonReader.toObjects(json, readOptions, null);
    }
}
