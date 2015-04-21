package com.cedarsoftware.util.io

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.cedarsoftware.util.io.JsonReader.JsonClassReader;

class TestCustomReaderIdentity {
	@BeforeClass
	public static void setUp(){
		
		JsonReader.addReader(CustomReaderClass.class,  new JsonClassReader() {
			
			@Override
			public Object read(Object jOb, Deque<JsonObject<String, Object>> stack) {
				CustomReaderClass customClass = new CustomReaderClass();
				customClass.setTest("blab");
				
				return customClass;
			}
		});
		
	}
	
	
	/**
	 * This test uses a customReader to read two identical instances in a collection.
	 * A customWriter is not necessary.
	 */
	@Test
	public void testCustomReaderSerialization(){
		
		List<CustomReaderClass> elements = new LinkedList<>();
		CustomReaderClass element = new CustomReaderClass();
		element.setTest("hallo");
		
		elements.add(element);
		elements.add(element);
		
		
		String json = JsonWriter.objectToJson(elements);
		
		System.out.println(json);
		
		Object obj = JsonReader.jsonToJava(json);
		
	}
	
	/**
	 * This test does not use a customReader to read two identical instances in a collection.
	 * A customWriter is not necessary.
	 */
	@Test
	public void testSerializationOld(){
		List<WithoutCustomReaderClass> elements = new LinkedList<>();
		WithoutCustomReaderClass element = new WithoutCustomReaderClass();
		element.setTest("hallo");
		
		elements.add(element);
		elements.add(element);
		
		
		String json = JsonWriter.objectToJson(elements);
		
		System.out.println(json);
		
		Object obj = JsonReader.jsonToJava(json);
	}
}
