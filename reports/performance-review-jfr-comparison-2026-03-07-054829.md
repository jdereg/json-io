# Performance Review Plan
Generated: 2026-03-07-054829
Scope: /Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_2026_03_07_004531.jfr compared to /Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_2026_03_05_234532.jfr

## Executive Summary
The newest `JsonPerformanceTest` JFR shows the recent `ToonReader.cacheSubstring()` change behaving as intended: substring-allocation pressure dropped sharply while overall `ToonReader` execution share ticked slightly down. The clearest win is in allocation-heavy string construction paths, where `cacheSubstring`-attributed allocation weight fell by about 98% and `ToonReader`'s overall allocation share dropped from about 30.3% to 22.0%. The main remaining hotspots are now `peekLine()` string creation, `readInlineArray()` token materialization, and `JsonObject`/object-array growth in `putValue()`-adjacent paths.

## Performance Bottlenecks
### Critical Issues (Severe Impact)
- [ ] **`peekLine()` still materializes trimmed line strings on every consumed line**  
      In the new recording, allocation weight attributed inside `ToonReader` shifted toward `peekLine()` and caller-visible line parsing work once `cacheSubstring()` stopped allocating on hits. This aligns with `new String(buf, ...)` in `ToonReader.peekLine()` remaining a prominent surviving allocation source.
- [ ] **Inline/tabular token parsing still creates temporary strings per element**  
      `readInlineArray()` remains visible in execution samples (`82 -> 87` first-frame samples), and the code still turns `StringBuilder` content into per-element `String` instances before scalar parsing. With substring churn reduced, this path is more exposed as the next likely TOON read allocator.
- [ ] **Object growth/copying is now a more visible allocator**  
      Sampled allocation weight for `[Ljava/lang/Object;` within `ToonReader` stacks rose from about `3.67 GB` to `9.68 GB`, which points at `JsonObject.appendFieldForParser()`/array growth and adjacent `putValue()` assembly work becoming the next dominant non-string cost.

### High Priority Issues
- [ ] **`cacheSubstring()` is now more CPU-visible even though it allocates far less**  
      First-frame execution samples for `ToonReader.cacheSubstring()` rose from `118` to `284` samples (`~2.0% -> ~5.0%` of all execution samples). This looks like a good trade so far, but the extra hash/range-compare work should be monitored with tighter A/B runs.
- [ ] **`readObject()` now carries more visible residual allocation weight**  
      Allocation weight attributed to first Toon frame `readObject` rose from about `2.38 GB` to `9.03 GB`. This likely reflects caller-level re-attribution after `cacheSubstring()` stopped allocating, but it also highlights that object field parsing still has meaningful work to remove.
- [ ] **Line parsing and object parsing are still broad TOON read bottlenecks**  
      Top new ToonReader execution frames remain `readLineRaw`, `peekLine`, `readObject`, `putValue`, and `trimAsciiRange`. None exploded, but they continue to dominate the remaining non-`cacheSubstring` read cost.

### Medium Priority Issues
- [ ] **`cacheString()` disappeared as a hotspot, but string comparison work moved into probe logic**  
      `cacheString()` first-frame execution samples fell from `147` to `0`, while `cacheSubstring()` stacks are now dominated by `regionMatches`/mismatch-style work. This is expected, but it means cache-hit rate and collision behavior now matter more.
- [ ] **Global string-array copying is down, but not gone**  
      Across the recordings, `Arrays.copyOfRangeByte` sampled allocation weight fell from about `20.00 GB` to `15.33 GB` and `StringLatin1.newString` from `2.45 GB` to `0.12 GB`. That is a strong improvement, but it also shows there is still plenty of residual string/byte-array traffic outside the optimized substring path.
- [ ] **The JFR comparison is sampled and run durations differ slightly**  
      Recording duration moved from `119s` to `116s`, so very small deltas should be treated as noise. The large allocation shifts are convincing; the small execution shifts deserve repeat runs before concluding finer-grained CPU movement.

## Performance Metrics
### Current State
- Recording assumption: no `.jfr` files were found under `/Users/jderegnaucourt/IdeaProjects`, so the newest and prior `JsonPerformanceTest_*.jfr` recordings under `/Users/jderegnaucourt/IdeaSnapshots` were compared instead.
- Recording duration: **119s -> 116s** (**-2.5%**)
- Execution samples: **5880 -> 5737** (**-2.4%**)
- Sampled allocation weight: **104.08 GB -> 94.88 GB** (**-8.8%**)
- `ToonReader` execution sample share: **18.76% (1103/5880) -> 17.41% (999/5737)**
- `ToonReader` allocation sample share: **30.32% -> 21.99%**
- `ToonReader.cacheSubstring()` execution samples: **118 -> 284**
- `ToonReader.cacheSubstring()` sampled allocation weight: **7.64 GB -> 0.185 GB**
- String-related sampled allocation inside `ToonReader` stacks (`String` + `[B` + `[C`): **25.91 GB -> 11.08 GB** (**-57%**)
- Global sampled allocation weight:
  - `Arrays.copyOfRangeByte`: **20.00 GB -> 15.33 GB**
  - `StringLatin1.newString`: **2.45 GB -> 0.12 GB**
