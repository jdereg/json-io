# Performance Review Plan
Generated: 2026-02-27-151621
Scope: /Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_2026_02_27_101308.jfr

## Executive Summary
The latest JsonPerformanceTest JFR confirms TOON paths are the dominant CPU/allocation cost versus JSON paths in this run.  
Execution samples show `ToonWriter` (27.3%) + `ToonReader` (21.3%) significantly exceed `JsonWriter` (14.4%) + `JsonParser` (9.9%), and allocation samples show a similar ~2x gap (`ToonWriter` + `ToonReader`: 8,569 vs `JsonWriter` + `JsonParser`: 4,204).  
The clearest hotspots are string quoting/scanning in `ToonWriter`, per-object field materialization in `ToonWriter.getObjectFields()`, line/token scanning in `ToonReader`, and repeated map insertion path overhead (`ToonReader.putValue()` + `JsonObject.put()`).

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **TOON quoting path is the #1 CPU hotspot**  
      `com.cedarsoftware.io.ToonWriter.needsQuoting` is 624/2071 (30.1%) of ToonWriter execution samples, with top frame at `ToonWriter.java:426`.  
      Related code: `json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java:390-433`.
- [ ] **TOON object-field extraction causes heavy CPU + allocation churn**  
      `ToonWriter.getObjectFields` is 223 execution samples and 878 allocation samples; it builds a `LinkedHashMap` each object write (`ToonWriter.java:1674-1706`), then often copies again with metadata (`ToonWriter.java:1651-1664`, `1228-1245`).
- [ ] **TOON reader key/value ingest is expensive and map-write heavy**  
      `ToonReader.putValue` (314 execution, 710 allocation samples) calls `target.get(key)` then `target.put(key, value)` (`ToonReader.java:1053,1061`), which drives `JsonObject.put` hotspot (`JsonObject.java:273-299`, top-frame count 227).

### High Priority Issues
- [ ] **Line parsing/scanning dominates TOON read path**  
      `peekLine`, `findColonPosition`, `getIndentLevel`, `readInlineArray`, and `parseNumber` together account for a large fraction of ToonReader CPU:  
      - `peekLine` (150), `findColonPosition` (148), `parseNumber` (122), `readInlineArray` (179), `readObject` (374).  
      Code: `ToonReader.java:873-904`, `916-973`, `493-531`, `154-235`.
- [ ] **TOON string serialization path does not use buffer recycler**  
      `JsonIo.toToon(Object, WriteOptions)` allocates `FastByteArrayOutputStream` and `ToonWriter` each call (`JsonIo.java:480-483`), unlike `toJson` which reuses thread-local buffers (`JsonIo.java:356-375`).
- [ ] **Type/primitive checks in write loops trigger conversion checks repeatedly**  
      `ToonWriter.isPrimitive` (`ToonWriter.java:1787-1800`) appears in both execution and allocation hotspots; `Converter.isConversionSupportedFor(...)` in tight loops is expensive.

### Medium Priority Issues
- [ ] **Map writing in TOON does multiple passes and repeated key conversions**  
      `writeMap` first scans for complex keys (`ToonWriter.java:1296-1303`) and then writes entries (`1352+`), with frequent `key.toString()` and `writeKeyString()` decisions.
- [ ] **TOON parsing creates many transient strings/byte arrays**  
      Top allocation locations include `Arrays.copyOfRangeByte`, `StringLatin1.newString`, and `StringBuilder.toString`, consistent with repeated `trim()/substring()/toString()` usage in `ToonReader`.

## Performance Metrics
### Current State
- Recording duration: **153s**
- Execution samples: **7,597**
- Allocation samples: **22,181**
- Average JVM CPU load: **9.9%** (P95: **12.5%**)
- Average machine CPU load: **19.2%**
- Execution sample presence by subsystem:
  - `ToonWriter`: **2,071 (27.3%)**
  - `ToonReader`: **1,619 (21.3%)**
  - `JsonWriter`: **1,097 (14.4%)**
  - `JsonParser`: **752 (9.9%)**
  - Jackson: **721 (9.5%)**
- Top execution hotspot (global): `ToonWriter.needsQuoting` (top frame count **341** at line **426**).
- Allocation sample presence by subsystem:
  - `ToonWriter`: **4,366 (19.7%)**
  - `ToonReader`: **4,203 (18.9%)**
  - `JsonParser`: **2,533 (11.4%)**
  - `JsonWriter`: **1,671 (7.5%)**
  - Jackson: **1,675 (7.6%)**

### Target State
- Reduce `ToonWriter.needsQuoting` share from ~30% of ToonWriter samples to **<15%**.
- Reduce TOON stack allocation samples by **30-40%** (focus: constructor/field-map churn/string churn).
- Bring TOON read/write stack sample share to within **~20-30%** of JSON stack share on the same benchmark payload.
- Eliminate `JsonObject.put` as a top-3 global hotspot in TOON read runs.

