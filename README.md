json-io
=======
<!--[![Build Status](https://travis-ci.org/jdereg/json-io.svg?branch=master)](https://travis-ci.org/jdereg/json-io) -->
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/json-io/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/json-io)
[![Javadoc](https://javadoc.io/badge/com.cedarsoftware/json-io.svg)](http://www.javadoc.io/doc/com.cedarsoftware/json-io)

Perfect Java serialization to and from JSON format (available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cjson-io)).
This library has <b>no dependencies</b> on other libraries for runtime.  Built purely on the JDK.

To include in your project:
##### Gradle
```
implementation 'com.cedarsoftware:json-io:4.14.2'
```

##### Maven
```
    <dependency>
      <groupId>com.cedarsoftware</groupId>
      <artifactId>json-io</artifactId>
      <version>4.14.2</version>
    </dependency>
```
___
**json-io** consists of two main classes, a reader (`JsonReader`) and a writer (`JsonWriter`).  **json-io** eliminates
the need for using `ObjectInputStream / ObjectOutputStream` to serialize Java and instead uses the JSON format.

**json-io** does not require that Java classes implement `Serializable` or `Externalizable` to be serialized,
unlike the JDK's `ObjectInputStream` / `ObjectOutputStream`.  It will serialize any Java object graph into JSON and retain
complete graph semantics / shape and object types.  This includes supporting private fields, private inner classes
(static or non-static), of any depth.  It also includes handling cyclic references.  Objects do not need to have
public constructors to be serialized.  The output JSON will not include `transient` fields, identical to the
ObjectOutputStream behavior.

**json-io** does not depend on any 3rd party libraries, has extensive support for Java Generics, and allows extensive customization.

### A few advantages of json-io over Google's gson library:
* gson will fail with infinite recursion (`StackOverflowError`) when there is a cycle in the input data.  [Illustrated here.](https://github.com/jdereg/json-io/blob/master/src/test/java/com/cedarsoftware/util/io/TestGsonNotHandleCycleButJsonIoCan.java)
* gson cannot handle non-static inner classes. [Illustrated here.](https://github.com/jdereg/json-io/blob/master/src/test/java/com/cedarsoftware/util/io/TestGsonNotHandleStaticInnerButJsonIoCan.java)
* gson cannot handle hetereogeneous `Collections`, `Object[]`, or `Maps`.  [Illustrated here.](https://github.com/jdereg/json-io/blob/master/src/test/java/com/cedarsoftware/util/io/TestGsonNotHandleHeteroCollections.java)
* gson cannot handle Maps with keys that are not Strings. [Illustrated here.](https://github.com/jdereg/json-io/blob/master/src/test/java/com/cedarsoftware/util/io/TestGsonNotHandleMapWithNonStringKeysButJsonIoCan.java)

### Format
**json-io** uses proper JSON format.  As little type information is included in the JSON format to keep it compact as
possible.  When an object's class can be inferred from a field type or array type, the object's type information is
left out of the stream.  For example, a `String[]` looks like `["abc", "xyz"]`.

When an object's type must be emitted, it is emitted as a meta-object field `"@type":"package.class"` in the object.  
When read, this tells the JsonReader what class to instantiate.  (`@type` output can be turned off - see [User Guide](/user-guide.md)).

If an object is referenced more than once, or references an object that has not yet been defined, (say A points to B,
and B points to C, and C points to A), it emits a `"@ref":n` where 'n' is the object's integer identity (with a
corresponding meta entry `"@id":n` defined on the referenced object).  Only referenced objects have IDs in the JSON
output, reducing the JSON String length.

### Performance
**json-io** was written with performance in mind.  In most cases **json-io** is faster than the JDK's
`ObjectInputStream / ObjectOutputStream`.  As the tests run, a log is written of the time it takes to
serialize / deserialize and compares it to `ObjectInputStream / ObjectOutputStream` (if the static
variable `_debug` is `true` in `TestUtil`).

### [User Guide](/user-guide.md)

### Pretty-Printing JSON
Use `JsonWriter.formatJson()` API to format a passed in JSON string to a nice, human readable format.  Also, when writing
JSON data, use the `JsonWriter.objectToJson(o, args)` API, where args is a `Map` with a key of `JsonWriter.PRETTY_PRINT`
and a value of 'true' (`boolean` or `String`).  When run this way, the JSON written by the `JsonWriter` will be formatted
in a nice, human readable format.

### RESTful support
**json-io** can be used as the fundamental data transfer method between a Javascript / JQuery / Ajax client and a web server
in a RESTful fashion.

See [json-command-servlet](https://github.com/jdereg/json-command-servlet) for a light-weight servlet that processes REST requests.

### Noteworthy
For useful Java utilities, check out [java-util](http://github.com/jdereg/java-util)

Featured on [json.org](http://json.org).

[Revision History](/changelog.md)
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

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

by John DeRegnaucourt
