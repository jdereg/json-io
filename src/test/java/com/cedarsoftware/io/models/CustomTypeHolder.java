package com.cedarsoftware.io.models;

/**
 * Test class with a field of a custom type that doesn't have
 * String converter support. Used for testing empty string to null fallback.
 */
public class CustomTypeHolder {
    private String name;
    private CustomType custom;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CustomType getCustom() {
        return custom;
    }

    public void setCustom(CustomType custom) {
        this.custom = custom;
    }

    /**
     * Inner class representing a custom type without String converter.
     */
    public static class CustomType {
        private int value;

        public CustomType() {}

        public CustomType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}
