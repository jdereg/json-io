package com.cedarsoftware.io.spring.ai.converter;

import java.util.List;

import com.cedarsoftware.io.TypeHolder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToonBeanOutputConverter}.
 */
class ToonBeanOutputConverterTest {

    // --- Simple POJO for testing ---
    public static class Person {
        public String name;
        public int age;

        public Person() {}

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    public static class Address {
        public String street;
        public String city;

        public Address() {}
    }

    public static class PersonWithAddress {
        public String name;
        public Address address;

        public PersonWithAddress() {}
    }

    @Test
    void getFormatReturnsToonInstructions() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        String format = converter.getFormat();

        assertThat(format).contains("TOON");
        assertThat(format).contains("key: value");
        assertThat(format).contains("indentation");
    }

    @Test
    void convertParsesToonToPojo() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        String toon = """
                name: John Smith
                age: 30""";

        Person person = converter.convert(toon);

        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("John Smith");
        assertThat(person.age).isEqualTo(30);
    }

    @Test
    void convertReturnsNullForNull() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        assertThat(converter.convert(null)).isNull();
    }

    @Test
    void convertReturnsNullForBlank() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        assertThat(converter.convert("")).isNull();
        assertThat(converter.convert("   ")).isNull();
    }

    @Test
    void convertStripsCodeFences() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        String toonWithFences = """
                ```toon
                name: Jane Doe
                age: 25
                ```""";

        Person person = converter.convert(toonWithFences);

        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("Jane Doe");
        assertThat(person.age).isEqualTo(25);
    }

    @Test
    void convertStripsGenericCodeFences() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        String toonWithFences = """
                ```
                name: Bob
                age: 40
                ```""";

        Person person = converter.convert(toonWithFences);

        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("Bob");
        assertThat(person.age).isEqualTo(40);
    }

    @Test
    void convertHandlesNestedObjects() {
        ToonBeanOutputConverter<PersonWithAddress> converter = new ToonBeanOutputConverter<>(PersonWithAddress.class);
        String toon = """
                name: Alice
                address:
                  street: 123 Main St
                  city: Springfield""";

        PersonWithAddress person = converter.convert(toon);

        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("Alice");
        assertThat(person.address).isNotNull();
        assertThat(person.address.street).isEqualTo("123 Main St");
        assertThat(person.address.city).isEqualTo("Springfield");
    }

    @Test
    void convertHandlesGenericTypeWithTypeHolder() {
        ToonBeanOutputConverter<List<Person>> converter =
                new ToonBeanOutputConverter<>(new TypeHolder<List<Person>>() {});
        // json-io's TOON folded array format: header row with keys, then CSV-like data rows
        String toon = """
                [2]{name,age}:
                  Alice,30
                  Bob,25""";

        List<Person> people = converter.convert(toon);

        assertThat(people).isNotNull();
        assertThat(people).hasSize(2);
        assertThat(people.get(0).name).isEqualTo("Alice");
        assertThat(people.get(0).age).isEqualTo(30);
        assertThat(people.get(1).name).isEqualTo("Bob");
        assertThat(people.get(1).age).isEqualTo(25);
    }

    @Test
    void convertHandlesListFormatFromFormatInstructions() {
        // This tests the hyphen-prefixed list format that getFormat() teaches the LLM to produce
        ToonBeanOutputConverter<List<Person>> converter =
                new ToonBeanOutputConverter<>(new TypeHolder<List<Person>>() {});
        String toon = """
                [2]:
                  - name: Alice
                    age: 30
                  - name: Bob
                    age: 25""";

        List<Person> people = converter.convert(toon);

        assertThat(people).isNotNull();
        assertThat(people).hasSize(2);
        assertThat(people.get(0).name).isEqualTo("Alice");
        assertThat(people.get(0).age).isEqualTo(30);
        assertThat(people.get(1).name).isEqualTo("Bob");
        assertThat(people.get(1).age).isEqualTo(25);
    }

    @Test
    void getFormatInstructionsDescribeHyphenPrefixedArrayElements() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        String format = converter.getFormat();

        // Format instructions must teach the list format that json-io can parse
        assertThat(format).contains("- name: Item A");
        assertThat(format).contains("- name: Item B");
    }

    @Test
    void convertHandlesTextWithLeadingTrailingWhitespace() {
        ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
        String toon = """

                name: Chris
                age: 35

                """;

        Person person = converter.convert(toon);

        assertThat(person).isNotNull();
        assertThat(person.name).isEqualTo("Chris");
        assertThat(person.age).isEqualTo(35);
    }
}
