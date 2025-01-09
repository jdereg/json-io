package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import com.cedarsoftware.io.models.FoodType;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.FastByteArrayOutputStream;
import com.google.gson.JsonParser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class EnumTests {
    private static final WriteOptions basicWriteOptions = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
    private static final WriteOptions enumAsPrimitiveOptions = new WriteOptionsBuilder().writeEnumAsJsonObject(false).build();

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

    @Test
    void testBasicEnum() {
        TestEnum1 actual = TestUtil.serializeDeserialize(TestEnum1.B);

        assertThat(actual).isSameAs(TestEnum1.B);
    }

    @ParameterizedTest
    @MethodSource("testDifferentWriteOptions")
    void testCollectionOfEnums(WriteOptions writeOptions) {

        List input = new LinkedList<>();
        input.addAll(listOf(TestEnum1.values()));
        input.addAll(listOf(TestEnum2.values()));
        input.addAll(listOf(TestEnum3.values()));
        input.addAll(listOf(ExternalEnum.values()));

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
    void testEnum_internalFieldsAreAlwaysSharedPerInstanceOfENum() throws Exception {
        TestEnum4 initial = loadObject("default-enum-with-changed-fields.json");

        assertThat(initial.age).isEqualTo(21);
        assertThat(initial.name()).isEqualTo("B");
        assertThat(initial.internal).isEqualTo(6);
        assertThat(initial.getFoo()).isEqualTo("bar");

        // changed on initial, too.
        assertThat(initial.internal).isEqualTo(6);
    }

    @Test
    void testEnum_readOldFormatwithChangedFields_withNoPrivateField() throws Exception {
        TestEnum4 initial = loadObject("default-enum-with-changed-fields.json");

        assertThat(initial.age).isEqualTo(21);
        assertThat(initial.name()).isEqualTo("B");
        assertThat(initial.internal).isEqualTo(6);
        assertThat(initial.getFoo()).isEqualTo("bar");

        initial.internal = 9;

        TestEnum4 actual = loadObject("default-enum-with-changed-fields-no-private.json");

        assertThat(actual.age).isEqualTo(21);
        assertThat(actual.name()).isEqualTo("B");
        assertThat(actual.internal).isEqualTo(9);
        assertThat(actual.getFoo()).isEqualTo("bar");
    }

    @Test
    void testEnumWithPrivateMembersAsField_withPrivatesOn() {
        TestEnum4 x = TestEnum4.B;

        WriteOptions options = new WriteOptionsBuilder().writeEnumAsJsonObject(false).build();
        String json = TestUtil.toJson(x, options);

        String expected = loadJson("default-enum-standalone-with-privates.json");
        assertThat(JsonParser.parseString(json)).isEqualTo(JsonParser.parseString(expected));
    }

    @Test
    void testEnumWithNoType_and_defaultWriter() {
        TestEnum4 x = TestEnum4.B;
        FastByteArrayOutputStream fbao = new FastByteArrayOutputStream();

        WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().build();
        JsonWriter writer = new JsonWriter(fbao, options);
        writer.write(x);
        String json = fbao.toString();
        
        String expected = loadJson("default-enum-with-no-type.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumWithPrivateMembersAsField_withPrivatesOff() {
        TestEnum4 x = TestEnum4.B;
        FastByteArrayOutputStream fbao = new FastByteArrayOutputStream();

        WriteOptions options = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();

        JsonWriter writer = new JsonWriter(fbao, options);
        writer.write(x);
        String json = fbao.toString();

        String expected = loadJson("default-enum-standalone-without-privates.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumInCollection_whenEnumsAreObject_andNoType_justOutputsPublicFields() {
        List list = listOf(FederationStrategy.FEDERATE_THIS, FederationStrategy.EXCLUDE);

        WriteOptions options = new WriteOptionsBuilder()
                .writeEnumAsJsonObject(true)
                .showTypeInfoNever().build();

        String json = TestUtil.toJson(list, options);

        assertThat(json).isEqualToIgnoringWhitespace("[{\"name\":\"FEDERATE_THIS\"},{\"name\":\"EXCLUDE\"}]");
    }

    @Test
    void testEnumInCollection_whenEnumsArePrimitive_andNoType_outputsNameOnly() {
        List list = listOf(FederationStrategy.FEDERATE_THIS, FederationStrategy.EXCLUDE);

        WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().build();

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

        WriteOptions options = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
        String json = TestUtil.toJson(mc, options);
        PrivateEnumWithNameOverride actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(PrivateEnumWithNameOverride.Z);
        assertThat(actual.name).isEqualTo("little z");
    }

    @Test
    void testPublicEnumWithGetterAndSetter_andAlsoShowEnumsAreBasicallySingletons() {
        PublicEnumWithNestedName mc = PublicEnumWithNestedName.Z;
        mc.name = "blech";

        WriteOptions options = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
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

        WriteOptions options = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
        String json = TestUtil.toJson(mc, options);
        EnumNestedWithinEnum actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(EnumNestedWithinEnum.THREE);
        assertThat(actual.getSimpleEnum()).isEqualTo(SimpleEnum.TWO);
    }

    @Test
    void testEnum_withPublicNameOverride() {
        EnumNestedWithinEnum mc = EnumNestedWithinEnum.THREE;
        mc.setSimpleEnum(SimpleEnum.TWO);

        WriteOptions options = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
        String json = TestUtil.toJson(mc, options);
        EnumNestedWithinEnum actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(EnumNestedWithinEnum.THREE);
        assertThat(actual.getSimpleEnum()).isEqualTo(SimpleEnum.TWO);
    }


    @ParameterizedTest
    @EnumSource(EnumWithValueField.class)
    void testEnum_thatHasValueField_parsedAsObject(EnumWithValueField field) {
        WriteOptions options = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
        String json = TestUtil.toJson(field, options);
        EnumWithValueField actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(field);
    }


    @ParameterizedTest
    @EnumSource(EnumWithValueField.class)
    void testEnum_thatHasValueField_parsedAsPrimitive(Enum<EnumWithValueField> item) {
        String json = TestUtil.toJson(item, new WriteOptionsBuilder().build());
        EnumWithValueField actual = TestUtil.toObjects(json, null);

        assertThat(actual).isEqualTo(item);
    }

    @ParameterizedTest
    @EnumSource(FoodType.class)
    void testEnum_valueProblems(Enum<FoodType> item) {
        String json = TestUtil.toJson(item, new WriteOptionsBuilder().writeEnumAsJsonObject(true).build());
        FoodType actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), FoodType.class);

        assertThat(actual).isEqualTo(item);
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
    public void getClassIfEnum_returnsEnumClass(Class<?> input, Class<?> expected) {
        Assertions.assertThat(ClassUtilities.getClassIfEnum(input)).isEqualTo(expected);
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

    public enum TestEnum4 {
        A, B, C;

        private int internal = 6;
        protected long age = 21;

        private String foo = "bar";

        public String getFoo() {
            return this.foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }
    }

    private static class NestedEnum {
        private TestEnum3 testEnum3;
        private TestEnum4 testEnum4;

        public TestEnum3 getTestEnum3() {
            return this.testEnum3;
        }

        public TestEnum4 getTestEnum4() {
            return this.testEnum4;
        }

        public void setTestEnum3(TestEnum3 testEnum3) {
            this.testEnum3 = testEnum3;
        }

        public void setTestEnum4(TestEnum4 testEnum4) {
            this.testEnum4 = testEnum4;
        }
    }

    public class DuplicateRefEnum {
        private final TestEnum3 enum1;
        private final TestEnum3 enum2;

        public DuplicateRefEnum(TestEnum3 enum1, TestEnum3 enum2) {
            this.enum1 = enum1;
            this.enum2 = enum2;
        }

        public TestEnum3 getEnum1() {
            return this.enum1;
        }

        public TestEnum3 getEnum2() {
            return this.enum2;
        }
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

    private class SimpleClass {
        private final String name;
        private final SimpleEnum myEnum;

        public SimpleClass(String name, SimpleEnum myEnum) {
            this.name = name;
            this.myEnum = myEnum;
        }

        public String getName() {
            return this.name;
        }

        public SimpleEnum getMyEnum() {
            return this.myEnum;
        }
    }


    private enum EnumNestedWithinEnum {
        ONE, TWO, THREE;

        private SimpleEnum simpleEnum;

        public SimpleEnum getSimpleEnum() {
            return this.simpleEnum;
        }

        public void setSimpleEnum(SimpleEnum simpleEnum) {
            this.simpleEnum = simpleEnum;
        }
    }

    private enum PrivateEnumWithNameOverride {
        X("little x"), Y("little y"), Z("little z");

        private final String name;

        PrivateEnumWithNameOverride(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    public enum PublicEnumWithNestedName {
        X, Y, Z;

        private String name;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private String loadJson(String fileName) {
        return ClassUtilities.loadResourceAsString("enums/" + fileName).trim();
    }

    private <T> T loadObject(String fileName) {
        return (T) TestUtil.toObjects(loadJson(fileName), null);
    }

    private <T> T loadObject(String fileName, ReadOptions options) {
        return (T) TestUtil.toObjects(loadJson(fileName), options, null);
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

        private String value;

        EnumWithValueField(String domain) {
            this.value = domain;
        }

        public String getValue() {
            return this.value;
        }
    }
}