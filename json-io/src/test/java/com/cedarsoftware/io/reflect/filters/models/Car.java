package com.cedarsoftware.io.reflect.filters.models;

import java.util.List;

public class Car extends Entity {
    String make;
    String model;

    ColorEnum color;

    List<Part> installedParts;
    List<Part> accessories;

    public String getMake() {
        return this.make;
    }

    public String getModel() {
        return this.model;
    }

    public ColorEnum getColor() {
        return this.color;
    }

    public List<Part> getInstalledParts() {
        return this.installedParts;
    }

    public List<Part> getAccessories() {
        return this.accessories;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setColor(ColorEnum color) {
        this.color = color;
    }

    public void setInstalledParts(List<Part> installedParts) {
        this.installedParts = installedParts;
    }

    public void setAccessories(List<Part> accessories) {
        this.accessories = accessories;
    }
}
