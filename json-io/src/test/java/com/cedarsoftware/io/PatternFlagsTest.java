package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Pattern is written with its flags inline, "(?i)abc", and reads back matching what it matched. The flags were
 * dropped: a CASE_INSENSITIVE Pattern was written "abc" and read back case-sensitive. Whitespace in the regex is kept;
 * " x " was read back as "x".
 */
class PatternFlagsTest {

    public static class Holder {
        public Pattern pattern;
        public Pattern[] patterns;
        public Map<String, Pattern> byName;
        public Map<Pattern, String> byPattern;
    }

    private static final Pattern CASELESS = Pattern.compile("abc", Pattern.CASE_INSENSITIVE);

    private static Holder holder() {
        Holder holder = new Holder();
        holder.pattern = CASELESS;
        holder.patterns = new Pattern[] {Pattern.compile("^b$", Pattern.MULTILINE)};
        holder.byName = new LinkedHashMap<>();
        holder.byName.put("dotall", Pattern.compile("a.b", Pattern.DOTALL));
        holder.byPattern = new LinkedHashMap<>();
        holder.byPattern.put(CASELESS, "key");
        return holder;
    }

    private static void assertMatchesAsBefore(Holder back) {
        assertTrue(back.pattern.matcher("ABC").matches(), back.pattern.toString());
        assertTrue(back.patterns[0].matcher("a\nb\nc").find(), back.patterns[0].toString());
        assertTrue(back.byName.get("dotall").matcher("a\nb").matches(), back.byName.toString());
        Pattern key = back.byPattern.keySet().iterator().next();
        assertTrue(key.matcher("ABC").matches(), key.toString());
    }

    @Test
    void flagsAreWrittenInlineAndReadBack() {
        String json = JsonIo.toJson(holder(), null);
        assertTrue(json.contains("\"(?i)abc\""), json);
        assertTrue(json.contains("\"(?m)^b$\""), json);
        assertMatchesAsBefore(JsonIo.toJava(json, null).asClass(Holder.class));

        WriteOptions never = new WriteOptionsBuilder().showTypeInfoNever().build();
        assertMatchesAsBefore(JsonIo.toJava(JsonIo.toJson(holder(), never), null).asClass(Holder.class));
    }

    @Test
    void aPatternAtTheRootKeepsItsFlags() {
        Pattern back = JsonIo.toJava(JsonIo.toJson(CASELESS, null), null).asClass(Pattern.class);
        assertEquals(Pattern.CASE_INSENSITIVE, back.flags());
        assertTrue(back.matcher("ABC").matches());

        Pattern literal = JsonIo.toJava(JsonIo.toJson(Pattern.compile("a.b", Pattern.LITERAL), null), null)
                .asClass(Pattern.class);
        assertTrue(literal.matcher("a.b").matches());
        assertFalse(literal.matcher("axb").matches());
    }

    @Test
    void toonKeepsTheFlags() {
        Pattern back = JsonIo.fromToon(JsonIo.toToon(CASELESS, null), null).asClass(Pattern.class);
        assertTrue(back.matcher("ABC").matches(), back.toString());
    }

    /** A Pattern without flags is written as before, and JSON written before reads as before. */
    @Test
    void aPatternWithoutFlagsIsUnchanged() {
        Holder holder = new Holder();
        holder.pattern = Pattern.compile("\\d+");
        assertTrue(JsonIo.toJson(holder, null).contains("\"\\\\d+\""));
        Holder back = JsonIo.toJava("{\"pattern\":\"abc\"}", null).asClass(Holder.class);
        assertEquals(0, back.pattern.flags());
        assertFalse(back.pattern.matcher("ABC").matches());
    }

    @Test
    void whitespaceIsPartOfTheRegex() {
        Holder holder = new Holder();
        holder.pattern = Pattern.compile(" x ");
        Holder back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
        assertEquals(" x ", back.pattern.pattern());
    }
}
