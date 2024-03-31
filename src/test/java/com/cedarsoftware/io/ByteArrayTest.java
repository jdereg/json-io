package com.cedarsoftware.io;

import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
class ByteArrayTest extends SerializationDeserializationMinimumTests<byte[]>
{
    @Test
    void testPerformance()
    {
        byte[] bytes = new byte[128 * 1024];
        Random r = new Random();
        r.nextBytes(bytes);
        String json = TestUtil.toJson(bytes);
        byte[] bytes2 = TestUtil.toObjects(json, null);

        for (int i = 0; i < bytes.length; i++)
        {
            assertEquals(bytes[i], bytes2[i]);
        }
    }

    @Test
    void testMultiDimensionalArray() {
        byte[][][] expected = createMultiDimensionalArray(3, 4, 5);

        String json = TestUtil.toJson(expected);

        byte[][][] actual = TestUtil.toObjects(json, null);
        assertMultiDimensionalArray(actual, 3, 4, 5);
    }

    private byte[][][] createMultiDimensionalArray(int one, int two, int three) {
        byte[][][] threeDArray = new byte[one][two][three];
        for (int i = 0; i < one; i++) {
            for (int j = 0; j < two; j++) {
                for (int k = 0; k < three; k++) {
                    threeDArray[i][j][k] = (byte) (i + j + k);
                }
            }
        }
        return threeDArray;
    }

    private void assertMultiDimensionalArray(byte[][][] bytes, int one, int two, int three) {
        for (int i = 0; i < one; i++) {
            for (int j = 0; j < two; j++) {
                for (int k = 0; k < three; k++) {
                    assertThat(bytes[i][j][k]).isEqualTo((byte) (i + j + k));
                }
            }
        }
    }

    @Override
    protected byte[] provideT1() {
        return new byte[]{0, 0x10, 0x20, 0x30};
    }

    @Override
    protected byte[] provideT2() {
        return new byte[]{0x01, 0x11, 0x21, 0x31};
    }

    @Override
    protected byte[] provideT3() {
        return new byte[]{0x02, 0x22, 0x32, 0x42};
    }

    @Override
    protected byte[] provideT4() {
        return new byte[]{0x11, 0x22, 0x33, 0x44};

    }

    @Override
    protected Class<byte[]> getTestClass() {
        return byte[].class;
    }

    @Override
    protected byte[][] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedByteArray array = (NestedByteArray) o;
        return new byte[][]{array.one, array.two};
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedByteArray(provideT1(), provideT2());
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        byte[] t1 = provideT1();
        return new NestedByteArray(t1, t1);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(byte[] expected, Object actual) {
        // not typed information so comes in as Object[] of longs.
        Object[] longs = new Object[expected.length];
        for (int i = 0; i < expected.length; i++) {
            longs[i] = (long) expected[i];
        }
        assertThat(actual).isEqualTo(longs);
    }

    public static class NestedByteArray {
        private final byte[] one;
        private final byte[] two;

        public NestedByteArray(byte[] one, byte[] two) {
            this.one = one;
            this.two = two;
        }

        public byte[] getOne() {
            return this.one;
        }

        public byte[] getTwo() {
            return this.two;
        }
    }
}
