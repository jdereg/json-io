package com.cedarsoftware.util.io;

import com.google.gson.Gson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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
public class TestEmptyEnumSetOnJDK17
{
    static enum TestEnum
    {
        V1
    }

    @Test
    public void testEmptyEnumSetOnJDK17()
    {
        Object o = EnumSet.noneOf(TestEnum.class);

        String json = JsonWriter.objectToJson(o);
        EnumSet es = (EnumSet) JsonReader.jsonToJava(json);

        assert es.isEmpty();
    }

    @Test
    public void testEnumSetOnJDK17()
    {
        EnumSet source = EnumSet.of(TestEnum.V1);

        String json = JsonWriter.objectToJson(source);
        EnumSet target = (EnumSet) JsonReader.jsonToJava(json);

        assert source.equals(target);
    }
}
