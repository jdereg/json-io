# Performance Review: ToonReader.java
Generated: 2026-03-07-001753  
Scope: `/Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/ToonReader.java`  
Background: Prior report `performance-review-toonreader-2026-03-01-155821.md` used as context only; all findings re-derived from current source.

---

## Executive Summary

Several prior optimizations are now in the code and working correctly: thread-local caches for string/number/lineBuf (lines 61-68), a single-String `peekLine()` that computes indent and trim in one pass from the raw `char[]` buffer (lines 1132-1176), and a custom integer fast-path inside `parseNumber()` (lines 1026-1073).  
What remains are four allocation-intensive patterns that are visible in cold reading of the code and were identified in the March 1 JFR traces as dominant: a **create-before-check** antipattern in `cacheSubstring`, a **per-element StringBuilder-to-String** materialization in `readInlineArray`, **string concatenation for array-notation key reassembly**, and a **per-call `new StringBuilder()`** in `parseColumnHeaders`. Beyond those, there are several smaller but corrigible issues in code structure and the testing infrastructure.

---

## Top Bottlenecks Ranked by Likely Impact

---

### #1 — `cacheSubstring` Allocates the Substring *Before* Checking the Cache  
**File/Lines:** `ToonReader.java:153-156`, called from `trimAsciiRange:193-208` and `parseQuotedString:971`

```java
// Current code:
private String cacheSubstring(String source, int start, int end) {
    if (start == end) return "";
    return cacheString((start == 0 && end == source.length()) ? source : source.substring(start, end));
}
```

`cacheString` (lines 141-151) checks the cache slot *after* it receives a fully-formed String. On a cache hit, the substring that was just allocated is immediately discarded. On every key extraction (`trimAsciiRange(trimmed, 0, colonPos)`, line 305 and line 852) and every value extraction (`trimAsciiRange(trimmed, valueStart, valueEnd)`, lines 312, 858), and every no-escape quoted string (`cacheSubstring(text, 1, len - 1)`, line 971), a `String` object is created before knowing whether the cache will accept it.

**Why it matters:** `trimAsciiRange` is the busiest non-trivial call in `readObject` (lines 270-367), `readInlineObject` (lines 775-906), and `parseQuotedString` (lines 966-1005). Every field in every object goes through `trimAsciiRange` at least twice (for key and value). With the benchmark payload exercising 100 K iterations, this is the dominant allocation site for short-lived Strings. The March 1 JFR identified `cacheSubstring → StringLatin1.newString` and `cacheSubstring → Arrays.copyOfRangeByte` as top allocation hotspots — this is exactly that path.

**Optimization:** Compute a hash from `(source, start, end)` without materializing the substring. If the cache slot holds a candidate String of length `(end - start)`, compare it character-by-character against `source[start..end]`. Only call `source.substring(start, end)` on a confirmed cache miss.

```java
// Sketch (no code changes intended — pseudocode only):
// hash from content chars, probe cache slot, compare in-place, substring only on miss
```

**Risk:** Low. The external contract of `cacheSubstring` is unchanged (same return type and semantics). Requires a helper that scans `source.charAt(i)` against the candidate — JIT will vectorize this. Validate with existing correctness tests + benchmark delta.

---

### #2 — `readInlineArray` Materializes a `String` per Element via `StringBuilder.toString()`  
**File/Lines:** `ToonReader.java:614-661`, specifically lines 644 and 653

```java
// Each delimiter-separated element:
elements.add(readScalar(trimAscii(current)));  // line 644
// Last element:
elements.add(readScalar(trimAscii(current)));  // line 653
```

`current` is the reused `inlineBuf` (line 617), but `trimAscii(StringBuilder)` at line 174 unconditionally calls `text.toString()` (line 179) or `text.substring(start, end)` (line 190), each of which allocates a new `String`. For an array of N elements, this is N `String` allocations from the StringBuilder conversion alone, before any further `readScalar` processing.

**Why it matters:** `readInlineArray` is called for every inline `[N]: e1,e2,...` array and for every row in a tabular array (line 559). The benchmark `TestData` has multiple arrays (`values[10]`, `integerList`, `largeIntArray[50]`, `tags`, `counterMap` keys). For a 50-element integer array, that is 50 `StringBuilder.toString()` + up to 50 substring allocations, 100K times per benchmark run.

