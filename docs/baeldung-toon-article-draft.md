# Introduction to TOON Format in Java

## 1. Overview

[JSON](https://www.baeldung.com/java-json) is the standard format for exchanging structured data between systems. But when we send JSON to [large language models (LLMs)](https://www.baeldung.com/cs/llm-cost), a surprising amount of the token budget goes toward syntax, including braces, brackets, quotes, and repeated key names rather than actual data.

TOON (Token-Optimized Object Notation) is a compact, human-readable format that encodes the same data model as JSON while using significantly fewer tokens. It replaces JSON's punctuation-heavy syntax with indentation and, for uniform collections, a tabular layout that states field names once and streams values row by row.

In this tutorial, we'll survey the available Java TOON libraries, use *json-io* for working examples of serializing and deserializing TOON, compare token counts between the formats, and discuss where TOON delivers the most value.

## 2. What Is TOON?

TOON encodes the same primitives, objects, and arrays as JSON. The differences are syntactic:

- **Objects** use `key: value` pairs separated by newlines and indentation, not braces
- **Arrays** use `[N]:` length-prefixed lists instead of brackets
- **Tabular arrays** declare field names once in a header row, then list values in CSV-like rows
- **Quoting** is minimal — strings only need quotes when they contain structural characters

Here is the same data in JSON and TOON:

**JSON:**
```json
[
  {"name": "Alice", "age": 28, "department": "Engineering"},
  {"name": "Bob", "age": 34, "department": "Marketing"},
  {"name": "Charlie", "age": 22, "department": "Sales"}
]
```

**TOON (tabular):**
```
[3]{name,age,department}:
  Alice,28,Engineering
  Bob,34,Marketing
  Charlie,22,Sales
```

The tabular format states `name`, `age`, and `department` once in the header. Each subsequent row contains only the values. As the number of rows grows, the savings accumulate because JSON repeats every key name on every object.

For a single object, TOON uses a [YAML-like](https://www.baeldung.com/java-snake-yaml) key-value layout:

```
name: Alice
age: 28
department: Engineering
```

The full specification lives at [toonformat.dev](https://toonformat.dev).

## 3. Java Libraries for TOON

The [TOON ecosystem](https://toonformat.dev/ecosystem/implementations) includes implementations in over 25 languages. For Java, there are two libraries available today:

| Library | Maven Coordinates                  | Java Version | Status | Notes |
|---------|------------------------------------|-------------|--------|-------|
| [JToon](https://github.com/toon-format/toon-java) | `dev.toonformat:jtoon:1.0.8`       | Java 17+ | Beta | Official community implementation. [Jackson](https://www.baeldung.com/jackson) annotation support. Focused on spec compliance. |
| [json-io](https://github.com/jdereg/json-io) | `com.cedarsoftware:json-io:4.97.0` | Java 8+ | Stable | Mature JSON library with TOON read/write. Jackson annotation support. Single runtime dependency (*java-util*). |

Both libraries support TOON encoding and decoding with tabular arrays. JToon is the official community Java implementation, built specifically for TOON with Jackson integration. *json-io* is a mature JSON serialization library that added TOON as an additional output format alongside JSON.

We can find the latest version of [*json-io* on Maven Central](https://mvnrepository.com/artifact/com.cedarsoftware/json-io). For the examples in this tutorial, we'll use *json-io* because it supports Java 8 through 24, allowing the widest range of projects to follow along:

```xml
<dependency>
    <groupId>com.cedarsoftware</groupId>
    <artifactId>json-io</artifactId>
    <version>4.97.0</version>
</dependency>
```

## 4. Writing Java Objects to TOON

### 4.1. Single Objects

We can convert any Java object to TOON with *JsonIo.toToon()*:

```java
Person person = new Person("Alice", 28, "Engineering");

String toon = JsonIo.toToon(person, null);
```

This produces:

```
name: Alice
age: 28
department: Engineering
```

The second parameter accepts a *WriteOptions* instance for controlling output. Passing `null` uses defaults.

### 4.2. Collections: The Tabular Format

The tabular format is where TOON delivers its biggest advantage. When we serialize a [list](https://www.baeldung.com/java-arrays-aslist-vs-list-of) of objects that share the same fields, *json-io* automatically uses the compact row-per-object layout:

```java
List<Employee> employees = Arrays.asList(
    new Employee("Alice Johnson", 28, "Engineering", 95000),
    new Employee("Bob Smith", 34, "Marketing", 78000),
    new Employee("Charlie Brown", 22, "Engineering", 72000)
);

String toon = JsonIo.toToon(employees, null);
```

Output:

```
[3]{name,age,department,salary}:
  Alice Johnson,28,Engineering,95000
  Bob Smith,34,Marketing,78000
  Charlie Brown,22,Engineering,72000
```

The `[3]` declares the array length. The `{name,age,department,salary}` header names the columns. Each row contains only comma-separated values. The key names `name`, `age`, `department`, and `salary` appear once rather than three times.

If we prefer the expanded list format, one key-value pair per line, we can enable *prettyPrint* using the [builder pattern](https://www.baeldung.com/java-builder-pattern):

```java
WriteOptions options = new WriteOptionsBuilder().prettyPrint(true).build();
String toon = JsonIo.toToon(employees, options);
```

This produces the verbose form:

```
[3]:
  - name: Alice Johnson
    age: 28
    department: Engineering
    salary: 95000
  - name: Bob Smith
    age: 34
    department: Marketing
    salary: 78000
  - name: Charlie Brown
    age: 22
    department: Engineering
    salary: 72000
```

The default tabular layout is preferred for LLM interactions because it minimizes tokens. The expanded *prettyPrint* form repeats every key name on every object, so **it uses roughly the same number of tokens as JSON** and the savings disappear.

### 4.3. Nested Structures

TOON handles nesting naturally through indentation. Here, a company contains departments stored in a [*LinkedHashMap*](https://www.baeldung.com/java-linked-hashmap), each with a list of members:

```java
Map<String, Object> eng = new LinkedHashMap<>();
eng.put("name", "Engineering");
eng.put("members", Arrays.asList(
    new Person("Alice", 28, "Engineering"),
    new Person("Bob", 34, "Engineering"),
    new Person("Charlie", 22, "Engineering")));

Map<String, Object> company = new LinkedHashMap<>();
company.put("name", "Acme Corp");
company.put("founded", 2010);
company.put("departments", Arrays.asList(eng, marketing, sales));  // marketing and sales built similarly

String toon = JsonIo.toToon(company, null);
```

Output:

```
name: Acme Corp
founded: 2010
departments[3]:
  - name: Engineering
    members[3]{name,age,department}:
      Alice,28,Engineering
      Bob,34,Engineering
      Charlie,22,Engineering
  - name: Marketing
    members[3]{name,age,department}:
      Eve,29,Marketing
      Frank,45,Marketing
      Grace,27,Marketing
  - name: Sales
    members[3]{name,age,department}:
      Hank,38,Sales
      Iris,33,Sales
      Jack,41,Sales
```

Notice that the inner `members` arrays use tabular format automatically. The outer `departments` array uses the expanded format because each department contains nested structured data, while the inner `members` arrays qualify for tabular format since their fields are all scalar values.

*json-io* applies this optimization at every level of the document: any array whose elements contain only scalar fields is automatically rendered in tabular format, regardless of nesting depth. In this example, each of the three `members` arrays gets its own column header and compact rows. This mix of key-value pairs and tabular rows within a single document is something CSV cannot express.

## 5. Reading TOON Back to Java

Parsing TOON is the mirror of writing it. We can deserialize to typed Java objects:

```java
String toon = "name: Alice\nage: 28\ndepartment: Engineering";

Person person = JsonIo.fromToon(toon, null).asClass(Person.class);

assertEquals("Alice", person.getName());
assertEquals(28, person.getAge());
assertEquals("Engineering", person.getDepartment());
```

For generic collection types, we use *TypeHolder* to work around [type erasure](https://www.baeldung.com/java-super-type-tokens):

```java
String toon = "[2]{name,age,department}:\n  Alice,28,Engineering\n  Bob,34,Marketing";

List<Person> people = JsonIo.fromToon(toon, null)
    .asType(new TypeHolder<List<Person>>(){});

assertEquals(2, people.size());
assertEquals("Alice", people.get(0).getName());
assertEquals("Engineering", people.get(0).getDepartment());
```

When we don't have Java classes available, for example in middleware or log processing, we can parse to [Maps](https://www.baeldung.com/java-hashmap):

```java
Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
```

## 6. Token Efficiency: Measuring the Difference

The motivation behind TOON is reducing token consumption when structured data is sent to LLMs. Rather than comparing character counts, which would be misleading since the formats have fundamentally different syntax density, we measure actual [BPE (Byte Pair Encoding)](https://www.baeldung.com/cs/gpt-tokenization) tokens using the same tokenizer that GPT-4 uses.

We use *jtokkit*, a Java implementation of OpenAI's tokenizer, with the `cl100k_base` encoding:

```java
EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);

int jsonTokens = encoding.countTokens(jsonString);
int toonTokens = encoding.countTokens(toonString);
```

Here are the results across several dataset sizes, serializing uniform lists of *Employee* objects (name, age, department, salary):

| Dataset | JSON Tokens | TOON Tokens | Savings |
|---------|------------|------------|---------|
| 3 items | 46 | 35 | 24% |
| 10 items | 206 | 121 | 41% |
| 20 items | 413 | 231 | 44% |
| 25 items (products, 5 fields) | 680 | 459 | 33% |

The savings grow with collection size because JSON repeats every key name on every object, while TOON states them once. At 20 items with 4 fields, we save 44% of the tokens.

For a single object, the savings are modest (about 5%) since there is no key repetition to eliminate. For nested structures with small embedded arrays, the savings are also modest (about 3-5%) but the inner arrays still benefit from tabular format.

### 6.1. How Does TOON Compare to CSV?

A fair question: if we're sending flat tabular data, why not just use [CSV](https://www.baeldung.com/java-csv)?

For purely flat data, CSV uses slightly fewer tokens than TOON: about 6% fewer in our benchmarks. The overhead comes from TOON's `[N]{fields}:` header and two-space indentation on each row.

But CSV cannot represent nested data. When we tried encoding the company-with-departments structure as CSV, we had to denormalize, repeating "Acme Corp" and "2010" on every row, which used *more* tokens than both TOON and JSON.

| Format | Flat Data (20 rows) | Nested Data |
|--------|-------------------|-------------|
| JSON | 413 tokens | 148 tokens |
| TOON | 231 tokens | 140 tokens |
| CSV | 218 tokens | 167 tokens (denormalized) |

TOON occupies a practical middle ground: within 6% of CSV's token efficiency for flat data, but it also handles the nesting and mixed structures that real-world APIs produce.

In agentic and tool-calling workflows, structured data often accounts for the majority of input tokens. A 30-40% reduction on that portion means **lower API costs** (providers charge per token) and a **more effective context window**, fitting more data into the same 128K or 200K window without truncation. A Java application using *json-io* can switch from *JsonIo.toJson()* to *JsonIo.toToon()* without any infrastructure changes.

## 7. Spring AI Integration

For projects using Spring AI, *json-io* provides a separate starter that automatically converts tool call results to TOON before they reach the LLM. We add one dependency:

```xml
<dependency>
    <groupId>com.cedarsoftware</groupId>
    <artifactId>json-io-spring-ai-toon</artifactId>
    <version>4.97.0</version>
</dependency>
```

Then we annotate any tool method with the *ToonToolCallResultConverter*:

```java
@Tool(description = "Look up employees by department",
      resultConverter = ToonToolCallResultConverter.class)
List<Employee> findByDepartment(String department) {
    return employeeRepository.findByDepartment(department);
}
```

When Spring AI calls this tool and passes the result back to the LLM, the converter serializes the employee list to TOON's tabular format, the same compact layout we saw in Section 4.2. The LLM receives fewer tokens without any changes to the tool's business logic.

## 8. When to Use TOON

TOON isn't a universal replacement for JSON. Here's where it fits:

**Use TOON when:**
- Sending structured data to LLMs (tool results, RAG payloads, data analysis contexts)
- Working with collections of uniform objects (the tabular format's sweet spot)
- Token cost or context window pressure is a concern
- Data has a mix of flat and nested structure

**Stick with JSON when:**
- Communicating between services (REST APIs, message queues) where every consumer expects JSON
- The data consumer is not an LLM
- Schema validation or JSON Schema tooling is required
- The data is purely flat with no nesting. CSV may be even more efficient.

TOON encodes the same data model as JSON, and *json-io* can write any object graph to either format. JSON's advantage is its ecosystem: virtually every language, framework, and tool speaks JSON natively, and standards like JSON Schema provide validation infrastructure that TOON does not yet have. Where that ecosystem matters, REST APIs, message queues, configuration files, JSON is the natural choice. Where token efficiency matters: LLM tool results, RAG payloads, agentic workflows, TOON delivers measurable savings with no loss of expressiveness.

## 9. Conclusion

In this article, we surveyed the Java TOON libraries available today: JToon and *json-io*, and used *json-io* to serialize Java objects to TOON, measure the token savings against JSON using OpenAI's BPE tokenizer, and integrate TOON into Spring AI tool calls. For collections of uniform objects, TOON's tabular format delivers 30-44% fewer tokens compared to clean JSON. It achieves near-CSV efficiency for flat data while also handling the nested structures that CSV cannot represent.

The complete code examples from this article are available [over on GitHub](https://github.com/eugenp/tutorials).
