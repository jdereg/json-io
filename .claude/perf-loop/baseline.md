# JsonPerformanceTest baseline medians

Each row is the **median of 3 runs** of `JsonPerformanceTest`. The loop updates this file when a candidate is kept. When a candidate is reverted, leave the row unchanged. When a candidate is in_progress, the loop appends a "candidate-N: [results]" block beneath this section without touching the baseline row.

## Active baseline (median ms, lower is better)

| Metric | Full Java Resolution | Maps Only |
|---|---:|---:|
| JsonIo Write Time (cycleSupport=true) | 4576.364 | 5000.579 |
| JsonIo Write Time (cycleSupport=false) | 4249.810 | 4183.566 |
| Toon Write Time (cycleSupport=true) | 4858.595 | 5069.451 |
| Toon Write Time (cycleSupport=false) | 4524.498 | 4614.733 |
| Jackson Write Time | 2757.456 | 2772.330 |
| JsonIo Read Time | 9231.364 | 4753.505 |
| Toon Read Time | 10360.692 | 7053.499 |
| Jackson Read Time | 5164.828 | 4065.556 |

## Active Jackson ratios (lower is closer to / better than Jackson)

| Ratio | Full Java | Maps Only |
|---|---:|---:|
| Read Ratio (Toon/Jackson) | 2.00 | 1.74 |
| Read Ratio (JsonIo/Jackson) | 1.78 | 1.17 |
| Write Ratio (Toon cycleSupport=false / Jackson) | 1.63 | 1.67 |
| Write Ratio (JsonIo cycleSupport=false / Jackson) | 1.52 | 1.52 |

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
