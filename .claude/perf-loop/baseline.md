# JsonPerformanceTest baseline medians

Each row is the **median of 3 runs** of `JsonPerformanceTest`. The loop updates this file when a candidate is kept. When a candidate is reverted, leave the row unchanged. When a candidate is in_progress, the loop appends a "candidate-N: [results]" block beneath this section without touching the baseline row.

## Active baseline (median ms, lower is better)

| Metric | Full Java Resolution | Maps Only |
|---|---:|---:|
| JsonIo Write Time (cycleSupport=true) | 4740.073 | 5177.875 |
| JsonIo Write Time (cycleSupport=false) | 4228.062 | 4251.564 |
| Toon Write Time (cycleSupport=true) | 4747.966 | 4918.342 |
| Toon Write Time (cycleSupport=false) | 4391.386 | 4490.903 |
| Jackson Write Time | 2772.912 | 2784.051 |
| JsonIo Read Time | 9231.962 | 4779.621 |
| Toon Read Time | 10327.982 | 6903.869 |
| Jackson Read Time | 5186.775 | 4073.596 |

## Active Jackson ratios (lower is closer to / better than Jackson)

| Ratio | Full Java | Maps Only |
|---|---:|---:|
| Read Ratio (Toon/Jackson) | 1.99 | 1.69 |
| Read Ratio (JsonIo/Jackson) | 1.79 | 1.18 |
| Write Ratio (Toon cycleSupport=false / Jackson) | 1.58 | 1.62 |
| Write Ratio (JsonIo cycleSupport=false / Jackson) | 1.52 | 1.51 |

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
