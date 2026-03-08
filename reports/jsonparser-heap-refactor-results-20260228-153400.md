# JsonParser heap parser refactor results

## Artifacts

- Baseline benchmark/JFR:
  - `reports/jsonperf-baseline-20260228-151625.txt` (stdout-only capture)
  - `/Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_heaprefactor_baseline_20260228-151625.jfr`
- Hybrid benchmark/JFR:
  - `reports/jsonperf-hybrid-final-20260228-153227.log`
  - `/Users/jderegnaucourt/IdeaSnapshots/JsonPerformanceTest_hybrid_final_20260228-153227.jfr`
- Additional hybrid reruns:
  - `reports/jsonperf-after-hybrid-heap-20260228-152733.txt`
  - `reports/jsonperf-after-hybrid-heap-rerun-20260228-152844.txt`

## Full Java Resolution

| Metric | Baseline (ms) | Hybrid (ms) | Delta (ms) | Delta % |
|---|---:|---:|---:|---:|
| JsonIo Read | 5348.954 | 5287.494 | -61.460 | -1.15% |
| JsonIo Write (cycleSupport=true) | 3108.092 | 3048.751 | -59.341 | -1.91% |
| JsonIo Write (cycleSupport=false) | 3109.263 | 2325.119 | -784.144 | -25.22% |
| Jackson Read | 1944.273 | 1904.465 | -39.808 | -2.05% |
| Jackson Write | 908.590 | 916.888 | +8.298 | +0.91% |

## Maps Only

| Metric | Baseline (ms) | Hybrid (ms) | Delta (ms) | Delta % |
|---|---:|---:|---:|---:|
| JsonIo Read | 3395.796 | 3428.659 | +32.863 | +0.97% |
| JsonIo Write (cycleSupport=true) | 3040.796 | 3073.108 | +32.312 | +1.06% |
| JsonIo Write (cycleSupport=false) | 2305.839 | 2208.730 | -97.109 | -4.21% |
| Jackson Read | 2068.318 | 2079.914 | +11.596 | +0.56% |
| Jackson Write | 903.744 | 920.926 | +17.182 | +1.90% |

## Text graph (lower is better)

```
Full Read  baseline   5349.0 ms |████████████████████████████████████████
Full Read  hybrid     5287.5 ms |████████████████████████████████████████
Maps Read  baseline   3395.8 ms |█████████████████████████
Maps Read  hybrid     3428.7 ms |██████████████████████████
```

## Summary

- Full Java JsonIo read improved by 1.15%.
- Maps-only JsonIo read changed by +0.97% (within ~2%).
- Hybrid mode keeps shallow-path performance near baseline while enabling heap parser beyond depth threshold.
