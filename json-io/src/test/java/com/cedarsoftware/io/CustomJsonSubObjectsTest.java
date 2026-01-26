package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.convert.Converter;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
	private static final Logger LOG = Logger.getLogger(CustomJsonSubObjectsTest.class.getName());

	static class Person {
		private String firstName;
		private String lastName;
		private String phoneNumber;
		private OffsetDateTime dob;
		private TestObjectKid[] kids;
		private Object[] friends;
		private List<TestObjectKid> pets;
		private Map<String, Object> items;
	}

	/**
	 * Custom Reader for the Person object's local fields.
	 */
    static class PersonFactory implements ClassFactory {
		@SuppressWarnings("unchecked")
		public Object newInstance(Class<?> c, JsonObject jsonObj, Resolver resolver) {
			Person person = new Person();		// Factory - create Java peer instance - root class only.

			// Use Resolver convenience methods for cleaner code
			person.firstName = resolver.readString(jsonObj, "first");
			person.lastName = resolver.readString(jsonObj, "last");
			person.phoneNumber = resolver.readString(jsonObj, "phone");
			person.dob = resolver.readObject(jsonObj, "dob", OffsetDateTime.class);

			// Handle complex field types using Resolver convenience methods
			person.kids = resolver.readArray(jsonObj, "kids", TestObjectKid[].class);
			person.friends = resolver.readArray(jsonObj, "friends", Object[].class);
			person.pets = resolver.readList(jsonObj, "pets");
			person.items = resolver.readMap(jsonObj, "items");
			return person;
		}
	}

	static class PersonWriter implements JsonClassWriter {
		public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException {
			Person p = (Person) o;
			// Using new WriterContext semantic API - cleaner, safer, automatic formatting
			// First field: no leading comma
			context.writeFieldName("first");
			context.writeValue(p.firstName);
			// Subsequent fields: include leading comma
			context.writeStringField("last", p.lastName);
			context.writeStringField("phone", p.phoneNumber);
			context.writeStringField("dob", p.dob.toString());

			// writeObjectField handles complex types (arrays, lists, maps) with full serialization
			// Automatically handles cycles, references, and nested structures
			context.writeObjectField("kids", p.kids);
			context.writeObjectField("friends", p.friends);
			context.writeObjectField("pets", p.pets);
			context.writeObjectField("items", p.items);
		}
	}

	Person createPersonJohn() {
		Person john = new Person();
		john.firstName = "John";
		john.lastName = "Smith";
		john.phoneNumber = "213-555-1212";
		john.dob = OffsetDateTime.parse("1976-07-04T16:20:00-08:00");
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
	void testCustomReaderSimpleJavaMode()
	{
		// You do not need to create all the aliases, I did that to make the JSON look cleaner.
		ReadOptionsBuilder readOptions = new ReadOptionsBuilder()
				.addClassFactory(Person.class, new PersonFactory())
				.aliasTypeName(Person.class, "Person")
				.aliasTypeName(TestObjectKid.class, "TestObjKid")
				.aliasTypeName(TestObjectKid[].class, "TestObjKid[]");

		WriteOptionsBuilder writeOptions = new WriteOptionsBuilder()
				.addCustomWrittenClass(Person.class, new PersonWriter())
				.aliasTypeName(Person.class, "Person")
				.aliasTypeName(TestObjectKid.class, "TestObjKid")
				.aliasTypeName(TestObjectKid[].class, "TestObjKid[]");

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
		String json = JsonIo.toJson(p1, writeOptions.build());
		Person p2 = JsonIo.toJava(json, readOptions.build()).asClass(Person.class);

		Map options = new HashMap<>();
		boolean equals = DeepEquals.deepEquals(p1, p2, options);
		if (!equals) {
			LOG.fine(options.get("diff").toString());
		}
		assertTrue(equals);
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
