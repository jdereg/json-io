# json-io Architecture

## Overview

json-io is a Java JSON serialization library that uses a **2-pass process** to read JSON:

1. **Parse Phase**: JSON is parsed into `JsonObject` instances (which are Maps)
2. **Resolve Phase**: `JsonObject` instances are resolved into Java objects (Java mode) or remain as Maps (Maps mode)

## Core Components

### Reading JSON

| Class | Purpose |
|-------|---------|
| `JsonIo` | Public API entry point |
| `JsonReader` | Stream preparation (may be removed in future) |
| `JsonParser` | Parses JSON text into `JsonObject` (Map) instances |
| `Resolver` | Base class for the resolution process |
| `ObjectResolver` | Resolves `JsonObject` into Java objects (Java mode) |
| `MapResolver` | Resolves `JsonObject` into Maps (Maps mode) |

### Writing JSON

| Class | Purpose |
|-------|---------|
| `JsonWriter` | Writes Java objects to JSON format |

### Configuration

| Class | Purpose |
|-------|---------|
| `ReadOptionsBuilder` | Builds `ReadOptions` for customizing JSON reading |
| `WriteOptionsBuilder` | Builds `WriteOptions` for customizing JSON writing |

## Key Features

### Cyclic Graph Support

json-io supports `@id`/`@ref` pairs enabling JSON to represent true cyclic graphs. Both forward and backward references are supported.

### ClassFactory

Custom `ClassFactory` classes can be added to support reading classes that are not readable via normal "scraping" (reflection). This is increasingly important starting with JDK 17+ due to module system restrictions.

### Custom JsonWriter Classes

Custom `JsonWriter` classes can be added to write custom JSON format for particular classes.

### Aliases

json-io supports aliases so that class names appearing in JSON can be shorter (e.g., not include the full package name).

## Configuration Resources

The `src/main/resources` folder contains parameterized configuration that allows users to override default behavior.
