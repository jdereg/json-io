json-io
=======
<!--[![Build Status](https://travis-ci.org/jdereg/json-io.svg?branch=master)](https://travis-ci.org/jdereg/json-io) -->
[![Maven Central](https://badgen.net/maven/v/maven-central/com.cedarsoftware/json-io)](https://central.sonatype.com/search?q=json-io&namespace=com.cedarsoftware)
[![Javadoc](https://javadoc.io/badge/com.cedarsoftware/json-io.svg)](http://www.javadoc.io/doc/com.cedarsoftware/json-io)

Useful tool for Java serialization to and from JSON format.
Available on [Maven Central](https://central.sonatype.com/search?q=json-io&namespace=com.cedarsoftware).
This library has <b>no dependencies</b> on other libraries for runtime other than our own `java-util.`
The `json-io.jar`file is only`185K` and `java-util` is `260K.` Compatible with JDK1.8 through JDK 22.
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
```
implementation 'com.cedarsoftware:json-io:4.22.0'
```

##### Maven
```
    <dependency>
      <groupId>com.cedarsoftware</groupId>
      <artifactId>json-io</artifactId>
      <version>4.22.0</version>
    </dependency>
```
___

## User Guide
>#### [Usage](/user-guide.md)
>#### [WriteOptions reference](/user-guide-writeOptions.md)
>#### [ReadOptions reference](/user-guide-readOptions.md)
>#### [Revision History](/changelog.md)

## Releases
>### 5.0.0 (future)
>- [ ] **Bundling**: Both JPMS (Java Platform Module System) and OSGi (Open Service Gateway initiative)
>- [ ] **Maintained**: Fully
>- [ ] **Java Package**: com.cedarsoftware.io
>- [ ] **Java**: JDK17+ (Class file 52 format, includes module-info.class - multi-release JAR)
>- [ ] **API**
   >  - Static methods on [JsonIo](https://www.javadoc.io/doc/com.cedarsoftware/json-io/latest/com/cedarsoftware/io/JsonIo.html): [toJson()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#toJson(java.lang.Object,com.cedarsoftware.io.WriteOptions)), [toObjects()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#toObjects(java.lang.String,com.cedarsoftware.io.ReadOptions,java.lang.Class)), [formatJson()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#formatJson(java.lang.String,com.cedarsoftware.io.ReadOptions,com.cedarsoftware.io.WriteOptions)), [deepCopy()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#deepCopy(java.lang.Object,com.cedarsoftware.io.ReadOptions,com.cedarsoftware.io.WriteOptions))
>  - Use [ReadOptionsBuilder](/user-guide-readOptions.md) and [WriteOptionsBuilder](/user-guide-writeOptions.md) to configure `JsonIo`
>  - Use [JsonReader.ClassFactory](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonReader.ClassFactory.html) for difficult classes (hard to instantiate & fill)
>  - Use [JsonWriter.JsonClassWriter](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonWriter.JsonClassWriter.html) to customize the output JSON for a particular class
>- [ ] Updates will be 5.1.0, 5.2.0, ...
>### 4.22.0 (current)
>- [ ] **Version**: [4.22.0](https://www.javadoc.io/doc/com.cedarsoftware/json-io/4.20.0/index.html)
>- [ ] **Bundling**: Both JPMS (Java Platform Module System) and OSGi (Open Service Gateway initiative)
>- [ ] **Maintained**: Fully
>- [ ] **Java Package**: com.cedarsoftware.io
>- [ ] **Java**: JDK1.8+ (Class file 52 format, includes module-info.class - multi-release JAR)
>- [ ] **API**
>  - Static methods on [JsonIo](https://www.javadoc.io/doc/com.cedarsoftware/json-io/latest/com/cedarsoftware/io/JsonIo.html): [toJson()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#toJson(java.lang.Object,com.cedarsoftware.io.WriteOptions)), [toObjects()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#toObjects(java.lang.String,com.cedarsoftware.io.ReadOptions,java.lang.Class)), [formatJson()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#formatJson(java.lang.String,com.cedarsoftware.io.ReadOptions,com.cedarsoftware.io.WriteOptions)), [deepCopy()](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonIo.html#deepCopy(java.lang.Object,com.cedarsoftware.io.ReadOptions,com.cedarsoftware.io.WriteOptions))
>  - Use [ReadOptionsBuilder](/user-guide-readOptions.md) and [WriteOptionsBuilder](/user-guide-writeOptions.md) to configure `JsonIo`
>  - Use [JsonReader.ClassFactory](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonReader.ClassFactory.html) for difficult classes (hard to instantiate & fill)
>  - Use [JsonWriter.JsonClassWriter](https://www.javadoc.io/static/com.cedarsoftware/json-io/4.20.0/com/cedarsoftware/io/JsonWriter.JsonClassWriter.html) to customize the output JSON for a particular class
>- [ ] Updates will be 4.22.0, 4.23.0, ...
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
### Sponsors
[![Alt text](https://www.yourkit.com/images/yklogo.png "YourKit")](https://www.yourkit.com/.net/profiler/index.jsp)

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.

<a href="https://www.jetbrains.com/idea/"><img alt="Intellij IDEA from JetBrains" src="https://s-media-cache-ak0.pinimg.com/236x/bd/f4/90/bdf49052dd79aa1e1fc2270a02ba783c.jpg" data-canonical-src="https://s-media-cache-ak0.pinimg.com/236x/bd/f4/90/bdf49052dd79aa1e1fc2270a02ba783c.jpg" width="100" height="100" /></a>
**Intellij IDEA**
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