**Optimization:** Since `content` (line 614) is already a `String`, scan it by index rather than accumulating into a StringBuilder. Track `segStart` and `segEnd` positions in `content` between delimiter characters. When a delimiter (or end of string) is found, call `trimAsciiRange(content, segStart, segEnd)` directly, bypassing the StringBuilder entirely. The only case that still needs the StringBuilder is when an escape sequence is present inside a quoted element — this is a minority path.

```java
// Sketch (pseudocode only):
// int segStart = 0; for each char in content { if delimiter and not in quotes: 
//   readScalar(trimAsciiRange(content, segStart, i)); segStart = i+1; }
// Fallback to StringBuilder only when escape == true
```

**Risk:** Medium. Must preserve quoting semantics (`inQuotes` flag, `escaped` flag). The escaping fallback path keeps the current StringBuilder logic. Needs regression against all inline array tests, including edge cases: trailing delimiter, empty elements, quoted values containing the delimiter.

---

### #3 — Array-Notation Key Reassembly Creates 2-3 Intermediate Strings  
**File/Lines:** `ToonReader.java:318-323` (in `readObject`) and `ToonReader.java:786-791`, `864-870` (in `readInlineObject`)

```java
// readObject (lines 318-323):
int bracketStart = key.indexOf('[');
String realKey = key.substring(0, bracketStart);          // String #1
String arraySyntax = key.substring(bracketStart) + ":";   // String #2 + concat String #3
if (!valuePart.isEmpty()) {
    arraySyntax += " " + valuePart;                        // String #4 + String #5
}
```

Five String allocations in the worst case just to rebuild the array syntax string that `parseArrayFromLine` will consume. Lines 786-791 in `readInlineObject` duplicate this pattern, and lines 864-870 duplicate it again. All three sites use `new StringBuilder(...)` (lines 789, 867) but only for the `readInlineObject` path; `readObject` (line 321) uses raw `+` string concatenation.

**Why it matters:** Every field that uses combined array notation (e.g., `data[5]:`, `items[10]{col1,col2}:`) hits this path. In the benchmark payload this applies to all the array fields inside `TestData`. Note also that `readObject:318` uses `key.contains("[")` — `String.contains(CharSequence)` compiles to `indexOf` but passes through an overload dispatch; `key.indexOf('[') >= 0` (char overload) is marginally faster and more explicit.

**Optimization:** Replace the String-concat chain with a single `inlineBuf` (already an instance-level StringBuilder, line 104). Reset it, append the bracket suffix, colon, and optional space+valuePart. Pass `inlineBuf.toString()` to `parseArrayFromLine` exactly once. Additionally, change `key.contains("[")` (line 318) to `key.indexOf('[') >= 0`.

**Risk:** Low. Purely mechanical replacement with equivalent output. Validate with array-notation TOON tests.

---

### #4 — `parseColumnHeaders` Allocates a `new StringBuilder()` on Every Call  
**File/Lines:** `ToonReader.java:480-498`, line 482

```java
private List<String> parseColumnHeaders(String headerStr, char delimiter) {
    List<String> headers = new ArrayList<>();
    StringBuilder current = new StringBuilder();   // line 482: fresh allocation each call
    ...
}
```

The class already owns two reusable `StringBuilder` fields: `quoteBuf` (line 103) and `inlineBuf` (line 104). `parseColumnHeaders` ignores both and allocates a new `StringBuilder` per call. Although tabular array headers are parsed once per array (not per row), any TOON document with tabular arrays hits this every array.

**Why it matters:** Minor in isolation, but this is also calling `trimAscii(current)` at lines 487 and 494, which triggers `current.toString()` per header token. For a tabular array with 10 columns, that is 11 `StringBuilder`/`String` allocations. The `ArrayList` at line 481 also allocates for every tabular header.

**Optimization:** Reuse `quoteBuf` (which is idle during header parsing since `parseColumnHeaders` is not called from inside `parseQuotedString`). Reset with `quoteBuf.setLength(0)`. Also consider accepting a pre-allocated `List<String>` argument (cleared by caller) to avoid the `new ArrayList<>()` allocation if tabular arrays are parsed repeatedly.

