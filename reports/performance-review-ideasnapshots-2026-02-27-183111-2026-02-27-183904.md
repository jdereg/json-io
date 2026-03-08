# Performance Review Plan
Generated: 2026-02-27
Scope: /Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_2026_02_27_183111.jfr

## Executive Summary
Latest TOON profile still shows writer/read-side hotspots concentrated in map/object traversal and scalar parsing paths.
Top TOON writer leaf hotspot remains `ToonWriter.needsQuoting` (230 leaf samples), while inclusive stack pressure is highest around `writeMapWithSimpleKeys` and nested map/object emission.
TOON reader pressure is still centered on `readObject`, `putValue`, and `parseNumber`, with heavy `HashMap.put*` participation indicating object materialization cost.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] `ToonReader.readObject` dominates inclusive samples (1961 hits) and drives object creation/field parse path (`json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:155`).
- [ ] `ToonWriter.writeMapWithSimpleKeys` / `writeMap` stack dominates writer work (772 + 641 hits) (`json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java:1467`).
- [ ] `HashMap.putVal` / `HashMap.put` appear in top overall frames (329/328 hits), indicating map build pressure during TOON read/write object paths.

### High Priority Issues
- [ ] `ToonWriter.needsQuoting` is the top TOON writer leaf hotspot (230 leaf hits; ~45% of TOON writer leaf samples) (`ToonWriter.java:427`).
- [ ] `ToonWriter.getObjectFields` remains materialization-heavy (236 inclusive hits) (`ToonWriter.java:1789`).
- [ ] `ToonReader.parseNumber` remains top TOON reader leaf hotspot (88 leaf hits) (`ToonReader.java:874`).

### Medium Priority Issues
- [ ] `ToonReader.putValue` still contributes in strict/non-strict meta key processing (`ToonReader.java:1077`).
- [ ] `ToonWriter.writeCollectionElementsWithHeader` and tabular paths still show repeated per-element branching (`ToonWriter.java:860`).
- [ ] Writer string/indent churn (`FastWriter.write`, `StringLatin1.getChars`) remains visible in top frames.

## Performance Metrics
### Current State
- ExecutionSample events analyzed: 6,596
- Top overall inclusive hotspots:
  - `ToonReader.readObject`: 1,961
  - `JsonParser.readValue`: 1,009
  - `ToonWriter.writeMapWithSimpleKeys`: 772
  - `ToonWriter.writeMap`: 641
  - `ToonWriter.writeObjectFields`: 330
- Top TOON writer leaf hotspots:
  - `needsQuoting`: 230
  - `isPrimitive`: 83
  - `writeMapWithSimpleKeys`: 44
- Top TOON reader leaf hotspots:
  - `parseNumber`: 88
  - `getIndentLevel`: 26
  - `putValue`: 22

### Target State
- Reduce TOON writer leaf samples in `needsQuoting` and map/object field paths.
- Reduce TOON reader leaf samples in `parseNumber` / `putValue` and associated HashMap materialization.
- Narrow TOON-vs-JSON stack-frame gap in write/read benchmark loops.

## Optimization Plan
### Quick Wins
1. Continue reducing redundant field extraction in tabular POJO collection path (already implemented in current workspace; pending new JFR confirmation).
2. Add branch-fast path in `needsQuoting` for common alnum/underscore tokens before delimiter/meta checks.
3. Specialize `putValue` meta-key classification path to reduce repeated string checks on non-meta keys.

### Major Optimizations
1. Reduce map/object write branching in `writeMapWithSimpleKeys` by splitting common simple-map fast path from metadata/cycle path.
2. Rework TOON reader object materialization to reduce transient `HashMap` pressure (pre-sized maps and fewer intermediate string slices).
3. Add number parse fast path for decimal-only tokens to avoid fallback overhead in `parseNumber`.

### Architectural Changes
1. Introduce optional symbol/intern cache strategy for repeated field names in TOON read loops.
2. Consider format-neutral shared scalar encoder/decoder micro-optimizations between JSON and TOON to reduce duplicated overhead.
3. Evaluate amortized writer context reuse for repeated benchmark iterations.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Validate current collection tabular field-map reuse optimization against a fresh JFR.
- Tune `needsQuoting` branch ordering based on latest hot token patterns.

### Phase 2: Algorithm Improvements
- Refactor map writing into stricter hot-path and cold-path branches.
- Trim `putValue` / `parseNumber` overhead with fast-path parsing and reduced string transforms.

### Phase 3: System-level Changes
- Add stable microbenchmark harness around TOON writer/reader subroutines.
- Track regressions with snapshot-to-snapshot hotspot delta reporting.

## Performance Testing Plan
- Capture A/B JFR before and after each targeted TOON optimization.
- Record leaf and inclusive frame deltas for TOON hotspots.
- Run `JsonPerformanceTest` and compare TOON/JSON ratios for both cycle modes.
- Keep full `mvn clean test` gate after each optimization iteration.

## Risk Assessment
### Safe Optimizations
- Branch-order changes in hot methods (`needsQuoting`, `putValue`, `parseNumber`).
- Eliminating duplicate field extraction in writer tabular paths.

### Moderate Risk
- Refactoring map/object serialization flow in `writeMapWithSimpleKeys`.
- Reader map materialization changes affecting metadata semantics.

### High Risk
- Shared scalar pipeline redesign across formats.
- Aggressive caching that can affect correctness/thread-safety.

## Recommendations
- Prioritize TOON writer `needsQuoting` + map path first, then reader `parseNumber`/`putValue`.
- Keep optimization steps narrow and JFR-verified to avoid regressions.
- Use two-snapshot deltas (not single-run conclusions) to filter JFR variance.

## Estimated Impact
- Quick wins: measurable reduction in TOON writer leaf hotspots.
- Major optimizations: meaningful write/read ratio improvement toward JSON baseline.
- Full plan: TOON performance parity progression with controlled correctness risk.

## Effort Estimate
- Quick wins: low implementation complexity.
- Major optimizations: medium complexity with focused regression coverage.
- Complete plan: iterative multi-cycle profiling and tuning.
