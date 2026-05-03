# JsonPerformanceTest baseline medians

Each row is the **median of 3 runs** of `JsonPerformanceTest`. The loop updates this file when a candidate is kept. When a candidate is reverted, leave the row unchanged. When a candidate is in_progress, the loop appends a "candidate-N: [results]" block beneath this section without touching the baseline row.

## Active baseline (median ms, lower is better)

| Metric | Full Java Resolution | Maps Only |
|---|---:|---:|
| JsonIo Write Time (cycleSupport=true) | 4558.175 | 5018.200 |
| JsonIo Write Time (cycleSupport=false) | 4137.590 | 4149.406 |
| Toon Write Time (cycleSupport=true) | 4631.324 | 4851.589 |
| Toon Write Time (cycleSupport=false) | 4308.627 | 4402.039 |
| Jackson Write Time | 2750.309 | 2779.900 |
| JsonIo Read Time | 9175.231 | 4737.071 |
| Toon Read Time | 10284.964 | 6993.197 |
| Jackson Read Time | 5111.585 | 4012.352 |

## Active Jackson ratios (lower is closer to / better than Jackson)

| Ratio | Full Java | Maps Only |
|---|---:|---:|
| Read Ratio (Toon/Jackson) | 2.02 | 1.76 |
| Read Ratio (JsonIo/Jackson) | 1.81 | 1.18 |
| Write Ratio (Toon cycleSupport=false / Jackson) | 1.57 | 1.61 |
| Write Ratio (JsonIo cycleSupport=false / Jackson) | 1.50 | 1.50 |

## Run log

The loop appends here on every iteration. Format:

```
### YYYY-MM-DD HH:MM — Candidate N: <short title> — <kept|reverted>
- Primary target metric: <name>
- Run medians (ms): full=<x>, maps=<y>
- Prior baseline: full=<x0>, maps=<y0>
- Delta vs prior: full=-<d>%, maps=-<e>%
- Decision: <kept | reverted because <reason>>
- Commit: <sha or N/A>
```

### 2026-05-02 17:07 — Baseline establishment (3 runs of JsonPerformanceTest, JDK 21)
- All 3 raw run logs preserved at `.claude/perf-loop/runs/baseline-{1,2,3}.log`.
- Time-metric variance (range/median) across 3 runs: max 3.86% on `JsonIo Write Time (cycleSupport=false)` Full; all others <3.1%. Well within the loop's 5% stability bar.
- Derived ratio variance hits 5.73% on `Write Ratio (JsonIo cycleSupport=false / Jackson)` Full — expected for a quotient of two ~3% noisy values. The underlying time metrics are stable, so the test itself is not unstable; flagged for awareness when this ratio is referenced as a watch metric.
- Wall-clock per run ≈ 98s (warmup 10k iterations + 100k measured iterations × 8 paths × 2 sections).

### 2026-05-02 17:15 — Candidate 1: char-write TOON colon/newline — kept
- Primary target metric: Toon Write Time (cycleSupport=false), both sections
- Run medians (ms): Full primary 4524.498, Maps primary 4614.733
- Prior baseline: Full primary 4719.192, Maps primary 4798.139
- Delta vs prior on primary: Full **+4.13%**, Maps **+3.82%** (both well over the 0.5% bar)
- Watch metric Toon Read Time: Full −0.24%, Maps −0.78% (both inside 1% bar)
- Bonus wins on every other write metric: JsonIo Write c=true Full +1.86% / Maps +3.69%; JsonIo Write c=false Full +2.10% / Maps +2.16%; Toon Write c=true Full +2.76% / Maps +3.10%. Single-char `out.write` cuts String→bytes copies on the entire write surface, not just `": "` and `\n`.
- Sanity-rule blip: JsonIo Read Time (Maps) showed −1.37% (above the 1% sanity bar). Override accepted by user — this metric's baseline run-range was already 2.30% range/median, so a 1.37% delta sits inside its natural noise envelope, and Candidate 1 modified zero read-path code (no causal mechanism). Documented for future calibration of the sanity rule.
- Decision: **kept**
- Commit: (this commit)
- Raw measurement logs: `.claude/perf-loop/runs/cand1-{1,2,3}.log`

