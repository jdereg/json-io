package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A WriteOptionsBuilder changes only its own settings. Its per-class settings start as shallow copies -- of the
 * permanent ones, or of the options it was copied from -- so the field-name sets and getter maps inside were theirs,
 * and addExcludedFields(), addIncludedField(s)() and addNonStandardGetter() added to them. Excluding a field of
 * Throwable on one builder dropped it from every WriteOptions built after, the default ones included, since the
 * permanent settings exclude fields of Throwable too. (addExcludedField(), singular, already copied.)
 */
class WriteOptionsBuilderOwnSettingsTest {

    private static boolean writesMessage(WriteOptions options) {
        return JsonIo.toJson(new IllegalStateException("boom"), options).contains("\"boom\"");
    }

    @Test
    void excludingFieldsOfAClassThePermanentSettingsCoverChangesNoOtherOptions() {
        WriteOptions excluding = new WriteOptionsBuilder()
                .addExcludedFields(Throwable.class, Collections.singletonList("detailMessage"))
                .build();
        assertThat(writesMessage(excluding)).isFalse();

        assertThat(writesMessage(new WriteOptionsBuilder().build())).as("a new WriteOptions").isTrue();
        assertThat(writesMessage(null)).as("the default WriteOptions").isTrue();
        assertThat(new WriteOptionsBuilder().build().getExcludedFields(Throwable.class)).doesNotContain("detailMessage");
    }

    @Test
    void aNonStandardGetterForAClassThePermanentSettingsCoverChangesNoOtherOptions() {
        WriteOptions viaToString = new WriteOptionsBuilder()
                .addNonStandardGetter(Throwable.class, "detailMessage", "toString")
                .build();
        assertThat(JsonIo.toJson(new IllegalStateException("boom"), viaToString))
                .contains("java.lang.IllegalStateException: boom");

        assertThat(JsonIo.toJson(new IllegalStateException("boom"), new WriteOptionsBuilder().build()))
                .as("a new WriteOptions reads the message with getMessage()")
                .doesNotContain("java.lang.IllegalStateException: boom");
    }

    public static class Pair {
        public String a;
        public String b;
        public String c;
    }

    @Test
    void addingToACopysFieldsChangesNotTheOriginal() {
        WriteOptions original = new WriteOptionsBuilder().addIncludedField(Pair.class, "a").build();
        WriteOptions copy = new WriteOptionsBuilder(original)
                .addIncludedField(Pair.class, "b")
                .addIncludedFields(Pair.class, Collections.singletonList("c"))
                .addExcludedFields(Pair.class, Arrays.asList("x", "y"))
                .build();

        assertThat(copy.getIncludedFields(Pair.class)).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(original.getIncludedFields(Pair.class)).containsExactly("a");
        assertThat(original.getExcludedFields(Pair.class)).isEmpty();      // none, as before the copy excluded some
        assertThat(copy.getExcludedFields(Pair.class)).containsExactlyInAnyOrder("x", "y");
        // A class with none: an empty set (these threw NullPointerException)
        assertThat(new WriteOptionsBuilder().build().getIncludedFields(Pair.class)).isEmpty();
        assertThat(new WriteOptionsBuilder().build().getExcludedFields(Pair.class)).isEmpty();
    }
}
