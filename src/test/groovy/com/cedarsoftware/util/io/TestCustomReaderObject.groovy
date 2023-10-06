package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

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
class TestCustomReaderObject
{
	static class CustomReader implements JsonReader.JsonClassReaderEx
	{
		public Object read(Object jOb, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
		{
			ObjectResolver resolver = (ObjectResolver) args.get(JsonReader.OBJECT_RESOLVER);
			resolver.traverseFields(stack, (JsonObject<String, Object>) jOb);
			Object target = ((JsonObject<String, Object>) jOb).getTarget();
			return target;
		}
	}

	/**
	 * This test uses a customReader to read the entire object.
	 */
	@Test
	public void testCustomReaderSerialization()
	{
		TestCustomWriter.Person p = TestCustomWriter.createTestPerson();
		String json = JsonWriter.objectToJson(p);
		TestCustomWriter.Person pRead = TestUtil.readJsonObject(json, [(CustomDataClass.class):new CustomReader()]) as TestCustomWriter.Person
		assert p.equals(pRead)
	}
}
