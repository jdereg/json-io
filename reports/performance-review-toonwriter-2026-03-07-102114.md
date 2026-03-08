# Performance Review Plan
Generated: 2026-03-07-102114
Scope: /Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java + /Users/jderegnaucourt/.copilot/session-state/2110e1b1-9a79-46aa-8a1c-443b3e83febb/files/toonwriter-review-2026-03-07-101007.jfr

## Executive Summary
Current ToonWriter is materially healthier than the February report suggested: `write()` now clears per-write state at entry and again in `finally` (`ToonWriter.java:256-278`), so the earlier writer-state-leak finding is stale. The remaining performance story is now dominated by allocation pressure rather than a single correctness bug. In the 25-second JFR recording, ToonWriter appeared in about 1,382 of 1,563 execution samples (~88.4%), the run produced 7,062 allocation samples, and G1 performed 100 young collections (~4 GC/s) with heap cycling from roughly 524 MB before GC down to ~36 MB after GC. That points to a short-lived allocation problem in current code.

The biggest remaining costs are: primitive boxing through `Accessor.retrieve()` in POJO paths, per-write `keyQuoteDecisionCache` churn in `writeKeyString()`, per-write work-stack allocation in `traceReferences()`, a two-pass map key scan in `writeMap()`, and tabular POJO pre-materialization in `getUniformPOJODataFromCollection()` / `getUniformPOJODataFromArray()`. The first two are the clearest hotspots to attack first.

## ToonWriter-Specific Hotspots from the JFR
### Top CPU Hotspots
| Rank | Method | Approx. evidence | Why it matters |
|---|---|---:|---|
| 1 | `isSimpleUnquotedAsciiToken(String)` (`ToonWriter.java:566-587`) | 42 self-time samples (~2.7% of all execution samples) | Repeated key-quoting classification work on hot write paths. |
| 2 | `isPrimitive(Object)` (`ToonWriter.java:2218-2231`) | 31 self-time samples (~2.0%) | Repeated inline/type classification during collection, array, and trace passes. |
| 3 | `writeMapWithSimpleKeys(Map)` (`ToonWriter.java:1645-1657`) | 17 self-time samples (~1.1%); ~126 stack appearances | Central map emission loop for the common simple-key case. |
| 4 | `writeObjectInline(Object)` (`ToonWriter.java:1436-1519`) | 13 self-time samples (~0.8%) | Hot inline POJO path; also triggers accessor boxing. |
| 5 | `writeFieldEntry(String,Object)` (`ToonWriter.java:1664-1737`) | 12 self-time samples (~0.8%); ~272 stack appearances | Central dispatch point for field writes. |
| 6 | `writeObjectFields(Object)` (`ToonWriter.java:1998-2061`) | 10 self-time samples (~0.6%); ~137 stack appearances | Regular POJO field writer; a key boxing/allocation path. |
| 7 | `getTypeNameForOutput(Class)` (cached type-name path) | 5 self-time samples (~0.3%) | Secondary metadata overhead, not a first-wave target. |

### Top Allocation Hotspots
| Rank | Allocation source | Approx. JFR evidence | Code path |
|---|---|---|---|
| 1 | Primitive boxing to `Double` / boxed numerics | Multiple `jdk.ObjectAllocationSample` events at ~4.0 MB each and one at ~36.0 MB rooted at `Double.valueOf` -> `Accessor.retrieve(Object)` -> ToonWriter | `writeObjectFields()` (`ToonWriter.java:2040`), `getObjectFields()` (`ToonWriter.java:2115`), `writeObjectInline()` (`ToonWriter.java:1490`), `pushReferenceCandidates()` (`ToonWriter.java:2318`) |
| 2 | `LinkedHashMap$Entry` from per-write key quote cache | Sampled allocation weight ~1.1 MB and ~1.7 MB at `writeKeyString:473` | `keyQuoteDecisionCache` field (`ToonWriter.java:148`), fill sites in `writeKeyString()` (`ToonWriter.java:459-482`) |
| 3 | `ArrayDeque` backing storage per `traceReferences()` call | 5 allocation samples rooted at `new ArrayDeque<>(256)` | `traceReferences()` (`ToonWriter.java:2252-2282`) |
| 4 | `int[]` from `IdentityIntMap` construction | Sampled weights roughly 312 kB to 4.0 MB from `IdentityIntMap.<init>` via ToonWriter construction | `ToonWriter` instance fields (`ToonWriter.java:140-142`) created per `JsonIo.toToon()` call (`JsonIo.java:476-489`) |
| 5 | Iterator churn from map key pre-scan | Allocation samples include `HashMap$KeySet.iterator()` rooted at `writeMap(Map)` | `writeMap()` pre-scan (`ToonWriter.java:1589-1596`) plus `writeMapWithSimpleKeys()` second pass (`ToonWriter.java:1647`) |

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Primitive boxing dominates POJO-heavy writes** (`ToonWriter.java:1490`, `2040`, `2115`, `2318`): `Accessor.retrieve()` returns `Object`, forcing boxing of primitive fields and creating the single biggest allocation stream seen in the JFR.
- [ ] **Per-write key quote cache churn creates avoidable CPU + allocation cost** (`ToonWriter.java:148`, `256-278`, `453-482`, `553-559`): field names are reclassified and reinserted for every write even though key strings are typically stable across writes.
- [ ] **Allocation-driven GC pressure remains high** (JFR: ~100 young GCs / 25 s, heap ~524 MB -> ~36 MB): current serialization is generating enough short-lived garbage that GC is now a first-order cost.