## Optimization Plan
### Quick Wins (1-3 days)
1. **Add fast-path + cache for `needsQuoting()` decisions**  
   Cache quoting decisions for repeated key/value strings in the writer instance; add branch-light ASCII fast path before full scan.  
   Target code: `ToonWriter.java:390-433`.
2. **Reduce TOON string-path allocation in `JsonIo.toToon()`**  
   Reuse thread-local byte/char buffers similar to `toJson()`.  
   Target code: `JsonIo.java:355-375` pattern into `JsonIo.java:476-489`.
3. **Avoid double map lookup in `ToonReader.putValue()`**  
   Replace `get+put` with a single insertion/update strategy for non-strict mode and a stricter conflict path only when required.  
   Target code: `ToonReader.java:1043-1064`, `JsonObject.java:273-320`.

### Major Optimizations (1-2 weeks)
1. **Stream object fields directly in ToonWriter (avoid intermediate maps)**  
   Bypass `LinkedHashMap` materialization in `getObjectFields()`/meta-copy paths by writing fields directly from `WriteFieldPlan` iteration.  
   Target code: `ToonWriter.java:1228-1248`, `1641-1706`.
2. **Refactor ToonReader line parsing to single-pass tokenization**  
   Consolidate `trim/findColon/getIndent` scans into one pass per line and reduce substring creation.  
   Target code: `ToonReader.java:154-235`, `916-973`.
3. **Optimize number parsing fast path**  
   Add cheap integer/long fast path before `MathUtilities.parseToMinimalNumericType()`.  
   Target code: `ToonReader.java:873-887`.

### Architectural Changes (2-4 weeks)
1. **Introduce TOON reader buffer recycler path**  
   Mirror JSON `FastReader` + reusable char/pushback buffers for TOON builders (`ToonStringBuilder`/`ToonStreamBuilder`).
2. **Parser-oriented append API for TOON object build path**  
   Add/extend parser append method (like `appendFieldForParser`) to avoid duplicate-key search overhead in common unique-key path.
3. **Benchmark-segmented profiling harness**  
   Emit explicit phase markers or segmented runs (JSON write/read, TOON write/read, maps mode) to isolate regressions and optimize by phase.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Implement `needsQuoting()` fast path + cache.
- Reuse buffers in `toToon()` string API.
- Remove unnecessary `get+put` in TOON key insertion.

### Phase 2: Algorithm Improvements
- Remove intermediate field-map construction in ToonWriter.
- Consolidate TOON line scanning into a single-pass parser flow.
- Add numeric parse fast path for common integer/long cases.

### Phase 3: System-level Changes
- Introduce TOON read buffer recycling across builder APIs.
- Add parser append fast path with strict-mode-safe duplicate handling.
- Add segmented benchmark/profiling runs for stable regression detection.

## Performance Testing Plan
- Re-run `JsonPerformanceTest` with JFR after each optimization batch.
- Track these KPIs per run:
  - `ToonWriter.needsQuoting` sample share
  - `ToonReader.putValue` + `JsonObject.put` sample share
  - TOON vs JSON subsystem sample ratios
  - TOON stack allocation sample counts
- Add focused microbenchmarks for:
  - `ToonWriter.needsQuoting`
  - `ToonReader.findColonPosition` / `getIndentLevel` / `parseNumber`
  - `putValue` object insertion path

## Risk Assessment
### Safe Optimizations
- Buffer reuse in TOON string path.
- `needsQuoting()` fast-path/caching with behavioral tests.
- `putValue` insertion-path simplification when strict mode is off.

### Moderate Risk
- Direct streaming of object fields without intermediate map.
- Single-pass parser refactor for TOON line/token scanning.
- Numeric parsing fast-path changes.

### High Risk
- Architectural parser/reader pipeline changes that alter parse semantics in edge cases.
- Broad refactors touching strict-mode validation and key-folding/path-expansion behavior.

## Recommendations
- Start with **ToonWriter.needsQuoting** and **TOON buffer reuse** first; these are high-impact and low-risk.
- Next, attack **ToonReader.putValue + JsonObject.put** interaction to cut both CPU and allocation overhead.
- Use phase-isolated profiling runs to prevent mixed-workload noise from masking regressions.
- Keep strict TOON conformance tests in CI while optimizing parser hot paths.

## Estimated Impact
- Quick wins: **15-30%** TOON write-path improvement, **10-20%** TOON read-path improvement.
- Major optimizations: **30-50%** cumulative TOON improvement versus current baseline.
- Full plan: potential **40-65%** reduction in TOON-specific sampled CPU/allocation hotspots.

## Effort Estimate
- Quick wins: **2-4 focused changes** (small PRs).
- Major optimizations: **3-5 medium PRs** with targeted benchmark validation.
- Complete plan: **multi-iteration performance hardening cycle** with JFR-guided verification after each phase.
