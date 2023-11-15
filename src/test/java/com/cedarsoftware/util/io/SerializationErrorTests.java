package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.models.Permission;
import com.cedarsoftware.util.reflect.models.SecurityGroup;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class SerializationErrorTests {

    @Test
    void testEnumWithValue_makeSureEnumCanBeParsed() {
        String json = loadJsonForTest("enum-with-value.json");
        EnumTests.EnumWithValueField actual = TestUtil.toJava(json);

        assertThat(actual).isEqualTo(EnumTests.EnumWithValueField.FOO);
    }

    @Test
    void testClone_whenWantingToAddtoDatabase_ClearsTheId() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .withDefaultOptimizations()
                .withFieldNameBlackList(SecurityGroup.class, MetaUtils.listOf("id"))
                .build();

        SecurityGroup group = new SecurityGroup();
        group.setId(45L);
        group.setType("Level 1");
        group.setName("Level 1 Security");
        group.setPermissions(new HashSet());

        String json = TestUtil.toJson(group, writeOptions);

        ReadOptions readOptions = new ReadOptionsBuilder()
                .failOnUnknownType()
                .build();

        SecurityGroup actual = TestUtil.toJava(json, readOptions);

        assertThat(actual.getId()).isNull();
        assertThat(actual.getType()).isEqualTo("LEVEL1");
        assertThat(actual.getName()).isEqualTo("Level 1");
        assertThat(actual.getPermissions()).isEmpty();
    }

    @Test
    void testSerializeLongId_doesNotFillInWithZeroWhenMissing() {
        ReadOptions options = new ReadOptionsBuilder()
                .failOnUnknownType()
                .withCustomTypeName(SecurityGroup.class, "sg")
                .withCustomTypeName(Permission.class, "perm")
                .withCustomTypeName(HashSet.class, "set")
                .build();

        String json = loadJsonForTest("security-group.json");
        SecurityGroup actual = TestUtil.toJava(json, options);

        assertThat(actual.getId()).isNull();
        assertThat(actual.getType()).isEqualTo("LEVEL1");
        assertThat(actual.getName()).isEqualTo("Level 1");
        assertThat(actual.getPermissions()).containsExactlyInAnyOrder(
                new Permission(89L, "ALLOW_VIEW", "Allow viwing"),
                new Permission(90L, "ALLOW_MOVING_MONEY", "Allow moving money"));
    }

    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("errors/" + fileName);
    }
}
