package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests special cases for EnumSets including:
 * - RegularEnumSet (<=64 constants) vs JumboEnumSet (>64 constants)
 * - Coercion cases
 * - Singleton enum sets
 * - All values sets
 * - Pretty printing variations
 */
class EnumSetSpecialCasesTest {
    // Enum with exactly 64 constants for RegularEnumSet testing
    private enum Enum64 {
        A00, A01, A02, A03, A04, A05, A06, A07, A08, A09,
        A10, A11, A12, A13, A14, A15, A16, A17, A18, A19,
        A20, A21, A22, A23, A24, A25, A26, A27, A28, A29,
        A30, A31, A32, A33, A34, A35, A36, A37, A38, A39,
        A40, A41, A42, A43, A44, A45, A46, A47, A48, A49,
        A50, A51, A52, A53, A54, A55, A56, A57, A58, A59,
        A60, A61, A62, A63
    }

    // Enum with 65 constants to force JumboEnumSet
    private enum Enum65 {
        B00, B01, B02, B03, B04, B05, B06, B07, B08, B09,
        B10, B11, B12, B13, B14, B15, B16, B17, B18, B19,
        B20, B21, B22, B23, B24, B25, B26, B27, B28, B29,
        B30, B31, B32, B33, B34, B35, B36, B37, B38, B39,
        B40, B41, B42, B43, B44, B45, B46, B47, B48, B49,
        B50, B51, B52, B53, B54, B55, B56, B57, B58, B59,
        B60, B61, B62, B63, B64
    }

    @Test
    void testEnumSet_regularEnumSetWithAllValues() {
        EnumSet<Enum64> source = EnumSet.allOf(Enum64.class);
        String json = TestUtil.toJson(source);
        EnumSet<?> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(64)
                .isInstanceOf(EnumSet.class);

        @SuppressWarnings("unchecked")
        EnumSet<Enum64> typed = (EnumSet<Enum64>) target;
        assertThat(typed).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Enum64.class));
    }

    @Test
    void testEnumSet_jumboEnumSetWithAllValues() {
        EnumSet<Enum65> source = EnumSet.allOf(Enum65.class);
        String json = TestUtil.toJson(source);
        EnumSet<?> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(65)
                .isInstanceOf(EnumSet.class);

        @SuppressWarnings("unchecked")
        EnumSet<Enum65> typed = (EnumSet<Enum65>) target;
        assertThat(typed).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Enum65.class));
    }

    @Test
    void testEnumSet_regularEnumSetCoercion() {
        // Create using RegularEnumSet
        EnumSet<Enum64> source = EnumSet.of(Enum64.A00, Enum64.A63);
        String json = TestUtil.toJson(source);

        // Verify coercion in JSON format
        assertThat(json).doesNotContain("RegularEnumSet");
        assertThat(json).contains(Enum64.class.getName());

        EnumSet<?> target = TestUtil.toObjects(json, null);

        @SuppressWarnings("unchecked")
        EnumSet<Enum64> typed = (EnumSet<Enum64>) target;
        assertThat(typed).containsExactly(Enum64.A00, Enum64.A63);
    }

    @Test
    void testEnumSet_jumboEnumSetCoercion() {
        // Create using JumboEnumSet
        EnumSet<Enum65> source = EnumSet.of(Enum65.B00, Enum65.B64);
        String json = TestUtil.toJson(source);

        // Verify coercion in JSON format
        assertThat(json).doesNotContain("JumboEnumSet");
        assertThat(json).contains(Enum65.class.getName());

        EnumSet<?> target = TestUtil.toObjects(json, null);

        @SuppressWarnings("unchecked")
        EnumSet<Enum65> typed = (EnumSet<Enum65>) target;
        assertThat(typed).containsExactly(Enum65.B00, Enum65.B64);
    }

    @Test
    void testEnumSet_singletonEnum() {
        // Test with singleton enum set
        EnumSet<Enum64> source = EnumSet.of(Enum64.A00);
        String json = TestUtil.toJson(source);

        EnumSet<?> target = TestUtil.toObjects(json, null);

        @SuppressWarnings("unchecked")
        EnumSet<Enum64> typed = (EnumSet<Enum64>) target;
        assertThat(typed)
                .hasSize(1)
                .containsExactly(Enum64.A00);
    }

    @Test
    void testEnumSet_prettyPrinting() {
        EnumSet<Enum64> source = EnumSet.of(Enum64.A00, Enum64.A01);

        // Test with pretty printing on
        WriteOptions prettyOptions = new WriteOptionsBuilder()
                .prettyPrint(true)
                .build();

        String prettyJson = TestUtil.toJson(source, prettyOptions);

        // Test with pretty printing off
        WriteOptions compactOptions = new WriteOptionsBuilder()
                .prettyPrint(false)
                .build();

        String compactJson = TestUtil.toJson(source, compactOptions);

        // Both should deserialize the same
        EnumSet<?> prettyTarget = TestUtil.toObjects(prettyJson, null);
        EnumSet<?> compactTarget = TestUtil.toObjects(compactJson, null);

        assertThat(prettyTarget).isEqualTo(compactTarget);

        @SuppressWarnings("unchecked")
        EnumSet<Enum64> typed = (EnumSet<Enum64>) prettyTarget;
        assertThat(typed).containsExactly(Enum64.A00, Enum64.A01);
    }

    @Test
    void testEnumSet_performanceCheck() {
        // Compare performance of regular vs jumbo
        EnumSet<Enum64> regular = EnumSet.allOf(Enum64.class);
        EnumSet<Enum65> jumbo = EnumSet.allOf(Enum65.class);

        long start = System.nanoTime();
        String regularJson = TestUtil.toJson(regular);
        long regularTime = System.nanoTime() - start;

        start = System.nanoTime();
        String jumboJson = TestUtil.toJson(jumbo);
        long jumboTime = System.nanoTime() - start;

        // Both should deserialize successfully
        EnumSet<?> regularTarget = TestUtil.toObjects(regularJson, null);
        EnumSet<?> jumboTarget = TestUtil.toObjects(jumboJson, null);

        assertThat(regularTarget).hasSize(64);
        assertThat(jumboTarget).hasSize(65);
    }
}