package com.cedarsoftware.io.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Race {
    private String name = "Race.name";
    private int age;

    private int strength;
    private int wisdom;
    private int intelligence;

    private double magicResistance;
    private double fireResistance;
}
