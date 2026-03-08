# Performance Review Plan
Generated: 2026-03-07-051326 UTC
Scope: /Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/ToonReader.java

## Executive Summary
`ToonReader` has already received several worthwhile optimizations in 4.97.0, but the current implementation is still primarily allocation-bound rather than compute-bound. The biggest remaining costs are eager substring materialization in the key/value hot path, per-element string creation in inline/tabular parsing, and repeated small-object assembly overhead. A fresh benchmark run on 2026-03-07 still shows TOON read behind JsonIo read by about 22% for full Java resolution and about 37% for maps-only parsing, so there is meaningful headroom without changing TOON semantics.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Substring cache still allocates before it can hit**  
      `cacheSubstring()` creates a new substring before checking whether the cache already contains the value (`json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:153-156`). That path is exercised from `trimAsciiRange()` (`ToonReader.java:193-209`), object field slicing (`ToonReader.java:305-312`, `852-858`), and the quoted-string fast path (`ToonReader.java:969-972`).
- [ ] **Inline arrays create a `String` per element before scalar parsing**  
      `readInlineArray()` accumulates each token in `inlineBuf`, then `trimAscii(StringBuilder)` materializes a `String` for every element (`ToonReader.java:614-654`, `174-190`). This is especially costly for tabular rows and dense inline arrays.
- [ ] **Tabular rows are parsed twice through an intermediate list**  
      `readTabularArray()` first parses each row into `List<Object> values = readInlineArray(...)` and then copies the values into a `JsonObject` (`ToonReader.java:518-591`, especially `557-574`). That adds avoidable short-lived allocations and a second per-column pass.

### High Priority Issues
- [ ] **Folded-key handling is two-pass and allocates per segment**  
      `validateAndCacheFoldedKey()` validates/counts segments first, then allocates a `String[]` and creates substrings in a second pass (`ToonReader.java:1265-1308`). `putWithKeyExpansion()` then performs repeated nested lookups and insertions while expanding the path (`ToonReader.java:1458-1479`).
- [ ] **Object field parsing logic is duplicated across two hot paths**  
      `readObject()` and `readInlineObject()` repeat nearly identical key/value parsing, field+array notation handling, and nested-structure branching (`ToonReader.java:270-366`, `775-905`). This duplication makes it harder to optimize once and benefit everywhere.
- [ ] **Parser-side `JsonObject` assembly is still a little heavy for small TOON objects**  
      Every `JsonObject` starts with two 16-slot arrays (`json-io/src/main/java/com/cedarsoftware/io/JsonObject.java:46-49`, `84-88`), and `appendFieldForParser()` clears `index` on every append even though parser-created objects typically never built one (`JsonObject.java:308-315`).

### Medium Priority Issues
- [ ] **Quoted colon detection still rescans full lines**  
      `findColonPosition()` does a full stateful scan whenever a quote appears before the first colon (`ToonReader.java:1197-1236`). That work is repeated across object, list, and tabular branches (`ToonReader.java:299`, `550`, `607`, `713`, `729`, `779`, `846`).
- [ ] **Potential entry-path setup gap versus JSON parsing**  
      JSON string parsing borrows reusable reader buffers from `BUFFER_RECYCLER` (`json-io/src/main/java/com/cedarsoftware/io/JsonIo.java:1097-1101`), while TOON string parsing goes through `new StringReader(...)` and `new ToonReader(...)` (`JsonIo.java:1343-1349`). This is likely smaller than the internal hot-path costs, but it is worth measuring once parser allocations come down.
- [ ] **Decimal/exponent parsing still falls back to the generic numeric parser**  
      `parseNumber()` has a good integer fast path, but decimal and exponent forms still route through `MathUtilities.parseToMinimalNumericType()` (`ToonReader.java:1077-1084`). That is probably secondary to string churn, but it remains a measurable optimization candidate for number-heavy TOON.

## Performance Metrics
### Current State
- Benchmark source: manual run of `com.cedarsoftware.io.JsonPerformanceTest both` on 2026-03-07 using the current `json-io` test/runtime classpath
- Average response time:
  - Full Java TOON read: **6166.853 ms / 100000 ops** = **0.0617 ms/op**
  - Maps-only TOON read: **4500.283 ms / 100000 ops** = **0.0450 ms/op**
  - Comparable JsonIo read: **0.0504 ms/op** full resolution, **0.0329 ms/op** maps-only
- Memory usage: no MB baseline is captured by the current repository harness, but code inspection shows the read path is allocation-heavy because of substring creation in `cacheSubstring()`, per-element `String` creation in `readInlineArray()`, and per-row intermediary lists in `readTabularArray()`
- CPU utilization: no direct CPU percentage is captured by the current harness; wall-clock results show TOON read is still **1.22x** JsonIo for full resolution and **1.37x** JsonIo for maps-only, which is consistent with CPU and GC time being spent inside parser allocation hot paths
- Key bottlenecks: eager substring materialization, inline/tabular token allocation, folded-key expansion overhead, and small-object assembly churn

### Target State
- Average response time:
  - Full Java TOON read: **< 5200 ms / 100000 ops** (**< 0.0520 ms/op**)
  - Maps-only TOON read: **< 3800 ms / 100000 ops** (**< 0.0380 ms/op**)
