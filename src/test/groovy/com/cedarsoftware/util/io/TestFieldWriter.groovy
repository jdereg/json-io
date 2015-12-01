package com.cedarsoftware.util.io

import org.junit.Test

import java.lang.reflect.Field

import static junit.framework.TestCase.assertTrue
import static org.junit.Assert.assertEquals

/**
 * @author Francis UPTON IV (francisu@gmail.com)
 *         <br>
 *         Copyright (c) Talend, Inc.
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
class TestFieldWriter
{

    static class FieldTest
    {
        public String fieldChange;
        public String fieldNormal;
    }

    FieldTest createFieldTest()
    {
        FieldTest ft = new FieldTest();
        ft.fieldChange = "fieldChange";
        ft.fieldNormal = "fieldNormal";
        return ft;
    }

    @Test
    void testFieldWriterReader()
    {
        FieldTest ft = createFieldTest();
        Field f = ft.getClass().getField("fieldChange");

        String jsonCustom = TestUtil.getJsonString(ft, [(JsonWriter.CUSTOM_FIELD_REPLACER_MAP): [(f): new FieldReplacer() {
            @Override
            Object replace(Field field, Object currentValue)
            {
                if (f == field)
                    return currentValue + "replaced";
                return currentValue;
            }
        }]]);

        System.out.println(jsonCustom);
        assertTrue(jsonCustom.contains("\"fieldChangereplaced\""));
        assertTrue(jsonCustom.contains("\"fieldNormal\""));

        FieldTest ft2 = TestUtil.readJsonObject(jsonCustom, [(JsonReader.CUSTOM_FIELD_REPLACER_MAP): [(f): new FieldReplacer() {
            @Override
            Object replace(Field field, Object currentValue)
            {
                if (f == field)
                    return currentValue + "AndRead";
                return currentValue;
            }
        }]]);
        assertEquals("fieldChangereplacedAndRead", ft2.fieldChange);
        assertEquals("fieldNormal", ft2.fieldNormal);

    }

}