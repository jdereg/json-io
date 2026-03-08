# Performance Review Plan
Generated: 2026-03-07-160206
Scope: /Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/JsonWriter.java
JFR: /Users/jderegnaucourt/.copilot/session-state/2110e1b1-9a79-46aa-8a1c-443b3e83febb/files/jsonwriter-review-2026-03-07-205018.jfr

## Executive Summary
Current JsonWriter performance remains materially behind the comparison baselines in the supplied benchmark: JsonIo write with `cycleSupport=true` is 2973.47 ms, `cycleSupport=false` is 2577.61 ms, Toon write is 2407.45 / 2014.53 ms, and Jackson write is 1014.81 ms. The fresh JFR shows that the biggest remaining costs in current `JsonWriter.java` are not the already-optimized string scanner or primitive-array paths; instead they are the generic object/map/collection write paths, especially custom-writer dispatch for boxed numbers, iterator-heavy loops, per-writer tracking structure allocation, and the reference-trace pass when cycle support is enabled.

The most actionable finding is that current code still routes many boxed numeric values through `writeImpl()` -> `writeUsingCustomWriter()` -> `writeCustom()` even when the class already has zero-allocation direct writers such as `writeIntDirect()`. That path shows both measurable CPU presence and large sampled allocation weight from `Integer.toString()` / `Long.toString()` generated `String` objects. The next highest-payoff items are iterator allocation in `writeObject()` and `writeCollection()`, constructor-time allocation of reference-tracking structures on every writer instance, and double-pass map key validation in string-key map handling.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Custom-writer numeric path still allocates heavily** in `writeImpl()` / `writeUsingCustomWriter()` / `writeCustom()` and downstream primitive writers (`JsonWriter.java:667-675`, `760-809`, `1104-1197`, `2245-2262`). JFR allocation samples repeatedly show `java.lang.String` created under `Integer.toString()` and `Long.toString()` from the `writeCustom()` path. Sampled JsonWriter allocation-frame counts show `writeCustom` present in **638 / 6880 allocation samples (~9.3%)**.
- [ ] **Map/object hot loops dominate remaining sampled CPU** in `writeMapBody()`, `writeField()`, `writeObject()`, and `writeMapWithStringKeys()` (`JsonWriter.java:2091-2155`, `2503-2545`, `2422-2465`, `2062-2089`). Frame-presence counts from JFR execution samples show roughly **77** `writeMapBody`, **59** `writeField`, **54** `writeObject`, and **53** `writeMapWithStringKeys` hits out of **1503 total execution samples**.
- [ ] **Reference tracing remains a real cycleSupport=true tax** in `write()` / `traceReferences()` / `processArray()` (`JsonWriter.java:822-856`, `867-1044`). The benchmark delta between `cycleSupport=true` and `false` is about **395.86 ms (~15%)**, and sampled execution stacks include `traceReferences` (**28**), `processArray` (**37**), `processMap` (**9**), and `processFields` (**6**).

### High Priority Issues
- [ ] **Per-instance writer setup allocates tracking arrays even before writing** (`JsonWriter.java:331-340`, `476-564`). JFR allocation samples show constructor-related `Object[]` and `int[]` allocations from `IdentityIntMap` / `IdentityHashMap` initialization, with constructor presence in **262 allocation samples** and individual sample weights of roughly **313 KB, 544 KB, 953 KB, and 1.1 MB**.
- [ ] **Iterator allocation in object and collection serialization** (`JsonWriter.java:1585-1608`, `2450-2453`). JFR allocation samples include `java.util.Collections$UnmodifiableCollection$1` under `writeObject()` and repeated `java.util.ArrayList$Itr` under `writeCollection()`.
- [ ] **Double-pass string-key map handling** (`JsonWriter.java:1844-1867`, `2062-2089`, `2230-2237`) first scans keys with `ensureJsonPrimitiveKeys()` and then iterates again to emit JSON. In a map-heavy benchmark, this compounds the already-hot `writeMapWithStringKeys()` / `writeMapBody()` path.

### Medium Priority Issues
- [ ] **Collection element path still falls back to generic recursion too often** in `writeCollectionElement()` (`JsonWriter.java:2245-2262`) instead of using already-available direct emitters for more numeric wrapper types.
- [ ] **Repeated ClassValue-based non-referenceable checks in tracing** (`JsonWriter.java:902-905`, `949-959`, `1046-1054`) still show up in sampled stacks for array tracing.
- [ ] **String writing remains visible but no longer dominant** in `writeJsonUtf8String()` / `writeStringValue()` (`JsonWriter.java:2737-2808`). It is still worth monitoring, but the JFR shows it as a secondary hotspot rather than the primary bottleneck now.