**Risk:** Low, but requires verifying `quoteBuf` is not on the call stack when `parseColumnHeaders` is called. Static analysis of the call graph (tabular arrays are parsed from `parseArrayFromLine:465` → `readTabularArray:518` → ..., never from a quoted-string context) confirms safety.

---

### #5 — Tabular Array Builds a Full `List<Object>` Intermediary Per Row  
**File/Lines:** `ToonReader.java:518-591`, specifically lines 559, 566-574

```java
// readTabularArray (lines 556-576):
JsonObject rowObj = new JsonObject();
List<Object> values = readInlineArray(trimmed, colHeadersSize, delimiter);  // line 559
int valuesSize = values.size();
for (int i = 0; i < colHeadersSize && i < valuesSize; i++) {
    // copy values into rowObj
}
```

`readInlineArray` allocates `new ArrayList<>(count)` and its backing `Object[]`, only for those elements to be immediately transferred into `rowObj` by the for-loop. The `List` and its backing array are ephemeral — they exist only to carry values from `readInlineArray` to the calling loop.

**Why it matters:** For a tabular array of 1000 rows × 10 columns, this creates 1000 `ArrayList` instances and 1000 backing `Object[]` arrays (each 10 elements). These are short-lived and will be GC'd quickly, but they generate Young Gen pressure at exactly the point where tabular parsing should be its most efficient.

**Optimization:** Introduce a package-private `readInlineArrayIntoObject(String content, List<String> headers, JsonObject target, char delimiter)` method that parses and populates `target` directly without materializing an intermediary List. This also removes the inner for-loop and the `values.get(i)` calls.

**Risk:** Medium. Changes the internal interface of `readInlineArray`. The method is private so the API is stable, but behavior must be carefully validated against: width mismatches, meta-keys in columns, and strict-mode count checks.

---

### #6 — `appendFieldForParser` Unconditionally Writes `index = null` (JsonObject)  
**File/Lines:** `JsonObject.java:308-315`

```java
public void appendFieldForParser(Object key, Object value) {
    hash = null;
    index = null;     // line 310: always written, even though it is always null in parser flow
    ensureCapacity(size + 1);
    ...
}
```

During parsing, `appendFieldForParser` is the only method called on a `JsonObject` being populated (it is the fast-path append). The `index` field is never set to non-null by this method or by the constructor. The unconditional write `index = null` is a null-to-null store that generates a memory barrier on every field append in the parser hot path.

**Why it matters:** With the benchmark payload (15 fields in `TestData`, 7+ in `NestedData`, repeated 100K times), this is ≥ 2.2 million null stores per benchmark run, all provably redundant. On a modern JIT this may be eliminated, but the field write is visible as a store instruction in profiles when the JIT doesn't optimize it away.

**Optimization:** Add a null check: `if (index != null) index = null;`. Since the field is private and the JIT sees the full class, this also provides a stronger invariant hint.

**Risk:** Negligible. Semantically equivalent — `index` cannot be non-null in the append path. Trivially verified by inspection.

---

### #7 — `validateAndCacheFoldedKey` Makes Two Full Traversals, Allocating a `String[]` on Success  
**File/Lines:** `ToonReader.java:1265-1308`

The method performs:
1. A validation traversal (lines 1270-1293): scans all chars to validate segment identifiers and count segment count.
2. A split traversal (lines 1298-1306): allocates `new String[segCount]` and fills it with `key.substring(segStart, i)` for each segment.

This pair is called from `putValue` (line 1327) for every unquoted key containing `.`. If the `TestData` object uses `@type` meta keys or dotted-path field names, this path is hot.

**Why it matters:** The two-pass design means the string is traversed twice and `segCount` substrings are allocated per folded key. For two-segment keys (the common case), that is 2 substring allocations plus a 2-element `String[]` per call. The old report flagged this as "Medium Priority."

**Optimization:** Merge the two passes into one. Accumulate segment starts/ends in a small fixed-size `int[]` scratch array (reuse as an instance field like `cachedFoldSegments` already is). Only `substring` on confirmed valid segments. Alternatively, since `cachedFoldSegments` is already an instance field (line 105), the `String[]` could be pre-allocated and reused by length-checking against `segCount`.

