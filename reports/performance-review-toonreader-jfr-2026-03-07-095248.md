# Performance Review Plan
Generated: 2026-03-07-095248 UTC
Scope: /Users/jderegnaucourt/.copilot/session-state/2110e1b1-9a79-46aa-8a1c-443b3e83febb/files/toonreader-loop-attempt5-baseline-20260307-043222.jfr + ToonReader/JsonObject benchmark path

## Executive Summary
Current HEAD already removed several larger TOON parser costs. In this JFR, the remaining `ToonReader` work is still allocation-heavy, but the biggest obvious `JsonObject` sizing levers were already tried and rejected. The best next change is to make **combined array-header parsing allocation-free on the common path** so `field[N]` / `field[N]{...}` parsing stops creating temporary `String` and `ArrayHeader` objects.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Combined array-header parsing still allocates pure parser scaffolding**  
      `ToonReader.parseCombinedArrayHeader()` allocates `countStr` via `key.substring(...)` at `ToonReader.java:546` and `new ArrayHeader(...)` at `ToonReader.java:587`. In the JFR these account for about **382.7 MB sampled allocation** inside Toon/JsonObject-attributed stacks (**222.8 MB + 160.0 MB**, **88 samples total**). This is removable overhead, not output data.
- [ ] **String slice materialization from the line buffer is still hot**  
      `ToonReader.cacheSubstringFromBuf()` (`ToonReader.java:1747-1775`) remains about **29/1335 execution samples (2.17%)** and **421.0 MB sampled allocation**. However, much of this cost is for strings the parser must actually return.
- [ ] **Object creation/growth is still large but mostly real output work**  
      `JsonObject.<init>()` (`JsonObject.java:85-86`) contributes about **2318 MB sampled allocation**, and `JsonObject.ensureCapacity()` adds about **269 MB**. Those are large, but the obvious parser-sizing experiments were already tried and did not clear the bar.

### High Priority Issues
- [ ] **Line reading remains the largest CPU leaf**  
      `FastReader.readUntil()` is the top Toon/FastReader execution leaf at about **64/1335 samples (4.79% total, 21.1% of Toon/Fast-attributed execution samples)** through `ToonReader.readLineRaw()` (`ToonReader.java:1569-1598`).
