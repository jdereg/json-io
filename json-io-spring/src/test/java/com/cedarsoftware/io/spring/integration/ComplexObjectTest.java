package com.cedarsoftware.io.spring.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.spring.JsonIoMediaTypes;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoAutoConfiguration;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoWebMvcAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for complex object serialization/deserialization including
 * nested objects, collections, cyclic references, polymorphic types, and null handling.
 */
@WebMvcTest(ComplexObjectTest.TestController.class)
@ImportAutoConfiguration({JsonIoAutoConfiguration.class, JsonIoWebMvcAutoConfiguration.class})
@TestPropertySource(properties = "spring.json-io.write.show-type-info=NEVER")
class ComplexObjectTest {

    @Autowired
    private MockMvc mockMvc;

    // ============= Nested Objects =============

    @Test
    void serializeNestedObject() throws Exception {
        mockMvc.perform(get("/complex/nested")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Parent"))
                .andExpect(jsonPath("$.child.name").value("Child"))
                .andExpect(jsonPath("$.child.age").value(10));
    }

    @Test
    void deserializeNestedObject() throws Exception {
        String json = "{\"name\":\"Parent\",\"child\":{\"name\":\"Child\",\"age\":5}}";

        mockMvc.perform(post("/complex/nested")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Parent"))
                .andExpect(jsonPath("$.child.name").value("Child"))
                .andExpect(jsonPath("$.child.age").value(5));
    }

    // ============= Collections =============

    @Test
    void serializeListOfObjects() throws Exception {
        mockMvc.perform(get("/complex/list")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("First"))
                .andExpect(jsonPath("$[1].name").value("Second"))
                .andExpect(jsonPath("$[2].name").value("Third"));
    }

    @Test
    void deserializeListOfObjects() throws Exception {
        String json = "[{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}]";

        mockMvc.perform(post("/complex/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[1].name").value("B"));
    }

    @Test
    void serializeMapOfObjects() throws Exception {
        mockMvc.perform(get("/complex/map")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key1.name").value("Value1"))
                .andExpect(jsonPath("$.key2.name").value("Value2"));
    }

    @Test
    void deserializeMapOfObjects() throws Exception {
        String json = "{\"k1\":{\"name\":\"V1\",\"age\":10},\"k2\":{\"name\":\"V2\",\"age\":20}}";

        mockMvc.perform(post("/complex/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.k1.name").value("V1"))
                .andExpect(jsonPath("$.k2.name").value("V2"));
    }

    @Test
    void serializeSetOfStrings() throws Exception {
        mockMvc.perform(get("/complex/set")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ============= Null Handling =============

    @Test
    void serializeObjectWithNullFields() throws Exception {
        mockMvc.perform(get("/complex/null-fields")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("HasNulls"));
    }

    @Test
    void deserializeObjectWithNullFields() throws Exception {
        String json = "{\"name\":\"NullTest\",\"child\":null}";

        mockMvc.perform(post("/complex/nested")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NullTest"));
    }

    // ============= Deep Nesting =============

    @Test
    void serializeDeeplyNestedObject() throws Exception {
        mockMvc.perform(get("/complex/deep")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nested.nested.nested.name").value("Level3"));
    }

    // ============= JSON5 with Complex Objects =============

    @Test
    void deserializeJson5ComplexObject() throws Exception {
        // JSON5 with trailing commas and comments
        String json5 = "{\n" +
                "  \"name\": \"Json5Test\",\n" +
                "  // This is a comment\n" +
                "  \"child\": {\n" +
                "    \"name\": \"Child\",\n" +
                "    \"age\": 15,\n" +
                "  },\n" +
                "}";

        mockMvc.perform(post("/complex/nested")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Json5Test"))
                .andExpect(jsonPath("$.child.name").value("Child"))
                .andExpect(jsonPath("$.child.age").value(15));
    }

    // ============= TOON with Complex Objects =============

    @Test
    void serializeComplexObjectToToon() throws Exception {
        mockMvc.perform(get("/complex/nested")
                        .accept(JsonIoMediaTypes.APPLICATION_TOON))
                .andExpect(status().isOk());
    }

    @Test
    void deserializeToonComplexObject() throws Exception {
        String toon = "name: ToonParent\n" +
                "child:\n" +
                "  name: ToonChild\n" +
                "  age: 20";

        mockMvc.perform(post("/complex/nested")
                        .contentType(JsonIoMediaTypes.APPLICATION_TOON)
                        .content(toon)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ToonParent"))
                .andExpect(jsonPath("$.child.name").value("ToonChild"))
                .andExpect(jsonPath("$.child.age").value(20));
    }

    // ============= Collections in JSON5 =============

    @Test
    void deserializeJson5List() throws Exception {
        String json5 = "[\n" +
                "  {\"name\": \"Item1\", \"age\": 1,},\n" +
                "  {\"name\": \"Item2\", \"age\": 2,},\n" +
                "]";

        mockMvc.perform(post("/complex/list")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Item1"))
                .andExpect(jsonPath("$[1].name").value("Item2"));
    }

    // ============= Empty Collections =============

    @Test
    void serializeEmptyList() throws Exception {
        mockMvc.perform(get("/complex/empty-list")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void serializeEmptyMap() throws Exception {
        mockMvc.perform(get("/complex/empty-map")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Configuration
    static class TestConfig {
        @Bean
        public TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        // Nested objects
        @GetMapping("/complex/nested")
        public Parent getNested() {
            Parent parent = new Parent();
            parent.name = "Parent";
            parent.child = new Child();
            parent.child.name = "Child";
            parent.child.age = 10;
            return parent;
        }

        @PostMapping("/complex/nested")
        public Parent postNested(@RequestBody Parent parent) {
            return parent;
        }

        // List
        @GetMapping("/complex/list")
        public List<Child> getList() {
            List<Child> list = new ArrayList<>();
            list.add(createChild("First", 1));
            list.add(createChild("Second", 2));
            list.add(createChild("Third", 3));
            return list;
        }

        @PostMapping("/complex/list")
        public List<Child> postList(@RequestBody List<Child> list) {
            return list;
        }

        // Map
        @GetMapping("/complex/map")
        public Map<String, Child> getMap() {
            Map<String, Child> map = new HashMap<>();
            map.put("key1", createChild("Value1", 1));
            map.put("key2", createChild("Value2", 2));
            return map;
        }

        @PostMapping("/complex/map")
        public Map<String, Child> postMap(@RequestBody Map<String, Child> map) {
            return map;
        }

        // Set
        @GetMapping("/complex/set")
        public Set<String> getSet() {
            Set<String> set = new HashSet<>();
            set.add("alpha");
            set.add("beta");
            set.add("gamma");
            return set;
        }

        // Null fields
        @GetMapping("/complex/null-fields")
        public Parent getNullFields() {
            Parent parent = new Parent();
            parent.name = "HasNulls";
            parent.child = null;
            return parent;
        }

        // Deep nesting
        @GetMapping("/complex/deep")
        public Parent getDeep() {
            Parent level0 = new Parent();
            level0.name = "Level0";

            Parent level1 = new Parent();
            level1.name = "Level1";
            level0.nested = level1;

            Parent level2 = new Parent();
            level2.name = "Level2";
            level1.nested = level2;

            Parent level3 = new Parent();
            level3.name = "Level3";
            level2.nested = level3;

            return level0;
        }

        // Empty collections
        @GetMapping("/complex/empty-list")
        public List<Child> getEmptyList() {
            return new ArrayList<>();
        }

        @GetMapping("/complex/empty-map")
        public Map<String, Child> getEmptyMap() {
            return new HashMap<>();
        }

        private Child createChild(String name, int age) {
            Child child = new Child();
            child.name = name;
            child.age = age;
            return child;
        }
    }

    static class Parent {
        public String name;
        public Child child;
        public Parent nested;
    }

    static class Child {
        public String name;
        public int age;
    }
}
