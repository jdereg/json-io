# JsonPerformanceTest baseline medians

Each row is the **median of 3 runs** of `JsonPerformanceTest`. The loop updates this file when a candidate is kept. When a candidate is reverted, leave the row unchanged. When a candidate is in_progress, the loop appends a "candidate-N: [results]" block beneath this section without touching the baseline row.

## Active baseline (median ms, lower is better)

| Metric | Full Java Resolution | Maps Only |
|---|---:|---:|
| JsonIo Write Time (cycleSupport=true) | 4613.892 | 5091.749 |
| JsonIo Write Time (cycleSupport=false) | 4223.209 | 4203.855 |
| Toon Write Time (cycleSupport=true) | 4694.445 | 4911.380 |
| Toon Write Time (cycleSupport=false) | 4429.957 | 4487.169 |
| Jackson Write Time | 2770.324 | 2781.221 |
| JsonIo Read Time | 9243.218 | 4757.052 |
| Toon Read Time | 10362.016 | 7056.516 |
| Jackson Read Time | 5173.636 | 4042.249 |

## Active Jackson ratios (lower is closer to / better than Jackson)

| Ratio | Full Java | Maps Only |
|---|---:|---:|
| Read Ratio (Toon/Jackson) | 2.01 | 1.75 |
| Read Ratio (JsonIo/Jackson) | 1.79 | 1.18 |
| Write Ratio (Toon cycleSupport=false / Jackson) | 1.60 | 1.61 |
| Write Ratio (JsonIo cycleSupport=false / Jackson) | 1.53 | 1.51 |

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
