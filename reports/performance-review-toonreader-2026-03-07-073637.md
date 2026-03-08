# Performance Review Plan
Generated: 2026-03-07-073637 UTC
Scope: /Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/ToonReader.java

## Executive Summary
`ToonReader` has improved materially today: substring cache hits no longer allocate, main object parsing now uses buffer-direct colon/key/value extraction, trimmed line strings are created lazily, and tabular rows no longer build an intermediate `List`. The remaining performance gap is now narrower and more focused: current benchmark results still put TOON read at roughly **6111 ms** vs **5146 ms** for full Java resolution and **4427 ms** vs **3287 ms** for maps-only parsing over 100k operations, so the next gains are likely to come from removing the last per-token `String` materialization, making parser-created `JsonObject`s capacity-aware, and broadening the buffer-direct parsing approach beyond the main object reader.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Unquoted inline/tabular tokens still become `String` objects before scalar parsing**  
      `parseRowIntoObject()` and `readInlineArray()` now have a no-quote/no-escape fast path, but they still call `trimAsciiRange(content, ...)` and then `readScalar(...)`, which means every token is still materialized as a `String` even when it is a simple number, boolean, or `null` (`json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:622-645`, `710-725`, `218-234`, `1020-1067`).
- [ ] **Parser-created `JsonObject`s still use generic default capacity and generic growth**  
      Tabular rows now parse directly into `JsonObject`, but each row still starts with the default 16-slot backing arrays (`ToonReader.java:576-580`; `json-io/src/main/java/com/cedarsoftware/io/JsonObject.java:45-49`, `84-88`). The parser already knows the expected row width, but cannot pass it through. Growth still uses doubling copies (`JsonObject.java:442-447`).
- [ ] **Non-object line-processing paths still allocate via `peekTrimmed()`**  
      `readObject()` now avoids full-line `String` materialization on its main path by using `findColonInBuf()` and `trimAsciiRangeBuf()` (`ToonReader.java:320-335`, `1307-1391`), but root dispatch, list parsing, tabular lookahead, nested checks, and inline-object parsing still rely on `peekTrimmed()`, which allocates a trimmed `String` from `lineBuf` when needed (`ToonReader.java:548-570`, `602-614`, `750-772`, `1198-1248`).

### High Priority Issues
- [ ] **`readInlineObject()` still runs largely on the older string-slicing path**  
      Main object parsing is now buffer-direct, but inline-object parsing still uses `findColonPosition(String)`, `trimAsciiRange(String, ...)`, and repeated string operations (`ToonReader.java:845-973`, `1397-1437`). That leaves a hot object path behind the current optimization level and duplicates future work.
- [ ] **Folded-key validation and expansion still rescans and allocates per segment**  
      `validateAndCacheFoldedKey()` validates segments, counts them, then allocates a `String[]` and uses `substring(...)` in a second pass (`ToonReader.java:1465-1508`). `putWithKeyExpansion()` then does repeated nested `get`/`put` work (`ToonReader.java:1658-1692`).
- [ ] **`appendFieldForParser()` still performs redundant per-field state clearing**  
      Parser append skips duplicate-key search, but still executes `index = null` on every append whether or not an index exists (`JsonObject.java:308-315`). This is a small cost by itself, but it is in the tightest parser insertion path and pairs naturally with parser-specific capacity tuning.

### Medium Priority Issues
- [ ] **`findColonPosition(String)` still does full quoted-line rescans outside the buffer-direct path**  
      The buffer-direct helper removed this from `readObject()`, but the string-based helper is still used in arrays, inline objects, and other side paths (`ToonReader.java:1397-1437`).
- [ ] **Broader line predicates still depend on `String` APIs**  
      `isArrayStart()`, delimiter checks, `startsWith("-")`, and other line decisions still rely on trimmed `String` operations in several branches. There is room for more buffer/range-aware helpers once the highest-value token work is addressed.
- [ ] **TOON benchmark coverage remains too coarse for narrow parser work**  
      `JsonPerformanceTest` is useful for end-to-end comparisons but does not isolate inline arrays, wide tabular rows, folded keys, or parser object growth (`json-io/src/test/java/com/cedarsoftware/io/JsonPerformanceTest.java`).

## Performance Metrics
### Current State
- Average response time:
  - Full Java TOON read: **6111.44 ms / 100000 ops** (**0.0611 ms/op**)
  - Maps-only TOON read: **4426.78 ms / 100000 ops** (**0.0443 ms/op**)
- Comparable JsonIo read:
  - Full Java resolution: **5146.34 ms / 100000 ops**
  - Maps-only: **3287.13 ms / 100000 ops**
