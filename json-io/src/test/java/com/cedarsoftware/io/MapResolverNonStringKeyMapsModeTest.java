package com.cedarsoftware.io;

import java.util.Map;

import com.cedarsoftware.io.models.UniversityFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapResolverNonStringKeyMapsModeTest {

    @Test
    void toMaps_withComplexMapKeys_doesNotThrow() {
        UniversityFixture.University university = UniversityFixture.createSampleUniversity();
        // Keep complex map keys enabled (PersonKey and Map keys)
        String json = JsonIo.toJson(university, new WriteOptionsBuilder().showTypeInfoNever().build());

        JsonObject root = assertDoesNotThrow(() -> JsonIo.toMaps(json, null).asClass(JsonObject.class));
        assertNotNull(root);

        Object advisorAssignments = root.get("advisorAssignments");
        assertNotNull(advisorAssignments);
        assertTrue(advisorAssignments instanceof JsonObject || advisorAssignments instanceof Map);

        Map<?, ?> advisorMap = assertInstanceOf(Map.class, advisorAssignments);
        assertFalse(advisorMap.isEmpty());
        Object firstAdvisorKey = advisorMap.keySet().iterator().next();
        Object firstAdvisorValue = advisorMap.values().iterator().next();
        assertTrue(firstAdvisorKey instanceof JsonObject || firstAdvisorKey instanceof Map);
        assertTrue(firstAdvisorValue instanceof JsonObject || firstAdvisorValue instanceof Map);

        Object oddMapKeys = root.get("oddMapKeys");
        assertNotNull(oddMapKeys);
        Map<?, ?> oddKeysMap = assertInstanceOf(Map.class, oddMapKeys);
        assertFalse(oddKeysMap.isEmpty());
        Object firstOddKey = oddKeysMap.keySet().iterator().next();
        assertTrue(firstOddKey instanceof JsonObject || firstOddKey instanceof Map);
        assertNotNull(oddKeysMap.values().iterator().next());
    }
}
