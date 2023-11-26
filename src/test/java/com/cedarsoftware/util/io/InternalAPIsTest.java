package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
public class InternalAPIsTest
{
    @Test
    public void testDistanceToInterface()
    {
        // The values below can differ depending on the JDK versions, so be careful the classes chosen
        assert MetaUtilsHelper.computeInheritanceDistance(Resolver.class, ObjectResolver.class) == -1;
        assert MetaUtilsHelper.computeInheritanceDistance(ObjectResolver.class, Resolver.class) > 0;
    }

    @Test
    public void testCleanString()
    {
        String s = MetaUtils.removeLeadingAndTrailingQuotes("\"Foo\"");
        assert "Foo".equals(s);
        s = MetaUtils.removeLeadingAndTrailingQuotes("Foo");
        assert "Foo".equals(s);
        s = MetaUtils.removeLeadingAndTrailingQuotes("\"Foo");
        assert "Foo".equals(s);
        s = MetaUtils.removeLeadingAndTrailingQuotes("Foo\"");
        assert "Foo".equals(s);
        s = MetaUtils.removeLeadingAndTrailingQuotes("\"\"Foo\"\"");
        assert "Foo".equals(s);
    }

    @Test
    public void testProtectedAPIs()
    {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DerivedWriter writer = new DerivedWriter(bao);
        Map ref = writer.getObjsReferenced();
        Map vis = writer.getObjVisited();
        assertNotNull(ref);
        assertNotNull(vis);
    }

    public static class DerivedWriter extends JsonWriter
    {
        public DerivedWriter(OutputStream out)
        {
            super(out);
        }
    }
}
