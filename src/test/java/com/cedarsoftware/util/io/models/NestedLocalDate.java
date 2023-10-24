package com.cedarsoftware.util.io.models;

import java.time.LocalDate;

public class NestedLocalDate {
    public LocalDate date1;
    public LocalDate date2;
    public String holiday;
    public Long value;

    public NestedLocalDate(LocalDate date1, LocalDate date2) {
        this.holiday = "Festivus";
        this.value = 999L;
        this.date1 = date1;
        this.date2 = date2;
    }

    public NestedLocalDate(LocalDate date) {
        this(date, date);
    }
}