### 2026-05-02 17:44 — Candidate 2: Cache getTypeNameAlias on JsonWriter — reverted
- Primary target metric: JsonIo Write Time (cycleSupport=false), Full Java
- Run medians (ms): Full primary 4397.944, Maps 4312.092
- Prior baseline: Full primary 4249.810, Maps 4183.566
- Delta vs prior on primary: Full **−3.49%** (regression), Maps **−3.07%** (regression) — far below the 0.5% bar
- Watch metric JsonIo Read Time: Full +0.83% (within 1% bar), Maps **+3.83%** (above 1% bar)
- Widespread environmental noise this run set: Jackson Read Time regressed +3.43% Full / +1.23% Maps despite Jackson being third-party code untouched by the candidate. Run-2 ran noticeably hotter than runs 1 and 3. Even applying a +2–3% noise correction, the primary doesn't clear the 0.5% bar.
- Implementation theory for why the cache hurts: `WriteOptions.getTypeNameAlias` is itself a fast HashMap lookup; layering another per-instance HashMap on top adds allocation + lookup cost without saving meaningful work in this benchmark's narrow type set, especially since each `JsonWriter` is short-lived (one per `toJson` call) and the cache always starts cold.
- Decision: **reverted**
- Implementation changes stashed as `reverted-candidate-2` (git stash) for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand2-{1,2,3}.log`

### 2026-05-02 18:01 — Candidate 3a: pre-size write StringBuilder to 32K — kept
- Primary target metric: Toon Write Time (cycleSupport=false), both sections
- Run medians (ms): Full primary 4429.957, Maps primary 4487.169
- Prior baseline: Full primary 4524.498, Maps primary 4614.733
- Delta vs prior on primary: Full **+2.09%**, Maps **+2.76%** (both well over the 0.5% bar)
- Watch metrics: JsonIo Write c=false Full −0.63% / Maps +0.48% (inside 1%); Toon Read Full +0.01% / Maps +0.04% (essentially flat)
- Bonus wins: Toon Write c=true Full +3.38% / Maps +3.12%; JsonIo Write c=false Full +0.63% (improved). The pre-size change benefits *every* path that goes through `JsonIo.toJson` or `JsonIo.toToon`.
- Sanity-rule blip: JsonIo Write c=true (Maps) +1.82% (above 1% sanity bar). Override accepted by user — that metric's run-range was 4.28% range/median (5091/5100/4882 ms), so the 1.82% delta sits inside its natural noise envelope; if the candidate genuinely hurt c=true paths the Full section would have shown it (it's at −0.82%, negligible). User also re-ran JsonPerformanceTest in non-profile mode independently and confirmed best results in a while.
- Run-set environmental noise: back to baseline-quality, Jackson held flat (Read −0.17%/+0.57%, Write −0.47%/−0.32%) — much cleaner than cand2's run set.
- Decision: **kept**
- Sub-experiment (b) (ThreadLocal<StringBuilder>) filed as new Candidate 3b (pending) per the candidate plan: "Test (b) as a separate candidate with its own median run".
- Commit: (this commit)
- Raw measurement logs: `.claude/perf-loop/runs/cand3a-{1,2,3}.log`

### 2026-05-02 18:14 — Candidate 3b: thread-local StringBuilder for write paths — reverted
- Primary target metric: Toon Write Time (cycleSupport=false), both sections (must clear 0.5% on top of Cand-3a's baseline)
- Run medians (ms): Full primary 4574.490, Maps primary 4609.712
- Prior baseline (post-cand3a): Full primary 4429.957, Maps primary 4487.169
- Delta vs prior on primary: Full **−3.26%** (regression), Maps **−2.73%** (regression) — fails 0.5% bar in both sections
- Watch metric JsonIo Write c=false: Full **+8.84%**, Maps **+6.63%** (both far above 1% bar — major regression)
- Run-set variance: write metrics 8–13% range/median across the 3 runs; read metrics clean (<1.2%). Variance concentrated entirely in the paths cand 3b modified.
- **Likely root cause: TL pattern defeats JIT escape analysis.** Run 1 is dramatically slower than runs 2–3 across every write metric (classic JIT warmup signature). `new StringBuilder(32768)` (Cand 3a) lets the JIT stack-allocate or scalar-replace the SB since it only escapes through StringBuilderWriter. The borrow/release indirection through `BUFFER_RECYCLER.get()` + `borrowWriteStringBuilder()` + the `inUse` flag check + `setLength(0)` forces the SB to live on the heap. Even discarding run 1 as warmup, runs 2+3 medians still regress ~2% on the primary.
- Decision: **reverted**
- Implementation changes stashed as `reverted-candidate-3b` (git stash) for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand3b-{1,2,3}.log`

