package com.cedarsoftware.io;

import java.util.EnumSet;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.io.JsonValue.ENUM;

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
public class EmptyEnumSetOnJDK17Test
{
    enum TestEnum
    {
        V1, V2, V3
    }

    static class MultiVersioned
    {
        EnumSet<TestEnum> versions;
        String dummy;
        EnumSet<Thread.State> states;

        public MultiVersioned(EnumSet<TestEnum> versions, String dummy, EnumSet<Thread.State> states) {
            this.versions = versions;
            this.dummy = dummy;
            this.states = states;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MultiVersioned that = (MultiVersioned) o;
            return Objects.equals(versions, that.versions) && Objects.equals(dummy, that.dummy);
        }

        public int hashCode() {
            return Objects.hash(versions, dummy);
        }
    }

    @Test
    void testEmptyEnumSetOnJDK17()
    {
        Object o = EnumSet.noneOf(TestEnum.class);
        String json = TestUtil.toJson(o);
        EnumSet<?> es = TestUtil.toObjects(json, null);
        assert es.isEmpty();
    }

    @Test
    void testEnumSetOnJDK17()
    {
        EnumSet<?> source = EnumSet.of(TestEnum.V1, TestEnum.V3);
        String json = TestUtil.toJson(source, new WriteOptionsBuilder().writeEnumSetOldWay(false).build());
        assert !json.contains(ENUM);    // Make sure writeEnumSetOldWay writeOption is working
        json = TestUtil.toJson(source);
        assert json.contains(ENUM);
        EnumSet<?> target = TestUtil.toObjects(json, null);
        assert source.equals(target);
    }

    @Test
    void testEnumSetInPoJoOnJDK17()
    {
        MultiVersioned m = new MultiVersioned(EnumSet.of(TestEnum.V1, TestEnum.V3), "what", EnumSet.of(Thread.State.NEW));
        String json = TestUtil.toJson(m);
        MultiVersioned target = TestUtil.toObjects(json, null);
        assert m.equals(target);
    }
}