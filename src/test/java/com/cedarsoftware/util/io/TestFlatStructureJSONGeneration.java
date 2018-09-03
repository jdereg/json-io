package com.cedarsoftware.util.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;



/**
 * @author Jeremie Ratomposon (jratompo+jsonio@gmail.com) <br>
 *         Copyright (c) Alqcodex <br>
 * <br>
 *         Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except in compliance
 *         with the License. You may obtain a copy of the License at <br>
 * <br>
 *         http://www.apache.org/licenses/LICENSE-2.0 <br>
 * <br>
 *         Unless required by applicable law or agreed to in writing, software distributed under the License is
 *         distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *         the License for the specific language governing permissions and limitations under the License.
 */


public class TestFlatStructureJSONGeneration {


	private static class FooClass {

		private int attrInt = 0;
		private String attrString = "";
		private FooClass other = null;

		FooClass(int attrInt, String attrString) {
			this.attrInt = attrInt;
			this.attrString = attrString;
		}

		public void setOther(FooClass other) {
			this.other = other;
		}
	}

	private static class FooClassWithCollection {

		private int attrInt = 0;
		private String attrString = "";
		private Collection<FooClassWithCollection> others = null;

		FooClassWithCollection(int attrInt, String attrString, Collection<FooClassWithCollection> others) {
			this.attrInt = attrInt;
			this.attrString = attrString;
			this.others = others;
		}

		public void addOther(FooClassWithCollection other) {
			this.others.add(other);
		}
	}

	// =========================================

	@Test
	public void testCycleFlatMapJSONWritingAndBack() {

		FooClass foo1 = new FooClass(11, "gru chaine 1");
		FooClass foo2 = new FooClass(12, "gru chaine 2");
		FooClass foo3 = new FooClass(13, "gru chaine 3");

		foo1.setOther(foo2);
		foo2.setOther(foo3);
		// We introduce a cycle :
		foo3.setOther(foo1);

		// -------------

		// We compare the two objects pairs using the normal mode JSON generation :
		String foo1JSONStr = FlatMapTestUtils.getAsFlatJSON(foo1);
		Object foo1Bis = FlatMapTestUtils.getAsTreeStructure(foo1JSONStr);

		FlatMapTestUtils.assertEqualsWithNormalJSONWriting(foo1, foo1Bis);

		// -------------

		// We compare the two objects pairs using the normal mode JSON generation :
		String foo2JSONStr = FlatMapTestUtils.getAsFlatJSON(foo2);
		Object foo2Bis = FlatMapTestUtils.getAsTreeStructure(foo2JSONStr);

		FlatMapTestUtils.assertEqualsWithNormalJSONWriting(foo2, foo2Bis);

		// -------------

		// We compare the two objects pairs using the normal mode JSON generation :

		String foo3JSONStr = FlatMapTestUtils.getAsFlatJSON(foo3);
		Object foo3Bis = FlatMapTestUtils.getAsTreeStructure(foo3JSONStr);

		FlatMapTestUtils.assertEqualsWithNormalJSONWriting(foo3, foo3Bis);

	}


	@Test
	public void testCycleWithArrayFlatMapJSONWritingAndBack() {

		FooClassWithCollection foo1 = new FooClassWithCollection(11, "gru chaine 1", new ArrayList<>());
		FooClassWithCollection foo2 = new FooClassWithCollection(12, "gru chaine 2", new ArrayList<>());
		FooClassWithCollection foo3 = new FooClassWithCollection(13, "gru chaine 3", new ArrayList<>());

		foo1.addOther(foo2);
		foo2.addOther(foo3);
		// We introduce a cycle :
		foo3.addOther(foo1);

		// -------------

		// We compare the two objects pairs using the normal mode JSON generation :
		String foo1JSONStr = FlatMapTestUtils.getAsFlatJSON(foo1);
		Object foo1Bis = FlatMapTestUtils.getAsTreeStructure(foo1JSONStr);

		FlatMapTestUtils.assertEqualsWithNormalJSONWriting(foo1, foo1Bis);

		// -------------

		// We compare the two objects pairs using the normal mode JSON generation :
		String foo2JSONStr = FlatMapTestUtils.getAsFlatJSON(foo2);
		Object foo2Bis = FlatMapTestUtils.getAsTreeStructure(foo2JSONStr);

		FlatMapTestUtils.assertEqualsWithNormalJSONWriting(foo2, foo2Bis);

		// -------------

		// We compare the two objects pairs using the normal mode JSON generation :

		String foo3JSONStr = FlatMapTestUtils.getAsFlatJSON(foo3);
		Object foo3Bis = FlatMapTestUtils.getAsTreeStructure(foo3JSONStr);

		FlatMapTestUtils.assertEqualsWithNormalJSONWriting(foo3, foo3Bis);

	}

	// =========================================

	private static class FlatMapTestUtils {

		static void assertEqualsWithNormalJSONWriting(Object object, Object objectBis) {
			String objectJSONVerification = getAsJSON(object);
			String objectBisJSONVerification = getAsJSON(objectBis);
			assert objectJSONVerification.equals(objectBisJSONVerification);
		}

		static String getAsJSON(Object rawObject) {

			Map<String, Object> params = new HashMap<>();

			params.put(JsonWriter.TYPE, true);
			params.put(JsonWriter.FLAT_STRUCTURE, true);
			String jsonStr = JsonWriter.objectToJson(rawObject, params);

			return jsonStr;
		}

		static String getAsFlatJSON(Object rawObject) {

			Map<String, Object> params = new HashMap<>();

			params.put(JsonWriter.TYPE, true);
			params.put(JsonWriter.FLAT_STRUCTURE, true);
			String jsonStr = JsonWriter.objectToJson(rawObject, params);

			return jsonStr;
		}

		static Object getAsTreeStructure(String oldMapString) {
			Map<Comparable, Object> result = (Map<Comparable, Object>) JsonReader.jsonToJava(oldMapString);
			// First object is the root object :
			return result.entrySet().iterator().next().getValue();
		}

	}

}
