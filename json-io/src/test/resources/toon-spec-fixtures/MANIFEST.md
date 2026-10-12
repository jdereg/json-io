# TOON Spec Conformance Fixtures — Vendored Snapshot

This directory contains the official language-agnostic conformance fixtures from the [TOON specification repository](https://github.com/toon-format/spec), vendored at a pinned commit so the test suite is deterministic and runs offline.

## Pinned version

| Field | Value |
|---|---|
| Spec repo | https://github.com/toon-format/spec |
| Spec release | **v4.4.0** |
| Commit | `d09c3d33e37389345a74bd96981eb96dd9be6c5e` |
| Date | 2026-10-08  |
| Source path | `tests/fixtures/` + `tests/fixtures.schema.json` |

## Layout

```
toon-spec-fixtures/
├── MANIFEST.md                 # this file
├── upstream-README.md          # README copied from the spec repo's tests/
├── fixtures.schema.json        # JSON schema for fixture validation
├── known-gaps.txt              # the official cases json-io does not pass yet
├── fixtures/                   # the official fixtures, unchanged
│   ├── encode/                 # 9 files: primitives, objects, objects-keyed, arrays-{primitive,tabular,nested,objects}, delimiters, whitespace
│   └── decode/                 # 14 files: primitives, numbers, objects, objects-keyed, arrays-{primitive,tabular,nested}, delimiters, whitespace, root-form, validation-errors, indentation-errors, blank-lines, comments
└── v3-options/                 # NOT official any more: spec v3.3's fixtures for the two options v4.0 removed
    ├── encode/key-folding.json     # json-io's opt-in WriteOptions toonKeyFolding
    └── decode/path-expansion.json  # json-io's opt-in ReadOptions toonExpandPaths
```

## Known gaps

`known-gaps.txt` lists, one per line, the official cases json-io does not pass yet (`decode/comments.json#3` is
the fourth case of that file). The test reports a listed case as skipped while it fails and **fails the build
when a listed case passes**, so the commit that fixes a case removes its line. A case that is not listed must
pass. An empty file means full conformance with the pinned spec release.

## Refresh

To pin to a newer commit:

```bash
scripts/refresh-toon-fixtures.sh <commit-sha>
```

The script downloads the spec repo tarball at the given commit, replaces the contents of `fixtures/` and `fixtures.schema.json`, and rewrites this MANIFEST's pinned-version fields. It leaves `v3-options/` and `known-gaps.txt` alone. Inspect the resulting diff before committing — fixture changes are spec evolution and may surface real test failures that need code changes.

A case is identified by its position in its file, so a refresh that inserts or removes cases shifts the ids in `known-gaps.txt`. Empty the file, run `ToonSpecFixtureTest`, and list the cases that fail.

## Test driver

`ToonSpecFixtureTest` (`json-io/src/test/java/com/cedarsoftware/io/ToonSpecFixtureTest.java`) discovers all `.json` files under `fixtures/` and `v3-options/` from the classpath and runs each test case as a JUnit row named by its id. The decode cases run in strict mode unless the case says otherwise, as the spec's default is strict; json-io's own default is non-strict.
