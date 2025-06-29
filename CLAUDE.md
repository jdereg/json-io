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

## 🚨 CRITICAL RULE - READ FIRST 🚨

**BEFORE doing ANYTHING else, understand this NON-NEGOTIABLE requirement:**

### MANDATORY FULL TEST SUITE VALIDATION

**EVERY change, no matter how small, MUST be followed by running the complete test suite:**

```bash
mvn clean test
```

**ALL 1800+ tests MUST pass before:**
- Moving to the next issue/file/task
- Committing any changes  
- Asking for human approval
- Starting any new work

**If even ONE test fails:**
- Stop immediately
- Fix the failing test(s)
- Run the full test suite again
- Only proceed when ALL tests pass

**This rule applies to:**
- Security fixes
- Performance improvements
- Feature additions
- Documentation changes
- ANY code modification

**❌ NEVER skip this step**
**❌ NEVER assume tests will pass**
**❌ NEVER move forward with failing tests**

**This is MORE IMPORTANT than the actual change itself.**

## 🎯 WORK PHILOSOPHY - INCREMENTAL ATOMIC CHANGES 🎯

**Mental Model: Work with a "List of Changes" approach**

### The Change Hierarchy
- **Top-level changes** (e.g., "Fix security issues in JsonWriter")
  - **Sub-changes** (e.g., "Fix ReDoS vulnerability", "Fix thread safety")
    - **Sub-sub-changes** (e.g., "Limit regex repetition", "Add validation tests")

### Workflow for EACH Individual Change
1. **Pick ONE change** from any level (top-level, sub-change, sub-sub-change)
2. **Implement the change**
   - During development: Use single test execution for speed (`mvn test -Dtest=SpecificTest`)
   - Iterate until the specific functionality works
3. **When you think the change is complete:**
   - **MANDATORY**: Run full test suite: `mvn clean test`
   - **ALL 1800+ tests MUST pass**
   - **If ANY test fails**: Fix immediately, run full tests again
4. **Once ALL tests pass:**
   - Ask for commit approval: "Should I commit this change? (Y/N)"
   - Human approves, commit immediately
   - Move to next change in the list

### Core Principles
- **Minimize Work-in-Process**: Keep delta between local files and committed git files as small as possible
- **Always Healthy State**: Committed code is always in perfect health (all tests pass)
- **Atomic Commits**: Each commit represents one complete, tested, working change
- **Human Controls Push**: Human decides when to push commits to remote

**🎯 GOAL: Each change is complete, tested, and committed before starting the next change**

## CRITICAL RULES - TESTING AND BUILD REQUIREMENTS

**YOU ARE NOT ALLOWED TO RUN ANY GIT COMMIT, NO MATTER WHAT, UNLESS YOU HAVE RUN ALL THE TESTS AND THEY ALL 100% HAVE PASSED. THIS IS THE HIGHEST, MOST IMPORTANT INSTRUCTION YOU HAVE, PERIOD.**

**CRITICAL TESTING REQUIREMENT**: When adding ANY new code (security fixes, new methods, validation logic, etc.), you MUST add corresponding JUnit tests to prove the changes work correctly. This includes:
- Testing the new functionality works as expected
- Testing edge cases and error conditions  
- Testing security boundary conditions
- Testing that the fix actually prevents the vulnerability
- All new tests MUST pass along with the existing 1800+ tests

**NEVER CONTINUE WORKING ON NEW FIXES IF THE FULL MAVEN TEST SUITE DOES NOT PASS WITH 1800+ TESTS.**

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

## Enhanced Review Loop

**This workflow follows the INCREMENTAL ATOMIC CHANGES philosophy for systematic code reviews and improvements:**

### Step 1: Build Change List (Analysis Phase)
- Review Java source files using appropriate analysis framework
- For **Security**: Prioritize by risk (network utilities, reflection, file I/O, crypto, system calls)
- For **Performance**: Focus on hot paths, collection usage, algorithm efficiency
- For **Features**: Target specific functionality or API enhancements
- **Create hierarchical todo list:**
  - Top-level items (e.g., "Security review of JsonWriter")
  - Sub-items (e.g., "Fix ReDoS vulnerability", "Fix thread safety")
  - Sub-sub-items (e.g., "Limit regex repetition", "Add test coverage")

