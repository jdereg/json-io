package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Options copied from options that write enums as objects write enums as objects. The copy left out the enum writer
 * (since 2024), so a copy wrote the bare name -- {@code "loc":"CELL"} -- where the original wrote {@code "loc":{"name":"CELL"}}.
 * n-cube hit it: its servlet copied its options to leave the stream open, and the editor reads {@code delta.loc.name}.
 */
class WriteOptionsCopyEnumTest {

    public enum Where { CELL, AXIS }

    public static class Delta {
        public Where loc = Where.CELL;
    }

    @Test
    void aCopyOfOptionsThatWriteEnumsAsObjectsWritesThemAsObjects() {
        WriteOptions original = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
        WriteOptions copy = new WriteOptionsBuilder(original).closeStream(false).build();

        assertThat(JsonIo.toJson(new Delta(), original)).contains("\"loc\":{\"name\":\"CELL\"}");
        assertThat(JsonIo.toJson(new Delta(), copy)).contains("\"loc\":{\"name\":\"CELL\"}");
    }

    @Test
    void aCopyOfOptionsThatWriteEnumsAsStringsWritesThemAsStrings() {
        WriteOptions original = new WriteOptionsBuilder().writeEnumsAsString().build();
        WriteOptions copy = new WriteOptionsBuilder(original).build();

        assertThat(JsonIo.toJson(new Delta(), copy)).contains("\"loc\":\"CELL\"");
    }

    @Test
    void aCopyCanChangeTheEnumFormWithoutChangingTheOriginal() {
        WriteOptions original = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
        WriteOptions asStrings = new WriteOptionsBuilder(original).writeEnumsAsString().build();

        assertThat(JsonIo.toJson(new Delta(), asStrings)).contains("\"loc\":\"CELL\"");
        assertThat(JsonIo.toJson(new Delta(), original)).contains("\"loc\":{\"name\":\"CELL\"}");
    }
}
