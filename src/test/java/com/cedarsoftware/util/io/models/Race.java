package com.cedarsoftware.util.io.models;

public class Race {
    private String name = "Race.name";
    private String age;

    private int strength;
    private int wisdom;
    private int intelligence;

    private double magicResistance;
    private double fireResistance;


    public String getAge() {
        return this.age;
    }

    public double getMagicResistance() {
        return this.magicResistance;
    }
}