## Performance Metrics
### Current State
- Benchmark runtime:
  - JsonIo Write `cycleSupport=true`: **2973.469 ms**
  - JsonIo Write `cycleSupport=false`: **2577.610 ms**
  - Toon Write `cycleSupport=true`: **2407.450 ms**
  - Toon Write `cycleSupport=false`: **2014.534 ms**
  - Jackson Write: **1014.814 ms**
- JFR totals:
  - Execution samples: **1503**
  - Allocation samples: **6880**
  - Young GCs: **95** in a 25 s recording
- Key sampled JsonWriter frames (frame presence, approximate only):
  - `writeImpl()`: **90**
  - `writeMapBody()`: **77**
  - `writeField()`: **59**
  - `writeObject()`: **54**
  - `writeMapWithStringKeys()`: **53**
  - `writeCollectionElement()`: **49**
  - `processArray()`: **37**
  - `traceReferences()`: **28**
  - `writeUsingCustomWriter()`: **26**
  - `writeCustom()`: **22**
  - `writeJsonUtf8String()`: **21**
- Key allocation evidence attributable to current JsonWriter paths:
  - `writeCustom()` path repeatedly allocates `java.lang.String` via `Integer.toString()` / `Long.toString()`.
  - `writeObject()` allocates `java.util.Collections$UnmodifiableCollection$1` iterators.
  - `writeCollection()` allocates `java.util.ArrayList$Itr` iterators.
  - `JsonWriter` construction allocates `Object[]` / `int[]` backing arrays for tracking structures.

### Target State
- Reduce JsonIo write time by roughly **15-25%** without semantic changes by attacking the current top write-path allocations first.
- Narrow the gap to Toon by roughly **200-400 ms** on this benchmark before attempting any higher-risk architectural changes.
- Cut sampled allocation pressure in the hottest JsonWriter frames enough to reduce GC frequency and leave the string escaper / primitive-array code as clearly minor contributors.

## Optimization Plan
### Quick Wins (1-3 days)
1. **Bypass generic custom-writer dispatch for more boxed numeric cases** in `writeCollectionElement()` using `writeIntDirect()` for `Integer`, `Short`, and `Byte` where semantics already match raw JSON number output (`JsonWriter.java:2245-2262`, `1284-1318`).
2. **Replace enhanced-for loops with index-based iteration** over `WriteFieldPlan` lists in `writeObject()` and, if kept consistent, `processFields()` (`JsonWriter.java:2450-2453`, `1031-1043`).
3. **Add fast-path handling for common List implementations** in collection writing to avoid iterator allocation when the runtime collection is a `List` / `RandomAccess` implementation (`JsonWriter.java:1585-1608`).
4. **Avoid eager allocation of tracking structures when they are not needed** for `cycleSupport=false` workloads (`JsonWriter.java:331-340`, `476-564`).
5. **Fuse string-key validation with emission** for map-with-string-keys paths to avoid the current pre-scan + write pass (`JsonWriter.java:1849-1850`, `2066-2068`, `2230-2237`).

### Major Optimizations (1-2 weeks)
1. Refactor the primitive/custom-writer boundary so native JSON number wrappers can stay on direct-write paths more often while preserving type and custom-writer semantics.
2. Rework the cycle-tracing traversal to reduce repeated class checks and stack operations on homogeneous arrays / collections.
3. Add a reusable, low-allocation write path for frequent map/list shapes encountered in benchmarks.

### Architectural Changes (2-4 weeks)
1. Explore reusable writer instances or pooling patterns for high-throughput call sites to amortize constructor-time allocation costs.
2. Revisit the custom-writer contract so primitive-form writers can stream directly to `Writer` without intermediate `String` creation where safe.
3. Consider separating the fully-generic serializer from an aggressively specialized fast path for common JSON-native object graphs.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Stop sending obvious JSON-native numeric wrappers through the generic custom-writer path where direct emitters already exist.
- Remove avoidable iterator objects from object-field and common collection emission.
- Remove the extra key-validation scan from string-key map serialization.

### Phase 2: Algorithm Improvements
- Reduce repeated per-element referenceability checks in tracing for common homogeneous object graphs.
- Collapse common write loops around lists/maps into lower-allocation specialized paths.
- Re-evaluate where declared element/key type context can eliminate repeated decision-making.

### Phase 3: System-level Changes
- Make writer lifetime / reuse a first-class performance pattern for throughput-sensitive callers.
- Revisit custom-writer streaming APIs to eliminate intermediary numeric/string materialization.
- Add more focused benchmark scenarios for object-heavy, map-heavy, and cycle-heavy write paths so each hot area is measured independently.

## Performance Testing Plan
- Re-run the same benchmark suite that produced the supplied numbers after each optimization batch.
- Capture before/after JFRs and compare:
  - `writeCustom()` allocation-frame presence
  - `writeMapBody()` / `writeField()` / `writeObject()` sample presence
  - iterator allocation samples (`ArrayList$Itr`, `Collections$UnmodifiableCollection$1`)
  - constructor allocations from `IdentityIntMap` / `IdentityHashMap`
