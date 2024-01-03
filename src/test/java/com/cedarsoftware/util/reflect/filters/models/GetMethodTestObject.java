package com.cedarsoftware.util.reflect.filters.models;

import lombok.AccessLevel;
import lombok.Getter;

public class GetMethodTestObject {

    @Getter(AccessLevel.PRIVATE)
    private final String test1 = "foo";

    @Getter(AccessLevel.PRIVATE)
    private String test2 = "bar";

    @Getter(AccessLevel.PROTECTED)
    protected String test3 = "foo";

    @Getter(AccessLevel.PACKAGE)
    String test4 = "bar";

    @Getter
    public String test5 = "foo";

    @Getter
    public static String test6 = "bar";
}
