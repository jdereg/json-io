# Ralph Loop — json-io / TOON Performance

You are running one iteration of a performance optimization loop on the json-io repo at `/Users/jderegnaucourt/workspace/json-io`. Each iteration picks the next pending candidate, implements it, validates with `JsonPerformanceTest`, and either keeps or reverts the change.

## Inputs (read these first)

1. `/Users/jderegnaucourt/workspace/json-io/.claude/perf-loop/candidates.md` — ordered list, status-tracked. Pick the **first candidate with status=pending**. If none, stop the loop and report "all candidates processed."
2. `/Users/jderegnaucourt/workspace/json-io/.claude/perf-loop/baseline.md` — current baseline medians and ratios.

## Decision metric (read this twice)

The accept/reject signal is the `JsonPerformanceTest` stdout — specifically the labelled timing lines (`Toon Read Time:`, `Toon Write Time (cycleSupport=false):`, etc.) and the Jackson ratio lines. JFR is **not** the signal. Use it only to understand *why* a candidate failed if you need to.

**Acceptance bar:** median of 3 new runs on the candidate's primary target metric must be **strictly better** than the prior recorded baseline median AND the relative improvement must be **≥0.5%**: `(prior - new) / prior ≥ 0.005`. Anything less → revert. No other metric may regress more than 1% (sanity check); if any does, revert the candidate.

If `baseline.md` shows `_TBD_` for the target metric, you are running the **first iteration**: produce the baseline by running JsonPerformanceTest 3× *before* implementing the candidate, fill in `baseline.md` from those run medians, then proceed with implementation. (Baseline establishment is a one-time step, not a separate iteration.)

## Step-by-step

1. **Pick.** Read `candidates.md`. Identify the first `status: pending` candidate. Note its primary target metric, watch metrics, and file/line refs.

2. **Pre-flight.** From the json-io workspace, run `git status -uno` and confirm a clean working tree (no uncommitted changes from a prior iteration). If dirty, stop and ask the user — do **not** stash or reset.

3. **Establish baseline if needed.** If the relevant baseline cells in `baseline.md` are `_TBD_`:
   - Run `cd /Users/jderegnaucourt/workspace/json-io/json-io && mvn -q -Dtest=JsonPerformanceTest test` 3 times (sequentially), capturing stdout each time.
   - Parse the labelled time lines (regex example: `^.*?(JsonIo|Toon|Jackson) (Read|Write) Time(?: \(cycleSupport=(true|false)\))?:\s+([0-9.]+) ms$`) and the ratio lines.
   - For each (metric, section) pair, compute the median across 3 runs and write it into `baseline.md`. Sections are "Full Java Resolution" and "Maps Only" — JsonPerformanceTest emits both unless invoked with `java`/`maps` arg.
   - Commit `baseline.md` with message `Perf: establish JsonPerformanceTest baseline medians`.

4. **Mark in progress.** Edit `candidates.md`: set the picked candidate's status from `pending` to `in_progress`. Don't commit this yet — the candidates file gets one commit at the end with status set to `kept` or `reverted`.

5. **Implement.** Make the changes described in the candidate's plan. Stick to the named files; do **not** sweep up unrelated cleanup. Match existing code style. If the plan was wrong (e.g., the file no longer has the line referenced), check with the user before improvising — don't silently expand scope.

6. **Test correctness.** Run `cd /Users/jderegnaucourt/workspace/json-io/json-io && mvn -q clean test`. If anything fails, fix it. If you can't fix it within ~10 minutes of focused work, revert the candidate (see step 9, revert path) and mark `status: reverted` with the reason "test failure: <summary>".

7. **Measure.** Run `JsonPerformanceTest` 3 times sequentially. Capture all three stdouts. For each labelled time line, record the three values. For the candidate's primary target metric, compute the median (sort, take middle).

8. **Decide.**
   - **Compare** the new median for the primary target against the prior baseline median.
   - Compute `delta = (prior - new) / prior`. If `new >= prior` (regression or flat), **revert**. If `delta < 0.005` (less than 0.5% gain), **revert**.
   - For each watch metric, compute its delta. If any watch metric regresses by more than 1% (`(new - prior) / prior > 0.01`), **revert**.
   - Otherwise, **keep**.

9. **Apply decision.**
   - **Keep path:** update `baseline.md`'s active rows for *every* metric (not just the target — the others moved too, and they're now the new baseline). Append a Run-log block with the deltas. Edit `candidates.md` to set `status: kept`. Stage `baseline.md`, `candidates.md`, and the implementation file(s). Commit with message: `Perf: <candidate-N short title> (<delta>% on <target metric>)`.
   - **Revert path:** revert the implementation changes only, **not** the candidates.md or baseline.md. Use `git checkout -- <impl-files>` after first running `git stash push -m "reverted-candidate-N" -- <impl-files>` per the user's git safety rule (the stash gives a recoverable copy if you reverted by mistake). Append a Run-log block with the deltas and "Decision: reverted because <reason>". Edit `candidates.md` to set `status: reverted`. Commit `baseline.md` (run-log only) and `candidates.md` with message: `Perf: <candidate-N short title> reverted (<reason>)`.

10. **Report.** Output a concise turn summary: which candidate, kept/reverted, primary delta, watch deltas, commit sha. End with a one-line offer: "ready for next iteration; <N> candidates remaining."

## Hard rules

- **One candidate per iteration.** If you finish early, stop — don't grab the next.
- **Do not edit the order of candidates.** Only flip statuses.
- **Do not invent new candidates** mid-loop. If you spot one, mention it in the turn summary; the user will decide whether to add it.
- **Do not skip the median-of-3 step**, even if the first run looks great or terrible.
- **Do not run with --no-verify**, do not amend prior commits, do not force-push.
- **Don't speculate about JFR-derived numbers in the keep/revert decision.** The JsonPerformanceTest median is the signal.
- If `JsonPerformanceTest` itself appears unstable (e.g., 3 runs span >5% of the median), report that and stop the loop — variance issues are a user-level decision, not a candidate-level one.

## Build/test commands (copy these — known good)

- Full test: `cd /Users/jderegnaucourt/workspace/json-io/json-io && mvn -q clean test`
- Single perf test: `cd /Users/jderegnaucourt/workspace/json-io/json-io && mvn -q -Dtest=JsonPerformanceTest test`

## Output parsing reference

JsonPerformanceTest emits two sections, each with these labelled lines that the loop must parse for medians:

```
JsonIo Write Time (cycleSupport=true):  <ms> ms
Toon Write Time (cycleSupport=true):    <ms> ms
JsonIo Write Time (cycleSupport=false): <ms> ms
Toon Write Time (cycleSupport=false):   <ms> ms
Jackson Write Time: <ms> ms
JsonIo Read Time: <ms> ms
Toon Read Time: <ms> ms
Jackson Read Time: <ms> ms
Read Ratio (Toon/Jackson): <x>x
Read Ratio (JsonIo/Jackson): <x>x
Write Ratio (Toon cycleSupport=false / Jackson): <x>x
Write Ratio (JsonIo cycleSupport=false / Jackson): <x>x
```

Sections are headed by `--- Full Java Resolution Results ---` and `--- Maps Only Results ---`. The labels repeat across sections, so qualify each by which section it came from when populating `baseline.md`.

## End-of-loop

When all candidates are `kept` or `reverted`, write a final summary commit message titled `Perf: optimization loop complete — <kept-count> kept, <reverted-count> reverted, <total>% cumulative gain on Toon Read Time` and stop.