### High Priority Issues
- [ ] **`traceReferences()` allocates a fresh work deque every write** (`ToonWriter.java:2256`): small individually, but guaranteed in `cycleSupport=true` mode.
- [ ] **`writeMap()` uses a two-pass simple-key scan** (`ToonWriter.java:1589-1596`, `1645-1657`): extra iterator allocation and extra traversal on the common simple-key path.
- [ ] **Tabular POJO detection pre-materializes full `Map<String,Object>` rows** (`ToonWriter.java:1152-1216`, `1223-1282`, `2099-2154`): this avoids a second retrieval pass, but still allocates a map per element and boxes every primitive field before output.

### Medium Priority Issues
- [ ] **ToonWriter construction allocates `IdentityIntMap` state on every `JsonIo.toToon()` call** (`ToonWriter.java:140-142`, `JsonIo.java:476-489`): visible in JFR, but likely lower payoff than fixing write-path allocations first.
- [ ] **`isPrimitive()` and other type checks sit in hot loops** (`ToonWriter.java:2218-2231`, collection/array checks around `ToonWriter.java:1157-1193`, `1228-1257`, `2237-2249`): worth revisiting only after larger allocation issues are addressed.

## Stale Prior Findings That Are No Longer Applicable
1. **Writer state leak across calls — stale.** The February report said reused ToonWriter instances leaked traversal state. Current `write()` now clears `objVisited`, `objsReferenced`, `activePath`, `keyQuoteDecisionCache`, and `nextIdentity` both on entry and in `finally` (`ToonWriter.java:256-278`).
2. **Key-folding infinite loop on cyclic single-key map chains — stale.** `collectFoldedPath()` now maintains a local `IdentityHashMap` guard and aborts on repeat visit (`ToonWriter.java:2379-2405`).
3. **Container cycle handling is incomplete — stale.** Current array, collection, map, inline-map, and nested-object paths all use `writeReferenceIfSeen()` or `enterActivePath()` guards (for example `ToonWriter.java:845`, `941`, `1373`, `1563`, `1977-1980`).
4. **Uniform POJO rows are computed twice — stale as originally reported.** Current code pre-materializes rows once and reuses them in `writeTabularPOJORows()` (`ToonWriter.java:1152-1216`, `1223-1282`, `1302-1317`). The remaining issue is still allocation-heavy materialization, not duplicate retrieval during row emission.

## Performance Metrics
### Current State
- JFR recording length: **25 seconds**.
- Total execution samples: **1,563**.
- Samples with ToonWriter anywhere in the stack: **1,382 (~88.4%)**.
- Total object-allocation samples: **7,062**.
- Young GC events: **100 in 25 seconds (~4 per second)**.
- Heap before GC peaked around **524 MB**, dropping to roughly **36 MB** after GC.
- Highest observed ToonWriter self-time sample: `isSimpleUnquotedAsciiToken()` at about **2.7%** of all samples.
- Largest sampled allocation stream: **boxed primitive numerics** on Accessor-based POJO reads.

