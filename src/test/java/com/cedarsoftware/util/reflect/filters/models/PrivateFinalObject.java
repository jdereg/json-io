package com.cedarsoftware.util.reflect.filters.models;

public class PrivateFinalObject {

    private final int x;
    private final long y;

    private final String key;

    private boolean flatuated;

    public PrivateFinalObject(int x, long y, String key, boolean flatuated) {
        this.x = x;
        this.y = y;
        this.key = key;
        this.flatuated = flatuated;
    }

    public String getKey() {
        return this.key;
    }

    public boolean isFlatuated() {
        return this.flatuated;
    }
}
