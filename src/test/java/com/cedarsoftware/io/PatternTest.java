package com.cedarsoftware.io;

import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PatternTest {

    @Test
    void testPatternStandalone() {
        Pattern expected = Pattern.compile("ab.*c");
        String json = TestUtil.toJson(expected);
        Pattern actual = TestUtil.toJava(json, null).asClass(null);
        assertEquals(expected.pattern(), actual.pattern());
        assertEquals(expected.flags(), actual.flags());
    }

    @Test
    void testPatternArray() {
        Pattern pattern = Pattern.compile("[A-Z]+\\d?");
        Pattern[] array = new Pattern[]{ pattern };
        String json = TestUtil.toJson(array);
        Pattern[] actual = TestUtil.toJava(json, null).asClass(null);
        assertEquals(1, actual.length);
        assertEquals(pattern.pattern(), actual[0].pattern());
        assertEquals(pattern.flags(), actual[0].flags());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPatternAsMapValue() {
        Pattern pattern = Pattern.compile("foo.*");
        Map<String, Pattern> map = mapOf("p", pattern);
        String json = TestUtil.toJson(map);
        Map<String, Pattern> actual = TestUtil.toJava(json, null).asClass(null);
        assertThat(actual).hasSize(1);
        assertEquals(pattern.pattern(), actual.get("p").pattern());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPatternAsMapKey() {
        Pattern pattern = Pattern.compile("foo\\d+");
        Map<Pattern, String> map = mapOf(pattern, "bar");
        String json = TestUtil.toJson(map);
        Map<Pattern, String> actual = TestUtil.toJava(json, null).asClass(null);
        assertThat(actual).hasSize(1);
        Pattern key = actual.keySet().iterator().next();
        assertEquals(pattern.pattern(), key.pattern());
    }

    @Test
    void testPatternReference() {
        Pattern pattern = Pattern.compile("h.*");
        Object[] array = new Object[]{ pattern, pattern };
        String json = TestUtil.toJson(array);
        Object[] actual = TestUtil.toJava(json, null).asClass(null);
        assertSame(actual[0], actual[1]);
        Pattern result = (Pattern) actual[0];
        assertEquals(pattern.pattern(), result.pattern());
    }
}
