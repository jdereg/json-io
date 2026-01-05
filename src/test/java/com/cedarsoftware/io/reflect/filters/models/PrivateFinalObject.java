package com.cedarsoftware.io.reflect.filters.models;

public class PrivateFinalObject {

    private final int x;
    private final int y;

    public PrivateFinalObject(int x, int y, String key, boolean fluctuated) {
        this.x = x;
        this.y = y;
        this.key = key;
        this.fluctuated = fluctuated;
    }

    public int getTotal() {
        return x + y;
    }

    private final String key;

    private final boolean fluctuated;

    public String getKey() {
        return this.key;
    }

    public boolean isFluctuated() {
        return this.fluctuated;
    }
}
