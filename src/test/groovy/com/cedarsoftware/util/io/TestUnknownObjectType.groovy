package com.cedarsoftware.util.io

import com.google.gson.JsonIOException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows
import static org.assertj.core.api.Assertions.assertThat;

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
class TestUnknownObjectType
{
    @Test
    void testUnknownObjects()
    {
        def json = '{"DashboardController.updateFilter":{"dto":{"@type":"java.util.Map","assignedToList":["1000004947"],"assignedToMap":{"1000004947":"Me"},"claimsExistIndicator":true,"defaultFilter":false,"defaultSortField":"trnEffectiveDate","defaultSortOrder":"asc","effectiveDateFrom":null,"effectiveDateTo":null,"fieldList":[],"needByDateFrom":null,"needByDateTo":null,"overdueIndicator":true,"pendedOption":null,"previouslyPendedIndicator":true,"producerCodeList":[],"producerCodesPCTerrField":[],"producerCodeMap":null,"profitCenterList":[],"rushIndicator":true,"territoryList":[],"underwriterList":[],"underwriterMap":{},"viewByOption":"view_by_actvity","countOnly":false,"newFilterName":"My Assigned To","originalFilterName":"My Assigned To","filterRanIndicator":false,"filterRanIndicatorMap":{"@type":"java.util.LinkedHashMap","1000004947":true,"0000029443":false,"0000020985":false,"0000020994":false},"rowLimit":0,"filterType":null,"filterName":"My Assigned To","com.gaic.bue.uwd.ra.common.dto.AbstractFilterDto.defaultFilter":false,"filterAppCode":"RADashboardFilter","createDate":null,"updateDate":null,"createHid":"1000004947","updateHid":null,"sourceNumber":0,"disabledFields":{},"screenCommands":{},"rpmSession":null}}}'
        def myParams = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        def inputParams = new JsonReader().jsonObjectsToJava(myParams)
        def json2 = JsonWriter.objectToJson(inputParams)
        assert json2.length() > 100
    }

    @Test
    void testUnknownClassType()
    {
        def json = '{"@type":"foo.bar.baz.Qux", "name":"Joe"}'
        def java = JsonReader.jsonToJava(json)
        assert java instanceof Map
        assert java.name == 'Joe'
    }

    @Test
    void testUnknownClassTypePassesWhenFailOptionFalse()
    {
        def json = '{"@type":"foo.bar.baz.Qux", "name":"Joe"}'
        def java = JsonReader.jsonToJava(json, [(JsonReader.FAIL_ON_UNKNOWN_TYPE): false ])
        assert java instanceof Map
        assert java.name == 'Joe'
    }

    @Test
    void testUnknownClassTypeFailsWhenFailOptionTrue()
    {
        def json = '{"@type":"foo.bar.baz.Qux", "name":"Joe"}'
        assertThrows(JsonIoException.class, { JsonReader.jsonToJava(json, [(JsonReader.FAIL_ON_UNKNOWN_TYPE): true]) });
    }
}
