package com.cedarsoftware.io;

import java.util.HashMap;
import java.util.Map;

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
public class CustomReaderObjectTest
{
	private boolean madeItHere = false;
	/**
	 * This test uses a customReader to read the entire object.
	 */
	@Test
	public void testCustomReaderSerialization()
	{
		CustomWriterTest.Person p = CustomWriterTest.createTestPerson();
		String json = TestUtil.toJson(p);
		Map<Class<?>, CustomReader> customReaders = new HashMap<>();
		customReaders.put(CustomWriterTest.Person.class, new CustomReader());
		CustomWriterTest.Person pRead = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(customReaders).build(), null);
		assert p.equals(pRead);
		assert madeItHere;
	}

	public class CustomReader implements JsonReader.JsonClassReader
	{
        public Object read(Object jsonObj, Resolver resolver)
		{
			resolver.traverseFields((JsonObject) jsonObj);
            Object target = ((JsonObject) jsonObj).getTarget();
			madeItHere = true;
			return target;
		}
	}
}
