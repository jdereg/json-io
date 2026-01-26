package com.cedarsoftware.io.models.array;

import java.util.Arrays;

public class Building {
    private String name;
    private int numberOfFloors;
    private String type;
    private Asset[] assets;
    private Object[] assetz;

    // Constructors
    public Building() {}

    public Building(String name, int numberOfFloors, String type, Asset[] assets, Object[] assetz) {
        this.name = name;
        this.numberOfFloors = numberOfFloors;
        this.type = type;
        this.assets = assets;
        this.assetz = assetz;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumberOfFloors() {
        return numberOfFloors;
    }

    public void setNumberOfFloors(int numberOfFloors) {
        this.numberOfFloors = numberOfFloors;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Asset[] getAssets() {
        return assets;
    }

    public void setAssets(Asset[] assets) {
        this.assets = assets;
    }

    public Object[] getAssetz() {
        return assetz;
    }

    public void setAssetz(Object[] assetz) {
        this.assetz = assetz;
    }

    @Override
    public String toString() {
        return "Building{" +
                "name='" + name + '\'' +
                ", numberOfFloors=" + numberOfFloors +
                ", type='" + type + '\'' +
                ", assets=" + Arrays.toString(assets) +
                ", assetz=" + Arrays.toString(assetz) +
                '}';
    }
}
