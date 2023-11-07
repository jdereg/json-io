package com.cedarsoftware.util.io.models;

import java.time.OffsetDateTime;

public class NestedOffsetDateTime {
    public OffsetDateTime date1;
    public OffsetDateTime date2;
    public String holiday;
    public Long value;

    public NestedOffsetDateTime(OffsetDateTime date1, OffsetDateTime date2) {
        this.holiday = "Festivus";
        this.value = 999L;
        this.date1 = date1;
        this.date2 = date2;
    }

    public NestedOffsetDateTime(OffsetDateTime date) {
        this(date, date);
    }
}
