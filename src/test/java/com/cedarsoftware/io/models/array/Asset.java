package com.cedarsoftware.io.models.array;

public class Asset {
    private String name;
    private Double value;

    // Constructors
    public Asset() {}

    public Asset(String name, Double value) {
        this.name = name;
        this.value = value;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Asset{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
