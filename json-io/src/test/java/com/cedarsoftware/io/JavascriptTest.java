package com.cedarsoftware.io;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

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
class JavascriptTest
{
    private static final ScriptEngineManager scm = new ScriptEngineManager(ClassLoader.getSystemClassLoader());

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilRefInsideArray() throws Exception
    {
        ScriptEngine engine = scm.getEngineByName("JavaScript");
        String jsonUtil = ClassUtilities.loadResourceAsString("jsonUtil.js");
        engine.eval(jsonUtil);
        engine.eval("var json = '[{\"@id\":1,\"_name\":\"Charlize\",\"_other\":null},{\"@ref\":1}]';\n" +
                "var array = JSON.parse(json)\n" +
                "assert(array[0]._name == 'Charlize');\n" +
                "assert(!array[1]._name);\n" +
                "\n" +
                "resolveRefs(array);\n" +
                "\n" +
                "assert(array[0]._name == 'Charlize');\n" +
                "assert(array[1]._name == 'Charlize');\n" +
                "assert(array[0] === array[1]);   // Exactly the same instance\n");
    }

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilForwardRefInsideArray() throws Exception
    {
        ScriptEngine engine = scm.getEngineByName("JavaScript");;
        String jsonUtil = ClassUtilities.loadResourceAsString("jsonUtil.js");
        engine.eval(jsonUtil);
        engine.eval("var json = '[{\"@ref\":1}, {\"@id\":1,\"_name\":\"Charlize\",\"_other\":null}]';\n" +
                "var array = JSON.parse(json)\n" +
                "assert(!array[0]._name);\n" +
                "assert(array[1]._name == 'Charlize');\n" +
                "\n" +
                "resolveRefs(array);\n" +
                "\n" +
                "assert(array[0]._name == 'Charlize');\n" +
                "assert(array[1]._name == 'Charlize');\n" +
                "assert(array[0] === array[1]);   // Exactly the same instance\n");
    }

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilRefInsideObject() throws Exception
    {
        ScriptEngine engine = scm.getEngineByName("JavaScript");
        String jsonUtil = ClassUtilities.loadResourceAsString("jsonUtil.js");
        engine.eval(jsonUtil);
        engine.eval("var json = '{\"@id\":1,\"_name\":\"Alpha\",\"_other\":{\"_name\":\"Bravo\",\"_other\":{\"@ref\":1}}}';\n" +
                "var testObj = JSON.parse(json)\n" +
                "assert(testObj._name == 'Alpha');\n" +
                "assert(testObj._other._name == 'Bravo');\n" +
                "assert(testObj._other._other['@ref'] == 1);\n" +
                "assert(!testObj._other._other._name);\n" +
                "\n" +
                "resolveRefs(testObj);\n" +
                "\n" +
                "assert(testObj._other._other === testObj);\n" +
                "assert(testObj._other._name == 'Bravo');\n" +
                "assert(testObj._other._other._name == 'Alpha');\n" +
                "assert(!testObj._other._other['@ref']);\n");
    }

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilRefCycle() throws Exception
    {
        ScriptEngine engine = scm.getEngineByName("JavaScript");
        String jsonUtil = ClassUtilities.loadResourceAsString("jsonUtil.js");
        engine.eval(jsonUtil);
        engine.eval("var json = '[{\"@id\":1,\"_name\":\"Alpha\",\"_other\":{\"@ref\":2}},{\"@id\":2,\"_name\":\"Bravo\",\"_other\":{\"@ref\":1}}]';\n" +
                "var array = JSON.parse(json);\n" +
                "var testObj1 = array[0];\n" +
                "var testObj2 = array[1];\n" +
                "assert(testObj1._name == 'Alpha');\n" +
                "assert(testObj1._other['@ref'] == 2);\n" +
                "assert(testObj2._name == 'Bravo');\n" +
                "assert(testObj2._other['@ref'] == 1);\n" +
                "\n" +
                "resolveRefs(array);\n" +
                "\n" +
                "assert(testObj1._other._name == 'Bravo');\n" +
                "assert(testObj1._other._other._name == 'Alpha');\n" +
                "assert(testObj1._other === testObj2);\n");
    }
}
