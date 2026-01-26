package com.cedarsoftware.io.reflect.filters.models;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class GetMethodTestObject {

    private final String test1 = "foo";

    private String test2 = "bar";

    protected String test3 = "foo";

    String test4 = "bar";

    public String test5 = "foo";

    public static String test6 = "bar";

    public static String getTest6() {
        return GetMethodTestObject.test6;
    }

    private String getTest1() {
        return this.test1;
    }

    private String getTest2() {
        return this.test2;
    }

    protected String getTest3() {
        return this.test3;
    }

    String getTest4() {
        return this.test4;
    }

    public String getTest5() {
        return this.test5;
    }
}