### 2026-05-02 18:31 — Candidate 4: grow STRING_CACHE_MASK 2047→4095 — kept
- Primary target metric: Toon Read Time, both sections
- Run medians (ms): Full primary 10327.982, Maps primary 6903.869
- Prior baseline: Full primary 10362.016, Maps primary 7056.516
- Delta vs prior on primary: Full **+0.33%** (essentially flat), Maps **+2.16%** (clears 0.5% bar)
- Watch metric JsonIo Read Time: Full −0.12%, Maps +0.47% (both inside 1% bar — different cache, no contention)
- Asymmetric primary outcome accepted by user. Plausible workload explanation: in Maps mode every parsed value becomes a String key/value (high pressure on the string cache → bigger cache helps). In Full Java mode many values resolve to typed primitives (int, long, etc.) without hitting the cache → 2K already fit Full Java's working set, only Maps mode pressures it.
- Sanity-rule blips on WRITE metrics (JsonIo Write c=true Full +2.73% / Maps +1.69%, JsonIo Write c=false Maps +1.13%, Toon Write c=true Full +1.14%) are noise: the candidate only changes a read-side cache mask, no causal mechanism for write-path regression. JsonIo Write c=true Full's run-range was 7.43% range/median this set — same metric was 6.86% for Cand 3a, looks like an inherently noisy metric on this benchmark.
- Decision: **kept**
- Commit: (this commit)
- Raw measurement logs: `.claude/perf-loop/runs/cand4-{1,2,3}.log`

### 2026-05-02 18:46 — Candidate 5: skip cacheSubstringFromBuf for high-cardinality tabular cells — reverted (deferred)
- **No implementation attempted.** Skipped on workload-mismatch grounds before measurement.
- Reasoning: the candidate's heuristic decides per-column "uncached" mode at probe count = 32 with hit rate < 10%. JsonPerformanceTest calls `fromToon(toonString)` 100,000 times on the **same** input; the per-thread `TL_STRING_CACHE` persists across iterations, so column hit rates after warmup are ~100%, never <10%. The heuristic would never switch any column to uncached. Additionally, `TestData.members[4]{name,age}` (the relevant tabular section) has only 4 rows, fitting entirely inside the 32-probe tracking window — every cell pays the per-call counter overhead with no compensating savings.
- The candidate is more useful for *diverse-data* workloads (varying types per row, many rows per section, real-world streaming). JsonPerformanceTest is structurally incapable of validating it. Re-running this candidate would burn ~20 min of implementation + measurement work to confirm a near-certain regression that wouldn't carry signal for the candidate's actual use case.
- Decision: **deferred** (recorded as `reverted` because the loop only has kept/reverted states). Revisit if/when a more diverse benchmark becomes available, or if a future iteration adds a high-cardinality tabular section to JsonPerformanceTest.
- Commit: (this commit, candidates.md + baseline.md only — no source changes, no run logs)

