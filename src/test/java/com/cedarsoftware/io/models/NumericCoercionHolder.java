package com.cedarsoftware.io.models;

/**
 * Test class with various numeric field types for testing
 * coerceLong and coerceDouble paths in MapResolver.
 */
public class NumericCoercionHolder {
    private double doubleField;
    private float floatField;
    private long longField;
    private int intField;

    public double getDoubleField() {
        return doubleField;
    }

    public void setDoubleField(double doubleField) {
        this.doubleField = doubleField;
    }

    public float getFloatField() {
        return floatField;
    }

    public void setFloatField(float floatField) {
        this.floatField = floatField;
    }

    public long getLongField() {
        return longField;
    }

    public void setLongField(long longField) {
        this.longField = longField;
    }

    public int getIntField() {
        return intField;
    }

    public void setIntField(int intField) {
        this.intField = intField;
    }
}
