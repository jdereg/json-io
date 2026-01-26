package com.cedarsoftware.io;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
class UnmodifiableTest {

    @Test
    void testUnmodifiableSetAtRoot() {
        // Create an unmodifiable set
        Set<String> set = new HashSet<>();
        set.add("foo");
        set.add("bar");
        set.add("baz");
        set = Collections.unmodifiableSet(set);

        // Serialize
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        String json = TestUtil.toJson(set, writeOptions);

        // Deserialize
        Set<String> set2 = TestUtil.toJava(json, null).asClass(Set.class);

        // Verify contents are preserved
        assertNotNull(set2);
        assertEquals(3, set2.size());
        assertTrue(set2.contains("foo"));
        assertTrue(set2.contains("bar"));
        assertTrue(set2.contains("baz"));

        // Verify the set is unmodifiable by attempting to add an element
        assertThrows(UnsupportedOperationException.class, () -> set2.add("qux"),
                "Deserialized set should be unmodifiable - add() should throw UnsupportedOperationException");

        // Verify remove also throws
        assertThrows(UnsupportedOperationException.class, () -> set2.remove("foo"),
                "Deserialized set should be unmodifiable - remove() should throw UnsupportedOperationException");

        // Verify clear also throws
        assertThrows(UnsupportedOperationException.class, () -> set2.clear(),
                "Deserialized set should be unmodifiable - clear() should throw UnsupportedOperationException");
    }
}
