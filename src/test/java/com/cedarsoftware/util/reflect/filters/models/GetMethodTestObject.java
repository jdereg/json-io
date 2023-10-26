package com.cedarsoftware.util.reflect.filters.models;

public class GetMethodTestObject {
    private final String test1 = "foo";
    private String test2 = "bar";
    protected String test3 = "foo";
    String test4 = "bar";

    public String test5 = "foo";

    public static String test6 = "bar";


    private String getTest1() {
        return test1;
    }

    private String getTest2() {
        return test2;
    }

    protected String getTest3() {
        return test3;
    }

    String getTest4() {
        return test4;
    }

    public String getTest5() {
        return test5;
    }

    public static String getTest6() {
        return test6;
    }
}
