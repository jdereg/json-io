package com.cedarsoftware.io.models;

import java.time.ZonedDateTime;

public class NestedZonedDateTime {
    public ZonedDateTime date1;
    public ZonedDateTime date2;
    public String holiday;
    public Long value;

    public NestedZonedDateTime(ZonedDateTime date1, ZonedDateTime date2) {
        this.holiday = "Festivus";
        this.value = 999L;
        this.date1 = date1;
        this.date2 = date2;
    }

    public NestedZonedDateTime(ZonedDateTime date) {
        this(date, date);
    }
}
