package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class SerializedExceptionTest
{
    class MyException extends RuntimeException
    {
        private String name;

        MyException(String name, String detail)
        {
            super(detail);
            this.name = name;
        }
    }
    
    @Disabled
    @Test
    void testAccessToPrivateField()
    {
        MyException exp = new MyException("foo", "bar");
        String json = JsonWriter.objectToJson(exp);
        System.out.println("json = " + json);
        MyException exp2 = (MyException) JsonReader.jsonToJava(json);
        System.out.println("exp2 = " + exp2);
    }

}
