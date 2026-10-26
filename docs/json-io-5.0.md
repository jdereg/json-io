# json-io 5.0: What Changes

This is the list of changes held for json-io 5.0, the next major version, because each one changes what existing code
gets. Each says what changes, who notices, and how to prepare on 4.x. Status:

- **Announced**: 4.x docs or javadoc already say it happens in 5.0.
- **Decided**: agreed for 5.0, not yet announced elsewhere.
- **Proposed**: under consideration; not decided.

## Writing: standard JSON by default (Announced)

`WriteOptionsBuilder.standardJson()` sets what 5.0 writes by default. Two of its settings are already 4.x defaults
(`writeOptionalAsObject(false)`, `preserveLeafContainerIdentity(false)`). These six change:

| Setting | 4.x default | 5.0 default | To keep the 4.x output |
|---|---|---|---|
| Type info | `showTypeInfoMinimal()` | `showTypeInfoNever()` | `.showTypeInfoMinimal()` |
| Type info on the root | shown | omitted (`omitRootTypeInfo()`) | `.showRootTypeInfo()` |
| References | `cycleSupport(true)`: `@id`/`@ref` | `false` | `.cycleSupport(true)` |
| Map keys that are not text | `@keys`/`@items` | stringified: `{"100": ...}` | `.stringifyMapKeys(false)` |
| `java.util.Date`, `java.sql.Date` | epoch milliseconds | ISO-8601 text | `.longDateFormat()` |
| Meta key prefix | `@` (`@type`) | `$` (`$type`) | `.useMetaPrefixAt()` |

**Who notices:** code that reads json-io's output back into classes it doesn't name (a field declared `Object` or an
interface), graphs with shared objects or cycles, and readers expecting epoch dates or `@`-prefixed keys. A Map mode
tree written with these defaults loses the types of its typed values (`{"@type":"Integer","value":5}` is written `5`).

**Prepare on 4.x:** call `standardJson()` now to see the 5.0 output, or set the settings above explicitly to keep
today's.

## Removed APIs

| Removed | Use instead | Status |
|---|---|---|
| `JsonIo.toObjects(...)` (3 overloads) | `JsonIo.toJava(...).asClass(type)` | Announced (javadoc) |
| `JsonClassWriter.write(T, boolean, Writer, WriterContext)` and `writePrimitiveForm(Object, Writer, WriterContext)`, and the `Writer`-based helpers in `Writers` | The `JsonGenerator` forms ([user guide](/user-guide.md#writing-custom-jsonclasswriter)) | Announced (user guide) |
| `ReadOptionsBuilder.returnAsNativeJsonObjects()` | `returnAsJsonObjects()` | Proposed (deprecated in 4.x) |
| `ReadOptionsBuilder.assignInstantiator(...)` | `addPermanentClassFactory(...)` | Proposed (deprecated in 4.x) |
| `JsonObject.getLength()` | `size()` | Proposed (deprecated in 4.x) |
| `JsonReader` and its nested `ClassFactory`, `MissingFieldHandler`, `JsonClassReader`; `JsonWriter.JsonClassWriter` | The top-level interfaces of the same names | Proposed (deprecated in 4.x) |
| `JsonWriter.writeBasicString(...)`, `writeSingleQuotedString(...)` | The `JsonGenerator` string writers | Proposed (deprecated in 4.x) |

## Map mode (Decided)

### A root `@type` no longer types the root's fields
**4.x:** when the root object's `@type` names a class on the classpath, Map mode converts the root's own fields to the
types that class declares: `"age":30` is an `Integer` for an `int age`, `"born":"1990-05-01"` a `LocalDate`, an enum
field its constant. Every other object in the tree keeps JSON's values. **5.0:** the root's fields do too (`Long`,
text), so the tree is the same with or without the class.

**Who notices:** code that casts a root field to the declared type: `(Integer) map.get("age")` throws a
`ClassCastException` in 5.0, and an `equals` against an `Integer` stops matching. JSON with no `@type` on the root
(most JSON) and JSON whose root class is not on the classpath see no change.

**Prepare on 4.x:** read the value with the type you want: `jsonObject.get("age", Integer.class)` (4.118.0) returns
the same answer in 4.x and 5.0.

### A typed array below the root
**4.x:** `{"@type":"int[]","@items":[1,2]}` is the `int[]` at the root, but a `JsonObject` holding the items
(`getItems()`) everywhere below it. **5.0:** to be settled. Converting it to the array everywhere would match the root
and is safe, since such an array holds no references. Code calling `getItems()` on it would notice.

## Proposed

- **One shape for arrays in Map mode.** A JSON read gives an array as an `Object[]`; a TOON read gives a `List`.
- **5.0 reads what 4.x wrote**, `@`-prefixed and `$`-prefixed, so stored JSON stays readable after the upgrade.
- **A migration guide** from the APIs before the builder (`JsonReader`/`JsonWriter` statics, `toObjects`) to
  `JsonIo.toJava()`/`toJson()` and the options builders, with the table above.
