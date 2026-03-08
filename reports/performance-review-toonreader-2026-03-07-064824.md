# Performance Review Plan
Generated: 2026-03-07-064824 UTC
Scope: /Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/ToonReader.java

## Executive Summary
`ToonReader` is in better shape than it was earlier on 2026-03-07: substring cache hits no longer allocate, line metadata is now loaded lazily, and tabular rows no longer route through an intermediate `List`. Even after those wins, the current benchmark still leaves TOON read behind JsonIo read by about **18.8%** for full Java resolution and **34.7%** for maps-only parsing, which means the remaining bottlenecks are now concentrated in token materialization, parser object growth, and repeated field-processing overhead rather than the earlier substring/line-loading issues. The best next move is a no-escape fast path for inline/tabular token parsing, followed by parser-oriented `JsonObject` pre-sizing and a cleanup of folded-key / duplicated field-parsing paths.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Inline/tabular token parsing still materializes one `String` per token**  
      `parseRowIntoObject()` and `readInlineArray()` still funnel each token through `StringBuilder`, `trimAscii(StringBuilder)`, and `readScalar(...)` (`json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:622-672`, `687-734`, `199-216`). Dense arrays and tabular rows therefore still pay per-cell allocation even after the earlier substring fixes.
- [ ] **Parser-created `JsonObject`s still over-allocate and resize more than necessary**  
      Tabular rows are now parsed directly into `JsonObject`, but each row still starts from the default 16-slot backing arrays (`ToonReader.java:576-580`; `json-io/src/main/java/com/cedarsoftware/io/JsonObject.java:45-49`, `84-88`). The parser append path also clears `index` on every append even though parser-created objects typically have not built one (`JsonObject.java:308-315`), and growth still doubles arrays (`JsonObject.java:442-447`).
- [ ] **Consumed lines that require content still allocate a trimmed `String`**  
      `hasLine()` removed eager string creation, but any path that actually inspects line text still triggers `peekTrimmed()` and therefore `new String(lineBuf, ...)` (`ToonReader.java:1198-1248`). That means line allocation pressure is reduced, not eliminated, and the remaining line-string work is still spread across object, list, and tabular parsing.

### High Priority Issues
- [ ] **Folded-key handling is still two-pass and still allocates per segment**  
      `validateAndCacheFoldedKey()` validates segments, counts them, then allocates a `String[]` and creates `substring(...)` entries in a second pass (`ToonReader.java:1330-1373`). `putWithKeyExpansion()` then performs repeated nested `get`/`put` work while walking the expanded path (`ToonReader.java:1523-1558`).
- [ ] **Object field parsing is still duplicated across the two hottest object readers**  
      `readObject()` and `readInlineObject()` still repeat nearly the same key slicing, array-notation handling, nested-value branching, and scalar dispatch (`ToonReader.java:294-389`, `845-973`). This duplication is not the biggest direct allocator today, but it doubles future optimization work and keeps the hot path harder to tune.
- [ ] **Simple parser-side checks still force full `String` APIs**  
      Even after lazy line loading, most downstream logic still operates on `String` content for `startsWith`, `indexOf`, `findColonPosition`, and `isArrayStart` decisions (`ToonReader.java:548-570`, `602-614`, `750-772`, `1244-1248`). A buffer/range-aware fast path for common cases could remove more line-level string churn.

### Medium Priority Issues
- [ ] **`findColonPosition()` still rescans quoted lines character-by-character**  
      The method has a good early fast path, but any line with quotes before the first colon still falls back to a full stateful scan (`ToonReader.java:1260-1300`). This work appears in object, list, and tabular parsing branches.
- [ ] **`appendFieldForParser()` still does an unconditional `index = null` store**  
      That store is cheap, but it happens for every parsed field and is usually redundant for parser-created objects that never built an index (`JsonObject.java:308-315`). It is a low-risk micro-optimization, especially if paired with parser-specific pre-sizing.
- [ ] **TOON-specific benchmark coverage is still too coarse**  
      `JsonPerformanceTest` gives useful end-to-end comparisons, but it does not isolate inline arrays, tabular rows, dotted keys, or parser object growth (`json-io/src/test/java/com/cedarsoftware/io/JsonPerformanceTest.java`). That makes it harder to validate narrow parser optimizations before/after changes.

## Performance Metrics
### Current State
- Benchmark source: fresh run of `com.cedarsoftware.io.JsonPerformanceTest both` on 2026-03-07 against the current workspace build
- Average response time:
  - Full Java TOON read: **6111.44 ms / 100000 ops** = **0.0611 ms/op**
  - Maps-only TOON read: **4426.78 ms / 100000 ops** = **0.0443 ms/op**
  - Comparable JsonIo read: **5146.34 ms / 100000 ops** full resolution and **3287.13 ms / 100000 ops** maps-only
- Memory usage: the latest available JFR comparison (captured before the most recent lazy-line-materialization change) still showed `ToonReader` allocation share at **21.99%**, down from **30.32%**, with remaining weight concentrated in line strings, token strings, and object-array growth (`reports/performance-review-jfr-comparison-2026-03-07-054829.md`)
- CPU utilization: no direct OS CPU percentage is captured by the repository harness; the latest available JFR showed `ToonReader` execution sample share at **17.41%** (`999/5737`)
- Key bottlenecks: token materialization in `parseRowIntoObject()` / `readInlineArray()`, parser-side `JsonObject` allocation/growth, line-string materialization via `peekTrimmed()`, and folded-key expansion

