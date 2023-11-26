package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ClassForNameTest
{
    @Test
    public void testClassForName()
    {
        Class<?> testObjectClass = MetaUtilsHelper.classForName("com.cedarsoftware.util.io.TestObject", ClassForNameTest.class.getClassLoader());
        assert testObjectClass instanceof Class<?>;
        assert "com.cedarsoftware.util.io.TestObject".equals(testObjectClass.getName());
    }

    @Test
    public void testClassForNameWithClassloader()
    {
        Class<?> testObjectClass = MetaUtilsHelper.classForName("ReallyLong", new AlternateNameClassLoader(new ClassForNameTest(), "ReallyLong", Long.class));
        assert testObjectClass instanceof Class<?>;
        assert "java.lang.Long".equals(testObjectClass.getName());
    }

    @Test
    public void testClassForNameNullClassErrorHandling()
    {
        assert null == MetaUtilsHelper.classForName(null, ClassForNameTest.class.getClassLoader());
        assert null == MetaUtilsHelper.classForName("Smith&Wesson", ClassForNameTest.class.getClassLoader());
    }

    @Test
    public void testClassForNameFailOnClassLoaderErrorTrue()
    {
        assert null == MetaUtilsHelper.classForName("foo.bar.baz.Qux", ClassForNameTest.class.getClassLoader());
    }

    @Test
    public void testClassForNameFailOnClassLoaderErrorFalse()
    {
        Class<?> testObjectClass = MetaUtilsHelper.classForName("foo.bar.baz.Qux", ClassForNameTest.class.getClassLoader());
        assert testObjectClass == null;
    }

    private static class AlternateNameClassLoader extends ClassLoader
    {
        AlternateNameClassLoader(ClassForNameTest enclosing, String alternateName, Class<?> clazz)
        {
            super(AlternateNameClassLoader.class.getClassLoader());
            this.alternateName = alternateName;
            this.clazz = clazz;
        }

        public Class<?> loadClass(String className) throws ClassNotFoundException
        {
            return findClass(className);
        }

        protected Class<?> findClass(String className)
        {
            try
            {
                return findSystemClass(className);
            }
            catch (Exception ignored)
            { }

            if (alternateName.equals(className))
            {
                return Long.class;
            }

            return null;
        }

        private final String alternateName;
        private final Class<?> clazz;
    }
}