**Risk:** Low for the single-pass merge. Medium if scratch array is reused across calls (must be careful with concurrent access — but ToonReader is not thread-safe by design anyway).

---

### #8 — `readObject` and `readInlineObject` Are ~130 Lines of Duplicated Logic  
**File/Lines:** `ToonReader.java:270-367` (`readObject`) and `ToonReader.java:775-906` (`readInlineObject`)

Both methods perform: colon search, key extraction, value extraction, bracket-notation detection, quoted-key handling, nested object/array branching, and `putValue` dispatch. Every optimization applied to one must be manually mirrored in the other. The array-notation `StringBuilder` pattern in `readObject` (line 321) uses string concatenation while `readInlineObject` (line 789) uses `new StringBuilder(...)` — an inconsistency that likely emerged from independent edits.

**Why it matters:** Code duplication is not a direct allocation issue, but it multiplies the effort required for every optimization in #1-#4 above, and the inconsistent StringBuilder usage is a latent source of future divergence. It also means JIT inlining budgets are spent on two similar-but-not-identical hot paths.

**Optimization:** Extract a shared `parseKeyValueLine(String trimmed, int colonPos, JsonObject target, int baseIndent)` method. The first-field handling in `readInlineObject` is specific to that case but could be called once with the already-parsed `firstFieldLine`. The subsequent-fields loop could then call the shared helper.

**Risk:** Medium. Any refactor here must preserve subtle differences: `readObject` uses `baseIndent` for nested depth; `readInlineObject` uses `hyphenIndent` and `fieldIndent`. Tests for nested inline objects in list arrays must pass.

---

### #9 — `mergeJsonObjects` Uses `String.valueOf(entry.getKey())` When Keys Are Always Strings  
**File/Lines:** `ToonReader.java:1496-1507`, line 1497

```java
for (Map.Entry<?, ?> entry : source.entrySet()) {
    String key = String.valueOf(entry.getKey());   // line 1497
```

`JsonObject` keys inserted by the parser are always `String` (only `appendFieldForParser` and `putValue` are used, both with `String` keys). `String.valueOf(Object)` checks for null and calls `toString()` — both unnecessary, and `toString()` on a `String` returns `this` but still goes through the vtable.

**Why it matters:** Low frequency (only called during folded-key merging). But the cast to `String` would be cleaner, faster, and self-documenting: `String key = (String) entry.getKey();`.

**Risk:** Negligible. If a non-String key ever reaches here it is a pre-existing bug. An explicit cast catches that bug earlier with a `ClassCastException`.

---

### #10 — `JsonObject.INITIAL_CAPACITY = 16` Oversizes Small Objects  
**File/Lines:** `JsonObject.java:46, 85-86`

```java
private static final int INITIAL_CAPACITY = 16;
// constructor:
keys = new Object[INITIAL_CAPACITY];
values = new Object[INITIAL_CAPACITY];
```

Every `new JsonObject()` allocates two 16-slot `Object[]` arrays (128 bytes of array data plus headers on a 64-bit JVM). The benchmark `NestedData` has 7 fields; most per-row objects in tabular arrays may have even fewer. For objects with ≤ 8 fields, INITIAL_CAPACITY of 16 wastes one full array expansion worth of space from creation.

**Why it matters:** ToonReader creates a `new JsonObject()` per parsed object (lines 271, 557, 726, 776, 1472, 1476). In a document with many small objects, reducing INITIAL_CAPACITY to 8 cuts the allocation size of each `JsonObject` in half (from two 16-element arrays to two 8-element arrays). This is a JsonObject-level change, but ToonReader is the primary driver.

**Risk:** Low. `ensureCapacity` (JsonObject.java:442-447) doubles the array on overflow, so correctness is unaffected. Objects with > 8 fields incur one extra `Arrays.copyOf` — measure this tradeoff with the benchmark's specific payloads before committing.

---

## Why They Matter (Summary Table)