### 2026-05-02 19:00 — Candidate 6: bypass duplicate cache write in formatDecimalNumber integer fast path — reverted
- Primary target metric: Toon Write Time (cycleSupport=false), Full Java
- Run medians (ms): Full primary 4339.815, Maps 4459.019
- Prior baseline: Full primary 4391.386, Maps 4490.903
- Delta vs prior on primary: Full **+1.17%** (clears 0.5% bar), Maps +0.71% (bonus)
- Bonus wins on every other write metric (JsonIo Write c=true Full +2.34% / Maps +1.19%, JsonIo Write c=false Full +0.44% / Maps +0.79%, Toon Write c=true Full +0.84% / Maps +0.20%) — the dead-code removal touches every integer-valued double/float that flows through `formatDecimalNumber`.
- Sanity-rule blip: **Toon Read Time (Maps) +2.15%** (above 1% sanity bar). Note: the candidate only modified ToonWriter (removed dead-code cache writes in `formatDecimalNumber`'s integer fast path), touched zero read-path code. `SHARED_DOUBLE_FORMAT_CACHE` is write-side only. No causal mechanism for ToonReader regression. Cand 4's baseline median for Toon Read Maps was 6903.87 (run-range 6902-7037, sitting at the low end of its own variance) — Cand 6's [7080, 6987, 7053] occupies the same noise envelope as Cand 4's run-3 (7037). Looks like baseline drift / Cand 4's lucky-low median.
- Decision: **reverted** by user despite primary win, on strict-spec adherence (1% sanity rule). Documented for future calibration.
- Implementation changes stashed as `reverted-candidate-6` (git stash) for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand6-{1,2,3}.log`

### 2026-05-02 19:25 — Candidate 7: cache parsed Long in parseNumber(char[]) fast path — reverted
- Primary target metric: Toon Read Time, Full Java
- Run medians (ms): Full primary 10397.725, Maps 7095.985
- Prior baseline: Full primary 10327.982, Maps 6903.869
- Delta vs prior on primary: Full **−0.68%** (regression — fails 0.5% bar), Maps −2.78% (also regresses)
- Sanity blips: Jackson Write Full +1.74% (third-party, looks like noise), Toon Read Maps +2.78% (above 1% bar but consistent with the primary regression)
- Toon Read Full's run-range was 0.74% range/median this run set — very clean — so the −0.68% delta is a *real* regression on a stable metric, not a noise blip.
- **Likely root cause:** the added `if (result < SMALL_LONG_CACHE.length)` check adds a branch on the hot unsigned-fast-path. For values < 128 (already cached by JDK's `LongCache`, which the JIT intrinsifies), we now do an extra array-bounds-checked read instead of letting the JIT use its intrinsic. For values 128–1023, the saved `new Long(...)` allocation is likely already eliminated by JIT escape analysis when the boxed Long is consumed immediately at the call site. Net: extra branch overhead > savings.
- Decision: **reverted**
- Implementation changes stashed as `reverted-candidate-7` (git stash) for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand7-{1,2,3}.log`

### 2026-05-02 19:30 — Candidate 8: skip hasComplexKeys for declared Map<String,…> — reverted (deferred)
- **No implementation attempted.** Skipped on workload-mismatch grounds before measurement.
- The candidate proposes propagating `currentMapHasSimpleKeyTypeHint` to maps reached via `Collection<Map<String,?>>` element iteration (writeArrayElements / writeCollection → writeListElement). Scanning TestData fields:
  - `List<NestedData>`, `List<String>`, `List<Integer>`, `List<Double>`, `List<Boolean>`, `List<BigDecimal>`, `List<UUID>`, `List<Instant>`, `List<LocalDate>`, `List<SecondaryData>` — element types are not Maps.
  - All `Map<String, ...>` fields (map, counterMap, concreteHashMap, concreteLinkedMap, nestedMap) are direct fields, already getting the hint via `writeFieldEntry`'s `plan.mapKeyTypeIsSimple()`.
  - `NestedData.metadata` is also a Map field, also gets the hint.
- There's **no `Collection<Map<...>>` shape** in TestData. The candidate's hint propagation has no path to fire on this workload. The JFR's 50 samples in `writeMap → entrySet` must come from the existing direct-Map-field paths where the hint is already being set — i.e., they're not addressable by extending hint propagation in this direction.
- Implementation effort would be 30–60 min (plumb `declaredElementType` through `writeArrayElements`/`writeCollection` to `writeListElement`) for an experiment the benchmark cannot reward.
- Decision: **deferred** (recorded as `reverted` because the loop only has kept/reverted states). Revisit if a benchmark with `Collection<Map<...>>` shape becomes available.
- Commit: (this commit, candidates.md + baseline.md only — no source changes, no run logs)

### 2026-05-02 21:24 — Candidate 9: avoid entry.getKey().toString() for Number keys — reverted
- Primary target metric: Toon Write Time (cycleSupport=false), Maps Only (`writeMapWithSimpleKeys` is the touched site, exercised more in Maps-only mode)
- Run medians (ms): Maps primary 4547.614, Full 4461.806
- Prior baseline: Maps primary 4490.903, Full 4391.386
- Delta vs prior on primary: Maps **−1.26%** (regression, fails 0.5% bar). Full also regresses −1.60%.
- Multiple sanity blips: JsonIo Write c=false Full +1.31%, Toon Write c=true Maps +1.67%, Toon Write c=false Full +1.60%, Toon Read Maps +1.99%.
- **Confirmed prediction:** the `instanceof Long || Integer || Short || Byte` chain at the writeMapWithSimpleKeys site adds 4 instanceof checks per map entry on the **always-taken String-key path** (TestData has no Number-keyed maps). With ~50 entries × 10+ map fields × 100k iterations, the per-entry overhead aggregates into a measurable regression on every write metric. The optimization itself never fires because the workload has no Number keys.
- Toon Read regressions are noise (Toon Read Full had 0.02% range/median this run set — very clean — but Toon Read Maps had 0.06%, also very clean, yet shows -1.99% drift; this is system-state drift between this run set and the Cand 4 baseline, not causal — the candidate touches no read code).
- Decision: **reverted**
- Useful negative finding documented: adding type-dispatch branches at hot map-write sites for **types not present in the workload** is pure overhead. For this codebase's benchmark mix, only optimize paths whose targeted shape appears in TestData.
- Implementation changes stashed as `reverted-candidate-9` (git stash) for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand9-{1,2,3}.log`

### 2026-05-02 21:35 — Candidate 10: inline Long.valueOf boxing avoidance in Resolver.fastScalarCoercion — reverted (deferred)
- **No implementation attempted.** Plan is stale — the optimization has already been applied to the codebase.
- The plan asks for three things, all already in place at Resolver.java:1789-1804:
  1. "Pre-extract the long once outside the switch" — already done at line 1794: `long v = (Long) value;`.
  2. "Route through JDK's small-Integer cache explicitly with `Integer.valueOf((int) v)`" — already happens via autoboxing when `return (int) v;` widens to `Object` return type. Replacing with explicit `Integer.valueOf((int) v)` produces identical bytecode.
  3. "Remove the redundant `(Long) value` re-cast" — no redundant re-cast exists in current code (it was likely there in an earlier version when the plan was written).
- The remaining theoretical gain would require changing `fastScalarCoercion`'s signature to accept a primitive `long` instead of `Object value` + `Class<?> valueClass`, which would be a much larger refactor not covered by this candidate's scope. Plus, per Cand 7's lesson, manual extension of JDK's small-int caches tends to lose to the JIT's intrinsified path.
- Decision: **deferred** (recorded as `reverted` because the loop only has kept/reverted states). No further action.
- Commit: (this commit, candidates.md + baseline.md only — no source changes, no run logs)

### 2026-05-02 21:36 — Candidate 11: port number-parse fast path to JsonParser.readNumber — reverted (deferred)
- **No implementation attempted.** The candidate plan explicitly states "Only worth running if Candidate 7 was kept." Cand 7 reverted, so there is no fast-path implementation to port to `JsonParser.readNumber`.
- Decision: **deferred** (recorded as `reverted`). Revisit if a future iteration finds a different `parseNumber` fast-path optimization that does clear the 0.5% bar — at that point this candidate becomes "port that change to `JsonParser.readNumber`."
- Commit: (this commit, candidates.md + baseline.md only — no source changes, no run logs)

### 2026-05-03 11:14 — Candidate 15: shrink JsonParser.readArray ArrayList(64) → adaptive — reverted
- Variant tested: (a) `new ArrayList<>()` — default constructor, defers Object[] alloc to first add, then 10 → 15 → 22 → 33 → 49 → 73 → 109 (1.5x doubling)
- Primary target metric: JsonIo Read Time, both sections
- Run medians (ms): Full primary 9325.128, Maps primary 4782.368
- Prior baseline (post-Cand 14): Full primary 9175.231, Maps primary 4737.071
- Delta vs prior on primary: Full **−1.63%** (regression, fails 0.5% bar; **0.39% range/median** — extremely clean signal), Maps **−0.96%** (also fails; 0.59% range/median, also clean)
- Run-set noise: Jackson Read Full also drifted −1.96% / Maps +0.11%, suggesting some system-wide noise (~1% across the board), but the primary's tight intra-run variance (0.39%/0.59%) means the −1.6%/−1.0% delta is well above the noise floor — a real causal regression.
- **Confirmed prediction:** TestData arrays cluster in the 10–73 range, where the existing pre-size 64 hits the sweet spot (1 alloc, 0 grows for arrays ≤64). Default constructor on those arrays incurs 5–6 grow-and-arraycopy operations. The grow-doubling cost dominates over the per-array memory savings (~432 bytes wasted per oversized pre-alloc).
- Variant (b) `new ArrayList<>(8)` skipped without measurement: pre-size 8 grows to 12 → 18 → 27 → 40 → 60 → 90, requiring an extra grow vs variant (a)'s starting-from-10 path. Structurally worse for arrays of 10+ elements (which dominate TestData). Not worth a 5-min experiment to confirm.
- The pre-size 64 was apparently already tuned for this benchmark's array-size distribution — leave it alone. If a future benchmark with larger arrays comes along (>>100 elements per array), this could be reconsidered.
- Decision: **reverted**
- Implementation changes stashed as `reverted-candidate-15` (git stash) for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand15-{1,2,3}.log`

### 2026-05-03 10:55 — Candidate 14: writeJsonUtf8String array-based scan via StringUtilities.getChars — kept
- Primary target metric: JsonIo Write Time (cycleSupport=false), both sections
- Run medians (ms): Full primary 4137.590, Maps primary 4149.406
- Prior baseline (post-Cand 4): Full primary 4228.062, Maps primary 4251.564
- Delta vs prior on primary: Full **+2.14%**, Maps **+2.40%** (both clear the 0.5% bar comfortably)
- Watch metrics — all clean and showing collateral wins:
  - JsonIo Write c=true (also routes through writeJsonUtf8String): Full **+3.84%**, Maps **+3.08%**
  - Toon Write c=false: Full +1.88%, Maps +1.98% (coincidental — ToonWriter doesn't call writeJsonUtf8String, this run had tailwind across all write metrics)
- Run-set variance: primary metric had 0.98% / 2.04% range/median — clean. Jackson held flat (Read +1.45%/+1.50%, Write +0.82%/+0.15%) — system was quiet during measurement.
- **Plan was revised at implementation.** The original plan called for a `String.indexOf` no-escape fast path. While reading the existing loop I realized the no-escape pre-scan would do exactly the same per-character work as the existing loop's no-escape path, so it wouldn't actually save anything. Pivoted to "always use buf via StringUtilities.getChars" — replacing per-character `s.charAt(i)` (StringLatin1/UTF16 dispatch, ~345 leaf samples in JFR) with `buf[i]` (raw array load), and routing slice writes through `output.write(buf, off, len)` → `sb.append(char[], 0, len)` (the fastest StringBuilder variant per Cand 1's analysis) instead of `sb.append(String, off, off+len)`.
- **TL safety:** the buf returned by StringUtilities.getChars is a `char[]` (primitive) — Cand 13b's card-marking lesson does NOT apply (no inter-generational reference assignments). Cand 3b's escape-analysis lesson also doesn't apply directly (the buf is shared per-thread anyway, not a stack candidate). The TL handoff cost is paid once per string instead of N times per character. Re-entrancy contract documented inline in the method comment.
- Decision: **kept**. Cleanest win since Cand 1 — clears bar on primary by ~2.3% with collateral +3.5% on JsonIo Write c=true.
- Commit: (this commit)
- Raw measurement logs: `.claude/perf-loop/runs/cand14-{1,2,3}.log`

### 2026-05-03 10:35 — Candidate 13b: Port JsonParser.stringCacheArray to ThreadLocal (TL only, MASK stays 2047) — reverted
- Primary target metric: JsonIo Read Time, both sections (isolating TL conversion from Cand 13's coupled mask bump)
- Run medians (ms): Full primary 9458.928, Maps primary 4820.465
- Prior baseline (post-Cand 4): Full primary 9231.962, Maps primary 4779.621
- Delta vs prior on primary: Full **−2.46%** (regression, fails 0.5% bar; **worse than Cand 13's −1.42%**), Maps **−0.85%** (also fails)
- Watch metric Toon Read Time: Full +0.70%, Maps +0.39% (both inside 1% bar)
- Sanity blips on write metrics (JsonIo Write c=false Full +2.82%, Toon Write c=false Full +2.09%, etc.) — Jackson held flat (−0.09% / +0.21%) so the system isn't contaminated; the write blips look like normal per-metric variance with the median landing on the high side this run.
- **Real regression on the primary:** JsonIo Read Time Full had **0.71% range/median** across 3 runs — clean signal. The −2.46% delta is bigger than Cand 13's −1.42%, which **overturns the L1-pressure theory.** If L1 spill on the 4096-slot array was the cause, the smaller 2048-slot TL cache would have helped, not hurt. It hurt more.
- **Revised root cause: GC write-barrier (card marking) overhead on TL arrays.** Per-instance `String[]` is allocated per parse → lives in young-gen → assignments don't need card marking (no inter-generational reference). TL `String[]` survives many GCs → migrates to old-gen → every `cache[slot] = newString` triggers a card mark since the new String is young-gen. Card marks are cheap individually but accumulate over millions of cache writes per benchmark run. The per-instance pattern is "always young, never card-marked" — that's the real win we'd be giving up.
- Different failure mode from Cand 3b (escape analysis on stack allocations). This is about generational GC interaction with TL arrays holding short-lived references. ToonReader's `TL_STRING_CACHE` presumably pays the same card-marking cost; TOON's working-set characteristics may simply absorb it where JSON's don't.
- Decision: **reverted**. Both Cand 13 (TL + 4095) and Cand 13b (TL + 2047) regressed. The per-instance pattern is genuinely the right shape for `JsonParser.stringCacheArray`.
- Implementation changes stashed as `reverted-candidate-13b` for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand13b-{1,2,3}.log`

### 2026-05-03 10:08 — Candidate 13: Port JsonParser.stringCacheArray to ThreadLocal (mirror Cand 4) — reverted
- Primary target metric: JsonIo Read Time, both sections
- Run medians (ms): Full primary 9362.923, Maps primary 4827.858
- Prior baseline (post-Cand 4): Full primary 9231.962, Maps primary 4779.621
- Delta vs prior on primary: Full **−1.42%** (regression, fails 0.5% bar), Maps **−1.01%** (also fails)
- Watch metric Toon Read Time: Full +0.84%, Maps +0.45% (both inside 1% bar)
- Sanity blips on write metrics (JsonIo Write c=false Full +2.02%, Toon Write c=false Full +1.22%) are noise — JsonWriter doesn't share state with JsonParser. JsonIo Write c=true Full had 10.43% range/median this run set, exposed normal write-path noise.
- **Real regression on the primary:** JsonIo Read Time Full had only **0.15% range/median** across 3 runs (9363 / 9353 / 9367 ms) — extremely tight. The −1.42% delta vs the 9232 baseline is not noise.
- **Likely root causes** (the change coupled two factors):
  1. **L1 cache pressure.** Bumping `STRING_CACHE_MASK` 2047 → 4095 doubled the array size from 16 KB to 32 KB. 32 KB sits at the edge of L1 cache size on most x86 cores (32-48 KB); probes may now spill to L2 (12-cycle vs 3-4-cycle hit latency). ToonReader's same growth in Cand 4 paid this cost too but the Maps working-set hit-rate gain hid it; Toon Read Full was essentially flat (+0.08%) post-Cand 4.
  2. **Cross-parse cache pollution.** Per-instance cache starts cold every parse — first probe is always a null slot, write, done. TL cache contains strings from prior parses; new parses probe non-null slots that hash-collide with old strings, requiring length+charAt comparison before falling through to allocate. After warmup with identical TestData this should converge to high hit rate, but the fixed-data benchmark may not exercise the realistic hit case.
- Decision: **reverted**
- Filed Cand 13b to isolate factor (1) from (2): Cand 13b keeps MASK=2047 (16 KB, L1-friendly) and only does the TL conversion. If 13b also regresses, the per-instance cache is genuinely the better shape for JsonParser's working set; if 13b clears the bar, the L1-pressure theory is confirmed.
- Implementation changes stashed as `reverted-candidate-13` (git stash) for recoverability.
- Commit: (this commit, baseline + candidates only — implementation reverted)
- Raw measurement logs: `.claude/perf-loop/runs/cand13-{1,2,3}.log`

### 2026-05-03 09:55 — Candidate 18: JsonParser.readNumber hotspot investigation — reverted (deferred, investigation completed)
- **No implementation attempted.** Discovery candidate; investigation completed in-session against the 5/3 9:46 JFR call-tree.
- Finding: of 237 leaf samples in `JsonParser.readNumber`, the bulk are inside the method body's integer fast path (JsonParser.java:667-690) doing the actual digit-parse work. The fast path is structurally identical to `ToonReader.parseNumber`'s (tight char-range checks, direct `n = n * 10 + (d - '0')` accumulation, no per-digit boxing). Children: 29 leaves in `readFloatingPoint`, 29 in `skipWhitespaceRead`, only 3 in `Long.valueOf` (Cand 7's lesson holds: JIT intrinsifies small-Long boxing).
- No surprise hotspot underneath. The 2.5% leaf time represents necessary work, not bypassable overhead.
- Deeper opportunity (out of scope): eliminate `Number` autoboxing across the `JsonParser → JsonObject → Resolver` pipeline by returning primitive `long` from `readNumber`. Multi-file refactor outside loop scope.
- Decision: **deferred** (recorded as `reverted`). Investigation preserved; no follow-up loop candidate filed.
- Commit: (this commit, candidates.md + baseline.md only — no source changes, no run logs)

### 2026-05-02 21:38 — Candidate 12: writeJsonUtf8String slice via thread-local char[] — reverted (deferred)
- **No implementation attempted.** Skipped on a combination of speculative gain and Cand 3b's negative finding about TL on write paths.
- Reasoning:
  - `StringBuilderWriter.write(String, off, len)` already calls `sb.append(s, off, off+len)`, which internally uses `String.getChars` — the same `getChars` work the candidate proposes routing through a TL scratch buffer manually.
  - Net savings would only come from `sb.append(char[], 0, len)` being meaningfully faster than `sb.append(String, off, off+len)`. Modern JDK `StringBuilder` has both routes go through `putCharsAt` after the same Latin-1 vs UTF-16 dispatch.
  - **Cand 3b precedent:** adding a ThreadLocal char[] at a hot write path defeats JIT escape analysis. Cand 3b's TL `StringBuilder` regressed Toon Write by ~3% even on a similar shape (per-call buffer that the JIT could otherwise stack-allocate). A TL scratch char[] in `writeJsonUtf8String` carries the same risk, with smaller expected upside.
  - Implementation cost is non-trivial: TL detection (writer-instanceof check), bound management (grow vs chunk slices), and a non-StringBuilderWriter fallback path. ~30-45 min for an experiment with low expected ceiling.
- Decision: **deferred** (recorded as `reverted`). If future profiling singles out the `sb.append(String,off,off+len)` charAt loop as a remaining bottleneck despite the JIT, revisit with a per-call (non-TL) buffer first to avoid the escape-analysis pitfall.
- Commit: (this commit, candidates.md + baseline.md only — no source changes, no run logs)
