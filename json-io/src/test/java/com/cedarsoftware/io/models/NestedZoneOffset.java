package com.cedarsoftware.io.models;

import java.time.ZoneOffset;

public class NestedZoneOffset {
    public ZoneOffset one;
    public ZoneOffset two;
    public String holiday;
    public Long value;

    public NestedZoneOffset(ZoneOffset one, ZoneOffset two) {
        this.one = one;
        this.two = two;
    }

    public NestedZoneOffset(ZoneOffset date) {
        this(date, date);
    }
}