### Target State
- Full Java TOON read: **< 5600 ms / 100000 ops**
- Maps-only TOON read: **< 4000 ms / 100000 ops**
- `ToonReader` allocation sample share: **< 18%**
- `ToonReader` execution sample share: **< 16%**
- Eliminate the dominant per-token `StringBuilder -> String -> readScalar` path for common unquoted tokens

## Optimization Plan
### Quick Wins (1-3 days)
1. Add a **no-quote / no-escape fast path** in `parseRowIntoObject()` and `readInlineArray()` that parses tokens from start/end indexes in the original line, falling back to the current `StringBuilder` path only when quotes or escapes are encountered.
2. Add a parser-oriented `JsonObject(int initialCapacity)` constructor and use the known tabular column count when building row objects.
3. Make `JsonObject.appendFieldForParser()` avoid redundant `index = null` writes when no index exists yet.

### Major Optimizations (1-2 weeks)
1. Introduce shared field-parsing helpers for `readObject()` and `readInlineObject()` so key/value slicing, array-notation handling, and nested-value branching are implemented and optimized once.
2. Collapse folded-key validation/splitting into a single pass with reusable boundary storage instead of repeated scanning plus `substring(...)`.
3. Extend line parsing beyond lazy loading by adding range/buffer-aware helpers for common `isArrayStart` / delimiter / colon tests before forcing `peekTrimmed()` to allocate a `String`.

### Architectural Changes (2-4 weeks)
1. Move ToonReader further toward **slice/range-based parsing** so keys and scalar fragments can remain buffer-backed until downstream APIs require a `String`.
2. Add TOON-specific microbenchmarks for inline arrays, tabular rows, dotted keys, and parser object assembly.
3. Add a repeatable JFR/perf harness for TOON read workloads so each change is validated with both throughput and allocation deltas.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Attack token materialization first because it affects both inline arrays and tabular rows.
- Pre-size parser-created row objects and trim redundant parser append work.
- Re-run `JsonPerformanceTest maps` and capture a fresh JFR after the token fast path lands.

### Phase 2: Algorithm Improvements
- Refactor duplicated object field parsing into shared helpers.
- Replace two-pass folded-key splitting with single-pass boundary tracking.
- Introduce range-aware helpers for common line predicates to reduce `peekTrimmed()` pressure.

### Phase 3: System-level Changes
- Push more parsing onto buffer slices/ranges instead of eagerly created `String`s.
- Expand the benchmark suite so TOON parser regressions are detectable without full end-to-end runs.
- Add repeatable profiling scripts so the team can compare JFR deltas after each optimization batch.

## Performance Testing Plan
- Keep using `JsonPerformanceTest both` and `JsonPerformanceTest maps` for top-level throughput tracking.
- Add targeted benchmarks for:
  - inline arrays with unquoted primitive tokens
  - tabular arrays with repeated short cells
  - dotted-key-heavy documents
  - small object / wide tabular row construction
- Capture a fresh JFR after the next optimization batch because the latest available JFR predates the newest lazy-line-materialization change.
- Validate semantics with:
  - `json-io/src/test/java/com/cedarsoftware/io/ToonReaderTest.java`
  - `json-io/src/test/java/com/cedarsoftware/io/ToonDelimiterTest.java`
  - `json-io/src/test/java/com/cedarsoftware/io/ToonSpecDecodeFixturesTest.java`
  - `json-io/src/test/java/com/cedarsoftware/io/ToonTokenComparisonTest.java`

## Risk Assessment
### Safe Optimizations
- Parser object pre-sizing
- Redundant field/index state micro-optimizations in `JsonObject`
- Narrow fast paths for clearly unquoted/unescaped inline tokens

### Moderate Risk
- Shared field-parsing extraction between `readObject()` and `readInlineObject()`
- Single-pass folded-key validation/splitting
- Buffer-aware helpers that avoid `String` creation for common line predicates

### High Risk
- Broad slice-based parser refactors touching most key/value extraction code
- Any change that could alter strict-mode validation, quoting, or escape handling
- Entry-path buffering/lifecycle changes outside ToonReader’s current parser core

## Recommendations
- Treat the previous substring-cache and lazy-line-loading work as completed wins and focus the next batch on token parsing plus parser object growth.
- Do **not** spend the next cycle on more `cacheSubstring()` tuning; its big win is already in place.
- Capture a new JFR after the next optimization because the latest profile data still predates the most recent line-loading refactor.
- Add TOON-specific microbenchmarks before attempting a broader slice-based refactor so the next changes can be validated in isolation.

## Estimated Impact
- Quick wins: **8-15%** TOON read improvement, mostly from lower token-allocation pressure and less parser object churn
- Major optimizations: **15-25%** additional improvement on top of the quick wins
- Full plan: **25-40%** cumulative TOON read improvement from the current post-optimization baseline

## Effort Estimate
- Quick wins: **1-3 engineering days**
- Major optimizations: **1-2 engineering weeks**
- Complete plan: **2-4 engineering weeks**
