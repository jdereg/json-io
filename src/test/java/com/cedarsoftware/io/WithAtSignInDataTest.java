package com.cedarsoftware.io;

import java.util.Map;

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
public class WithAtSignInDataTest
{
    @Test
    public void testFormatThatUsesAtSign()
    {
        String json = "{\"PrincipalName\":{\"@type\":\"fir:IndividualNameType\",\"NamePrefix\":{\"NamePrefixText\":\"Ms\"},\"FirstName\":\"Marge\",\"LastName\":\"Simpson\",\"FullName\":\"Marge Simpson\"},\"JobTitle\":[{\"JobTitleText\":{\"$\":\"President\"}}],\"CurrentManagementResponsibility\":[{\"ManagementResponsibilityText\":{\"@ManagementResponsibilityCode\":\"A1A6\",\"$\":\"President\"}}],\"PrincipalIdentificationNumberDetail\":[{\"@DNBCodeValue\":24226,\"@TypeText\":\"Professional Contact Identifier\",\"PrincipalIdentificationNumber\":\"178125299\"}]}";
        Map<String, Object> map = TestUtil.toObjects(json, new ReadOptionsBuilder().failOnUnknownType(false).build(), null);
        assertTheHeckOutOfThisStructure(map);
        map = TestUtil.toObjects(json, new ReadOptionsBuilder()
                .returnAsJsonObjects()
                .failOnUnknownType(false)
                .build(), null);
        assertTheHeckOutOfThisStructure(map);
    }

    public void assertTheHeckOutOfThisStructure(Map map)
    {
        Object[] responsibility = (Object[]) map.get("CurrentManagementResponsibility");
        Map mgrDetails = (Map) responsibility[0];
        Map details = (Map) mgrDetails.get("ManagementResponsibilityText");
        assert details.get("$").equals("President");
        assert details.get("@ManagementResponsibilityCode").equals("A1A6");
        Map principalName = (Map) map.get("PrincipalName");
        Map namePrefix = (Map) principalName.get("NamePrefix");
        assert "Ms".equals(namePrefix.get("NamePrefixText"));
        assert "Marge".equals(principalName.get("FirstName"));
        assert "Simpson".equals(principalName.get("LastName"));

        Object[] principalDetail = (Object[]) map.get("PrincipalIdentificationNumberDetail");
        Map detail = (Map)principalDetail[0];
        assert detail.get("@DNBCodeValue").equals(24226L);
    }
}
