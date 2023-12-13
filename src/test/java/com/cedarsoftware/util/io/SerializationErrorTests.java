package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.io.models.FoodType;
import com.cedarsoftware.util.io.models.MismatchedGetter;
import com.cedarsoftware.util.io.models.ObjectSerializationIssue;
import com.cedarsoftware.util.reflect.models.Permission;
import com.cedarsoftware.util.reflect.models.SecurityGroup;

class SerializationErrorTests {

    @Test
    void testEnumWithValue_makeSureEnumCanBeParsed() {
        String json = loadJsonForTest("enum-with-value.json");
        EnumTests.EnumWithValueField actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(EnumTests.EnumWithValueField.FOO);
    }

    @Test
    void testClone_whenWantingToAddtoDatabase_ClearsTheId() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .addExcludedFields(SecurityGroup.class, MetaUtils.listOf("id")).build();

        SecurityGroup group = new SecurityGroup();
        group.setId(45L);
        group.setType("Level 1");
        group.setName("Level 1 Security");
        group.setPermissions(new HashSet<>());

        String json = TestUtil.toJson(group, writeOptions);

        ReadOptions readOptions = new ReadOptions().failOnUnknownType(true);

        SecurityGroup actual = TestUtil.toObjects(json, readOptions, null);

        assertThat(actual.getId()).isNull();
        assertThat(actual.getType()).isEqualTo("Level 1");
        assertThat(actual.getName()).isEqualTo("Level 1 Security");
        assertThat(actual.getPermissions()).isEmpty();
    }

    @Test
    void testMismatchedMethodTypes_withMinimalTying() {
        MismatchedGetter model = new MismatchedGetter();
        model.generate("foo");

        String json = TestUtil.toJson(model);
        MismatchedGetter actual = TestUtil.toObjects(json, new ReadOptions(), MismatchedGetter.class);

        assertThat(actual.getRawValue()).isEqualTo("foo");
        assertThat(actual.getValues()).containsExactlyElementsOf(Arrays.asList(model.getValues()));
        assertThat(actual.getTypes()).containsExactlyElementsOf(Arrays.asList(model.getTypes()));
    }

    @Test
    void testMismatchedMethodTypes_withFullTyping() {
        MismatchedGetter model = new MismatchedGetter();
        model.generate("foo");

        String json = TestUtil.toJson(model, new WriteOptionsBuilder().showTypeInfoAlways().build());
        MismatchedGetter actual = TestUtil.toObjects(json, new ReadOptions(), MismatchedGetter.class);

        assertThat(actual.getRawValue()).isEqualTo("foo");
        assertThat(actual.getValues()).containsExactlyElementsOf(Arrays.asList(model.getValues()));
        assertThat(actual.getTypes()).containsExactlyElementsOf(Arrays.asList(model.getTypes()));
    }

    @Test
    void testSerializeLongId_doesNotFillInWithZeroWhenMissing() {
        ReadOptions options = new ReadOptions()
                .failOnUnknownType(true)
                .aliasTypeName(SecurityGroup.class, "sg")
                .aliasTypeName(Permission.class, "perm")
                .aliasTypeName(HashSet.class, "set");

        String json = loadJsonForTest("security-group.json");
        SecurityGroup actual = TestUtil.toObjects(json, options, null);

        assertThat(actual.getId()).isNull();
        assertThat(actual.getType()).isEqualTo("LEVEL1");
        assertThat(actual.getName()).isEqualTo("Level 1");
        assertThat(actual.getPermissions()).containsExactlyInAnyOrder(
                new Permission(89L, "ALLOW_VIEW", "Allow viewing"),
                new Permission(90L, "ALLOW_MOVING_MONEY", "Allow moving money"));
    }

    @Test
    void testSerialization_whereDisplay_hasGetterOfDifferentType() {
        ObjectSerializationIssue o = new ObjectSerializationIssue();
        o.setFoodType(FoodType.MILKS);

        ObjectSerializationIssue actual = JsonIo.deepCopy(o, new ReadOptions().build(), new WriteOptionsBuilder().build());

        assertThat(actual.getFoodType()).isEqualTo(FoodType.MILKS);
    }

    @Disabled("needs Factory abd Writer for DateFormatter and maybe other Chronos types")
//    @Test
    void testWriteOptions() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        String json = TestUtil.toJson(writeOptions);
        WriteOptions backFromSleep = TestUtil.toObjects(json, null);
        assertTrue(DeepEquals.deepEquals(writeOptions, backFromSleep));
    }

    @Disabled("needs Factory abd Writer for DateFormatter and maybe other Chronos types")
//    @Test
    void testReadOptions() {
        ReadOptions readOptions = new ReadOptions();
        String json = TestUtil.toJson(readOptions);
        ReadOptions backFromSleep = TestUtil.toObjects(json, null);
        assertTrue(DeepEquals.deepEquals(readOptions, backFromSleep));
    }

    private String loadJsonForTest(String fileName) {
        return MetaUtils.loadResourceAsString("errors/" + fileName);
    }
}
