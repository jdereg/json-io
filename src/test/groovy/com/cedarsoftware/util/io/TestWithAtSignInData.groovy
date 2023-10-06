package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThatNoException;

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
class TestWithAtSignInData
{
    @Test
    void testFormatThatUsesAtSign()
    {
        String json = '{"PrincipalName":{"@type":"fir:IndividualNameType","NamePrefix":{"NamePrefixText":"Ms"},"FirstName":"Marge","LastName":"Simpson","FullName":"Marge Simpson"},"JobTitle":[{"JobTitleText":{"$":"President"}}],"CurrentManagementResponsibility":[{"ManagementResponsibilityText":{"@ManagementResponsibilityCode":"A1A6","$":"President"}}],"PrincipalIdentificationNumberDetail":[{"@DNBCodeValue":24226,"@TypeText":"Professional Contact Identifier","PrincipalIdentificationNumber":"178125299"}]}'

        Map map;
        assertThatNoException().isThrownBy ({ map = (Map)JsonReader.jsonToJava(json); })
        assert (map.CurrentManagementResponsibility instanceof Object[])
        assert map.PrincipalName.NamePrefix.NamePrefixText == 'Ms'
        assert map.PrincipalIdentificationNumberDetail[0]['@DNBCodeValue'] == 24226

        map = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assert (map.CurrentManagementResponsibility instanceof Object[])
        assert map.PrincipalName.NamePrefix.NamePrefixText == 'Ms'
        assert map.PrincipalIdentificationNumberDetail[0]['@DNBCodeValue'] == 24226
    }
}
