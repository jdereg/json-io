package com.cedarsoftware.util.reflect.filters.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class PrivateFinalObject {

    private final int x;
    private final int y;

    public int getTotal() {
        return x + y;
    }

    @Getter
    private final String key;

    @Getter
    private final boolean flatuated;
}
