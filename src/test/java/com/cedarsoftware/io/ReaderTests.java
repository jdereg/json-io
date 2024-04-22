package com.cedarsoftware.io;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
class ReaderTests {
    /*
        @Test

        void testNewInstance() {
            Date d = (Date) MetaUtils.newInstance(Date.class, null);
            Integer a = (Integer) MetaUtils.newInstance(Integer.class, null);
            String x = (String) MetaUtils.newInstance(String.class, null);

            assert d instanceof Date;
            assert a instanceof Integer;
            assert x instanceof String;

            assert "".equals(x);
            assert 0 == a;
        }
    */
    private static Stream<Arguments> stringsThatAreEmptyWhenTrimmed() {
        return Stream.of(Arguments.of("    "),
                Arguments.of(" \n\t\r "));
    }

    @ParameterizedTest
    @MethodSource("stringsThatAreEmptyWhenTrimmed")
    @NullAndEmptySource
    void testJsonToJavaVariant_returnsNullForEmptyOrNullString(String json) {
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }

    @ParameterizedTest
    @MethodSource("stringsThatAreEmptyWhenTrimmed")
    @NullAndEmptySource
    void testToObjects_returnsNullForEmptyOrNullString(String json) {
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }

    @ParameterizedTest
    @MethodSource("stringsThatAreEmptyWhenTrimmed")
    @NullAndEmptySource
    void testToMaps_returnsNullForEmptyOrNullString(String json) {
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }
}
