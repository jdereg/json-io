package com.cedarsoftware.io.models;

import java.util.HashMap;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class ModelHoldingSingleHashMap {
    @Getter
    private final HashMap<String, String> map;
}
