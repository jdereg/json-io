package com.cedarsoftware.util.io;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.util.*;
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
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
class EnumTests {

    private static final Map<String, Object> basicWriteOptions = new WriteOptionsBuilder().writeEnumsAsObjects().build();
    private static final Map<String, Object> enumAsPrimitiveOptions = new WriteOptionsBuilder().build();

    private static Stream<Arguments> testDifferentWriteOptions() {
        return Stream.of(
                Arguments.of(basicWriteOptions),
                Arguments.of(enumAsPrimitiveOptions)
        );
    }

    @ParameterizedTest
    @MethodSource("testDifferentWriteOptions")
    void testNestedEnum(Map<String, Object> writeOptions) {
        NestedEnum expected = new NestedEnum();
        expected.setTestEnum3(TestEnum3.A);
        expected.setTestEnum4(TestEnum4.B);

        String json = TestUtil.toJson(expected, writeOptions);

        NestedEnum actual = TestUtil.toJava(json);

        assertThat(actual.getTestEnum3()).isSameAs(expected.getTestEnum3());
        assertThat(actual.getTestEnum4()).isSameAs(expected.getTestEnum4());
    }

    @ParameterizedTest
    @MethodSource("testDifferentWriteOptions")
    void testDuplicateRef(Map<String, Object> writeOptions) {
        DuplicateRefEnum expected = new DuplicateRefEnum(TestEnum3.A);

        String json = TestUtil.toJson(expected, writeOptions);

        assertThat(json)
                .contains("@ref")
                .contains("@i");

        DuplicateRefEnum actual = TestUtil.toJava(json);

        assertThat(actual.getEnum1())
                .isSameAs(expected.getEnum1())
                .isSameAs(actual.getEnum2());
    }

