package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class JsonObjectTest
{
    @Test
    void testGetId()
    {
        JsonObject jObj = new JsonObject();
        assert -1L == jObj.getId();
    }

/*    @Test
    void testGetPrimitiveValue()
    {
        JsonObject jObj = new JsonObject();
        jObj.setJavaType(long.class);
        jObj.setValue(10L);
        assertEquals(jObj.getPrimitiveValue(com.cedarsoftware.io.Converter.instance), 10L);
        
        try
        {
            jObj.getLength();
        }
        catch (JsonIoException e)
        {
            assert e.getMessage().toLowerCase().contains("called");
            assert e.getMessage().toLowerCase().contains("non-collection");
        }
    }
 */

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @ValueSource(classes = {HashMap.class, LinkedHashMap.class, JsonObject.class})
    void testRefsInArray_generatesIdAndRef(Class<? extends Map> c) throws Exception {
        Map jsonObj1 = generateBatman_withCircularReference_usingObjectArray(c);

        String json = TestUtil.toJson(jsonObj1, new WriteOptionsBuilder().showTypeInfoNever().build());

        assertThat(json)
                .containsOnlyOnce("@id")
                .containsOnlyOnce("@ref");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @ValueSource(classes = {HashMap.class, LinkedHashMap.class, JsonObject.class})
    void testRefsInArray_reconstitutesCorrectlyAsMaps(Class<? extends Map> c) throws Exception {
        Map jsonObj1 = generateBatman_withCircularReference_usingObjectArray(c);

        Map batman = (Map) TestUtil.serializeDeserializeAsMaps(jsonObj1);

        assertThat(batman)
                .containsEntry("name", "Batman")
                .containsKey("partners");

        Object[] batmansPartners = (Object[]) batman.get("partners");
        Map robin = (Map) batmansPartners[0];

        assertThat(robin)
                .containsEntry("name", "Robin")
                .containsKey("partners");

        Object[] robinsPartners = (Object[]) robin.get("partners");
        assertThat(robinsPartners[0]).isSameAs(batman);
        assertThat(batmansPartners[0]).isSameAs(robin);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @ValueSource(classes = {LinkedHashMap.class, JsonObject.class})
    void testRefsInArray_createsJsonWithoutTypes_inOrderOfInsertion(Class<? extends Map> c) throws Exception {
        Map jsonObj1 = generateBatman_withCircularReference_usingObjectArray(c);

        String json = TestUtil.toJson(jsonObj1, new WriteOptionsBuilder().showTypeInfoNever().build());

        assertThat(json)
                .isEqualToIgnoringWhitespace("{\"@id\":1,\"name\":\"Batman\",\"partners\":[{\"name\":\"Robin\",\"partners\":[{\"@ref\":1}]}]}");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map generateBatman_withCircularReference_usingObjectArray(Class<? extends Map> c) throws Exception {
        Map map1 = c.getConstructor().newInstance();
        Map map2 = c.getConstructor().newInstance();

        map1.put("name", "Batman");
        map1.put("partners", new Object[]{map2});
        map2.put("name", "Robin");
        map2.put("partners", new Object[]{map1});
        return map1;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map generateBatman_withCircularReference_usingCollections(Class<? extends Map> mapClass, Class<? extends Collection> collectionClass) throws Exception {
        Map jsonObj1 = mapClass.getConstructor().newInstance();
        Map jsonObj2 = mapClass.getConstructor().newInstance();

        Collection c1 = collectionClass.getConstructor().newInstance();
        c1.add(jsonObj2);

        Collection c2 = collectionClass.getConstructor().newInstance();
        c2.add(jsonObj1);

        jsonObj1.put("name", "Batman");
        jsonObj1.put("partners", c1);
        jsonObj2.put("name", "Robin");
        jsonObj2.put("partners", c2);
        return jsonObj1;
    }

    private static Stream<Arguments> mapWithCollectionClasses() {
        return Stream.of(
                Arguments.of(HashMap.class, LinkedList.class),
                Arguments.of(LinkedHashMap.class, LinkedList.class),
                Arguments.of(JsonObject.class, LinkedList.class),
                Arguments.of(HashMap.class, ArrayList.class),
                Arguments.of(LinkedHashMap.class, ArrayList.class),
                Arguments.of(JsonObject.class, ArrayList.class),
                Arguments.of(HashMap.class, HashSet.class),
                Arguments.of(LinkedHashMap.class, HashSet.class),
                Arguments.of(JsonObject.class, HashSet.class)
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @MethodSource("mapWithCollectionClasses")
    void testRefsInCollection_reconstitutesCorrectlyAsMaps(Class<? extends Map> mapClass, Class<? extends Collection> collectionclass) throws Exception {
        Map jsonObj1 = generateBatman_withCircularReference_usingCollections(mapClass, collectionclass);

        Map batman = (Map) TestUtil.serializeDeserializeAsMaps(jsonObj1);

        assertThat(batman)
                .containsEntry("name", "Batman")
                .containsKey("partners");

        Object[] batmansPartners = (Object[]) batman.get("partners");
        Map robin = (Map) batmansPartners[0];

        assertThat(robin)
                .containsEntry("name", "Robin")
                .containsKey("partners");

        Object[] robinsPartners = (Object[]) robin.get("partners");
        assertThat(robinsPartners[0]).isSameAs(batman);
        assertThat(batmansPartners[0]).isSameAs(robin);

        assertThat(batman).isInstanceOf(JsonObject.class);
        assertThat(robin).isInstanceOf(JsonObject.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @MethodSource("mapWithCollectionClasses")
    void testRefsInCollection_generatesIdAndRef(Class<? extends Map> mapClass, Class<? extends Collection> collectionclass) throws Exception {
        Map jsonObj1 = generateBatman_withCircularReference_usingCollections(mapClass, collectionclass);

        String json = TestUtil.toJson(jsonObj1, new WriteOptionsBuilder().showTypeInfoNever().build());

        assertThat(json)
                .containsOnlyOnce("@id")
                .containsOnlyOnce("@ref");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map generateBatman_withCircularReference_usingMaps(Class<? extends Map> c) throws Exception {
        Map map1 = c.getConstructor().newInstance();
        Map map2 = c.getConstructor().newInstance();

        map1.put("partner", map2);
        map2.put("partner", map1);

        map1.put("name", "Batman");
        map2.put("name", "Robin");
        return map1;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @ValueSource(classes = {HashMap.class, LinkedHashMap.class, JsonObject.class})
    void testRefsInMaps_generatesIdAndRef(Class<? extends Map> c) throws Exception {
        Map jsonObj1 = generateBatman_withCircularReference_usingMaps(c);

        String json = TestUtil.toJson(jsonObj1, new WriteOptionsBuilder().showTypeInfoNever().build());

        assertThat(json)
                .containsOnlyOnce("@id")
                .containsOnlyOnce("@ref");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @ValueSource(classes = {HashMap.class, LinkedHashMap.class, JsonObject.class})
    void testRefsInMaps_reconstitutesCorrectlyAsMaps(Class<? extends Map> c) throws Exception {
        Map jsonObj1 = generateBatman_withCircularReference_usingMaps(c);

        Map batman = (Map) TestUtil.serializeDeserializeAsMaps(jsonObj1);

        assertThat(batman)
                .containsEntry("name", "Batman")
                .containsKey("partner");

        Map robin = (Map) batman.get("partner");
        assertThat(robin)
                .containsEntry("name", "Robin")
                .containsKey("partner");

        assertThat(robin.get("partner")).isSameAs(batman);
        assertThat(batman.get("partner")).isSameAs(robin);
    }
    
    @Test
    public void testAsArray()
    {
        JsonObject jObj = new JsonObject();
        assert !jObj.isArray();
        assert !jObj.isCollection();
        assert !jObj.isMap();
        jObj.setItems(new Object[] {"hello", "goodbye"});
        assert jObj.isArray();
        assert !jObj.isCollection();
        assert !jObj.isMap();
        JsonObject jObj2 = new JsonObject();
        jObj2.setItems(new Object[] {"hello", "goodbye"});
        assert DeepEquals.deepEquals(jObj, jObj2);
    }
}
