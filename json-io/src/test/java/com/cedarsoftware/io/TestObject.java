package com.cedarsoftware.io;

import java.io.Serializable;

/**
 * Simple test class for unit tests.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
public class TestObject implements Comparable, Serializable
{
    public TestObject(String name)
    {
        _name = name;
    }

    public TestObject(String name, TestObject other)
    {
        _name = name;
        _other = other;
    }

    public int compareTo(Object that)
    {
        if (that instanceof String) {
            return _name.compareTo((String) that);
        }
        if (!(that instanceof TestObject))
        {
            return 1;
        }

        return _name.compareTo(((TestObject) that)._name);
    }

    public int hashCode()
    {
        return _name == null ? 0 : _name.hashCode();
    }

    public boolean equals(Object that)
    {
        if (that == null)
        {
            return false;
        }

        return that instanceof TestObject && _name.equals(((TestObject) that)._name);
    }

    public String toString()
    {
        return "name=" + _name;
    }

    public String getName()
    {
        return _name;
    }

    protected String _name;
    protected TestObject _other;
}
