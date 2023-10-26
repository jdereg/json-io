package com.cedarsoftware.util.reflect.filters.models;

public enum CarEnumWithCustomFields {

    MUSTANG(167.9, 8.9f, "Michelin", true),
    COMARO(135.7, 6.7f, "Goodyear", false),
    MERCEDES(117.7, 8.2f, "Pirelli", false),
    FERRARI(239.7, 9.9f, "Pirelli", true);

    CarEnumWithCustomFields(double speed, float rating, String tire, boolean stick) {
        this.speed = speed;
        this.rating = rating;
        this.tire = tire;
        this.stick = stick;
    }

    private final double speed;
    private final float rating;

    private final String tire;

    private final boolean stick;
}