| # | Site | Pattern | Call Frequency | Allocation Per Call |
|---|------|---------|----------------|---------------------|
| 1 | `cacheSubstring` L153 | Create-before-check | Every key + every value + every unescaped string | 1 substring |
| 2 | `readInlineArray` L644/653 | StringBuilder→String per element | N per inline array row | N Strings |
| 3 | `readObject` L318-323 | String concat for bracket-key rebuild | Per array-notation field | 2-5 Strings |
| 4 | `parseColumnHeaders` L482 | `new StringBuilder()` per call | Per tabular array header | 1 SB + N Strings |
| 5 | `readTabularArray` L559 | Intermediary `List<Object>` per row | Per tabular row | 1 ArrayList + Object[] |
| 6 | `appendFieldForParser` L310 | Null-to-null store | Per field per object | 0 alloc, 1 store barrier |
| 7 | `validateAndCacheFoldedKey` L1265 | Double traversal + String[] | Per dotted key | String[] + N substrings |
| 8 | `readObject`/`readInlineObject` | Code duplication | N/A (maintenance) | N/A |
| 9 | `mergeJsonObjects` L1497 | `String.valueOf` on String key | Per folded-key merge | 0 alloc, vtable miss |
| 10 | `JsonObject` constructor | INITIAL_CAPACITY=16 | Per object parsed | 2× Object[16] |

---

## Most Promising Optimization Ideas

