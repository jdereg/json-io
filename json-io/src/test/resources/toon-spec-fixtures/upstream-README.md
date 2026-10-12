# TOON Test Fixtures

This directory contains **language-agnostic JSON test fixtures** for validating TOON implementations against the specification. These fixtures cover core specification requirements; conformance is defined by SPEC.md (§13 and Appendix C), not by this fixture suite.

The [Test Coverage](#test-coverage) tables below index every fixture file.

## Fixture Format

Every fixture file follows [`fixtures.schema.json`](./fixtures.schema.json), which documents each field and option:

```json
{
  "version": "<spec-version>",
  "category": "encode",
  "description": "Brief description of test category",
  "tests": [
    {
      "name": "descriptive test name",
      "input": "JSON value or TOON string",
      "expected": "TOON string or JSON value",
      "options": {},
      "specSection": "7.2",
      "note": "Optional explanation"
    }
  ]
}
```

### Error Tests

Error tests use `shouldError: true` to indicate that the test expects an error to be thrown:

```json
{
  "name": "throws on array length mismatch",
  "input": "tags[3]: a,b",
  "expected": null,
  "shouldError": true,
  "options": { "strict": true }
}
```

**Note:** Error tests do not specify expected error messages, as these are implementation-specific and vary across languages.

## Using These Tests

Load each fixture file and run every entry in its `tests` array through your encoder or decoder with `test.options` applied, skipping entries whose `minSpecVersion` is newer than the spec version you target; assert the `expected` output – or that an error is thrown when `shouldError` is `true`.

**Note:** `name`, `description`, and `note` are prose, not identifiers. Key your runner on file path and array index, never on these strings – they follow the spec's terminology and are rewritten whenever it changes.

## Test Coverage

### Encoding Tests (`fixtures/encode/`)

| File | Description | Spec Sections |
|------|-------------|---------------|
| `primitives.json` | String, number, boolean, null encoding and escaping | §7.1/§7.2, §2 |
| `objects.json` | Simple objects, nested objects, key encoding | §8 (keys: §7.3/§7.1) |
| `objects-keyed.json` | Keyed tabular form for objects of uniform objects | §9.5, §10 |
| `arrays-primitive.json` | Inline primitive arrays, empty arrays | §9.1 |
| `arrays-tabular.json` | Tabular form with header and rows | §9.3 |
| `arrays-nested.json` | Arrays of arrays, mixed arrays | §9.2/§9.4 |
| `arrays-objects.json` | Objects as list items, complex nesting | §9, §10 |
| `delimiters.json` | Tab and pipe delimiter options | §11 |
| `whitespace.json` | Formatting invariants and indentation | §12 |

### Decoding Tests (`fixtures/decode/`)

| File | Description | Spec Sections |
|------|-------------|---------------|
| `primitives.json` | Parsing primitives, unescaping, ambiguity | §4, §7.1/§7.4 |
| `numbers.json` | Number edge cases, exponent forms, leading zeros | §4 |
| `objects.json` | Parsing objects, keys, nesting | §8 (keys: §7.4/§7.1) |
| `objects-keyed.json` | Keyed header and entry-row parsing | §9.5, §10 |
| `arrays-primitive.json` | Inline array parsing | §9.1 |
| `arrays-tabular.json` | Tabular form parsing | §9.3 |
| `arrays-nested.json` | Nested and mixed array parsing | §9.2/§9.4 |
| `delimiters.json` | Delimiter detection and parsing | §11 |
| `whitespace.json` | Whitespace tolerance and token trimming | §12 |
| `root-form.json` | Root form detection (empty, single primitive) | §5 |
| `validation-errors.json` | Syntax errors, length mismatches, malformed input | §6, §14 |
| `indentation-errors.json` | Indentation validation and its non-strict recoveries | §8, §12, §14.2, §14.4 |
| `blank-lines.json` | Blank lines inside and outside header spans | §12, §14.2, §14.4 |
| `comments.json` | Comment-line stripping and full-line-only edge cases | §5.1 |

**Coverage note:** §3 host-type normalization (NaN/±Infinity → null, host Date/Set/Map/BigInt mappings, the error on an unpaired surrogate) and §4 byte-input decoding (ill-formed UTF-8 errors in both modes, never U+FFFD) are intentionally outside these JSON fixtures, since the fixture format can express neither non-JSON encode inputs nor raw bytes. Implementations should cover both in their language-local test suites.

## Contributing Test Cases

Add a case to the matching fixture file with its `specSection`, check `expected` against SPEC.md, and run `pnpm test` – it validates every fixture against the schema and checks that its `category` matches its directory.