- [ ] **Main object/scalar parsing is still hot**  
      `ToonReader.readObject()` (`304-391`) shows about **33/1335 execution samples (2.47%)`; `hasLine()` (`1604-1644`) about **30/1335 (2.25%)`; `readScalar(char[],...)` (`1238-1286`) about **27/1335 (2.02%)`.
- [ ] **Inline arrays still allocate container/intermediate state**  
      `ToonReader.readInlineArray(char[],...)` (`878-940`) contributes about **156.1 MB sampled allocation**, mostly `Object[]` plus `ArrayList`.

### Medium Priority Issues
- [ ] **Resolution-side `JsonObject.entrySet()` work is visible**  
      `JsonObject.entrySet()` contributes about **345.0 MB sampled allocation**, but that is resolver-side and less directly attributable to the next parser-only win.
- [ ] **Further `cacheSubstringFromBuf()` work is higher risk**  
      The remaining `cacheSubstringFromBuf()` cost is intermixed with necessary returned `String` values, so incremental gains there are likely harder and riskier than removing temporary header-parsing objects.

## Performance Metrics
### Current State
- JFR duration: **25 s**
- Execution samples: **1335**
- Allocation samples: **7013**
- Young GCs: **99**
- Toon/FastReader-attributed execution samples: **304**
  - `FastReader.readUntil`: **64** (**4.79% total**)
  - `ToonReader.readObject`: **33** (**2.47% total**)
  - `ToonReader.hasLine`: **30** (**2.25% total**)
  - `ToonReader.cacheSubstringFromBuf`: **29** (**2.17% total**)
  - `ToonReader.readScalar(char[],...)`: **27** (**2.02% total**)
- Toon/JsonObject-attributed sampled allocation weight: **4190.2 MB**
  - `JsonObject.<init>:85-86`: **2318.4 MB**
  - `ToonReader.cacheSubstringFromBuf:1773`: **421.0 MB**
  - `ToonReader.parseCombinedArrayHeader:546 + :587`: **382.7 MB**
  - `JsonObject.ensureCapacity:455-456`: **269.2 MB**
  - `ToonReader.readInlineArray:879`: **156.1 MB**
- Current measured benchmark baseline (`JsonPerformanceTest maps`, 100k ops, local run):
  - `Toon Read Time`: **4031.69 ms**
  - `JsonIo Read Time`: **3270.49 ms**
  - `Jackson Read Time`: **2595.24 ms**

### Target State
- Reduce `parseCombinedArrayHeader()` sampled allocation by **80%+**
- Improve `JsonPerformanceTest maps` TOON read from **4031.69 ms** to **<= 3870 ms** (**~4% win**) before keeping the change
- Avoid regressions in full-Java TOON read beyond noise

## Optimization Plan
### Quick Wins (1-3 days)
1. Parse `[N]`, `[N|]`, `[N\t]`, and optional `{cols}` directly from the existing key slice without allocating `countStr`.
2. Avoid allocating `ArrayHeader` when only count/delimiter are needed; keep a lightweight branch or inline locals in `parseCombinedArrayField(...)`.
3. If `{cols}` is present, parse headers from the same key range instead of `substring()` materialization first.

### Major Optimizations (1-2 weeks)
1. Revisit `FastReader.readUntil()` / `readLineRaw()` only after the pure parser-overhead bucket is removed.
2. Explore additional buffer-direct handling in inline/list object branches.
3. Re-profile `cacheSubstringFromBuf()` only if it remains the top removable ToonReader bucket after header parsing is fixed.

### Architectural Changes (2-4 weeks)
1. Broaden range-based parsing so parser-only scaffolding objects are not materialized.
2. Add a smaller TOON parse-only benchmark for array-header-heavy payloads to isolate this path from resolver noise.
3. Keep validating each parser optimization with JFR, since hotspot attribution shifts quickly.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Refactor `parseCombinedArrayHeader()` / `parseCombinedArrayField()` to operate on existing key chars/ranges.
- Preserve current strict-mode validation and delimiter semantics.
- Capture a fresh JFR plus `JsonPerformanceTest maps`.

### Phase 2: Algorithm Improvements
- If needed, fold header-column parsing into the same scan.
- Reduce secondary string allocations in inline/list branches that still consume the parsed header result.

### Phase 3: System-level Changes
- Only after this change, reconsider line-reader and returned-string costs with a new JFR.

## Performance Testing Plan
- Primary benchmark:
  - `mvn -q -pl json-io -DskipTests test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.cedarsoftware.io.JsonPerformanceTest -Dexec.args=maps`
- Secondary regression check:
  - same command with `-Dexec.args=both`
- JFR validation:
  - capture another TOON-read-heavy recording and compare `ToonReader.parseCombinedArrayHeader:546` and `:587`
- Correctness validation:
  - `ToonReaderTest`
  - `ToonDelimiterTest`
  - `ToonSpecDecodeFixturesTest`
  - any tests covering combined `field[N]` and `field[N]{...}` forms

## Risk Assessment
### Safe Optimizations
- Eliminating temporary `countStr`
- Avoiding temporary `ArrayHeader` allocation on non-tabular/common paths

### Moderate Risk
- Parsing `{cols}` directly from ranges while preserving strict-mode errors
- Refactoring the helper return shape without changing behavior

### High Risk
- Broader parser refactors that also change `cacheSubstringFromBuf()` or line-reading semantics in the same patch

## Recommendations
- **Implement now:** make combined array-header parsing allocation-free on the common path.
- **Do not choose next:** more `JsonObject` sizing work (already tried/reverted), global `INITIAL_CAPACITY` changes (already reverted), or unsafe FastReader buffer ownership (already rejected).
- **Defer:** `FastReader.readUntil()` tuning until after removing this easier parser-only overhead bucket.
- **Revisit later:** `cacheSubstringFromBuf()` only if it remains the largest removable ToonReader bucket after the header change.

## Estimated Impact
- Quick win from this change alone: **~4-7%** on `JsonPerformanceTest maps` TOON read if the current header-heavy benchmark mix remains representative
- Additional benefit: lower GC pressure by removing temporary header strings/objects

## Effort Estimate
- Implementation + test pass: **0.5-1.5 days**
- Benchmark/JFR validation: **0.5 day**
- Total: **~1-2 days**
