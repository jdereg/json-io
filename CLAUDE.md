# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **json-io**, a powerful JSON serialization library for Java that handles complex object graphs, cyclic references, and polymorphic types. Unlike basic JSON parsers, json-io preserves object references and maintains relationships in data structures.

**Key characteristics:**
- Main package: `com.cedarsoftware.io`
- Current version: 4.56.0
- Java compatibility: JDK 1.8 through JDK 24
- Zero external dependencies (except java-util)
- Maven-based build system
- Comprehensive test suite with 150+ test files

## Development Workflow

When working on tasks, follow this standard workflow:

### 1. Task Planning Phase
- Create a checklist of items to be completed
- Present the checklist to the user for review
- Work through items one by one

### 2. Implementation Phase
For each task item:
1. **Implement the change**
2. **Run all tests**: `mvn test`
  - All 1800+ tests should complete in ~2.5 seconds
  - Monitor JSON-io performance vs gson/jackson in test output
  - **Performance Rule**: If json-io Read/Write gets slower by ≥5ms, re-run tests up to 3 times to confirm
3. **Run performance test**: `mvn test -Dtest=JsonPerformanceTest`
  - **Performance Rule**: If slower by >20ms, re-run up to 3 times to confirm
  - If performance degradation exceeds thresholds after 3 runs, ask user for approval before proceeding
4. **Update documentation**:
  - **Always update**: `changelog.md` with new features/changes
  - **When APIs/behavior change**: Update user guide
  - **Only when major changes**: Ask before updating README
5. **Commit changes** with proper attribution

### 3. Git Commit Guidelines
- **Attribution**: Use LLM identity for git blame, not jdereg/jderegnaucourt
- **Naming convention**:
  - Claude 3.5s (Sonnet), Claude 3.5o (Opus)
  - Claude 4.0s (Sonnet), Claude 4.0o (Opus)
  - etc.

#### Git Author Configuration
**IMPORTANT**: Before making any commits, configure the git author identity using environment variables:

```bash
# Set git author identity (adjust version/model as appropriate)
export GIT_AUTHOR_NAME="Claude4.0s"
export GIT_AUTHOR_EMAIL="claude4.0s@ai.assistant"
export GIT_COMMITTER_NAME="Claude4.0s" 
export GIT_COMMITTER_EMAIL="claude4.0s@ai.assistant"

# Then commit normally - the AI identity will be used automatically
git add .
git commit -m "Your commit message"
````

### 4. Performance Monitoring
- **Test suite tolerance**: ±5ms for json-io vs gson/jackson
- **Performance test tolerance**: ±20ms for JsonPerformanceTest
- **Re-run policy**: Up to 3 attempts to confirm performance within tolerance
- **Escalation**: Ask user approval if performance degrades beyond tolerance (new capability may justify slight performance cost)

## Build Commands

### Building and Testing
```bash
# Build the project
mvn clean compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=JsonIoMainTest

# Run tests matching a pattern
mvn test -Dtest="*EnumSet*"

# Run performance tests specifically
mvn test -Dtest=JsonPerformanceTest

# Package without running tests
mvn package -DskipTests

# Full build with all artifacts
mvn clean package

# Install to local repository
mvn install
```

### Development Commands
```bash
# Compile only main sources
mvn compile

# Compile test sources
mvn test-compile

# Clean build artifacts
mvn clean

# Generate Javadoc
mvn javadoc:javadoc

# Run specific test with debugging
mvn test -Dtest=JsonIoMainTest -Dmaven.surefire.debug
```

## Architecture Overview

### Core API Classes
- **`JsonIo`** (`src/main/java/com/cedarsoftware/io/JsonIo.java`): Main entry point with static methods for JSON conversion
  - `toJson()` - Convert Java objects to JSON
  - `toJava()` - Parse JSON to Java objects (returns builders)
  - `formatJson()` - Pretty-print JSON strings
  - `deepCopy()` - Deep copy objects via JSON serialization

- **`JsonReader`** (`src/main/java/com/cedarsoftware/io/JsonReader.java`): Handles JSON parsing and deserialization
- **`JsonWriter`** (`src/main/java/com/cedarsoftware/io/JsonWriter.java`): Handles Java object serialization to JSON

### Configuration System
- **`ReadOptions`/`ReadOptionsBuilder`**: Configure JSON parsing behavior
  - Type resolution settings
  - Custom readers/factories
  - Field filtering and aliasing

- **`WriteOptions`/`WriteOptionsBuilder`**: Configure JSON output format
  - Pretty printing
  - Type information inclusion
  - Custom writers
  - Field exclusion/inclusion

### Key Subsystems

#### Factory System (`src/main/java/com/cedarsoftware/io/factory/`)
Handles complex object instantiation during deserialization:
- `ArrayFactory`, `CollectionFactory`, `MapFactory` - Standard collections
- `EnumSetFactory`, `RecordFactory` - Specialized types
- `ThrowableFactory` - Exception handling

#### Reflection Utilities (`src/main/java/com/cedarsoftware/io/reflect/`)
Manages field access and method injection:
- `Accessor`/`Injector` - Field and method access abstractions
- Filters for controlling which fields/methods are processed
- Factories for creating accessors and injectors

#### Writers (`src/main/java/com/cedarsoftware/io/writers/`)
Custom serialization for specific types:
- `ByteArrayWriter`, `ByteBufferWriter` - Binary data handling
- `ZoneIdWriter`, `LongWriter` - Specialized type handling

### Test Structure
The test suite is comprehensive with over 150 test classes in `src/test/java/com/cedarsoftware/io/`:
- Type-specific tests (e.g., `EnumTests.java`, `LocalDateTests.java`)
- Feature tests (e.g., `CustomReaderTest.java`, `SecurityTest.java`)
- Integration tests (e.g., `JsonIoMainTest.java`)
- Performance tests (`JsonPerformanceTest.java`)

## Development Patterns

### Adding New Type Support
1. Create custom reader in factory package if complex instantiation needed
2. Create custom writer in writers package if special serialization required
3. Add comprehensive tests following existing patterns
4. Update configuration files in `src/main/resources/config/` if needed

### Configuration Files (`src/main/resources/config/`)
- `aliases.txt` - Type aliases for JSON
- `customReaders.txt`/`customWriters.txt` - Custom type handlers
- `nonRefs.txt` - Types that don't need reference tracking
- `fieldsNotExported.txt`/`fieldsNotImported.txt` - Field filtering

### Testing Conventions
- Test classes follow `*Test.java` pattern
- Use JUnit 5 (`@Test`, `@ParameterizedTest`)
- Test models in `src/test/java/com/cedarsoftware/io/models/`
- Test resources in `src/test/resources/` with JSON fixtures

### Code Style
- Package-private visibility for internal classes
- Extensive Javadoc on public APIs
- Builder pattern for configuration objects
- Immutable options objects after building
- Proper exception handling with `JsonIoException`

## Debugging Tips

### Running Individual Tests
```bash
# Test a specific feature
mvn test -Dtest=EnumSetFormatTest

# Test with specific JVM options
mvn test -Dtest=SecurityTest -Duser.timezone=America/New_York
```

### JSON Validation
Use the built-in formatter for debugging:
```java
String prettyJson = JsonIo.formatJson(jsonString);
```

### Type Inspection
The main method shows all supported type conversions:
```bash
java -cp target/classes com.cedarsoftware.io.JsonIo
```

## Documentation Files to Maintain
- `changelog.md` - **Always update** with new features, fixes, and behavioral changes
- User guide - **Update when** APIs change or behavior is modified
- `README.md` - **Ask before updating** (only for major changes)