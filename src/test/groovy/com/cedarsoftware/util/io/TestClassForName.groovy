package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.fail

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestClassForName
{
    @Test
    void testClassForName()
    {
        Class testObjectClass = MetaUtils.classForName('com.cedarsoftware.util.io.TestObject', TestClassForName.class.getClassLoader())
        assert testObjectClass instanceof Class
        assert 'com.cedarsoftware.util.io.TestObject' == ((Class)testObjectClass).name
    }

    @Test
    void testClassForNameWithClassloader()
    {
        Class testObjectClass = MetaUtils.classForName('ReallyLong', new AlternateNameClassLoader('ReallyLong', Long.class))
        assert testObjectClass instanceof Class
        assert 'java.lang.Long' == ((Class)testObjectClass).name
    }

    @Test
    void testClassForNameNullClassErrorHandling()
    {
        try
        {
            MetaUtils.classForName(null, TestClassForName.class.getClassLoader())
            fail()
        }
        catch (JsonIoException ignored)
        { }

        assert Map.class.isAssignableFrom(MetaUtils.classForName('Smith&Wesson', TestClassForName.class.getClassLoader()))
    }

    @Test
    void testClassForNameFailOnClassLoaderErrorTrue()
    {
        try {
            MetaUtils.classForName('foo.bar.baz.Qux', TestClassForName.class.getClassLoader(), true)
            fail()
        }
        catch (JsonIoException ignored)
        { }
    }

    @Test
    void testClassForNameFailOnClassLoaderErrorFalse()
    {
        Class testObjectClass = MetaUtils.classForName('foo.bar.baz.Qux', TestClassForName.class.getClassLoader(), false)
        assert testObjectClass instanceof Class
        assert 'java.util.LinkedHashMap' == ((Class)testObjectClass).name
    }

    private class AlternateNameClassLoader extends ClassLoader
    {
        private final String alternateName;
        private final Class<?> clazz;

        AlternateNameClassLoader(String alternateName, Class<?> clazz)
        {
            super(AlternateNameClassLoader.class.getClassLoader());
            this.alternateName = alternateName;
            this.clazz = clazz;
        }

        Class<?> loadClass(String className) throws ClassNotFoundException
        {
            return findClass(className);
        }

        protected Class<?> findClass(String className) throws ClassNotFoundException
        {
            try
            {
                return findSystemClass(className);
            }
            catch (Exception ignored) { }

            if (alternateName.equals(className))
            {
                return Long.class;
            }

            return null;
        }
    }
}
