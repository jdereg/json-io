package com.cedarsoftware.util.reflect.filters.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Car extends Entity {
    String make;
    String model;

    ColorEnum color;

    List<Part> installedParts;
    List<Part> accessories;
}