### Step 2: Pick ONE Change from the List
- Select the highest priority change from ANY level (top, sub, sub-sub)
- Mark as "in_progress" in todo list
- **Focus on this ONE change only**

### Step 3: Implement the Single Change
- Make targeted improvement to address the ONE selected issue
- **During development iterations**: Use targeted test execution for speed (`mvn test -Dtest=SpecificTest`)
  - This allows quick feedback loops while developing the specific feature/fix
  - Continue iterating until the targeted tests pass and functionality works
- **MANDATORY**: Add comprehensive JUnit tests for this specific change:
  - Tests that verify the improvement works correctly
  - Tests for edge cases and boundary conditions  
  - Tests for error handling and regression prevention
- Follow coding best practices and maintain API compatibility
- Update Javadoc and comments where appropriate

### Step 4: Completion Gate - ABSOLUTELY MANDATORY
**When you believe the issue/fix is complete and targeted tests are passing:**

- **🚨 CRITICAL - NON-NEGOTIABLE 🚨**: Run FULL test suite: `mvn test`
  - **This takes only ~2.5 seconds but tests ALL 1800+ tests**
  - **This is the quality gate that ensures project health**
- **🚨 VERIFY ALL TESTS PASS 🚨**: Ensure 1800+ tests pass
- **🚨 ZERO TOLERANCE FOR TEST FAILURES 🚨**: All tests must be 100% passing before proceeding
- **If even ONE test fails**: Fix issues immediately, run full tests again
- **NEVER move to Step 5, 6, 7, or 8 until ALL tests pass**
- **NEVER start new work until ALL tests pass**
- Mark improvement todos as "completed" only when ALL tests pass

**⚠️ WARNING: Skipping full test validation is a CRITICAL PROCESS VIOLATION ⚠️**

**THE PROCESS:**
1. **Development Phase**: Use targeted tests (`mvn test -Dtest=SpecificTest`) for fast iteration
2. **Completion Gate**: Run full test suite (`mvn test`) when you think you're done
3. **Quality Verification**: ALL 1800+ tests must pass before proceeding

### Step 5: Update Documentation (for this ONE change)
- **changelog.md**: Add entry for this specific change under appropriate version
- **User guide**: Update if this change affects public APIs or usage patterns  
- **Javadoc**: Ensure documentation reflects this change
- **README.md**: Update if this change affects high-level functionality

### Step 6: Request Atomic Commit Approval
**MANDATORY HUMAN APPROVAL STEP for this ONE change:**
Present a commit approval request to the human with:
- Summary of this ONE improvement made (specific security fix, performance enhancement, etc.)
- List of files modified for this change
- Test results confirmation (ALL 1800+ tests passing)
- Documentation updates made for this change
- Clear description of this change and its benefits
- Ask: "Should I commit this change?"

**CRITICAL COMMIT RULES:**
- **ONLY commit if human responds exactly "Y" or "Yes"**
- **If human does NOT write "Y" or "Yes", do NOT commit**
- **If human does not respond "Y" or "Yes", pay close attention to next instruction**
- **NEVER commit without explicit "Y" or "Yes" approval**

### Step 7: Atomic Commit (Only After Human Approval)
- **Immediately commit this ONE change** after receiving "Y" approval
- Use descriptive commit message format for this specific change:
  ```
  [Type]: [Brief description of this ONE change]
  
  - [This specific change implemented]
  - [Test coverage added for this change]
  - [Any documentation updated]
  
  🤖 Generated with [Claude Code](https://claude.ai/code)
  
  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
  Where [Type] = Security, Performance, Feature, Refactor, etc.
- Mark this specific todo as "completed"
- **Repository is now in healthy state with this change committed**

### Step 8: Return to Change List
- **Pick the NEXT change** from the hierarchical list (top-level, sub, sub-sub)
- **Repeat Steps 2-7 for this next change**
- **Continue until all changes in the list are complete**
- Maintain todo list to track progress across entire scope

**Special Cases - Tinkering/Exploratory Work:**
For non-systematic changes, individual experiments, or small targeted fixes, the process can be adapted:
- Steps 1-2 can be simplified or skipped for well-defined changes
- Steps 4-6 remain mandatory (testing, documentation, human approval)
- Commit messages should still be descriptive and follow format

**This loop ensures systematic code improvement with proper testing, documentation, and human oversight for all changes.**

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