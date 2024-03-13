package com.cedarsoftware.io.reflect.filters.models;

public class ObjectWithBooleanValues {

    private final boolean test1 = true;
    private boolean test2 = false;
    protected boolean test3 = true;
    boolean test4 = false;

    public boolean test5 = true;

    public static boolean test6 = false;


    private boolean isTest1() {
        return test1;
    }

    private boolean isTest2() {
        return test2;
    }

    protected boolean isTest3() {
        return test3;
    }

    boolean isTest4() {
        return test4;
    }

    public boolean isTest5() {
        return test5;
    }

    public static boolean isTest6() {
        return test6;
    }
}
