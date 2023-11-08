package com.cedarsoftware.util.io;

public class Convention {
    public static void throwIfNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
