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
class TestCustomReaderIdentity
{
	static class CustomDataReader implements JsonReader.JsonClassReader
	{
		public Object read(Object jOb, Deque<JsonObject<String, Object>> stack)
		{
			CustomDataClass customClass = new CustomDataClass();
			customClass.setTest("blab");
			return customClass;
		}
	}

	/**
	 * This test uses a customReader to read two identical instances in a collection.
	 * A customWriter is not necessary.
	 */
	@Test
	public void testCustomReaderSerialization()
	{
		List<CustomDataClass> elements = new LinkedList<>();
		CustomDataClass element = new CustomDataClass();
		element.setTest("hallo");

		elements.add(element);
		elements.add(element);

		String json = JsonWriter.objectToJson(elements);


		Object obj = TestUtil.readJsonObject(json, [(CustomDataClass.class):new CustomDataReader()])
		assert obj != null
	}

	/**
	 * This test does not use a customReader to read two identical instances in a collection.
	 * A customWriter is not necessary.
	 */
	@Test
	public void testSerializationOld()
	{
		List<WithoutCustomReaderClass> elements = new LinkedList<>();
		WithoutCustomReaderClass element = new WithoutCustomReaderClass();
		element.setTest("hallo");

		elements.add(element);
		elements.add(element);

		String json = JsonWriter.objectToJson(elements);

		Object obj = TestUtil.readJsonObject(json, [(CustomDataClass.class):new CustomDataReader()])
		assert obj != null
	}

	@Test
	public void testInSet(){
		Set<WithoutCustomReaderClass> set = new HashSet<>();

		CustomDataClass element = new CustomDataClass();
		element.setTest("hallo");

		WithoutCustomReaderClass e1 = new WithoutCustomReaderClass();
		e1.setCustomReaderInner(element);

		WithoutCustomReaderClass e2 = new WithoutCustomReaderClass();
		e2.setCustomReaderInner(element);

		set.add(e1);
		set.add(e2);

		String json = JsonWriter.objectToJson(set);

		Object obj = TestUtil.readJsonObject(json, [(CustomDataClass.class):new CustomDataReader()])
		assert obj != null
	}

	@Test
	public void testInArray(){
		CustomDataClass[] array = new CustomDataClass[2];
		CustomDataClass element = new CustomDataClass();
		element.setTest("hallo");

		array[0] = element;
		array[1] = element;

		String json = JsonWriter.objectToJson(array);

		Object obj = TestUtil.readJsonObject(json, [(CustomDataClass.class):new CustomDataReader()])
		assert obj != null
	}
}
