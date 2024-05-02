package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.util.Map;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.DeepEquals.deepEquals;

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
class CustomJsonTest
{
	static class Person {
		private String firstName;
		private String lastName;
		private String phoneNumber;
		private OffsetDateTime dob;
	}

    static class PersonFactory implements JsonReader.ClassFactory {
		public Object newInstance(Class<?> c, JsonObject jsonObject, Resolver resolver) {
			Person person = new Person();		// Factory - create Java peer instance - root class only.
			Map<String, Object> map = (Map) jsonObject;
			Converter converter = resolver.getConverter();

			person.firstName = converter.convert(map.get("first"), String.class);
			person.lastName = converter.convert(map.get("last"), String.class);
			person.phoneNumber = converter.convert(map.get("phone"), String.class);
			person.dob = converter.convert(map.get("dob"), OffsetDateTime.class);
			return person;
		}
	}

	class PersonWriter implements JsonWriter.JsonClassWriter {
		public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException {
			Person p = (Person) o;
			output.write("\"first\":\"");
			output.write(p.firstName);
			output.write("\",\"last\":\"");
			output.write(p.lastName);
			output.write("\",\"phone\":\"");
			output.write(p.phoneNumber);
			output.write("\",\"dob\":\"");
			output.write(p.dob.toString());
			output.write("\"");
		}
	}

	Person createPersonJohn() {
		Person john = new Person();
		john.firstName = "John";
		john.lastName = "Smith";
		john.phoneNumber = "213-555-1212";
		john.dob = OffsetDateTime.parse("1976-07-04T16:20:00-08:00");
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
		Person p2 = JsonIo.toObjects(json, readOptions.build(), Person.class);

		assert deepEquals(p1, p2);
	}
}
