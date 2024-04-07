json-io
=======
<!--[![Build Status](https://travis-ci.org/jdereg/json-io.svg?branch=master)](https://travis-ci.org/jdereg/json-io) -->
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/json-io/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/json-io)
[![Javadoc](https://javadoc.io/badge/com.cedarsoftware/json-io.svg)](http://www.javadoc.io/doc/com.cedarsoftware/json-io)

Excellent Java serialization to and from JSON format.
Available on [Maven Central](https://central.sonatype.com/search?q=json-io&namespace=com.cedarsoftware).
This library has <b>no external dependencies</b> on other libraries for runtime other than our own `java-util.`
The `json-io.jar`file is only`185K` and combined with `java-util` totals `250K.` 
Works with`JDK 1.8`through`JDK 21`.
The classes in the`.jar`file are version 52 (`JDK 1.8`).
___
To include in your project:
##### Gradle
```
implementation 'com.cedarsoftware:json-io:4.19.14'
```

##### Maven
```
    <dependency>
      <groupId>com.cedarsoftware</groupId>
      <artifactId>json-io</artifactId>
      <version>4.19.14</version>
    </dependency>
```
___

## User Guide
>#### [Usage](/user-guide.md)
>#### [WriteOptions reference](/user-guide-writeOptions.md)
>#### [ReadOptions reference](/user-guide-readOptions.md)
>#### [Revision History](/changelog.md)

## Releases
>#### 5.0.0 (future)
>- [ ] **Maintained**: Fully
>- [ ] **Packaging**: com.cedarsoftware.io
>- [ ] **Java**: JDK17+ (Class file 61 format, will include module-info.java)
>- [ ] **API**
   >  - Static methods on `JsonIo` ==> `toJson()`, `toObjects()`, `formatJson()`, `deepCopy()`
>  - Use [ReadOptionsBuilder](/user-guide-readOptions.md) and [WriteOptionsBuilder](/user-guide-writeOptions.md) to configure `JsonIo.`
>  - Use `JsonReader.ClassFactory` for difficult classes (hard to instantiate & fill).
>  - Use `JsonWriter.JsonClassWriter` to customize the output JSON for a particular class
>- [ ] Updates will be 5.1.0, 5.2.0, ...
>#### 4.20.x (current)
>- [ ] **Version**: 4.20.0
>- [ ] **Maintained**: Fully
>- [ ] **Packaging**: com.cedarsoftware.io
>- [ ] **Java**: JDK1.8+ (Class file 52 format, includes module-info.java)
>- [ ] **API**
>  - Static methods on `JsonIo` ==> `toJson()`, `toObjects()`, `formatJson()`, `deepCopy()`
>  - Use [ReadOptionsBuilder](/user-guide-readOptions.md) and [WriteOptionsBuilder](/user-guide-writeOptions.md) to configure `JsonIo.`
>  - Use `JsonReader.ClassFactory` (not `JsonReader.JsonClassReader`) for difficult classes (hard to instantiate & fill).
>  - Use `JsonWriter.JsonClassWriter` to customize the output JSON for a particular class
>- [ ] Updates will be 4.21.0, 4.22.0, ...
>#### 4.14.x (supported)
>- [ ] **Version**: 4.14.3
>- [ ] **Maintained**: Bug fixes, CVE's
>- [ ] **Packaging**: com.cedarsoftware.util.io
>- [ ] **Java**: JDK1.8+ (Class file 52 format)
>- [ ] **API**
>  - Static methods on `JsonReader` and `JsonWriter`
>  - Configuration options passed via `Map` with constants defined on `JsonReader`/`JsonWriter`
>  - Use `JsonReader.JsonClassReader` for difficult classes (hard to instantiate & fill).
>  - Use `JsonWriter.JsonClassWriter` to customize the output JSON for a particular class
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