### Target State
- Cut young-GC frequency on the same benchmark to **under 2/sec** by removing avoidable per-write allocations.
- Reduce sampled `Double`/boxed-number allocation weight enough that it is no longer the dominant allocation source in the same JFR scenario.
- Reduce `writeKeyString()`/`isSimpleUnquotedAsciiToken()` hot-path cost so key classification falls below roughly **1% self-time** on the same benchmark.
- Eliminate the sampled `LinkedHashMap$Entry` and `ArrayDeque` allocation streams attributable to per-write cache/deque creation.

## Top 5 Optimization Opportunities (Ranked by Likely Payoff)
### 1. Add typed primitive retrieval to avoid `Accessor.retrieve()` boxing
**Likely payoff:** Very High  
**Classification:** Riskier refactor  
**Primary references:** `ToonWriter.java:1490`, `2040`, `2115`, `2318`

The largest sampled allocation source is primitive boxing caused by the `Object`-typed accessor API. Current write paths know field metadata but still retrieve primitive values as boxed objects. Adding typed accessors (for example `retrieveDouble`, `retrieveInt`, etc.) and specializing `writeObjectFields()` / `writeObjectInline()` would directly attack the biggest allocation source in the recording.

### 2. Remove the per-write `keyQuoteDecisionCache` and rely on the shared quote-decision cache
**Likely payoff:** High  
**Classification:** Safe quick win  
**Primary references:** `ToonWriter.java:148`, `256-278`, `453-482`, `553-559`

`writeKeyString()` currently uses a per-write `LinkedHashMap` even though stable field names are already ideal candidates for the shared static cache used by `needsQuoting()`. This creates both CPU work (`isSimpleUnquotedAsciiToken`) and sampled `LinkedHashMap$Entry` allocation churn every write. Consolidating on the shared cache is low-risk and should noticeably reduce allocation rate and key-classification CPU.

### 3. Reuse `traceReferences()` traversal scratch state instead of allocating it per write
**Likely payoff:** Medium-High  
**Classification:** Safe quick win  
**Primary references:** `ToonWriter.java:2252-2282`, `2379-2405`

`traceReferences()` allocates `new ArrayDeque<>(256)` on every call, and `collectFoldedPath()` allocates a fresh `IdentityHashMap` for each fold traversal. Both are reusable scratch structures on a single-threaded writer instance. Hoisting them to cleared instance fields would reduce guaranteed per-write garbage with minimal behavioral risk.

### 4. Collapse `writeMap()`'s simple-key detection and write path into one `entrySet()` traversal
**Likely payoff:** Medium  
**Classification:** Safe quick win  
**Primary references:** `ToonWriter.java:1589-1596`, `1645-1657`

For maps with simple keys, current code first scans `keySet()` for complexity, then scans `entrySet()` again to write. The JFR shows iterator churn rooted at that pre-scan. A single-pass `entrySet()` strategy, or a single buffered pass reused for output, should save traversal work in a central hot path.

### 5. Stream tabular POJO rows instead of materializing `List<Map<String,Object>>`
**Likely payoff:** Medium  
**Classification:** Riskier refactor  
**Primary references:** `ToonWriter.java:1152-1216`, `1223-1282`, `1302-1317`, `2099-2154`

Current code is better than the stale report suggested because it does not reread fields during row emission, but it still allocates one `LinkedHashMap` per element and boxes all primitive field values before writing. A streaming row emitter using cached `WriteFieldPlan`s could preserve output shape while avoiding most row-map allocation and much of the boxing overhead in the tabular path.

## Optimization Plan
### Quick Wins (1-3 days)
1. Replace the per-write `keyQuoteDecisionCache` with the existing shared quote-decision cache and reprofile.
2. Reuse `traceReferences()` deque and `collectFoldedPath()` seen-map scratch structures across writes.
3. Remove the two-pass simple-map traversal in `writeMap()`.

### Major Optimizations (3-7 days)
1. Introduce typed accessor retrieval for primitive fields and specialize hot POJO write paths.
2. Rework tabular POJO emission to avoid `Map<String,Object>` row materialization.
3. Re-evaluate ToonWriter construction overhead only after the main write-path allocations are reduced.

### Architectural Changes (1-2 weeks)
1. If constructor cost still matters after hot-path fixes, consider a reusable/poolable ToonWriter path or pooled traversal structures behind `JsonIo.toToon()`.
2. Add dedicated performance harnesses for POJO-heavy, map-heavy, and `cycleSupport=true` workloads so future refactors are measured rather than inferred.
3. Treat JFR-driven allocation budgets as a release gate for ToonWriter changes.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Implement opportunities #2, #3, and #4 first.
- Re-run the same benchmark/JFR scenario and verify reduced `LinkedHashMap$Entry`, `ArrayDeque`, and iterator allocation evidence.
- Confirm no output-format regressions using existing ToonWriter tests.

