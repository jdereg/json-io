package com.cedarsoftware.io;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

/**
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
public class ClassForNameTest
{
    @Test
    public void testClassForName()
    {
        Class<?> testObjectClass = ClassUtilities.forName("com.cedarsoftware.io.TestObject", ClassUtilities.getClassLoader(ClassForNameTest.class));
        assert testObjectClass instanceof Class<?>;
        assert "com.cedarsoftware.io.TestObject".equals(testObjectClass.getName());
    }

    @Test
    public void testClassForNameWithClassloader()
    {
        Class<?> testObjectClass = ClassUtilities.forName("ReallyLong", new AlternateNameClassLoader(new ClassForNameTest(), "ReallyLong", Long.class));
        assert testObjectClass instanceof Class<?>;
        assert "java.lang.Long".equals(testObjectClass.getName());
    }

    @Test
    public void testClassForNameNullClassErrorHandling()
    {
        assert null == ClassUtilities.forName(null, ClassUtilities.getClassLoader(ClassForNameTest.class));
        assert null == ClassUtilities.forName("Smith&Wesson", ClassUtilities.getClassLoader(ClassForNameTest.class));
    }

    @Test
    public void testClassForNameFailOnClassLoaderErrorTrue()
    {
        assert null == ClassUtilities.forName("foo.bar.baz.Qux", ClassUtilities.getClassLoader(ClassForNameTest.class));
    }

    @Test
    public void testClassForNameFailOnClassLoaderErrorFalse()
    {
        Class<?> testObjectClass = ClassUtilities.forName("foo.bar.baz.Qux", ClassUtilities.getClassLoader(ClassForNameTest.class));
        assert testObjectClass == null;
    }

    private static class AlternateNameClassLoader extends ClassLoader
    {
        AlternateNameClassLoader(ClassForNameTest enclosing, String alternateName, Class<?> clazz)
        {
            super(ClassUtilities.getClassLoader(ClassForNameTest.class));
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