- Latest available JFR context: `reports/performance-review-jfr-comparison-2026-03-07-054829.md`
  - `ToonReader` execution sample share: **17.41%**
  - `ToonReader` allocation sample share: **21.99%**
- Key bottlenecks:
  - token-to-`String` materialization in inline/tabular fast paths
  - generic `JsonObject` growth/copying
  - residual `peekTrimmed()` string creation outside `readObject()`
  - folded-key expansion and duplicated inline-object parsing

### Target State
- Full Java TOON read: **< 5600 ms / 100000 ops**
- Maps-only TOON read: **< 4000 ms / 100000 ops**
- `ToonReader` allocation sample share: **< 18%**
- `ToonReader` execution sample share: **< 16%**
- Remove the dominant per-token `String` materialization for common unquoted tokens

## Optimization Plan
### Quick Wins (1-3 days)
1. Add a **range-based scalar fast path** so unquoted/unescaped tokens in `parseRowIntoObject()` and `readInlineArray()` can parse numbers/booleans/null without first becoming a `String`.
2. Add `JsonObject(int initialCapacity)` and use known row width for tabular row objects.
3. Make `appendFieldForParser()` skip redundant `index = null` writes when `index` is already null.

### Major Optimizations (1-2 weeks)
1. Refactor `readInlineObject()` to share the newer buffer-direct object field parsing machinery used by `readObject()`.
2. Collapse folded-key validation and splitting into a single pass with less substring churn.
3. Add buffer-aware helpers for common line predicates so more branches can avoid forcing `peekTrimmed()`.

### Architectural Changes (2-4 weeks)
1. Push ToonReader further toward slice/range-based parsing across all hot branches.
2. Add TOON-specific microbenchmarks for inline arrays, tabular rows, folded keys, and parser object construction.
3. Capture fresh JFR recordings after the next optimization batch, since the latest JFR predates the newest line-materialization changes.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Optimize unquoted token parsing in `parseRowIntoObject()` and `readInlineArray()`.
- Pre-size parser-created `JsonObject`s where width is already known.
- Re-run `JsonPerformanceTest maps` and capture a new JFR.

### Phase 2: Algorithm Improvements
- Port inline-object parsing onto the buffer-direct field path.
- Replace folded-key two-pass splitting with single-pass boundary tracking.
- Expand buffer-direct line checks to more branches that currently rely on `peekTrimmed()`.

### Phase 3: System-level Changes
- Broaden slice/range-based parsing across the remaining TOON parser branches.
- Add targeted performance regression coverage.
- Use repeated JFR + benchmark deltas to validate each optimization batch.

## Performance Testing Plan
- Keep using:
  - `com.cedarsoftware.io.JsonPerformanceTest both`
  - `com.cedarsoftware.io.JsonPerformanceTest maps`
- Add targeted benchmarks for:
  - inline arrays of unquoted primitives
  - wide tabular rows with repeated scalar cells
  - dotted-key-heavy objects
  - inline-object-heavy arrays
- Validate correctness with:
  - `ToonReaderTest`
  - `ToonDelimiterTest`
  - `ToonSpecDecodeFixturesTest`
  - `ToonTokenComparisonTest`
- Capture a new JFR after the next parser optimization batch.

## Risk Assessment
### Safe Optimizations
- `JsonObject` pre-sizing
- redundant parser-insertion state cleanup removal
- narrow unquoted-token fast paths with fallback to current behavior

### Moderate Risk
- range-based scalar parsing
- buffer-direct line predicate helpers
- folded-key single-pass validation/splitting

### High Risk
- migrating all inline-object parsing to the buffer-direct path
- broad slice/range parser refactors across all TOON branches
- changes that could affect quoting, escaping, or strict-mode semantics

## Recommendations
- Focus next on **range-based scalar parsing for unquoted tokens**; it is the highest-leverage remaining parser-local opportunity.
- Pair that with **parser-aware `JsonObject` sizing**, since tabular row width is already known and easy to exploit.
- Do not repeat stale work on `cacheSubstring()` or tabular intermediate-list removal; those wins are already in place.
- Capture a fresh JFR after the next change, because the latest profile data is already partially stale relative to the current parser.

## Estimated Impact
- Quick wins: **6-12%** TOON read improvement
- Major optimizations: **12-22%** additional improvement
- Full plan: **20-35%** cumulative TOON read improvement from the current baseline

## Effort Estimate
- Quick wins: **1-3 engineering days**
- Major optimizations: **1-2 engineering weeks**
- Complete plan: **2-4 engineering weeks**