- Memory usage: establish an allocation baseline with JFR or JMH and reduce normalized allocation/op by **25-35%**
- CPU utilization: reduce TOON read cost so it stays under **1.15x JsonIo** for full resolution and under **1.20x JsonIo** for maps-only parsing
- Key bottlenecks: remove allocation from cache-hit substring paths and eliminate per-row/per-element intermediary containers in the common TOON array paths

## Optimization Plan
### Quick Wins (1-3 days)
1. Rework `cacheSubstring()` so it probes the cache from a source range before allocating a new substring.
2. Add a no-escape fast path to `readInlineArray()` that parses directly from the original `content` string via start/end indexes instead of `StringBuilder -> String` per token.
3. Remove redundant parser-side work in `JsonObject.appendFieldForParser()` and benchmark whether smaller parser-oriented object capacity helps row-heavy TOON inputs.

### Major Optimizations (1-2 weeks)
1. Parse tabular rows directly into `JsonObject` so `readTabularArray()` no longer creates `List<Object>` for each row.
2. Factor shared field parsing out of `readObject()` and `readInlineObject()` so key slicing, field+array notation, and nested-value branching are optimized in one place.
3. Convert folded-key validation/splitting into a single-pass routine with reusable boundary storage and fewer intermediate strings.

### Architectural Changes (2-4 weeks)
1. Introduce range/slice-based parsing for key and value fragments so `ToonReader` can defer `String` creation until a downstream API really needs it.
2. Evaluate a TOON-side reusable input-buffer strategy similar to the JSON path once internal parser allocations have been reduced.
3. Add a dedicated TOON microbenchmark suite for field parsing, inline arrays, tabular arrays, dotted keys, and allocation tracking.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Fix the substring cache-hit path first because it affects nearly every parsed key and many parsed values.
- Add an inline-array fast path for the common no-escape case.
- Benchmark parser-side `JsonObject` micro-optimizations before making broader structural changes.

### Phase 2: Algorithm Improvements
- Remove the tabular-row intermediate list and parse directly into the final row object.
- Consolidate duplicated object-field parsing logic so one optimized implementation covers both standard and inline-object flows.
- Replace two-pass folded-key validation with a single-pass expansion-friendly implementation.

### Phase 3: System-level Changes
- Move the parser toward slice-based token handling to reduce transient `String` creation across the board.
- Measure whether TOON should mirror JSON's entry-point buffer recycling.
- Add automated TOON-focused performance regression coverage so improvements stay durable.

## Performance Testing Plan
- Baseline current throughput with `JsonPerformanceTest` in both full-resolution and maps-only modes.
- Add TOON-specific microbenchmarks for:
  - object field parsing (`cacheSubstring()` / `trimAsciiRange()` heavy workloads)
  - inline arrays with and without quoted/escaped elements
  - tabular arrays with realistic row counts and meta columns
  - folded keys with and without array suffixes
- Capture allocation profiles with JFR or JMH `gc.alloc.rate.norm` before and after each optimization batch.
- Validate semantic safety with existing correctness suites:
  - `json-io/src/test/java/com/cedarsoftware/io/ToonReaderTest.java`
  - `json-io/src/test/java/com/cedarsoftware/io/ToonDelimiterTest.java`
  - `json-io/src/test/java/com/cedarsoftware/io/ToonSpecDecodeFixturesTest.java`
- Extend `PerformanceBenchmarkTest` or add a dedicated TOON benchmark because the current class does not exercise `toToon()` / `fromToon()`.

## Risk Assessment
### Safe Optimizations
- Cache-hit improvements inside `cacheSubstring()` that preserve returned values exactly
- No-escape fast path in `readInlineArray()` with a fallback to the current escape-aware path
- Minor `JsonObject` parser-append micro-optimizations that do not change insertion order or duplicate-key behavior

### Moderate Risk
- Direct tabular-row parsing without the current intermediate `List<Object>`
- Shared object-field parsing extracted from `readObject()` and `readInlineObject()`
- Single-pass folded-key validation and expansion logic

### High Risk
- Large-scale slice/range-based parser refactoring touching most key/value extraction paths
- Any buffer-lifecycle changes around TOON entry points
- Changes that could subtly affect quoted keys, escaped delimiters, folded-key conflicts, or strict-mode error behavior

## Recommendations
- Prioritize allocation reduction over tiny arithmetic or branch micro-optimizations; the current read path still looks allocation-dominated.
- Keep TOON correctness regression tests close to every optimization step, especially around quoted strings, escaped delimiters, folded keys, and strict-mode validation.
- Add TOON-specific performance coverage to the automated benchmark suite; the current `PerformanceBenchmarkTest` does not cover TOON at all.
- Use paired throughput and allocation measurements for every batch so a wall-clock win is not hiding a memory regression.

## Estimated Impact
- Quick wins: **8-15%** read-side improvement with a noticeable drop in transient allocation pressure
- Major optimizations: **15-25%** additional read-side improvement, especially on row-heavy and dotted-key workloads
- Full plan: **25-45%** cumulative TOON read improvement, enough to close most of the current gap to JsonIo on the repository benchmark

## Effort Estimate
- Quick wins: **1-3 engineering days**
- Major optimizations: **1-2 engineering weeks**
- Complete plan: **2-4 engineering weeks**
