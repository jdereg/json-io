package com.cedarsoftware.util.io;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
class EnumTests {

    private static final WriteOptions basicWriteOptions = new WriteOptions().writeEnumAsJsonObject(true);
    private static final WriteOptions enumAsPrimitiveOptions = new WriteOptions().writeEnumAsJsonObject(false);

    private static Stream<Arguments> testDifferentWriteOptions() {
        return Stream.of(
                Arguments.of(basicWriteOptions),
                Arguments.of(enumAsPrimitiveOptions)
        );
    }

    @ParameterizedTest
    @MethodSource("testDifferentWriteOptions")
    void testNestedEnum(WriteOptions writeOptions) {
        NestedEnum expected = new NestedEnum();
        expected.setTestEnum3(TestEnum3.A);
        expected.setTestEnum4(TestEnum4.B);

        String json = TestUtil.toJson(expected, writeOptions);

        NestedEnum actual = TestUtil.toObjects(json, null);

        assertThat(actual.getTestEnum3()).isSameAs(expected.getTestEnum3());
        assertThat(actual.getTestEnum4()).isSameAs(expected.getTestEnum4());
    }

    @ParameterizedTest
    @MethodSource("testDifferentWriteOptions")
    void testDuplicateRef(WriteOptions writeOptions) {
        DuplicateRefEnum expected = new DuplicateRefEnum(TestEnum3.A, TestEnum3.A);

        String json = TestUtil.toJson(expected, writeOptions);

        assertThat(json)
                .contains("@ref")
                .contains("@i");

        DuplicateRefEnum actual = TestUtil.toObjects(json, null);

        assertThat(actual.getEnum1())
                .isSameAs(expected.getEnum1())
                .isSameAs(actual.getEnum2());
    }

    @ParameterizedTest
    @MethodSource("testDifferentWriteOptions")
    void testCollectionOfEnums(WriteOptions writeOptions) {

        List input = new LinkedList<>();
        input.addAll(MetaUtils.listOf(TestEnum1.values()));
        input.addAll(MetaUtils.listOf(TestEnum2.values()));
        input.addAll(MetaUtils.listOf(TestEnum3.values()));
        input.addAll(MetaUtils.listOf(ExternalEnum.values()));

        String json = TestUtil.toJson(input, writeOptions);

        Collection<Object> actual = TestUtil.toObjects(json, null);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(input);
    }

    private static Stream<Arguments> testArraysWithDifferentWriteOptions() {
        return Stream.of(
                Arguments.of(TestEnum1.values(), basicWriteOptions),
                Arguments.of(TestEnum1.values(), enumAsPrimitiveOptions),
                Arguments.of(TestEnum2.values(), basicWriteOptions),
                Arguments.of(TestEnum2.values(), enumAsPrimitiveOptions),
                Arguments.of(TestEnum3.values(), basicWriteOptions),
                Arguments.of(TestEnum3.values(), enumAsPrimitiveOptions),
                Arguments.of(TestEnum4.values(), basicWriteOptions),
                Arguments.of(TestEnum4.values(), enumAsPrimitiveOptions),
                Arguments.of(ExternalEnum.values(), basicWriteOptions),
                Arguments.of(ExternalEnum.values(), enumAsPrimitiveOptions)
        );
    }

    @ParameterizedTest
    @MethodSource("testArraysWithDifferentWriteOptions")
    void testEnumsIndividually_doesRoundTrip_successfully(Enum[] enums, WriteOptions writeOptions) {
        Arrays.stream(enums).forEach(e -> readWriteAndCompareEnum(e, writeOptions));
    }

    @ParameterizedTest
    @MethodSource("testArraysWithDifferentWriteOptions")
    void testArrayOfEnums_doesRoundTrip_successfully(Enum[] values, WriteOptions writeOptions) {
        readWriteAndCompareEnumArray(values, writeOptions);
    }

    @Test
    void testEnum_readOldFormatWithoutPrivates_stillWorks() throws Exception {
        TestEnum4 actual = loadObject("default-enum-standalone-without-privates.json");

        assertThat(actual.age).isEqualTo(21);
        assertThat(actual.name()).isEqualTo("B");
        assertThat(actual.internal).isEqualTo(9);
        assertThat(actual.getFoo()).isEqualTo("bar");
    }

