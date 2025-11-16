# GitHub Copilot Instructions for json-io

These instructions guide GitHub Copilot when assisting with modifications to this repository.

## Project Overview

This is **json-io**, a powerful JSON serialization library for Java that handles complex object graphs, cyclic references, and polymorphic types. Unlike basic JSON parsers, json-io preserves object references and maintains relationships in data structures.

**Key characteristics:**
- Main package: `com.cedarsoftware.io`
- Java compatibility: JDK 1.8 through JDK 24
- Zero external dependencies (except java-util)
- Maven-based build system
- Comprehensive test suite with 2,100+ tests

## Coding Conventions

- Use **four spaces** for indentationno tabs.
- End every file with a newline and use Unix line endings.
- Keep code lines under **120 characters** where possible.
- Follow standard Javadoc style for any new public APIs.
- This library maintains **JDK 1.8 source compatibility**do not use source constructs or JDK library calls beyond JDK 1.8.

## Key Utilities from java-util Dependency

- **Reflection**: Use `ReflectionUtils` APIs from java-util instead of direct reflection
- **Data structure verification**: Use `DeepEquals.deepEquals()` in JUnit tests (pass options to see "diff")
- **Null-safe ConcurrentMap**: Use java-util's `ConcurrentMaps` for null support
- **Date parsing**: Use `DateUtilities.parse()` or `Converter.convert()`
- **Type conversion**: Use `Converter.convert()` to marshal data types
- **Fast I/O**: Use `FastByteArrayInputStream`, `FastByteArrayOutputStream`, `FastReader`, `FastWriter`
- **String utilities**: Use `StringUtilities` APIs for null-safe string operations
- **Unique IDs**: Use `UniqueIdGenerator.getUniqueId19()` for strictly increasing long IDs
- **I/O utilities**: Use `IOUtilities` for stream closing and transfer operations
- **ClassValue helpers**: Use `ClassValueMap` and `ClassValueSet` for easier ClassValue usage
- **Case-insensitive maps**: Use `CaseInsensitiveMap`
- **Compact maps**: Use `CompactMap` variants for memory-efficient maps

## Testing Requirements

- Run `mvn test` before committing to ensure tests pass
- All 2,100+ tests must pass before any commit
- Add JUnit tests for any new functionality
- Use JUnit 5 annotations (`@Test`, `@ParameterizedTest`)
- Test models go in `src/test/java/com/cedarsoftware/io/models/`
- Test resources go in `src/test/resources/`

## Documentation

- Update `changelog.md` with a bullet about your change
- Update user guide documentation when you add or modify public-facing APIs
- Add comprehensive Javadoc for all public APIs

## Code Style

- Package-private visibility for internal classes
- Extensive Javadoc on public APIs
- Builder pattern for configuration objects
- Immutable options objects after building
- Proper exception handling with `JsonIoException`

## Commit Messages

- Start with a short imperative summary (max ~50 characters)
- Leave a blank line after the summary, then add further details if needed
- Don't amend or rewrite existing commits

## Pull Request Notes

- Summarize key changes and reference the main files touched
- Include a brief "Testing" section summarizing test results or noting any limitations

## Architecture

### Core API Classes
- **`JsonIo`**: Main entry point with static methods (`toJson()`, `toJava()`, `formatJson()`, `deepCopy()`)
- **`JsonReader`**: Handles JSON parsing and deserialization
- **`JsonWriter`**: Handles Java object serialization to JSON

### Configuration System
- **`ReadOptions`/`ReadOptionsBuilder`**: Configure JSON parsing behavior
- **`WriteOptions`/`WriteOptionsBuilder`**: Configure JSON output format

### Key Subsystems
- **Factory System** (`factory/`): Handles complex object instantiation
- **Reflection Utilities** (`reflect/`): Manages field access and method injection
- **Writers** (`writers/`): Custom serialization for specific types

### Configuration Files
Located in `src/main/resources/config/`:
- `aliases.txt` - Type aliases for JSON
- `customReaders.txt`/`customWriters.txt` - Custom type handlers
- `nonRefs.txt` - Types that don't need reference tracking
- `fieldsNotExported.txt`/`fieldsNotImported.txt` - Field filtering
