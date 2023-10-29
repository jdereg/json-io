package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class UnknownObjectTypeTest
{
    @Test
    public void testUnknownObjects()
    {
        String json = "{\"DashboardController.updateFilter\":{\"dto\":{\"@type\":\"java.util.Map\",\"assignedToList\":[\"1000004947\"],\"assignedToMap\":{\"1000004947\":\"Me\"},\"claimsExistIndicator\":true,\"defaultFilter\":false,\"defaultSortField\":\"trnEffectiveDate\",\"defaultSortOrder\":\"asc\",\"effectiveDateFrom\":null,\"effectiveDateTo\":null,\"fieldList\":[],\"needByDateFrom\":null,\"needByDateTo\":null,\"overdueIndicator\":true,\"pendedOption\":null,\"previouslyPendedIndicator\":true,\"producerCodeList\":[],\"producerCodesPCTerrField\":[],\"producerCodeMap\":null,\"profitCenterList\":[],\"rushIndicator\":true,\"territoryList\":[],\"underwriterList\":[],\"underwriterMap\":{},\"viewByOption\":\"view_by_actvity\",\"countOnly\":false,\"newFilterName\":\"My Assigned To\",\"originalFilterName\":\"My Assigned To\",\"filterRanIndicator\":false,\"filterRanIndicatorMap\":{\"@type\":\"java.util.LinkedHashMap\",\"1000004947\":true,\"0000029443\":false,\"0000020985\":false,\"0000020994\":false},\"rowLimit\":0,\"filterType\":null,\"filterName\":\"My Assigned To\",\"com.gaic.bue.uwd.ra.common.dto.AbstractFilterDto.defaultFilter\":false,\"filterAppCode\":\"RADashboardFilter\",\"createDate\":null,\"updateDate\":null,\"createHid\":\"1000004947\",\"updateHid\":null,\"sourceNumber\":0,\"disabledFields\":{},\"screenCommands\":{},\"rpmSession\":null}}}";
        JsonObject myParams = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        Object inputParams = new JsonReader().jsonObjectsToJava(myParams);
        String json2 = TestUtil.toJson(inputParams);
        assert json2.length() > 100;
    }

    @Test
    public void testUnknownClassType()
    {
        String json = "{\"@type\":\"foo.bar.baz.Qux\", \"name\":\"Joe\"}";
        Map java = TestUtil.toJava(json);
        assert java.get("name").equals("Joe");
    }

    @Test
    public void testUnknownClassTypePassesWhenFailOptionFalse()
    {
        String json = "{\"@type\":\"foo.bar.baz.Qux\", \"name\":\"Joe\"}";
        Map java = TestUtil.toJava(json);// failOnUnknownType = false (default no need to set option)
        assert java.get("name").equals("Joe");
    }

    @Test
    public void testUnknownClassTypeFailsWhenFailOptionTrue()
    {
        String json = "{\"@type\":\"foo.bar.baz.Qux\", \"name\":\"Joe\"}";
        assertThrows(JsonIoException.class, () -> { TestUtil.toJava(json, new ReadOptionsBuilder().failOnUnknownType().build()); });
    }
}
