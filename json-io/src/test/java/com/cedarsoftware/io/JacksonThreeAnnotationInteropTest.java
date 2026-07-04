package com.cedarsoftware.io;

import java.util.LinkedList;
import java.util.List;

import com.cedarsoftware.io.annotation.IoNaming;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonNaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies json-io honors Jackson <b>3.x</b> databind annotations ({@code @JsonNaming},
 * {@code @JsonDeserialize}), which moved from the {@code com.fasterxml.jackson.databind.annotation}
 * package (Jackson 2.x) to {@code tools.jackson.databind.annotation} (Jackson 3.x).
 * <p>
 * The {@code tools.jackson.databind.*} types referenced here are test-only stand-ins declared at the
 * real Jackson-3 coordinates (see those classes' Javadoc). Because {@code AnnotationResolver} detects
 * these annotations by fully-qualified name and maps naming strategies by {@code getSimpleName()},
 * the stand-ins drive the exact production path a real Jackson 3 annotation would — without pulling
 * Jackson 3 (and its Java 17 floor) onto the test classpath. Jackson 2.x interop is covered separately
 * by {@link GlobalNamingStrategyTest} and {@code AnnotationTest}.
 */
class JacksonThreeAnnotationInteropTest {

    // ---- @JsonNaming (Jackson 3.x) on a class ----

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class SnakeProfile {
        String firstName;
        String lastName;
        int loginCount;

        SnakeProfile() {}
        SnakeProfile(String first, String last, int count) {
            this.firstName = first;
            this.lastName = last;
            this.loginCount = count;
        }
    }

    @JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
    static class KebabProfile {
        String firstName;
        String lastName;

        KebabProfile() {}
        KebabProfile(String first, String last) {
            this.firstName = first;
            this.lastName = last;
        }
    }

    // @IoNaming (native) must still win over a Jackson 3.x @JsonNaming on the same class
    @IoNaming(IoNaming.Strategy.KEBAB_CASE)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class ConflictProfile {
        String firstName;
        String lastName;

        ConflictProfile() {}
        ConflictProfile(String first, String last) {
            this.firstName = first;
            this.lastName = last;
        }
    }

    // ---- @JsonDeserialize(as=...) (Jackson 3.x) on a field ----

    static class DeserializeV3Field {
        @JsonDeserialize(as = LinkedList.class)
        private List<String> items;
        private int count;

        DeserializeV3Field() {}

        public List<String> getItems() { return items; }
        public int getCount() { return count; }
    }

    // ---- Tests ----

    @Test
    void v3JsonNamingProducesSnakeCase() {
        String json = JsonIo.toJson(new SnakeProfile("Alice", "Smith", 3),
                new WriteOptionsBuilder().showTypeInfoNever().build());
        assertTrue(json.contains("first_name"), json);
        assertTrue(json.contains("last_name"), json);
        assertTrue(json.contains("login_count"), json);
        assertFalse(json.contains("firstName"), json);
        assertFalse(json.contains("loginCount"), json);
    }

    @Test
    void v3JsonNamingProducesKebabCase() {
        String json = JsonIo.toJson(new KebabProfile("Alice", "Smith"),
                new WriteOptionsBuilder().showTypeInfoNever().build());
        assertTrue(json.contains("first-name"), json);
        assertTrue(json.contains("last-name"), json);
        assertFalse(json.contains("firstName"), json);
    }

    @Test
    void nativeIoNamingWinsOverV3JsonNaming() {
        String json = JsonIo.toJson(new ConflictProfile("Alice", "Smith"),
                new WriteOptionsBuilder().showTypeInfoNever().build());
        // @IoNaming(KEBAB_CASE) wins over the Jackson 3.x @JsonNaming(SnakeCaseStrategy)
        assertTrue(json.contains("first-name"), json);
        assertTrue(json.contains("last-name"), json);
        assertFalse(json.contains("first_name"), json);
    }

    @Test
    void v3JsonDeserializeAsHonoredOnRead() {
        String json = "{\"@type\":\"" + DeserializeV3Field.class.getName()
                + "\",\"items\":[\"a\",\"b\",\"c\"],\"count\":3}";
        DeserializeV3Field result = JsonIo.toJava(json, new ReadOptionsBuilder().build())
                .asClass(DeserializeV3Field.class);
        assertNotNull(result.getItems());
        assertTrue(result.getItems() instanceof LinkedList,
                "items should be LinkedList but was: " + result.getItems().getClass().getName());
        assertEquals(3, result.getItems().size());
        assertEquals("a", result.getItems().get(0));
    }
}