    @Test
    void testEnum_internalFieldsAreAlwaysSharedPerInstanceOfENum() throws Exception {
        TestEnum4 initial = loadObject("default-enum-with-changed-fields.json");

        assertThat(initial.age).isEqualTo(21);
        assertThat(initial.name()).isEqualTo("B");
        assertThat(initial.internal).isEqualTo(6);
        assertThat(initial.getFoo()).isEqualTo("bar");

        initial.internal = 9;

        TestEnum4 actual = loadObject("default-enum-with-changed-fields.json");

        assertThat(actual.age).isEqualTo(21);
        assertThat(actual.name()).isEqualTo("B");
        assertThat(actual.internal).isEqualTo(9);
        assertThat(actual.getFoo()).isEqualTo("bar");
    }

    @Test
    void testEnum_readOldFormatwithChangedFields_withNoPrivateField() throws Exception {
        TestEnum4 actual = loadObject("default-enum-with-changed-fields-no-private.json");

        assertThat(actual.age).isEqualTo(21);
        assertThat(actual.name()).isEqualTo("B");
        assertThat(actual.internal).isEqualTo(9);
        assertThat(actual.getFoo()).isEqualTo("bar");
    }

    @Test
    void testEnumWithPrivateMembersAsField_withPrivatesOn() {
        TestEnum4 x = TestEnum4.B;

        WriteOptions options = new WriteOptions().writeEnumAsJsonObject(false);
        String json = TestUtil.toJson(x, options);

        String expected = loadJson("default-enum-standalone-with-privates.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumWithNoType_and_defaultWriter() {
        TestEnum4 x = TestEnum4.B;
        ByteArrayOutputStream ba = new ByteArrayOutputStream();

        WriteOptions options = new WriteOptions().showTypeInfoNever();
        JsonWriter writer = new JsonWriter(ba, options);
        writer.write(x);
        String json = new String(ba.toByteArray());
        
        String expected = loadJson("default-enum-with-no-type.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumWithPrivateMembersAsField_withPrivatesOff() {
        TestEnum4 x = TestEnum4.B;
        ByteArrayOutputStream ba = new ByteArrayOutputStream();

        WriteOptions options = new WriteOptions().writeEnumAsJsonObject(true);

        JsonWriter writer = new JsonWriter(ba, options);
        writer.write(x);
        String json = new String(ba.toByteArray());

        String expected = loadJson("default-enum-standalone-without-privates.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumInCollection_whenEnumsAreObject_andNoType_justOutputsPublicFields() {
        List list = MetaUtils.listOf(FederationStrategy.FEDERATE_THIS, FederationStrategy.EXCLUDE);

        WriteOptions options = new WriteOptions()
                .writeEnumAsJsonObject(true)
                .showTypeInfoNever();

        String json = TestUtil.toJson(list, options);

        assertThat(json).isEqualToIgnoringWhitespace("[{\"name\":\"FEDERATE_THIS\"},{\"name\":\"EXCLUDE\"}]");
    }

    @Test
    void testEnumInCollection_whenEnumsArePrimitive_andNoType_outputsNameOnly() {
        List list = MetaUtils.listOf(FederationStrategy.FEDERATE_THIS, FederationStrategy.EXCLUDE);

        WriteOptions options = new WriteOptions().showTypeInfoNever();

        String json = TestUtil.toJson(list, options);

        assertThat(json).isEqualToIgnoringWhitespace("[\"FEDERATE_THIS\",\"EXCLUDE\"]");
    }

    @Test
    void testEnumField() {
        SimpleClass mc = new SimpleClass("Dude", SimpleEnum.ONE);
        String json = TestUtil.toJson(mc);
        assertThat(json).contains("Dude");

        SimpleClass actual = TestUtil.toObjects(json, null);
        assertThat(actual.getName()).isEqualTo("Dude");
        assertThat(actual.getMyEnum()).isEqualTo(SimpleEnum.ONE);
    }

    @Test
    void testEnum_whenHasNameMethodOverride_parsesCorrectly_and_nameGetsSet() {
        PrivateEnumWithNameOverride mc = PrivateEnumWithNameOverride.Z;

        WriteOptions options = new WriteOptions().writeEnumAsJsonObject(true);
        String json = TestUtil.toJson(mc, options);
        PrivateEnumWithNameOverride actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(PrivateEnumWithNameOverride.Z);
        assertThat(actual.name).isEqualTo("little z");
    }

    @Test
    void testPublicEnumWithGetterAndSetter_andAlsoShowEnumsAreBasicallySingletons() {
        PublicEnumWithNestedName mc = PublicEnumWithNestedName.Z;
        mc.name = "blech";

        WriteOptions options = new WriteOptions().writeEnumAsJsonObject(true);
        String json = TestUtil.toJson(mc, options);

        mc.name = "foo";

        PublicEnumWithNestedName actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(PublicEnumWithNestedName.Z);
        assertThat(actual.name).isEqualTo("blech");

        // basically ENUMS are singletons and editing the field name on one will also edit the name
        assertThat(mc.name).isEqualTo("blech");
    }


    @Test
    void testEnumNestedWithinEnum() {
        EnumNestedWithinEnum mc = EnumNestedWithinEnum.THREE;
        mc.setSimpleEnum(SimpleEnum.TWO);

        WriteOptions options = new WriteOptions().writeEnumAsJsonObject(true);
        String json = TestUtil.toJson(mc, options);
        EnumNestedWithinEnum actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(EnumNestedWithinEnum.THREE);
        assertThat(actual.getSimpleEnum()).isEqualTo(SimpleEnum.TWO);
    }

    @Test
    void testEnum_withPublicNameOverride() {
        EnumNestedWithinEnum mc = EnumNestedWithinEnum.THREE;
        mc.setSimpleEnum(SimpleEnum.TWO);

        WriteOptions options = new WriteOptions().writeEnumAsJsonObject(true);
        String json = TestUtil.toJson(mc, options);
        EnumNestedWithinEnum actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(EnumNestedWithinEnum.THREE);
        assertThat(actual.getSimpleEnum()).isEqualTo(SimpleEnum.TWO);
    }


    @ParameterizedTest
    @EnumSource(EnumWithValueField.class)
    void testEnum_thatHasValueField_parsedAsObject(EnumWithValueField field) {
        WriteOptions options = new WriteOptions().writeEnumAsJsonObject(true);
        String json = TestUtil.toJson(field, options);
        EnumWithValueField actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(field);
    }


    @ParameterizedTest
    @EnumSource(EnumWithValueField.class)
    void testEnum_thatHasValueField_parsedAsPrimitive() {
        EnumWithValueField mc = EnumWithValueField.FOO;

        String json = TestUtil.toJson(mc, new WriteOptions());
        EnumWithValueField actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(EnumWithValueField.FOO);
    }


    private static Stream<Arguments> testIsEnum_whenNotAnonymousInnerClass() {
        return Stream.of(
                Arguments.of(TestEnum1.A),
                Arguments.of(TestEnum4.A),
                Arguments.of(ExternalEnum.ONE),
                Arguments.of(FederationStrategy.EXCLUDE),
                Arguments.of(SimpleEnum.ONE)
        );
    }

    @ParameterizedTest
    @MethodSource("testIsEnum_whenNotAnonymousInnerClass")
    void testIsEnum_whenNotAnonymousInnerClass_returnsTrue(Object o) {
        assertThat(o.getClass().isEnum()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("testIsEnum_whenNotAnonymousInnerClass")
    void testIsEnum_whenNotAnonymousInnerClass_isAssignableToEnum(Object o) {
        assertThat(Enum.class).isAssignableFrom(o.getClass());
    }

    private static Stream<Arguments> testIsEnum_whenAnonymousInnerClass() {
        return Stream.of(
                Arguments.of(TestEnum2.A),
                Arguments.of(TestEnum3.A)
        );
    }

    @ParameterizedTest
    @MethodSource("testIsEnum_whenAnonymousInnerClass")
    void testIsEnum_whenAnonymousInnerClass_returnsFalse(Object o) {
        assertThat(o.getClass().isEnum()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("testIsEnum_whenAnonymousInnerClass")
    void testIsAssignableToEnum_whenAnonymousInnerClass_returnsFalse(Object o) {
        assertThat(Enum.class).isAssignableFrom(o.getClass());
    }

    @ParameterizedTest
    @MethodSource("testIsEnum_whenAnonymousInnerClass")
    void testIsEnumOnEnclosingclass_whenAnonymousInnerClass_returnsTrue(Object o) {
        assertThat(o.getClass().getEnclosingClass().isEnum()).isTrue();
    }

    private static Stream<Arguments> testIsEnumIsFalse() {
        return Stream.of(
                Arguments.of(TestEnum2.A),
                Arguments.of(TestEnum3.A),
                Arguments.of("foo"),
                Arguments.of(9L)
        );
    }

    @ParameterizedTest
    @MethodSource("testIsEnumIsFalse")
    void testIsEnum_variousClasses_returnsFalse(Object o) {
        assertThat(o.getClass().isEnum()).isFalse();
    }


    @ParameterizedTest
    @MethodSource("testIsEnum_whenAnonymousInnerClass")
    void testIsAssignableToEnum_whenAnonymousInnerClass_returnsTrue(Object o) {
        assertThat(Enum.class.isAssignableFrom(o.getClass().getEnclosingClass())).isTrue();
    }

    private static Stream<Arguments> getClassIfEnumTests() {
        return Stream.of(Arguments.of(EnumTests.TestEnum1.class, EnumTests.TestEnum1.class),
                Arguments.of(EnumTests.TestEnum1.A.getClass(), EnumTests.TestEnum1.class),
                Arguments.of(EnumTests.TestEnum2.A.getClass(), EnumTests.TestEnum2.class),
                Arguments.of(EnumTests.TestEnum3.A.getClass(), EnumTests.TestEnum3.class),
                Arguments.of(EnumTests.TestEnum4.A.getClass(), EnumTests.TestEnum4.class),
                Arguments.of(EnumTests.FederationStrategy.EXCLUDE.getClass(), EnumTests.FederationStrategy.class),
                Arguments.of(EnumTests.SimpleEnum.ONE.getClass(), EnumTests.SimpleEnum.class));
    }

    @ParameterizedTest
    @MethodSource("getClassIfEnumTests")
    public void getClassIfEnum_returnsEnumClass(Class input, Class expected) {
        Assertions.assertThat(MetaUtils.getClassIfEnum(input).get()).isEqualTo(expected);
    }


    private enum TestEnum1 {
        A, B, C;
    }

    private enum TestEnum2 {
        A() {

        }, B() {

        }, C() {

        };
    }

    private enum TestEnum3 {
        A("Foo") {
            public void doXX() {
            }


        }, B("Bar") {
            public void doXX() {
            }


        }, C(null) {
            public void doXX() {
            }


        };

        TestEnum3(String val) {
            this.val = val;
        }

        public abstract void doXX();

        private final String val;
    }

    private enum TestEnum4 {
        A, B, C;

        private int internal = 6;
        protected long age = 21;

        @Getter
        @Setter
        private String foo = "bar";
    }

    @Getter
    @Setter
    private static class NestedEnum {
        private TestEnum3 testEnum3;
        private TestEnum4 testEnum4;
    }

    @AllArgsConstructor
    @Getter
    public class DuplicateRefEnum {
        private final TestEnum3 enum1;
        private final TestEnum3 enum2;
    }

    private enum FederationStrategy {
        EXCLUDE, FEDERATE_THIS, FEDERATE_ORIGIN;

        public static FederationStrategy fromName(String name) {
            for (FederationStrategy type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }

            }

            return FEDERATE_THIS;
        }

        public boolean exceeds(FederationStrategy type) {
            return this.ordinal() > type.ordinal();
        }

        public boolean atLeast(FederationStrategy type) {
            return this.ordinal() >= type.ordinal();
        }

        public String toString() {
            return name();
        }
    }

    private enum SimpleEnum {
        ONE, TWO;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private class SimpleClass {
        private final String name;
        private final SimpleEnum myEnum;
    }


    private enum EnumNestedWithinEnum {
        ONE, TWO, THREE;

        @Getter
        @Setter
        private SimpleEnum simpleEnum;
    }

    private enum PrivateEnumWithNameOverride {
        X("little x"), Y("little y"), Z("little z");

        @Getter
        private final String name;

        PrivateEnumWithNameOverride(String name) {
            this.name = name;
        }
    }

    public enum PublicEnumWithNestedName {
        X, Y, Z;

        @Getter
        @Setter
        private String name;
    }

    private String loadJson(String fileName) {
        return TestUtil.fetchResource("enums/" + fileName).trim();
    }

    private <T> T loadObject(String fileName) {
        return (T) TestUtil.toObjects(loadJson(fileName), null);
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> T readWriteAndCompareEnum(T input, WriteOptions writeOptions) {
        String json = TestUtil.toJson(input, writeOptions);
        Enum actual = TestUtil.toObjects(json, null);
        assertThat(actual).isEqualTo(input);
        return (T) actual;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> T readWriteAndCompareEnumArray(T[] enumArray, WriteOptions writeOptions) {
        String json = TestUtil.toJson(enumArray, writeOptions);
        T[] actual = TestUtil.toObjects(json, null);
        assertThat(actual).isEqualTo(enumArray);
        return (T) actual;
    }

    public enum EnumWithValueField {
        FOO("foo.com"), BAR("bar.gov");

        @Getter
        private String value;

        EnumWithValueField(String domain) {
            this.value = domain;
        }
    }
}