    @ParameterizedTest
    @MethodSource("testDifferentWriteOptions")
    void testCollectionOfEnums(Map<String, Object> writeOptions) {

        var input = new LinkedList<>();
        input.addAll(List.of(TestEnum1.values()));
        input.addAll(List.of(TestEnum2.values()));
        input.addAll(List.of(TestEnum3.values()));
        input.addAll(List.of(ExternalEnum.values()));

        String json = TestUtil.toJson(input, writeOptions);

        Collection<Object> actual = TestUtil.toJava(json);
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
    void testEnumsIndividually_doesRoundTrip_successfully(Enum[] enums, Map<String, Object> writeOptions) {
        Arrays.stream(enums).forEach(e -> readWriteAndCompareEnum(e, writeOptions));
    }

    @ParameterizedTest
    @MethodSource("testArraysWithDifferentWriteOptions")
    void testArrayOfEnums_doesRoundTrip_successfully(Enum[] values, Map<String, Object> writeOptions) {
        readWriteAndCompareEnumArray(values, writeOptions);
    }

    @Test
    void testEnum_readOldFormatWithoutPrivates_stillWorks() throws Exception {
        TestEnum4 actual = loadObject("default-enum-standalone-without-privates.json");

        assertThat(actual.age).isEqualTo(21);
        assertThat(actual.name()).isEqualTo("B");
        assertThat(actual.internal).isEqualTo(6);
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
        assertThat(actual.internal).isEqualTo(6);
        assertThat(actual.getFoo()).isEqualTo("bar");

        assertThat(initial.internal).isEqualTo(6);
    }

    @Test
    public void testEnum_readOldFormatwithChangedFields_withNoPrivateField() throws Exception {
        TestEnum4 actual = loadObject("default-enum-with-changed-fields-no-private.json");

        assertThat(actual.age).isEqualTo(21);
        assertThat(actual.name()).isEqualTo("B");
        assertThat(actual.internal).isEqualTo(6);
        assertThat(actual.getFoo()).isEqualTo("bar");
    }

    @Test
    void testEnumWithPrivateMembersAsField_withPrivatesOn() {
        TestEnum4 x = TestEnum4.B;

        var options = new WriteOptionsBuilder()
                .writeEnumsAsObjects()
                .build();

        String json = TestUtil.toJson(x, options);

        var expected = loadJson("default-enum-standalone-with-privates.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumWithNoType_and_defaultWriter() {
        TestEnum4 x = TestEnum4.B;
        ByteArrayOutputStream ba = new ByteArrayOutputStream();

        var options = new WriteOptionsBuilder()
                .withNoTypeInformation()
                .build();

        JsonWriter writer = new JsonWriter(ba, options);
        writer.write(x);
        String json = new String(ba.toByteArray());
        
        var expected = loadJson("default-enum-with-no-type.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumWithPrivateMembersAsField_withPrivatesOff() {
        TestEnum4 x = TestEnum4.B;
        ByteArrayOutputStream ba = new ByteArrayOutputStream();

        var options = new WriteOptionsBuilder()
                .doNotWritePrivateEnumFields()
                .build();

        JsonWriter writer = new JsonWriter(ba, options);
        writer.write(x);
        String json = new String(ba.toByteArray());

        var expected = loadJson("default-enum-standalone-without-privates.json");
        assertThat(json).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void testEnumNoOrdinal_enumsAsPrimitive() {
        var list = List.of(FederationStrategy.FEDERATE_THIS, FederationStrategy.EXCLUDE);

        var options = new WriteOptionsBuilder()
                .withNoTypeInformation()
                .doNotWritePrivateEnumFields()
                .build();

        var json = TestUtil.toJson(list, options);

        assertThat(json).isEqualToIgnoringWhitespace("[{\"name\":\"FEDERATE_THIS\"},{\"name\":\"EXCLUDE\"}]");
    }

    @Test
    void testEnumField() {
        SimpleClass mc = new SimpleClass(this, "Dude", SimpleEnum.ONE);
        String json = TestUtil.toJson(mc);
        assertThat(json).contains("Dude");

        SimpleClass actual = TestUtil.toJava(json);
        assertThat(actual.getName()).isEqualTo("Dude");
        assertThat(actual.getMyEnum()).isEqualTo(SimpleEnum.ONE);
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

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        private int internal = 6;
        protected long age = 21;
        private String foo = "bar";
    }

    private static class NestedEnum {
        public TestEnum3 getTestEnum3() {
            return testEnum3;
        }

        public void setTestEnum3(Enum testEnum3) {
            this.testEnum3 = (TestEnum3) testEnum3;
        }

        public TestEnum4 getTestEnum4() {
            return testEnum4;
        }

        public void setTestEnum4(Enum testEnum4) {
            this.testEnum4 = (TestEnum4) testEnum4;
        }

        private TestEnum3 testEnum3;
        private TestEnum4 testEnum4;
    }

    public class DuplicateRefEnum {
        private DuplicateRefEnum(TestEnum3 value) {
            this.enum1 = value;
            this.enum2 = value;
        }

        public TestEnum3 getEnum1() {
            return enum1;
        }

        public TestEnum3 getEnum2() {
            return enum2;
        }

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

    private class SimpleClass {
        public SimpleClass(EnumTests enclosing, String name, SimpleEnum myEnum) {
            this.name = name;
            this.myEnum = myEnum;
        }

        public String getName() {
            return name;
        }

        public SimpleEnum getMyEnum() {
            return myEnum;
        }

        private final String name;
        private final SimpleEnum myEnum;
    }

    private enum EnumNestedWithinEnum {
        ONE, TWO, THREE;

        private SimpleEnum simpleEnum;

        public void setSimpleEnum(SimpleEnum simpleEnum) {
            this.simpleEnum = simpleEnum;
        }

        public SimpleEnum getSimpleEnum() {
            return this.simpleEnum;
        }
    }

    private String loadJson(String fileName) {
        return TestUtil.fetchResource("enums/" + fileName).trim();
    }

    private <T> T loadObject(String fileName) {
        return (T) TestUtil.toJava(loadJson(fileName));
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> T readWriteAndCompareEnum(T input, Map<String, Object> writeOptions) {
        String json = TestUtil.toJson(input);
        Enum actual = TestUtil.toJava(json);
        assertThat(actual).isEqualTo(input);
        return (T) actual;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> T readWriteAndCompareEnumArray(T[] enumArray, Map<String, Object> writeOptions) {
        String json = TestUtil.toJson(enumArray, writeOptions);
        T[] actual = TestUtil.toJava(json);
        assertThat(actual).isEqualTo(enumArray);
        return (T) actual;
    }
}
