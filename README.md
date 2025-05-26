json-io
=======
[![Maven Central](https://badgen.net/maven/v/maven-central/com.cedarsoftware/json-io)](https://central.sonatype.com/search?q=json-io&namespace=com.cedarsoftware)
[![Javadoc](https://javadoc.io/badge/com.cedarsoftware/json-io.svg)](http://www.javadoc.io/doc/com.cedarsoftware/json-io)

json-io is a powerful and lightweight Java library that simplifies JSON serialization and deserialization while handling complex object graphs with ease. Unlike basic JSON parsers, json-io preserves object references, handles polymorphic types, and maintains cyclic relationships in your data structures. Whether you're working with sophisticated domain models, dealing with legacy Java objects, or need high-performance JSON processing, json-io provides a robust solution with minimal configuration.

Key Features:
- Preserves object references and handles cyclic relationships
- Supports polymorphic types and complex object graphs
- Zero external dependencies (other than java-util)
- Fully compatible with both JPMS and OSGi environments
- Lightweight (`json-io.jar` is 255K, `java-util` is 456K)
- Compatible with JDK 1.8 through JDK 24
- Extensive configuration options via `ReadOptionsBuilder` and `WriteOptionsBuilder`
- Featured on [json.org](http://json.org)
## Compatibility

### JPMS (Java Platform Module System)

This library is fully compatible with JPMS, commonly known as Java Modules. It includes a `module-info.class` file that
specifies module dependencies and exports.

### OSGi

This library also supports OSGi environments. It comes with pre-configured OSGi metadata in the `MANIFEST.MF` file, ensuring easy integration into any OSGi-based application.

Both of these features ensure that our library can be seamlessly integrated into modular Java applications, providing robust dependency management and encapsulation.

___
To include in your project:
##### Gradle
```groovy
implementation 'com.cedarsoftware:json-io:4.54.0'
```

##### Maven
```xml
 <dependency>
   <groupId>com.cedarsoftware</groupId>
   <artifactId>json-io</artifactId>
   <version>4.54.0</version>
 </dependency>
```
___

## User Guide
>#### [Usage](/user-guide.md)
>#### [WriteOptions reference](/user-guide-writeOptions.md)
>#### [ReadOptions reference](/user-guide-readOptions.md)
>#### [Revision History](/changelog.md)

## Releases
>### 4.54.0 (current)
>- [ ] **Version**: [4.54.0](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.54.0/index.html)
>- [ ] **Bundling**: Both JPMS (Java Platform Module System) and OSGi (Open Service Gateway initiative)
>- [ ] **Maintained**: Fully
>- [ ] **Java Package**: com.cedarsoftware.io
>- [ ] **Java**: JDK1.8+ (Class file 52 format, includes module-info.class - multi-release JAR)
>- [ ] **API**
>  - Static methods on [JsonIo](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.54.0/com/cedarsoftware/io/JsonIo.html): [toJson()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.54.0/com/cedarsoftware/io/JsonIo.html#toJson(java.lang.Object,com.cedarsoftware.io.WriteOptions)), [toJava()](https://www.javadoc.io/doc/com.cedarsoftware/json-io/latest/com/cedarsoftware/io/JsonIo.html#toJava(com.cedarsoftware.io.JsonObject,com.cedarsoftware.io.ReadOptions)), [formatJson()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.54.0/com/cedarsoftware/io/JsonIo.html#formatJson(java.lang.String)), [deepCopy()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.54.0/com/cedarsoftware/io/JsonIo.html#deepCopy(java.lang.Object,com.cedarsoftware.io.ReadOptions,com.cedarsoftware.io.WriteOptions))
>  - Use [ReadOptionsBuilder](/user-guide-readOptions.md) and [WriteOptionsBuilder](/user-guide-writeOptions.md) to configure `JsonIo`
>  - Use [JsonReader.ClassFactory](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.54.0/com/cedarsoftware/io/JsonReader.ClassFactory.html) for difficult classes (hard to instantiate & fill)
>  - Use [JsonWriter.JsonClassWriter](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.54.0/com/cedarsoftware/io/JsonWriter.JsonClassWriter.html) to customize the output JSON for a particular class
>- [ ] Updates will be 4.55.0, 4.56.0, ...
>### 4.14.x (supported)
>- [ ] **Version**: [4.14.3](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/index.html)
>- [ ] **Bundling**: Both JPMS (Java Platform Module System) and OSGi (Open Service Gateway initiative)
>- [ ] **Maintained**: Bug fixes, CVE's
>- [ ] **Java Package**: com.cedarsoftware.util.io
>- [ ] **Java**: JDK1.8+ (Class file 52 format, includes module-info.class - multi-release JAR)
>- [ ] **API**
>  - Static methods on [JsonReader](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonReader.html): [jsonToJava()](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonReader.html#jsonToJava-java.lang.String-java.util.Map-), [jsonToMaps()](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonReader.html#jsonToMaps-java.lang.String-java.util.Map-), special: [jsonObjectsToJava()](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonReader.html#jsonObjectsToJava-com.cedarsoftware.util.io.JsonObject-)
>  - Static methods on [JsonWriter](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonWriter.html): [objectToJson()](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonWriter.html#objectToJson-java.lang.Object-java.util.Map-)), [formatJson()](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonWriter.html#formatJson-java.lang.String-java.util.Map-java.util.Map-)
>  - Configuration via `Map` with constants defined in [JsonReader](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.14.3/constant-values.html#com.cedarsoftware.util.io.JsonReader.CLASSLOADER)/[JsonWriter](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.14.3/constant-values.html#com.cedarsoftware.util.io.JsonWriter.CLASSLOADER).
>  - Use [JsonReader.JsonClassReader](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonReader.JsonClassReader.html) or [JsonReader.JsonClassReaderEx](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonReader.JsonClassReaderEx.html) for customer reader
>  - Use [JsonReader.assignInstantiator()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonReader.html#assignInstantiator-java.lang.Class-com.cedarsoftware.util.io.JsonReader.Factory-) when `json-io` cannot construct a particular class
>  - Use [JsonWriter.JsonClassWriter](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonWriter.JsonClassWriter.html) or [JsonWriter.JsonClassWriterEx](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.14.3/com/cedarsoftware/util/io/JsonWriter.JsonClassWriterEx.html) to customize particular class output JSON
>- [ ] Updates will be 4.14.4, 4.14.5, ...

Featured on [json.org](http://json.org).

For useful Java utilities, check out [java-util](http://github.com/jdereg/java-util)
___
### License
```
Copyright (c) 2007 Cedar Software LLC.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

by John DeRegnaucourt and Kenny Partlow
