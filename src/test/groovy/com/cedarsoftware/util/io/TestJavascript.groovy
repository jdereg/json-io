package com.cedarsoftware.util.io


import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

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
class TestJavascript {
    private static final ScriptEngineManager scm = new ScriptEngineManager(ClassLoader.getSystemClassLoader());

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilRefInsideArray() {

        ScriptEngine engine = scm.getEngineByName("JavaScript")

        String jsonUtil = TestUtil.fetchResource('jsonUtil.js')
        engine.eval(jsonUtil)
        engine.eval("""\
var json = '[{"@id":1,"_name":"Charlize","_other":null},{"@ref":1}]';
var array = JSON.parse(json)
assert(array[0]._name == 'Charlize');
assert(!array[1]._name);

resolveRefs(array);

assert(array[0]._name == 'Charlize');
assert(array[1]._name == 'Charlize');
assert(array[0] === array[1]);   // Exactly the same instance
""")
    }

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilForwardRefInsideArray() {
        ScriptEngine engine = scm.getEngineByName("JavaScript")

        String jsonUtil = TestUtil.fetchResource('jsonUtil.js')
        engine.eval(jsonUtil)
        engine.eval("""\
var json = '[{"@ref":1}, {"@id":1,"_name":"Charlize","_other":null}]';
var array = JSON.parse(json)
assert(!array[0]._name);
assert(array[1]._name == 'Charlize');

resolveRefs(array);

assert(array[0]._name == 'Charlize');
assert(array[1]._name == 'Charlize');
assert(array[0] === array[1]);   // Exactly the same instance
""")
    }

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilRefInsideObject() {
        ScriptEngine engine = scm.getEngineByName("JavaScript")

        String jsonUtil = TestUtil.fetchResource('jsonUtil.js')
        engine.eval(jsonUtil)
        engine.eval("""\
var json = '{"@id":1,"_name":"Alpha","_other":{"_name":"Bravo","_other":{"@ref":1}}}';
var testObj = JSON.parse(json)
assert(testObj._name == 'Alpha');
assert(testObj._other._name == 'Bravo');
assert(testObj._other._other['@ref'] == 1);
assert(!testObj._other._other._name);

resolveRefs(testObj);

assert(testObj._other._other === testObj);
assert(testObj._other._name == 'Bravo');
assert(testObj._other._other._name == 'Alpha');
assert(!testObj._other._other['@ref']);
""")
    }

    @Test
    @EnabledOnJre(value = JRE.JAVA_8, disabledReason = "java dropped support for javascript engine after 8")
    void testJsonUtilRefCycle() {
        ScriptEngine engine = scm.getEngineByName("JavaScript")

        String jsonUtil = TestUtil.fetchResource('jsonUtil.js')
        engine.eval(jsonUtil)
        engine.eval("""\
var json = '[{"@id":1,"_name":"Alpha","_other":{"@ref":2}},{"@id":2,"_name":"Bravo","_other":{"@ref":1}}]';
var array = JSON.parse(json);
var testObj1 = array[0];
var testObj2 = array[1];
assert(testObj1._name == 'Alpha');
assert(testObj1._other['@ref'] == 2);
assert(testObj2._name == 'Bravo');
assert(testObj2._other['@ref'] == 1);

resolveRefs(array);

assert(testObj1._other._name == 'Bravo');
assert(testObj1._other._other._name == 'Alpha');
assert(testObj1._other === testObj2);
""")
    }
}