### Phase 2: Algorithm and Allocation Improvements
- Prototype typed primitive access and quantify allocation reduction in POJO-heavy writes.
- Prototype streaming tabular-row emission using cached `WriteFieldPlan`s.
- Compare throughput and allocation rate before/after on both `cycleSupport=true` and `cycleSupport=false` paths.

### Phase 3: System-level / Lifecycle Improvements
- Only if still justified by profiling, investigate reducing ToonWriter construction cost per `JsonIo.toToon()` call.
- Preserve thread-safety and current API semantics while considering any reuse/pooling strategy.

## Performance Testing Plan
- Use `JsonPerformanceTest.testMapsOnly()` (`JsonPerformanceTest.java:212-275`) as one baseline scenario because it is the workload named in the JFR.
- Add at least one POJO-heavy microbenchmark specifically designed to stress `writeObjectFields()`, `writeObjectInline()`, and tabular POJO output, since those are where the biggest boxing allocations were sampled.
- For each change set, capture:
  - throughput / elapsed time,
  - young-GC count and allocation rate,
  - JFR sample share for `isSimpleUnquotedAsciiToken`, `writeMapWithSimpleKeys`, and POJO accessor paths.
- Validate with both `cycleSupport=true` and `cycleSupport=false` because `traceReferences()` only applies to the former.

## Risk Assessment
### Safe Optimizations
- Consolidating key quote decisions into the shared static cache.
- Reusing scratch deques/maps that are already logically per-writer.
- Removing the duplicate map traversal in `writeMap()`.

### Moderate Risk
- Streaming tabular POJO rows instead of precomputed maps.
- Rebalancing hot-path type checks or primitive detection logic.

### High Risk
- Changing the accessor API to support typed primitive retrieval.
- Any pooling/reuse strategy that changes ToonWriter lifecycle or object ownership semantics.

## Safe Quick Wins vs Riskier Refactors
| Opportunity | Classification | Why |
|---|---|---|
| Shared quote-decision cache for keys | Safe quick win | Mostly internal cache consolidation; behavior should stay identical if cache rules are unchanged. |
| Reuse `traceReferences()` / key-fold scratch structures | Safe quick win | Pure allocation reduction with little algorithmic change. |
| Single-pass simple-map traversal | Safe quick win | Small local algorithm cleanup in an already well-bounded code path. |
| Typed primitive accessor retrieval | Riskier refactor | Requires API and implementation changes in accessors and multiple write paths. |
| Streaming tabular POJO rows | Riskier refactor | Alters the structure of uniform-row processing and must preserve schema/order semantics. |
| ToonWriter reuse/pooling across `toToon()` calls | Riskier refactor | Crosses API/lifecycle boundaries and risks subtle state bugs if done incorrectly. |

## Recommendations
- Start with the three safe quick wins and capture a new JFR before attempting larger refactors.
- Treat the February report's state-leak and cycle-gap findings as closed; they should not drive current prioritization.
- Bias future work toward reducing allocation rate first, because current GC pressure is strong evidence that throughput is now allocation-limited.
- Add a dedicated POJO serialization benchmark, not just maps-only, before prioritizing the typed-accessor work; the JFR says boxing matters, but a focused benchmark will tell how much it matters in representative workloads.

## Estimated Impact
- **Quick wins:** likely **15-25%** reduction in short-lived allocation rate, with measurable but smaller CPU gains.
- **Typed primitive accessor work:** potentially the single biggest improvement for POJO-heavy writes; likely to materially cut GC frequency and allocation weight if the workload is field-heavy.
- **Full plan:** plausible path to meaningfully lower GC pressure (potentially around **2x fewer** young collections in similar runs) plus improved write throughput, but this should be validated empirically.

## Effort Estimate
- **Quick wins:** about **0.5-2 days** total including profiling/validation.
- **Moderate refactors:** about **3-7 days** for typed-accessor and/or tabular streaming prototypes plus tests.
- **Complete plan with validation:** about **1-2 weeks** including benchmark coverage and follow-up profiling.