- Key bottlenecks after the change: `peekLine()` line-string creation, `readInlineArray()` token materialization, `readObject()`/`putValue()` object assembly, and object-array growth/copying

### Target State
- `ToonReader` execution sample share: **< 16%**
- `ToonReader` allocation sample share: **< 18%**
- `cacheSubstring()` sampled allocation weight: keep near zero while preventing its execution share from becoming a new bottleneck
- Remaining line/token string allocation: reduce `peekLine()` + inline-array/string-builder derived allocation by another **25-35%**
- Object-array growth/copying: reduce `[Ljava/lang/Object;` sampled allocation within ToonReader stacks by **30%+**

## Optimization Plan
### Quick Wins (1-3 days)
1. Re-profile and optimize `peekLine()` string creation now that `cacheSubstring()` is no longer masking it.
2. Add a no-escape fast path for `readInlineArray()` to avoid `StringBuilder -> String` materialization for common inline/tabular tokens.
3. Measure and trim parser-side `JsonObject` append growth/copy overhead, especially around `appendFieldForParser()` and backing-array resizing.

### Major Optimizations (1-2 weeks)
1. Parse tabular rows directly into `JsonObject` without the intermediate `List<Object>`.
2. Refactor `readObject()` / `readInlineObject()` into a shared field parser so any trimming/tokenization improvement benefits both hot paths.
3. Reduce line parsing allocations by introducing range/slice handling for trimmed content or a non-materializing blank/comment/indent fast path where possible.

### Architectural Changes (2-4 weeks)
1. Move ToonReader further toward range-based token parsing so strings are materialized only when needed by downstream APIs.
2. Add TOON-specific microbenchmarks and repeated JFR capture scripts to distinguish signal from single-run noise.
3. Consider parse-context/input-buffer reuse at the TOON entry path after the internal hot spots are reduced.

## Implementation Strategy
### Phase 1: Low-hanging Fruit
- Validate the `cacheSubstring()` improvement with one or two additional identical JFR A/B runs.
- Attack `peekLine()` and `readInlineArray()` next, since they are the most visible remaining string allocators.
- Track object-array growth in `JsonObject` during parse to confirm whether capacity policy is the next easy win.

### Phase 2: Algorithm Improvements
- Eliminate the tabular-row intermediate list.
- Consolidate duplicated object-field parsing and trimming logic.
- Reduce redundant line/token materialization by switching more code paths to range-based parsing.

### Phase 3: System-level Changes
- Add repeatable TOON-specific profiling scripts and regression metrics.
- Revisit TOON entry-point buffer reuse if parser-internal allocations stop dominating.
- Use JFR plus allocation-focused tooling to validate each batch of improvements before moving on.

## Performance Testing Plan
- Repeat the same `JsonPerformanceTest` scenario at least 3 times before and after each targeted TOON optimization.
- Capture JFRs for:
  - full benchmark run
  - maps-only TOON read
  - parse-only microbenchmarks for inline arrays and object-heavy documents
- Track:
  - `ToonReader` execution/allocation share
  - `cacheSubstring`, `peekLine`, `readInlineArray`, `readObject`, `putValue`
  - string/byte-array/object-array sampled allocation weights
- Add targeted benchmarks for:
  - cache-hit-heavy short keys/values
  - dense inline arrays
  - tabular arrays
  - dotted-key/object-growth scenarios

## Risk Assessment
### Safe Optimizations
- Additional allocation-focused tuning in `peekLine()` and `readInlineArray()`
- Profiling-only instrumentation or repeated JFR capture
- Small parser-side collection/capacity optimizations with strong regression coverage

### Moderate Risk
- Range-based parsing changes in object and array hot paths
- Direct-to-`JsonObject` tabular parsing
- `JsonObject` storage policy changes for parser-heavy workloads

### High Risk
- Broad slice-based parser refactors touching most ToonReader branches
- Entry-path buffer lifecycle changes
- Changes that could affect strict TOON parsing semantics or quoted/escaped token behavior

## Recommendations
- Treat the `cacheSubstring()` change as a successful allocation optimization; it materially reduced TOON read allocation pressure.
- Do not overreact to the higher `cacheSubstring()` execution sample count without repeated A/B runs; overall ToonReader CPU share still improved slightly.
- Focus the next optimization batch on `peekLine()`, `readInlineArray()`, and object-array growth in parser output structures.
- Keep using JFR-guided deltas rather than single-run wall-clock numbers for TOON parser work, because attribution shifts are common after each hotspot is reduced.

## Estimated Impact
- Quick wins: **8-15%** additional TOON read allocation reduction and modest CPU improvement
- Major optimizations: **15-25%** additional TOON read improvement
- Full plan: **25-40%** cumulative TOON read improvement from the current post-`cacheSubstring()` baseline

## Effort Estimate
- Quick wins: **1-3 engineering days**
- Major optimizations: **1-2 engineering weeks**
- Complete plan: **2-4 engineering weeks**
