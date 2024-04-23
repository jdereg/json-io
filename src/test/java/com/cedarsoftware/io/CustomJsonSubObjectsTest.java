package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.DeepEquals;
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
class CustomJsonSubObjectsTest
{
	static class Person {
		private String firstName;
		private String lastName;
		private String phoneNumber;
		private ZonedDateTime dob;
		private TestObjectKid[] kids;
		private Object[] friends;
		private List pets;
		private Map<String, Object> items;
	}

	/**
	 * Customer Reader for the Person object's local fields.
	 */
	class PersonReader implements JsonReader.JsonClassReader {
		public Object read(Object jsonObj, Resolver resolver) {
			Person person = new Person();
			Map<String, Object> map = (Map<String, Object>) jsonObj;
			person.firstName = (String) map.get("first");
			person.lastName = (String) map.get("last");
			person.phoneNumber = (String) map.get("phone");
			person.dob = ZonedDateTime.parse((String) map.get("dob"));

			// Typed[]
			// New instance is created and assigned to the field, but has not been resolved.
			JsonObject kids = (JsonObject) map.get("kids");
			person.kids = (TestObjectKid[])resolver.createInstance(kids);
			resolver.push(kids);	// Ask the resolver to finish the deep mapping work.
			
			// Object[]
			JsonObject array = new JsonObject();
			person.friends = (Object[]) map.get("friends");
			array.setTarget(person.friends);
			array.setJsonArray(person.friends);
			resolver.push(array);	// resolver - you do the rest of the mapping

			// List
			JsonObject pets = (JsonObject) map.get("pets");
			person.pets = (List)resolver.createInstance(pets);
			resolver.push(pets);	// thank you, resolver

			// Map
			JsonObject items = (JsonObject) map.get("items");
			person.items = (Map<String, Object>)resolver.createInstance(items);
			resolver.push(items);	// finish up the Map for me

			// Person's local fields are filled in here, and the resolver will continue processing the items on the
			// stack and fill in the "kids" substructure automatically.
			return person;
		}
	}

	class PersonWriter implements JsonWriter.JsonClassWriter {
		public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException {
			Person p = (Person) o;
			// Changing the field names on the Person class
			output.write("\"first\":\"");
			output.write(p.firstName);
			output.write("\",\"last\":\"");
			output.write(p.lastName);
			output.write("\",\"phone\":\"");
			output.write(p.phoneNumber);
			output.write("\"");
			output.write(",\"dob\":\"");
			output.write(p.dob.toString());
			output.write("\",\"kids\":");

			// Handles substructure, with unknown depth here.
			context.writeImpl(p.kids, true);
			output.write(",\"friends\":");
			context.writeImpl(p.friends, false);
			output.write(",\"pets\":");
			context.writeImpl(p.pets, true);
			output.write(",\"items\":");
			context.writeImpl(p.items, true);
		}
	}

	Person createPersonJohn() {
		Person john = new Person();
		john.firstName = "John";
		john.lastName = "Smith";
		john.phoneNumber = "213-555-1212";
		john.dob = ZonedDateTime.parse("1976-07-04T16:20:00-08:00");
		john.kids = new TestObjectKid[] {new TestObjectKid("Lisa", "lisa@gmail.com"), new TestObjectKid("Annie", "annie@gmail.com")};

		// Put a cycle in the object graph
		john.kids[0]._other = john.kids[1];
		john.kids[1]._other = john.kids[0];

		john.friends = new Object[] { new TestObjectKid("Ken", "ken@gmail.com"), new TestObjectKid("Josh", "josh@gmail.com") };
		john.pets = Arrays.asList(new TestObjectKid("Eddie", "eddie@gmail.com"), new TestObjectKid("Bella", "bella@gmail.com"));
		john.items = new CompactMap<>();
		john.items.put("laptop", "Apple Macbook Pro M3");
		john.items.put("phone", "Samsung Galaxy S20");
		john.items.put("bitcoin", 8.5);
		return john;
	}

	@Test
	void testCustomReaderSimple()
	{
		// You do not need to create all the aliases, I did that to make the JSON look cleaner.
		// I do recommend using .withExtendedAliases() as that will catch all the common Java types and Collections, Maps, etc.
		ReadOptionsBuilder readOptions = new ReadOptionsBuilder()
				.addCustomReaderClass(Person.class, new PersonReader())
				.aliasTypeName(Person.class, "Person")
				.aliasTypeName(TestObjectKid.class, "TestObjKid")
				.aliasTypeName(TestObjectKid[].class, "TestObjKid[]")
				.aliasTypeName(CompactMap.class, "CompactMap")
				.withExtendedAliases();

		WriteOptionsBuilder writeOptions = new WriteOptionsBuilder()
				.addCustomWrittenClass(Person.class, new PersonWriter())
				.aliasTypeName(Person.class, "Person")
				.aliasTypeName(TestObjectKid.class, "TestObjKid")
				.aliasTypeName(TestObjectKid[].class, "TestObjKid[]")
				.aliasTypeName(CompactMap.class, "CompactMap")
				.withExtendedAliases();

		// Note: You could use the ReadOptionsBuilder/WriteOptionsBuilder's static "addPermanent()" APIs instead of
		// calling the ReadOptions/WriteOptions each time for transfer (for some options). This will set them for
		// JVM lifecycle scope.
		//
		// Examples:
		//
		//   WriteOptionsBuilder.addPermanentWriter(Person.class, new PersonWriter());
		//   ReadOptionsBuilder.addPermanentReader(Person.class, new PersonReader());
		//
		//   WriteOptionsBuilder.addPermanentAlias(CompactMap.class, "CompactMap");
		//   ReadOptionsBuilder.addPermanentAlias(CompactMap.class, "CompactMap");

		Person p1 = createPersonJohn();
		String json = JsonIo.toJson(p1, writeOptions.build());	// default WriteOptions
		System.out.println(json);
		Person p2 = JsonIo.toObjects(json, readOptions.build(), Person.class);

		assert DeepEquals.deepEquals(p1, p2);
	}


	/* Here's the JSON that was Written/Read
	{
	  "@type": "Person",
	  "first": "John",
	  "last": "Smith",
	  "phone": "213-555-1212",
	  "dob": "1976-07-04T16:20-08:00",
	  "kids": {
		"@type": "TestObjKid[]",
		"@items": [
		  {
			"@id": 2,
			"_email": "lisa@gmail.com",
			"_name": "Lisa",
			"_other": {
			  "@id": 1,
			  "@type": "TestObjKid",
			  "_email": "annie@gmail.com",
			  "_name": "Annie",
			  "_other": {
				"@ref": 2
			  }
			}
		  },
		  {
			"@ref": 1
		  }
		]
	  },
	  "friends": [
		{
		  "@type": "TestObjKid",
		  "_email": "ken@gmail.com",
		  "_name": "Ken",
		  "_other": null
		},
		{
		  "@type": "TestObjKid",
		  "_email": "josh@gmail.com",
		  "_name": "Josh",
		  "_other": null
		}
	  ],
	  "pets": {
		"@type": "ArraysAsList",
		"@items": [
		  {
			"@type": "TestObjKid",
			"_email": "eddie@gmail.com",
			"_name": "Eddie",
			"_other": null
		  },
		  {
			"@type": "TestObjKid",
			"_email": "bella@gmail.com",
			"_name": "Bella",
			"_other": null
		  }
		]
	  },
	  "items": {
		"@type": "CompactMap",
		"laptop": "Apple Macbook Pro M3",
		"phone": "Samsung Galaxy S20",
		"bitcoin": 8.5
	  }
	}
	 */
}
