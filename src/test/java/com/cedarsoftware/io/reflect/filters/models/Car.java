package com.cedarsoftware.io.reflect.filters.models;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Car extends Entity {
    String make;
    String model;

    ColorEnum color;

    List<Part> installedParts;
    List<Part> accessories;
}
