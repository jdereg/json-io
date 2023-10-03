package com.cedarsoftware.util.io;

public class ArgumentHelper {
    /**
     * @param setting Object setting value from JsonWriter args map.
     * @return boolean true if the value is (boolean) true, Boolean.TRUE, "true" (any case), or non-zero if a Number.
     */
    public static boolean isTrue(Object setting)
    {
        if (setting instanceof Boolean)
        {
            return Boolean.TRUE.equals(setting);
        }

        if (setting instanceof String)
        {
            return "true".equalsIgnoreCase((String) setting);
        }

        if (setting instanceof Number)
        {
            return ((Number)setting).intValue() != 0;
        }

        return false;
    }

}
