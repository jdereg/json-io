package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.convert.Converter;
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
class CustomJsonSubObjectTest
{
	static class Person {
		private String firstName;
		private String lastName;
		private String phoneNumber;
		private OffsetDateTime dob;
		private TestObjectKid kid;
	}

	static class PersonFactory implements ClassFactory {
		public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
			Person person = new Person();		// Factory - create Java peer instance - root class only.

			// Use Resolver convenience methods for cleaner code
			person.firstName = resolver.readString(jObj, "first");
			person.lastName = resolver.readString(jObj, "last");
			person.phoneNumber = resolver.readString(jObj, "phone");
			person.dob = resolver.readObject(jObj, "dob", OffsetDateTime.class);

			// Handle complex field types using Resolver convenience method
			person.kid = resolver.readObject(jObj, "kid", TestObjectKid.class);
			return person;
		}
	}

	static class PersonWriter implements JsonClassWriter {
		public void write(Object o, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
			Person p = (Person) o;
			gen.writeStringField("first", p.firstName);
			gen.writeStringField("last",  p.lastName);
			gen.writeStringField("phone", p.phoneNumber);
			gen.writeStringField("dob",   p.dob.toString());
			// writeObjectField delegates to JsonIo for full sub-object serialization (cycles, custom writers, $type)
			gen.writeObjectField("kid", p.kid);
		}
	}

	Person createPersonJohn() {
		Person john = new Person();
		john.firstName = "John";
		john.lastName = "Smith";
		john.phoneNumber = "213-555-1212";
		john.dob = OffsetDateTime.parse("1976-07-04T16:20:00-08:00");
		john.kid = new TestObjectKid("Lisa", "lisa@gmail.com");
		return john;
	}

	@Test
	void testCustomReaderSimple()
	{
		ReadOptionsBuilder readOptions = new ReadOptionsBuilder()
				.addClassFactory(Person.class, new PersonFactory())
				.aliasTypeName(Person.class, "Person");

		WriteOptionsBuilder writeOptions = new WriteOptionsBuilder()
				.addCustomWrittenClass(Person.class, new PersonWriter())
				.aliasTypeName(Person.class, "Person");

		Person p1 = createPersonJohn();
		String json = JsonIo.toJson(p1, writeOptions.build());
		Person p2 = JsonIo.toJava(json, readOptions.build()).asClass(Person.class);

		assert DeepEquals.deepEquals(p1, p2);
	}
}
