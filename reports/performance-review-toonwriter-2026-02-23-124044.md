# Performance Review Plan
Generated: 2026-02-23T15:59:01Z
Scope: /Users/jderegnaucourt/workspace/json-io/json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java

## Executive Summary
ToonWriter has three high-severity correctness defects and several high-impact performance hotspots. The most severe defects are cycle handling gaps for container types and state leakage across multiple `write()` calls on the same writer instance. Performance opportunities are concentrated in repeated reflection work for tabular POJO output, repeated per-element array access abstraction costs, and indentation write amplification in deep/nested outputs.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **Container cycle handling is incomplete** (`ToonWriter.java:421-498`, `503-575`, `892-993`, `1337-1350`): self-referential `Map`/`List` structures are not cycle-guarded and can produce `StackOverflowError` or unbounded looping/OOM during key folding.
- [ ] **Writer state leaks across calls** (`ToonWriter.java:79`, `112-119`, `877-887`, `1098-1125`): `objVisited` is never reset per top-level write, so reused `ToonWriter` instances produce false cycle hits and data loss.
- [ ] **Key-folding traversal can loop forever on cyclic single-key map chains** (`ToonWriter.java:1337-1350`): `collectFoldedPath()` has no visited guard and keeps appending path segments.

### High Priority Issues
- [ ] **Repeated reflection/materialization for uniform POJO tabular output** (`ToonWriter.java:725-755`, `761-778`, `1145-1200`): POJO fields are computed during uniformity detection and recomputed during row emission.
- [ ] **Object-array element access uses generic reflection path in hot loops** (`ToonWriter.java:431-498`, `725-753`, `1276-1288`): `ArrayUtilities.getElement()` is used per element even when component type is non-primitive and could be direct-indexed.
- [ ] **Indent writing issues many small writes at depth** (`ToonWriter.java:1294-1298`): deep structures pay repeated call overhead per line.

### Medium Priority Issues
- [ ] **Map key complexity requires a full key pre-scan before main write pass** (`ToonWriter.java:898-911`): two-pass map traversal overhead on large maps.
- [ ] **String numeric-shape detection runs per candidate string** (`ToonWriter.java:224-314`): additional CPU in string-heavy payloads.

## Performance Metrics
### Current State
- Targeted regression/probe results:
  - Reusing one `ToonWriter` for two writes emits `null` for the second write of the same object graph (state leak).
  - Self-referential list serialization throws `StackOverflowError`.
  - Self-referential map with key folding disabled throws `StackOverflowError`.
  - Self-referential map with key folding enabled can fail with `OutOfMemoryError` (unbounded path growth).
- Baseline tests:
  - `mvn -q -pl json-io -Dtest=ToonWriterTest test` passes, indicating current tests do not cover the above container-cycle and writer-reuse failure modes.

### Target State
- No stack overflow or infinite traversal for cyclic `Map`/`Collection`/array graphs.
- Deterministic behavior for reused `ToonWriter` instances (no stale-visit contamination).
- Reduced per-object overhead in uniform-tabular POJO serialization.
- Lower allocation/write overhead in deeply nested structures.

## Optimization Plan
### Quick Wins
1. Reset per-write traversal state (`objVisited`, depth guard) at entry of `write()`.
2. Add cycle detection for structural containers (`Map`, `Collection`, arrays), not only POJOs.
3. Add visited guard to `collectFoldedPath()` to stop cyclic map-chain expansion.

### Major Optimizations
1. Cache/reuse `getObjectFields()` results during uniformity check + row write in tabular POJO flows.
2. Fast-path object arrays by casting to `Object[]` where legal, retaining reflective path only for primitive arrays.
3. Introduce indentation cache (per-depth prefix strings) to reduce tiny write calls.

### Architectural Changes
1. Unify traversal/cycle bookkeeping across TOON writer paths to mirror one consistent graph-walk policy.
2. Introduce explicit TOON behavior for duplicate references when `cycleSupport` is enabled vs disabled, then enforce consistently.
3. Add focused microbench harnesses for tabular POJO and deep nesting workloads to prevent regressions.

## Implementation Strategy
### Phase 1: Correctness Guardrails
- Fix writer-state reset and container-cycle traversal safety.
- Add failing regression tests for:
  - `ToonWriter` reuse across multiple writes.
  - Self-referential map/list/array handling (with and without key folding).

### Phase 2: Hot-path Optimizations
- Remove duplicate field extraction in tabular POJO mode.
- Add object-array fast path and indent cache.

### Phase 3: Behavioral Alignment
- Define and test cycle/duplicate-reference semantics for TOON relative to `WriteOptions.isCycleSupport()`.
- Ensure reader/writer interoperability for the chosen semantics.

## Performance Testing Plan
- Add regression tests covering cycle scenarios and writer reuse.
- Add microbench scenarios (JMH or equivalent) for:
  - Uniform POJO arrays (1k/10k elements).
  - Deep nested maps/lists.
  - String-heavy payloads with mixed quoting requirements.
- Track allocation rate, throughput, and p99 serialization latency before/after fixes.

## Risk Assessment
### Safe Optimizations
- State reset per write.
- Guarding key-fold traversal against revisiting the same map node.
- Indentation string caching.

### Moderate Risk
- Changing cycle behavior for container types (may alter output for previously broken cases).
- Reworking tabular POJO internals with field-cache reuse.

### High Risk
- Introducing TOON reference semantics changes tied to `cycleSupport` if backward compatibility assumptions exist.

## Recommendations
- Prioritize correctness defects first (cycle safety + state reset).
- Add dedicated regression tests before optimization refactors.
- Treat `cycleSupport` behavior as a documented contract and align ToonWriter implementation to that contract.

## Estimated Impact
- Correctness fixes eliminate catastrophic failures (SO/OOM/hangs) and data-loss cases in affected graphs.
- Hot-path improvements should materially reduce CPU/allocation overhead for large uniform collections and deep nested structures.

## Effort Estimate
- Quick wins: Small/Medium.
- Major optimizations: Medium.
- Full plan completion: Medium/Large depending on `cycleSupport` compatibility decisions.
