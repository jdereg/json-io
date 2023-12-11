package com.cedarsoftware.util.io;

public class Convention {
    public static void throwIfNull(Object value, String message) {
        if (value == null) {
            throw new JsonIoException(message);
        }
    }

    public static void throwIfNullOrEmpty(String value, String message) {
        if (value == null || value.isEmpty()) {
            throw new JsonIoException(message);
        }
    }
}
