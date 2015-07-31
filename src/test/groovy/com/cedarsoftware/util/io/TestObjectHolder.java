package com.cedarsoftware.util.io;

import org.junit.Test;

import com.cedarsoftware.util.io.Writers.JsonStringWriter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

public class TestObjectHolder {

	@Test
	public void simpleTest(){
		ObjectHolder holder = new ObjectHolder("bool",true);
		String json = JsonWriter.objectToJson(holder);
		ObjectHolder deserialized = (ObjectHolder) JsonReader.jsonToJava(json);
		
		assertTrue(holder.equals(deserialized));
	}
	
	@Test
	public void testWithoutMetaData(){
		ObjectHolder boolHolder = new ObjectHolder("bool",true);
		ObjectHolder stringHolder = new ObjectHolder("string","true");
		ObjectHolder intHolder = new ObjectHolder("int",123l); //convenience for test
		//Arrrays will be created as Object[] arrays, as Javascript allows non uniform arrays. In deserialization process this could be checked, too.
		
		
		
		testSerialization(boolHolder);
		testSerialization(stringHolder);
		testSerialization(intHolder);
		
	}
	
	private void testSerialization(ObjectHolder holder){
		Map<String, Object> serialParams = new HashMap<String, Object>();
		serialParams.put(JsonWriter.TYPE, false);
		Map<String, Object> deSerialParams = new HashMap<String, Object>();
		deSerialParams.put(JsonReader.UNKNOWN_OBJECT, ObjectHolder.class.getName());
		
		String json = JsonWriter.objectToJson(holder, serialParams);
		ObjectHolder deserialized = (ObjectHolder)JsonReader.jsonToJava(json, deSerialParams);
		assertTrue(holder.equals(deserialized));	
	}
	
}
