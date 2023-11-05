package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.factory.PersonFactory;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

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
class ReaderTests {
    @Test
    void testNewInstance() {
        Date d = (Date) MetaUtils.newInstance(Date.class);
        Integer a = (Integer) MetaUtils.newInstance(Integer.class);
        String x = (String) MetaUtils.newInstance(String.class);

        assert d instanceof Date;
        assert a instanceof Integer;
        assert x instanceof String;

        assert "".equals(x);
        assert 0 == a;
    }

    @Test
    void testConstructor_whenNoArgsArePassed_classFactoriesIsInstantiatedWithGlobalFactories() {
        JsonReader reader = new JsonReader();
        assertThat(reader.classFactories).containsAllEntriesOf(JsonReader.BASE_CLASS_FACTORIES);
    }

    @Test
    void testConstructor_whenOptionsContainsClassFactories_thoseAreAppendedToBaseClassFactories() {
        Map options = new ReadOptionsBuilder()
                .withClassFactory(CustomWriterTest.Person.class, new PersonFactory())
                .build();

        JsonReader reader = new JsonReader(options);

        assertThat(reader.classFactories)
                .containsAllEntriesOf(JsonReader.BASE_CLASS_FACTORIES)
                .containsAllEntriesOf((Map<String, JsonReader.ClassFactory>) options.get(JsonReader.FACTORIES))
                .hasSize(JsonReader.BASE_CLASS_FACTORIES.size() + 1);
    }

    @Test
    void testConstructor_whenNoArgs_readersAreInstantiatedWithBaseReaders() {
        JsonReader reader = new JsonReader();
        assertThat(reader.readers).containsAllEntriesOf(JsonReader.BASE_READERS);
    }

    @Test
    void testConstructor_whenOptionsContainsReaders_thoseAreAppendedToBaseReaders() {
        Map options = new ReadOptionsBuilder()
                .withCustomReader(CustomWriterTest.Person.class, new CustomWriterTest.CustomPersonReader())
                .build();

        JsonReader reader = new JsonReader(options);

        assertThat(reader.readers)
                .containsAllEntriesOf(JsonReader.BASE_READERS)
                .containsAllEntriesOf((Map<Class<?>, JsonReader.JsonClassReader>) options.get(JsonReader.CUSTOM_READER_MAP))
                .hasSize(JsonReader.BASE_READERS.size() + 1);
    }

}