- Validate both `cycleSupport=true` and `cycleSupport=false` paths independently, because the bottlenecks differ.
- Treat Jackson and Toon as comparison baselines, but use JsonWriter-vs-previous-JsonWriter as the acceptance metric.

## Risk Assessment
### Safe Optimizations
- Index-based loops for `WriteFieldPlan` traversal.
- Lazy/conditional allocation of tracking structures that are unused in `cycleSupport=false` mode.
- Fusing map key validation with map emission.
- Extending existing direct-write numeric fast paths to more wrapper types when raw JSON semantics stay identical.

### Moderate Risk
- Changing when boxed numerics bypass custom writers, because custom-writer overrides and type-emission rules must stay correct.
- Adding specialized list/map fast paths that must preserve ordering, null behavior, and type metadata behavior.
- Memoizing per-element tracing decisions in a way that still handles heterogeneous arrays correctly.

### High Risk
- Changing the custom-writer contract to stream directly without intermediate strings.
- Broader serializer specialization that forks behavior between generic and fast paths.
- Any redesign that changes `$id` / `$ref` or `@type` emission behavior.

## Recommendations
- Prioritize fixes in this order: **numeric custom-writer bypass**, **iterator removal**, **tracking-structure allocation**, **map key pre-scan removal**, then **trace-pass tuning**.
- Do **not** spend time first on the already-optimized string escaper, primitive-array writers, or write-type dispatch cache unless a new JFR moves them back to the top.
- Add narrow benchmarks for:
  - many boxed integers/longs in maps/lists,
  - many POJOs with identical field plans,
  - many `ArrayList` collections,
  - many short-lived `JsonWriter` instances,
  - identical object graphs with cycle support toggled.

## Estimated Impact
- Quick wins: **10-20%** improvement is realistic if the boxed-number and iterator findings translate cleanly to the benchmark workload.
- Major optimizations: **20-35%** additional improvement if constructor allocation and generic map/list paths are reduced materially.
- Full plan: likely enough to close much of the gap to Toon on this benchmark, but not likely to reach Jackson without broader serializer specialization.

## Effort Estimate
- Quick wins: **1-3 days**
- Major optimizations: **1-2 weeks**
- Complete plan: **2-4 weeks**

## Top 5 Optimization Opportunities Ranked by Likely Payoff
1. **Short-circuit boxed numeric wrappers onto direct-write paths** instead of routing through `writeCustom()` (`JsonWriter.java:2245-2262`, `667-675`, `760-809`, `1104-1197`, `1284-1318`).
2. **Replace field-plan enhanced-for loops with indexed loops** in `writeObject()` / `processFields()` (`JsonWriter.java:2450-2453`, `1031-1043`).
3. **Special-case common list iteration to avoid iterator allocation** in `writeCollection()` / `writeElements()` (`JsonWriter.java:1585-1608`).
4. **Lazy/conditional allocation of `objVisited`, `objsReferenced`, `activePath`, and depth buffers** when the write mode does not need them all (`JsonWriter.java:331-340`, `822-856`).
5. **Remove the current map key double pass** in `writeMapWithStringKeys()` / `writeJsonObjectMapWithStringKeys()` / `ensureJsonPrimitiveKeys()` (`JsonWriter.java:1844-1867`, `2062-2089`, `2230-2237`).

## Stale Findings / Areas to Exclude
- Exclude old advice to optimize primitive-array emission first: current code already has dedicated array writers and these show only minor sampled presence (`JsonWriter.java:299-309`, `1433-1556`).
- Exclude old advice to replace repeated `instanceof` dispatch in `writeImpl()`: current code already uses `writeTypeCache` (`JsonWriter.java:401-416`, `1156-1197`).
- Exclude old advice to rework JSON string escaping first: current batch escaper is already in place and only a secondary hotspot in this recording (`JsonWriter.java:2737-2787`).
- Exclude old advice to remove boxing from reference tracking: current code already uses `IdentityIntMap` and primitive depth arrays (`JsonWriter.java:331-340`, `878-947`).

## Safe Quick Wins vs Riskier Refactors
### Safe Quick Wins
- Indexed loops for field plans.
- Additional direct-write handling for `Integer` / `Short` / `Byte` where semantics stay raw-number-compatible.
- List fast paths for common random-access collections.
- Lazy allocation of structures unused in `cycleSupport=false` mode.
- Single-pass validation + emission for string-key maps.

### Riskier Refactors
- Reworking custom-writer selection rules for primitive/native JSON values.
- Broader changes to reference-tracing logic.
- Direct-streaming custom writer API changes that remove intermediate `String` creation.
- Any change that could alter `@type`, `@id`, or `@ref` output behavior.
