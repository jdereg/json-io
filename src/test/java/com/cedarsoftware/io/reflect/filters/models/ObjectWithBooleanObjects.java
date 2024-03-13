package com.cedarsoftware.io.reflect.filters.models;

public class ObjectWithBooleanObjects {

    private final Boolean test1 = true;
    private Boolean test2 = false;
    protected Boolean test3 = true;
    Boolean test4 = false;

    public Boolean test5 = true;

    public static Boolean test6 = false;


    private Boolean isTest1() {
        return test1;
    }

    private Boolean isTest2() {
        return test2;
    }

    protected Boolean isTest3() {
        return test3;
    }

    Boolean isTest4() {
        return test4;
    }

    public Boolean isTest5() {
        return test5;
    }

    public static Boolean isTest6() {
        return test6;
    }
}
