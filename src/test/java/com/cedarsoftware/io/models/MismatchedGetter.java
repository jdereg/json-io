package com.cedarsoftware.io.models;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

public class MismatchedGetter {
    private List<String> types;
    private List<String> values;

    @Getter
    private String rawValue;

    public void generate(String rawValue) {
        this.rawValue = rawValue;
        types = new ArrayList<>();
        types.add("bn");
        types.add("cn");
        types.add("cn");

        values = new ArrayList<>();
        values.add(".com");
        values.add(".net");
        values.add(".au");
    }

    public String[] getTypes() {
        return types.toArray(new String[]{});
    }

    public String[] getValues() {
        return values.toArray(new String[]{});
    }
}
