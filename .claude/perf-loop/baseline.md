# JsonPerformanceTest baseline medians

Each row is the **median of 3 runs** of `JsonPerformanceTest`. The loop updates this file when a candidate is kept. When a candidate is reverted, leave the row unchanged. When a candidate is in_progress, the loop appends a "candidate-N: [results]" block beneath this section without touching the baseline row.

## Active baseline (median ms, lower is better)

| Metric | Full Java Resolution | Maps Only |
|---|---:|---:|
| JsonIo Write Time (cycleSupport=true) | 4662.908 | 5192.084 |
| JsonIo Write Time (cycleSupport=false) | 4341.043 | 4275.896 |
| Toon Write Time (cycleSupport=true) | 4996.674 | 5231.749 |
| Toon Write Time (cycleSupport=false) | 4719.192 | 4798.139 |
| Jackson Write Time | 2764.765 | 2753.642 |
| JsonIo Read Time | 9185.253 | 4689.256 |
| Toon Read Time | 10336.108 | 6998.894 |
| Jackson Read Time | 5193.748 | 4078.200 |

## Active Jackson ratios (lower is closer to / better than Jackson)

| Ratio | Full Java | Maps Only |
|---|---:|---:|
| Read Ratio (Toon/Jackson) | 1.99 | 1.72 |
| Read Ratio (JsonIo/Jackson) | 1.77 | 1.15 |
| Write Ratio (Toon cycleSupport=false / Jackson) | 1.71 | 1.74 |
| Write Ratio (JsonIo cycleSupport=false / Jackson) | 1.57 | 1.54 |

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
