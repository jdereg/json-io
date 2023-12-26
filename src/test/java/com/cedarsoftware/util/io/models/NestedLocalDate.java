package com.cedarsoftware.util.io.models;

import java.time.LocalDate;

import lombok.Getter;

@Getter
public class NestedLocalDate {
    private final LocalDate date1;
    private final LocalDate date2;
    private final String holiday;
    private final Long value;

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
