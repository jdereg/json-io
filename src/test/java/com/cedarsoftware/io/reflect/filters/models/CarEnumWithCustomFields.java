package com.cedarsoftware.io.reflect.filters.models;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
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

    public final float rating;

    private final String tire;

    private final boolean stick;

    public float getRating() {
        return this.rating;
    }

    public boolean isStick() {
        return this.stick;
    }
}