### High-Value, Low-Risk
1. **`cacheSubstring` hash-then-probe** (#1): Replace the substring-before-check pattern with a range-hash probe. Compare candidate against `source[start..end]` in-place. Prototype in isolation; the performance gain here is proportional to cache hit rate for short tokens (field names, boolean literals, common numeric strings) — likely very high for repetitive object schemas.

2. **`readInlineArray` index-scan instead of StringBuilder** (#2): Parse `content` directly by tracking `segStart`/`segEnd` positions and calling `trimAsciiRange(content, segStart, segEnd)`. Only fall back to the current StringBuilder path when an unquoted backslash is encountered (rare). This eliminates the per-element `toString()` and is safe because `content` is already materialized.

3. **Array-notation key reassembly via `inlineBuf`** (#3): One `inlineBuf.setLength(0)` + two appends + one `inlineBuf.toString()`. Also change `key.contains("[")` to `key.indexOf('[') >= 0`.

4. **`appendFieldForParser` guarded null write** (#6): One-liner. Zero risk.

5. **`mergeJsonObjects` cast instead of `String.valueOf`** (#9): One-liner. Zero risk.

### Medium-Value, Moderate Work
6. **`parseColumnHeaders` reuse `quoteBuf`** (#4): Replace `new StringBuilder()` with `quoteBuf.setLength(0); StringBuilder current = quoteBuf;`. Verify call graph safety (safe — see analysis above).

7. **Tabular direct-populate method** (#5): New `readInlineArrayIntoObject` private method. High value for TOON documents heavy on tabular arrays.

8. **`validateAndCacheFoldedKey` single-pass with int[] scratch** (#7): Use an instance `int[]` to record segment boundaries in the validation pass, then substring directly in the same loop.

### Structural
9. **Extract `parseKeyValueLine` shared helper** (#8): Reduces duplication and ensures #1-#4 only need to be applied once.

10. **Reduce `JsonObject.INITIAL_CAPACITY` to 8** (#10): Benchmark before/after with the real payload; the gain depends on proportion of small vs large objects.

---

## Risk & Validation Notes

| Change | Risk Level | Validation Approach |
|--------|-----------|---------------------|
| `cacheSubstring` hash-probe | Low | Existing round-trip tests; add a cache-hit-rate counter (removable) |
| `readInlineArray` index-scan | Medium | All `readInlineArray` unit tests; edge cases: empty element, quoted delimiter, backslash inside quoted value, trailing delimiter |
| Array-key reassembly via `inlineBuf` | Low | Array-notation TOON tests; ensure `inlineBuf` is reset before use |
| `appendFieldForParser` guard | Negligible | Compile and run existing suite |
| `mergeJsonObjects` cast | Negligible | Compile and run existing suite |
| `parseColumnHeaders` reuse `quoteBuf` | Low | Tabular array tests; confirm `quoteBuf` is not in use on that call path |
| Tabular direct-populate | Medium | Tabular array tests; strict-mode count checks; meta-key columns |
| `validateAndCacheFoldedKey` single-pass | Low | Folded-key tests; no behavioral change |
| `readObject`/`readInlineObject` refactor | Medium-High | Full test suite pass; focus on nested inline objects inside list arrays |
| `JsonObject.INITIAL_CAPACITY` reduction | Low | Benchmark with real payload first; check for regression on large objects |

**General validation rule:** For every optimization, run:
1. `mvn -pl json-io -q test` (full correctness suite)
2. `JsonPerformanceTest` before and after (both `java` and `maps` modes), 3 runs each side, report median
3. JFR capture to verify allocation-site weights moved in expected direction

---

## Benchmarking Gaps

### Critical Gap: No TOON Tests in the Automated JUnit Suite
`PerformanceBenchmarkTest.java` (lines 87-334) tests only `JsonIo.toJson`/`JsonIo.toJava` (JSON format). It never calls `JsonIo.toToon`/`JsonIo.fromToon`/`JsonIo.fromToonToMaps`. This means:
- CI cannot catch TOON-specific performance regressions automatically.
- The performance threshold assertions (e.g., `avgTimeMs < 2.0` at line 134) give no signal for TOON.

**Recommendation:** Add `benchmarkToonSerialization()` and `benchmarkToonDeserialization()` test methods to `PerformanceBenchmarkTest.java` that mirror the existing JSON benchmarks but call `JsonIo.toToon`/`JsonIo.fromToon`. The initial thresholds should be set at 2-3× the baseline to act as regression guards rather than aspirational targets.

### Gap: No Tabular-Array Specific Benchmark
`JsonPerformanceTest.java` uses `TestData` which has simple inline arrays but no tabular-format arrays. The tabular path (`readTabularArray` + `readInlineArray` per row) is a distinct hot path that the current benchmark does not exercise at scale.

**Recommendation:** Add a `TestData` variant with a tabular-format field (e.g., 100-row × 5-column tabular array) and benchmark it separately. This would directly quantify the impact of optimization #5 (tabular intermediary List elimination).

### Gap: No JMH Micro-Benchmarks
Both `JsonPerformanceTest` and `PerformanceBenchmarkTest` use `System.nanoTime()` loops with manual warmup. JMH (`org.openjdk.jmh`) provides reproducible, JIT-aware benchmarks with statistical confidence intervals and proper warmup. The current approach is adequate for macro-level comparisons but insufficient for validating micro-optimizations like #1 (cache probe) or #2 (index-scan inline array).

**Recommendation:** Add a minimal JMH module (or JMH dependency in the test scope) with benchmarks for `ToonReader.readObject` and `ToonReader.readInlineArray` on representative payloads. This enables reliable A/B comparison for individual optimizations.

### Gap: No Benchmark for Maps-Mode Specifically
`JsonPerformanceTest.testMapsOnly()` exists as a manual main-class test but is absent from the automated JUnit suite. Maps-only mode (`fromToonToMaps`) skips Java object resolution and exposes pure ToonReader parse throughput — it is the most direct measure of ToonReader performance independent of the Resolver.

**Recommendation:** Mirror `testMapsOnly()` in `PerformanceBenchmarkTest` as a JUnit test with a threshold assertion.

---

## Estimated Impact

| Group | Items | Likely Throughput Gain (Read) |
|-------|-------|-------------------------------|
| Quick wins (items #3, #6, #9) | 3 one-liners | 2–5% |
| Medium wins (items #1, #2, #4) | 3 focused refactors | 15–25% |
| Structural (items #5, #7, #8, #10) | 4 design changes | 10–20% additional |
| Full combined | All 10 | 25–45% cumulative (workload dependent) |

These estimates assume the benchmark payload (TestData with arrays) where inline array parsing and field extraction dominate. Payloads that are deeply nested but field-sparse will see a higher proportion of gain from #1 and #10; array-heavy payloads will see more from #2 and #5.

---

## Effort Estimate

| Work | Effort |
|------|--------|
| Items #3, #6, #9 (one-liners) | 1–2 hours |
| Items #1, #2, #4 | 1–2 days |
| Items #5, #7 | 1–3 days |
| Item #8 (structural refactor) | 3–5 days (includes thorough testing) |
| Item #10 + benchmarking gaps | 1–2 days |
| **Total** | **~2 engineering weeks** |
