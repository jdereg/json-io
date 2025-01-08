package com.cedarsoftware.io;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.cedarsoftware.util.convert.CollectionsWrappers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
    @Disabled
    @Test
    void testUnmodifiableSetAtRoot() {
        Set set = new HashSet();
        set.add("foo");
        set.add("bar");
        set.add("baz");
        set = Collections.unmodifiableSet(set);

        WriteOptions writeOptionss = new WriteOptionsBuilder().build();
        String json = TestUtil.toJson(set, writeOptionss);
        assert json.contains("\"UnmodifiableSet\"");

        Set set2 = TestUtil.toObjects(json, CollectionsWrappers.getUnmodifiableSetClass());
        assert CollectionsWrappers.getUnmodifiableCollectionClass().isAssignableFrom(set2.getClass());
        json = TestUtil.toJson(set2, writeOptionss);
        assert json.contains("\"UnmodifiableSet\"");

        // Only asking for a "Set", not an "unmodifiable" Set.
        Set set3 = TestUtil.toObjects(json, Set.class);

        // Lock in expectation that LinkedHashSet is fallback
        assert LinkedHashSet.class.isAssignableFrom(set3.getClass());
    }
}