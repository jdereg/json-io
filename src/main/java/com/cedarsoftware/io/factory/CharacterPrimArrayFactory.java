package com.cedarsoftware.io.factory;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
public class CharacterPrimArrayFactory extends ArrayFactory<char[]> {

    public CharacterPrimArrayFactory() {
        super(char[].class);
    }

    public char[] newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object[] items = jObj.getItems();
        Object value;

        if (items == null) {
            value = null;
        } else {
            int len = items.length;
            if (len == 0) {
                value = new char[0];
            } else if (len == 1) {
                String s = (String) items[0];
                value = s.toCharArray();
            } else {
                throw new JsonIoException("char[] should only have one String in the [], found " + len + ", line " + jObj.getLine() + ", col " + jObj.getCol());
            }
        }
        return (char[]) jObj.setTarget(value);
    }
}
