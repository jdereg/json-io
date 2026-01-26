package com.cedarsoftware.io.models;

import java.util.ArrayList;
import java.util.List;

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
public class MismatchedGetter {
    private List<String> types;
    private List<String> values;

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

    public String getRawValue() {
        return this.rawValue;
    }
}
