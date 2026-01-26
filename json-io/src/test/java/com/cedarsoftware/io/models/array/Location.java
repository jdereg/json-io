package com.cedarsoftware.io.models.array;

import java.util.Arrays;

public class Location {
    private String locationName;
    private String streetAddress;
    private Building[] buildings;
    private Object[] buildingz;

    // Constructors
    public Location() {}

    public Location(String locationName, String streetAddress, Building[] buildings, Object[] buildingz) {
        this.locationName = locationName;
        this.streetAddress = streetAddress;
        this.buildings = buildings;
        this.buildingz = buildingz;
    }

    // Getters and Setters
    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public Building[] getBuildings() {
        return buildings;
    }

    public void setBuildings(Building[] buildings) {
        this.buildings = buildings;
    }

    public Object[] getBuildingz() {
        return buildingz;
    }

    public void setBuildingz(Object[] buildingz) {
        this.buildingz = buildingz;
    }

    @Override
    public String toString() {
        return "Location{" +
                "locationName='" + locationName + '\'' +
                ", streetAddress='" + streetAddress + '\'' +
                ", buildings=" + Arrays.toString(buildings) +
                ", buildingz=" + Arrays.toString(buildingz) +
                '}';
    }
}
