package com.cedarsoftware.io;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class CustomReaderIdentityTest
{
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

		String json = TestUtil.toJson(elements);

		Map<Class<CustomDataClass>, CustomDataReader> readerMap = new HashMap<>(1);
		readerMap.put(CustomDataClass.class, new CustomDataReader());
        Object obj = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(readerMap).build(), null);
		assert obj instanceof List;
		List list = (List) obj;
		assert list.get(0) == list.get(1);
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

		String json = TestUtil.toJson(elements);

		Map<Class<CustomDataClass>, CustomDataReader> readerMap = new HashMap<>(1);
		readerMap.put(CustomDataClass.class, new CustomDataReader());
        Object obj = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(readerMap).build(), null);
		assert obj instanceof List;
		List list = (List) obj;
		assert list.get(0) == list.get(1);
	}

	@Test
	public void testInSet()
	{
		Set<WithoutCustomReaderClass> set = new HashSet<>();

		CustomDataClass element = new CustomDataClass();
		element.setTest("hallo");

		WithoutCustomReaderClass e1 = new WithoutCustomReaderClass();
		e1.setCustomReaderInner(element);

		WithoutCustomReaderClass e2 = new WithoutCustomReaderClass();
		e2.setCustomReaderInner(element);

		set.add(e1);
		set.add(e2);

		String json = TestUtil.toJson(set);

		Map<Class<CustomDataClass>, CustomDataReader> readerMap = new HashMap<>(1);
		readerMap.put(CustomDataClass.class, new CustomDataReader());
        Object obj = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(readerMap).build(), null);
		assert obj instanceof Set;
		set = (Set) obj;
		assert set.size() == 2;
	}

	@Test
	public void testInArray()
	{
		CustomDataClass[] array = new CustomDataClass[2];
		CustomDataClass element = new CustomDataClass();
		element.setTest("hallo");

		array[0] = element;
		array[1] = element;

		String json = TestUtil.toJson(array);

		Map<Class<CustomDataClass>, CustomDataReader> readerMap = new HashMap<>(1);
		readerMap.put(CustomDataClass.class, new CustomDataReader());
        Object obj = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(readerMap).build(), null);
		assert obj instanceof CustomDataClass[];
		array = (CustomDataClass[]) obj;
		assert array.length == 2;
		assert array[0] == array[1];
	}

	public static class CustomDataReader implements JsonReader.JsonClassReader
	{
        public Object read(Object jOb, Resolver resolver)
		{
			CustomDataClass customClass = new CustomDataClass();
			customClass.setTest("blab");
			return customClass;
		}
	}
}
