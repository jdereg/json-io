# TOON Spec Conformance Fixtures — Vendored Snapshot

This directory contains the official language-agnostic conformance fixtures from the [TOON specification repository](https://github.com/toon-format/spec), vendored at a pinned commit so the test suite is deterministic and runs offline.

## Pinned version

| Field | Value |
|---|---|
| Spec repo | https://github.com/toon-format/spec |
| Spec release | **v3.3** |
| Commit | `07161ccc84242a5571f14d33504fc0b4b84da0b2` |
| Date | 2026-05-21 |
| Source path | `tests/fixtures/` + `tests/fixtures.schema.json` |

## Layout

```
toon-spec-fixtures/
├── MANIFEST.md                 # this file
├── upstream-README.md          # README copied from the spec repo's tests/
├── fixtures.schema.json        # JSON schema for fixture validation
└── fixtures/
    ├── encode/                 # 9 files: primitives, objects, arrays-{primitive,tabular,nested,objects}, delimiters, whitespace, key-folding
    └── decode/                 # 13 files: primitives, numbers, objects, arrays-{primitive,tabular,nested}, delimiters, whitespace, root-form, validation-errors, indentation-errors, blank-lines, path-expansion
```

## Refresh

To pin to a newer commit:

```bash
scripts/refresh-toon-fixtures.sh <commit-sha>
```

The script downloads the spec repo tarball at the given commit, replaces the contents of `fixtures/` and `fixtures.schema.json`, and rewrites this MANIFEST's pinned-version fields. Inspect the resulting diff before committing — fixture changes are spec evolution and may surface real test failures that need code changes.

## Test driver

`ToonSpecFixtureTest` (`json-io/src/test/java/com/cedarsoftware/io/ToonSpecFixtureTest.java`) discovers all `.json` files under `fixtures/encode/` and `fixtures/decode/` from the classpath and parameterizes each test case as a JUnit row. Fixtures whose `options` reference features json-io does not implement (`flattenDepth`, `expandPaths: "safe"`, etc.) are skipped via JUnit `Assumptions` so the gap is visible in the test output without failing the build.
