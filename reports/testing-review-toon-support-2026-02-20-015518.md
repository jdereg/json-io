# TOON Test Coverage Gap Review
Generated: 2026-02-20-015518
Scope: /Users/jderegnaucourt/workspace/json-io (TOON support in json-io core tests)

## Executive Summary
TOON coverage is broad, but several high-value conformance gaps remain when compared against TOON Spec v3.0 (especially strict-mode validation and delimiter/path-expansion edge behavior). The current test suite includes many lenient "does not crash" cases where spec-conforming strict decoders should error.

## Spec references used
- TOON Spec v3.0 (2025-11-24): https://raw.githubusercontent.com/toon-format/spec/main/SPEC.md
- Section 7 (strings/keys), 11 (delimiters), 12 (indentation), 13.4 (key folding/path expansion), 14 (strict-mode errors)

## Current coverage highlights
- String quoting/escapes and special values are heavily tested (`ToonReaderTest`, `ToonWriterTest`).
- Tabular/list/inline array forms and nested list-item tabular encoding are covered (`ToonWriterTest:420+`, `ToonReaderTest:2053+`).
- Basic key folding/path expansion behavior is covered (`ToonWriterTest:695+`, `ToonReaderTest:2425+`).

## Missing or weak test cases

### 1) Strict-mode error behavior is not asserted (high priority)
Spec says strict mode must error on count mismatches, indentation violations, and blank lines inside arrays/tabular blocks (§14.1, §14.3, §14.4).

Current tests often assert lenient behavior instead:
- Blank lines in arrays/tabular accepted: `ToonReaderTest:2859`, `2874`, `2886`
- Odd indentation accepted: `ToonReaderTest:2910`, `2921`, `2932`
- Array count mismatch tolerated: `ToonReaderTest:2948`–`3004`
- Unclosed quote sometimes treated as string: `ToonReaderTest:3116`

**Missing tests:** strict-mode=true variants asserting `JsonIoException` for these inputs, and strict-mode=false variants documenting lenient behavior.

### 2) Delimiter consistency mismatch tests (high priority)
Spec requires bracket delimiter, field-list delimiter, and row split delimiter to match within a scope (§6, §11).

No targeted tests found for malformed mixed delimiters, e.g.:
- `items[2|]{a,b}:` (pipe in bracket, comma in fields)
- `items[2\t]{a\tb}:` rows using comma

**Missing tests:** malformed delimiter combos should error in strict mode.

### 3) Path expansion conflict policy tests (high priority)
Spec §13.4/§14.5 requires:
- strict=true: conflict must error
- strict=false: deterministic last-write-wins

Existing tests cover deep merge (`ToonReaderTest:2438`) but not strict conflict semantics with object-vs-primitive collisions (example from spec: `a.b: 1` then `a: 2`).

**Missing tests:** explicit conflict cases for both strict=true and strict=false.

### 4) Quoted key + array-header syntax tests (medium priority)
Spec §7.3 allows/requires quoted keys in headers where key isn’t identifier-safe, e.g. `"my-key"[3]:`.

No dedicated test found for quoted non-identifier keys preceding array headers or tabular headers.

**Missing tests:** round-trip and parser tests for `"my-key"[N]:` and `"x-items"[N]{...}:`.

### 5) Canonical list-item tabular indentation assertions (medium priority)
Spec §10 requires canonical layout when first list-item field is tabular (header on hyphen line, rows at depth +2, sibling fields at +1).

There is a good functional round-trip test (`ToonWriterTest:420`) but it does not strictly assert row/sibling indentation depth by line.

**Missing tests:** exact line-by-line indentation assertions for this canonical v3.0 rule.

### 6) Header error-surface precision tests (medium priority)
Spec strict syntax rules require errors for malformed headers and missing colons (§6, §14.2).

Some malformed cases currently allow either throw or non-throw (`ToonReaderTest:3143`, `3155`), which can hide regressions.

**Missing tests:** deterministic assertions that malformed header forms must throw in strict mode.

## Suggested prioritized additions
1. Add strict conformance test block for §14 (counts, indentation, blank lines, syntax).
2. Add delimiter-mismatch negative tests (§6/§11).
3. Add path-expansion conflict policy tests (§13.4/§14.5).
4. Add quoted-array-key header tests (§7.3 + §6 interaction).
5. Tighten canonical indentation assertions for §10 list-item tabular output.

## Notes
- The suite is already extensive; gaps are concentrated in negative/strict conformance and a few syntax interactions.
- If json-io intentionally implements a permissive profile, tests should explicitly separate “spec-strict” vs “compatibility/permissive” expectations.
