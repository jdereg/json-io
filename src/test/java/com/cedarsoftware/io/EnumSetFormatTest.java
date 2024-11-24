package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify the format of enum serialization within EnumSets
 */
class EnumSetFormatTest {
    // Simple enum type (2 fields - ordinal, name)
    private enum SimpleEnum {
        A, B, C
    }

    // Complex enum type (more than 2 fields)
    private enum VerboseEnum {
        TEST("description", 1, "extra1Value", "extra2Value");

        private final String desc;
        private final int num;
        private final String extra1;
        private final String extra2;

        VerboseEnum(String desc, int num, String extra1, String extra2) {
            this.desc = desc;
            this.num = num;
            this.extra1 = extra1;
            this.extra2 = extra2;
        }

        public String getDesc() { return desc; }
        public int getNum() { return num; }
        public String getExtra1() { return extra1; }
        public String getExtra2() { return extra2; }
    }

    @Test
    void testEnumSet_verifyWriteFormat() {
        // Test simple enum format (<=2 fields)
        EnumSet<SimpleEnum> simpleSource = EnumSet.of(SimpleEnum.A, SimpleEnum.B);
        String simpleJson = TestUtil.toJson(simpleSource);

        EnumSet<?> simpleTarget = TestUtil.toObjects(simpleJson, null);
        assertThat(simpleTarget).isEqualTo(simpleSource);

        // Test complex enum format (>2 fields)
        EnumSet<VerboseEnum> verboseSource = EnumSet.of(VerboseEnum.TEST);
        String verboseJson = TestUtil.toJson(verboseSource);

        EnumSet<?> verboseTarget = TestUtil.toObjects(verboseJson, null);
        assertThat(verboseTarget).isEqualTo(verboseSource);

        // Verify the field values are maintained
        @SuppressWarnings("unchecked")
        EnumSet<VerboseEnum> typedTarget = (EnumSet<VerboseEnum>) verboseTarget;
        VerboseEnum test = typedTarget.iterator().next();
        assertThat(test.getDesc()).isEqualTo("description");
        assertThat(test.getNum()).isEqualTo(1);
        assertThat(test.getExtra1()).isEqualTo("extra1Value");
        assertThat(test.getExtra2()).isEqualTo("extra2Value");
    }
}