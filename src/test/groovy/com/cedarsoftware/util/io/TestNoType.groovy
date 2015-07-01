package com.cedarsoftware.util.io

import org.junit.Test

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
class TestNoType
{
    static class Junk
    {
        Object name
        List things = []
        Map namesToAge = [:]
        Object[] stuff
    }

    @Test
    void testNoType()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.clear()
        cal.set(-700, 5, 10)

        Junk j = new Junk()
        j.stuff = [(int)1, 2L, new BigInteger(3), new BigDecimal(4.0), cal.getTime(), 'Hello', Junk.class] as Object[]
        j.name = 'Zeus'
        j.things = [(int)1, 2L, new BigInteger(3), new BigDecimal(4.0), cal.getTime(), 'Hello', Junk.class] as Object[]
        j.namesToAge.Appollo = 2500L
        j.namesToAge.Hercules = 2489 as int
        j.namesToAge.Poseidon = 2502 as BigInteger
        j.namesToAge.Aphrodite = 2499.0
        j.namesToAge.Zeus = cal.getTime()

        String json = TestUtil.getJsonString(j)
        String json2 = TestUtil.getJsonString(j, [(JsonWriter.TYPE):false])
        assert json != json2
        assert json2 == '{"name":"Zeus","things":[1,2,"3","4",-84243801600000,"Hello","com.cedarsoftware.util.io.TestNoType$Junk"],"namesToAge":{"Appollo":2500,"Hercules":2489,"Poseidon":"2502","Aphrodite":"2499.0","Zeus":-84243801600000},"stuff":[1,2,"3","4",-84243801600000,"Hello","com.cedarsoftware.util.io.TestNoType$Junk"]}'
    }
}

