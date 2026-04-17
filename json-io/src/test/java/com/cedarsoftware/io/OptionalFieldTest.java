package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for defect reported by Jim Ronan (2026-04-16):
 * <pre>
 *   {@code Without adding writeOptions "showTypeInfoAlways" you get illegal JSON output}
 *   Input:  Junk{optional1=Optional.empty, optional2=Optional[hello]}
 *   Output: {"@t":"...Junk","optional1":{null},"optional2":{"hello"}}
 *   Round-trip fails: "Expected ':' between field and value, instead found '}'"
 * </pre>
 *
 * Root cause: {@code OptionalWriter.write()} produced invalid JSON body content
 * (bare {@code null} or bare value) when {@code showType=false}. The framework
 * always wraps custom-writer output in {@code { ... }}, so the body must be
 * valid key:value pairs.
 *
 * Fix: always emit {@code "present":true/false} (and {@code ,"value":X} when
 * present), regardless of {@code showType}. The framework separately emits
 * the {@code @type} marker when needed.
 */
class OptionalFieldTest {

    public static class OptHolder {
        public Optional<String> optional1;
        public Optional<String> optional2;

        public OptHolder() {}

        public OptHolder(Optional<String> optional1, Optional<String> optional2) {
            this.optional1 = optional1;
            this.optional2 = optional2;
        }
    }

    /** Exact reproduction of Jim Ronan's failing scenario. */
    @Test
    void jimRonanRepro_withShortMetaKeys() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .writeEnumAsJsonObject(true)
                .shortMetaKeys(true)
                .showTypeInfoAlways()  // his original workaround
                .build();
        ReadOptions readOptions = new ReadOptionsBuilder()
                .failOnUnknownType(false)
                .unknownTypeClass(LinkedHashMap.class).build();

        OptHolder j = new OptHolder(Optional.empty(), Optional.of("hello"));
        String json = JsonIo.toJson(j, writeOptions);
        OptHolder j2 = JsonIo.toJava(json, readOptions).asClass(OptHolder.class);

        assertThat(j2.optional1).isEmpty();
        assertThat(j2.optional2).isPresent().contains("hello");
    }

    /**
     * The defect path: without {@code showTypeInfoAlways}, the writer produced
     * illegal JSON body content {@code {null}} and {@code {"hello"}}.
     */
    @Test
    void withoutShowTypeInfoAlways_producesValidJson() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .shortMetaKeys(true)
                .build();
        ReadOptions readOptions = new ReadOptionsBuilder()
                .failOnUnknownType(false)
                .unknownTypeClass(LinkedHashMap.class).build();

        OptHolder j = new OptHolder(Optional.empty(), Optional.of("hello"));
        String json = JsonIo.toJson(j, writeOptions);

        // The raw output must be valid JSON — no bare `{null}` or `{"hello"}`
        assertThat(json)
                .doesNotContain("{null}")
                .doesNotContain("{\"hello\"}");

        // And the JSON must round-trip correctly
        OptHolder j2 = JsonIo.toJava(json, readOptions).asClass(OptHolder.class);
        assertThat(j2.optional1).isEmpty();
        assertThat(j2.optional2).isPresent().contains("hello");
    }

    @Test
    void withShowTypeInfoNever_producesValidJson() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        OptHolder j = new OptHolder(Optional.empty(), Optional.of("world"));
        String json = JsonIo.toJson(j, writeOptions);

        // Must be parseable JSON
        assertThat(json).doesNotContain("{null}");
        assertThat(json).doesNotContain("{\"world\"}");

        // Round-trip
        OptHolder restored = JsonIo.toJava(json, new ReadOptionsBuilder().build())
                .asClass(OptHolder.class);
        assertThat(restored.optional1).isEmpty();
        assertThat(restored.optional2).isPresent().contains("world");
    }

    @Test
    void withShowTypeInfoMinimal_roundTrips() {
        // Default behavior (MINIMAL) should also work correctly
        OptHolder j = new OptHolder(Optional.empty(), Optional.of("data"));
        String json = JsonIo.toJson(j);

        assertThat(json).doesNotContain("{null}");
        OptHolder restored = JsonIo.toJava(json).asClass(OptHolder.class);
        assertThat(restored.optional1).isEmpty();
        assertThat(restored.optional2).isPresent().contains("data");
    }

    @Test
    void bothOptionalsEmpty() {
        OptHolder j = new OptHolder(Optional.empty(), Optional.empty());
        String json = JsonIo.toJson(j);

        assertThat(json).doesNotContain("{null}");
        OptHolder restored = JsonIo.toJava(json).asClass(OptHolder.class);
        assertThat(restored.optional1).isEmpty();
        assertThat(restored.optional2).isEmpty();
    }

    @Test
    void bothOptionalsPresent() {
        OptHolder j = new OptHolder(Optional.of("alpha"), Optional.of("beta"));
        String json = JsonIo.toJson(j);

        assertThat(json).doesNotContain("{\"alpha\"}");
        assertThat(json).doesNotContain("{\"beta\"}");
        OptHolder restored = JsonIo.toJava(json).asClass(OptHolder.class);
        assertThat(restored.optional1).isPresent().contains("alpha");
        assertThat(restored.optional2).isPresent().contains("beta");
    }

    @Test
    void topLevelOptionalEmpty() {
        Optional<String> opt = Optional.empty();
        String json = JsonIo.toJson(opt);
        // Must be valid JSON
        @SuppressWarnings("unchecked")
        Optional<String> restored = JsonIo.toJava(json).asClass(Optional.class);
        assertThat(restored).isEmpty();
    }

    @Test
    void topLevelOptionalPresent() {
        Optional<String> opt = Optional.of("value");
        String json = JsonIo.toJson(opt);
        @SuppressWarnings("unchecked")
        Optional<String> restored = JsonIo.toJava(json).asClass(Optional.class);
        assertThat(restored).isPresent().contains("value");
    }

    @Test
    void optionalWithIntegerValue() {
        // Optional<Integer>
        class IntHolder {
            public Optional<Integer> opt;
            public IntHolder() {}
            public IntHolder(Optional<Integer> o) { this.opt = o; }
        }
        IntHolder h = new IntHolder(Optional.of(42));
        String json = JsonIo.toJson(h);
        assertThat(json).doesNotContain("{42}");
        // At minimum, validate the JSON is parseable by the reader
        assertThat(JsonIo.toMaps(json).asClass(java.util.Map.class)).isNotNull();
    }
}
