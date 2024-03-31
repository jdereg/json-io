package com.cedarsoftware.io.reflect.filters.models;

public class PrivateFinalObject {

    private final int x;
    private final int y;

    public PrivateFinalObject(int x, int y, String key, boolean flatuated) {
        this.x = x;
        this.y = y;
        this.key = key;
        this.flatuated = flatuated;
    }

    public int getTotal() {
        return x + y;
    }

    private final String key;

    private final boolean flatuated;

    public String getKey() {
        return this.key;
    }

    public boolean isFlatuated() {
        return this.flatuated;
    }
}
