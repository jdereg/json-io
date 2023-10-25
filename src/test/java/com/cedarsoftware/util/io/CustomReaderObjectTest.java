package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

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
		Map<String, Object> args = new HashMap<>();
		Map<Class<?>, CustomReader> customReaders = new HashMap<>();
		customReaders.put(CustomWriterTest.Person.class, new CustomReader());
		args.put(JsonReader.CUSTOM_READER_MAP, customReaders);
		CustomWriterTest.Person pRead = TestUtil.toJava(json, args);
		assert p.equals(pRead);
		assert madeItHere;
	}

	public class CustomReader implements JsonReader.JsonClassReader
	{
		public Object read(Object jOb, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
		{
			ObjectResolver resolver = (ObjectResolver) args.get(JsonReader.OBJECT_RESOLVER);
			resolver.traverseFields(stack, (JsonObject<String, Object>) jOb);
			Object target = ((JsonObject<String, Object>) jOb).getTarget();
			madeItHere = true;
			return target;
		}
	}
}
