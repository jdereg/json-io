package com.cedarsoftware.io.reflect.filters.models;

public class PrimitiveAccessorTestObject {
    private final boolean booleanValue = true;
    private final byte byteValue = 12;
    private final char charValue = 'J';
    private final short shortValue = 32000;
    private final int intValue = 123456789;
    private final long longValue = 9876543210L;
    private final float floatValue = 3.25f;
    private final double doubleValue = 6.5d;

    public boolean isBooleanValue() {
        return booleanValue;
    }

    public byte getByteValue() {
        return byteValue;
    }

    public char getCharValue() {
        return charValue;
    }

    public short getShortValue() {
        return shortValue;
    }

    public int getIntValue() {
        return intValue;
    }

    public long getLongValue() {
        return longValue;
    }

    public float getFloatValue() {
        return floatValue;
    }

    public double getDoubleValue() {
        return doubleValue;
    }
}
