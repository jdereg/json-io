# Performance Review Plan
Generated: 2026-03-01-155821
Scope: /Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/ToonReader.java (plus latest JsonPerformanceTest JFR snapshots)

## Executive Summary
ToonReader remains the dominant read-side bottleneck in TOON parsing, with line/token string creation and substring caching still driving most CPU and allocation pressure.  
Latest benchmark run (`reports/toonreader-analysis-benchmark-20260301-155655.log`) shows Toon read performance still behind JsonIo and Jackson on both full-object and maps-only modes.  
The highest-impact next step is to push ToonReader further toward range-based parsing (char buffer + offsets) to reduce `String` and byte-array churn without changing TOON behavior.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Per-line String creation in hot line path**  
      `peekLine()` still materializes a new `String` per line (`ToonReader.java:1106-1142`), with JFR showing top allocation sites at `StringUTF16.compress` and `peekLine` string creation.
- [ ] **Substring/caching churn in key/value extraction**  
      `trimAsciiRange()` + `cacheSubstring()` + `cacheString()` (`ToonReader.java:126-186`) are repeatedly hit from `readObject`/`readInlineObject` and drive `Arrays.copyOfRangeByte` + `StringLatin1.newString` allocation hotspots.
- [ ] **Parser-instance cache array allocations**  
      `stringCache`, `numberCacheKeys`, and `numberCacheValues` are allocated per `ToonReader` instance (`ToonReader.java:95-98`), and constructor sites appear prominently in allocation samples.

### High Priority Issues
- [ ] **Inline/tabular row parsing allocates intermediate objects**  
      `readTabularArray()` builds `List<Object>` rows first (`ToonReader.java:536`) and then maps to `JsonObject`, adding avoidable temporary structures.
- [ ] **Repeated scalar trimming + conversion in arrays**  
      `readInlineArray()` repeatedly calls `trimAscii(StringBuilder)` + `readScalar(...)` (`ToonReader.java:590-630`), causing repeated temporary string creation.
- [ ] **Folded-key validation splits and allocates per dotted key**  
      `validateAndCacheFoldedKey()` performs validation + `substring` splitting (`ToonReader.java:1239-1282`) for each folded key path.

### Medium Priority Issues
- [ ] **Colon detection + quote scan rework opportunity**  
      `findColonPosition()` (`ToonReader.java:1171-1211`) still performs full scans for quoted lines and is called across object/inline parsing branches.
- [ ] **Decimal parse fallback still allocates char arrays**  
      `parseNumber()` fallback to `MathUtilities.parseToMinimalNumericType` (`ToonReader.java:1051-1053`) remains an allocation source for floating/exp forms.

## Performance Metrics
### Current State
- Benchmark source: `reports/toonreader-analysis-benchmark-20260301-155655.log`
- **Full Java Resolution**
  - JsonIo Read: **5330.92 ms**
  - Toon Read: **6759.00 ms** (≈26.8% slower than JsonIo)
  - Jackson Read: **1940.44 ms**
- **Maps Only**
  - JsonIo Read: **3343.71 ms**
  - Toon Read: **5002.76 ms** (≈49.6% slower than JsonIo)
  - Jackson Read: **2085.99 ms**
- Latest JFR (`JsonPerformanceTest_2026_03_01_155155.jfr`)
  - ToonReader execution sample share: **18.67%** (`1069/5727`)
  - ToonReader allocation sample share: **22.74%** (`3777/16607`)
  - Top ToonReader methods by samples: `readLineRaw`, `cacheString`, `trimAsciiRange`, `cacheSubstring`, `peekLine`, `readObject`
  - Top ToonReader allocation sites:
    - `peekLine -> StringUTF16.compress`
    - `cacheSubstring -> StringLatin1.newString`
    - `cacheSubstring -> Arrays.copyOfRangeByte`

### Target State
- Full Java Toon read: **< 5800 ms**
- Maps-only Toon read: **< 4200 ms**
- ToonReader allocation sample share: **< 17%**
- Reduce top `peekLine`/`cacheSubstring` allocation weights by **30%+**

## Optimization Plan
### Quick Wins (1-3 days)
1. Add a non-materializing fast path for blank/comment/indent-only lines so `peekLine()` avoids `String` creation for those cases.
2. Reuse parser caches across reads (pool/thread-local strategy) to cut constructor-time array allocations.
3. Tighten `cacheString()` heuristics so obviously unique tokens bypass cache slot contention.

### Major Optimizations (1-2 weeks)
1. Refactor ToonReader token extraction to operate on `lineBuf` ranges (start/end) and materialize `String` only when required by downstream APIs.
2. Remove `List<Object>` intermediary in tabular rows by parsing directly into `JsonObject` columns.
3. Add a decimal/exp numeric fast path that avoids generic fallback overhead for common numeric forms.

### Architectural Changes (2-4 weeks)
1. Introduce reusable parse context objects for repeated deserialization loops to reduce parser setup allocations.
2. Add a segmented token representation (lightweight slice object) to reduce duplicate key/value string creation across parse phases.
3. Evaluate a parser-mode that batches object field insertion for large rows/maps to reduce map growth/resizing churn.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Keep TOON grammar identical.
- Target `peekLine`, constructor allocations, and cache heuristics first.
- Validate with existing regression suites + benchmark delta.

### Phase 2: Algorithm Improvements
- Move key/value extraction to char-range parsing.
- Eliminate intermediate list/object creation in tabular/inline parsing.
- Re-check JFR for reduced `String` and `[B` allocation hotspots.

### Phase 3: System-level Changes
- Add reusable parsing context for repeated parse calls.
- Optimize high-volume row/object materialization paths.
- Track memory and throughput under sustained benchmark loops.

## Performance Testing Plan
- Baseline benchmark command:
  - `mvn -pl json-io -q test-compile`
  - `java -cp <test+runtime cp> com.cedarsoftware.io.JsonPerformanceTest`
- Baseline profiler inputs:
  - `/Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_2026_03_01_154047.jfr`
  - `/Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_2026_03_01_155155.jfr`
- For each optimization batch:
  1. Run targeted ToonReader tests.
  2. Run full `mvn clean test`.
  3. Re-run benchmark and capture JFR.
  4. Compare read times and allocation-site deltas.

## Risk Assessment
### Safe Optimizations
- Cache sizing/reuse improvements.
- Constructor allocation reductions.
- Minor parser fast paths that do not alter parsing semantics.

### Moderate Risk
- Range-based parsing refactors in `readObject`/`readInlineObject`.
- Direct tabular row parsing without intermediate list.

### High Risk
- Large-scale token model changes across all parse branches.
- Any redesign that could alter quoting, folded-key, or meta-key interpretation behavior.

## Recommendations
- Prioritize allocation reduction over micro-CPU tweaks; current JFR is allocation-dominated in read path.
- Keep every optimization gated by golden-output + round-trip correctness checks (no TOON syntax/behavior drift).
- Continue using paired benchmark + JFR captures so wins are confirmed both in throughput and allocation profiles.

## Estimated Impact
- Quick wins: **10-18%** ToonReader read improvement.
- Major optimizations: **25-40%** additional read improvement.
- Full plan: **35-55%** cumulative ToonReader read-side gain (workload dependent).

## Effort Estimate
- Quick wins: **6-12 engineering hours**
- Major optimizations: **3-7 engineering days**
- Complete plan: **2-4 engineering weeks**
