# Performance Optimization Candidates

Each candidate has: a status, a target metric (the JsonPerformanceTest line whose median must improve by ≥0.5% vs the prior committed median), file:line refs, and a one-paragraph plan. Status values: `pending`, `in_progress`, `kept`, `reverted`.

The loop processes the first `pending` candidate per iteration. After implementation it runs `mvn -pl json-io clean test` then `JsonPerformanceTest` 3×, takes the median of the target metric, and either keeps (commit + status=kept) or reverts (git stash drop + status=reverted). Other metrics must not regress more than 1%; if they do, treat the candidate as a regression and revert.

Source-of-truth ordering: don't reorder; just flip statuses.

---

## Candidate 1 — Eliminate `": "` and `NEW_LINE` String appends in TOON write

- **Status:** kept
- **Primary target:** `Toon Write Time (cycleSupport=false)` (Maps Only and Full Java)
- **Secondary watch:** `Toon Read Time` (no regression)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java` — every call site of `out.write(": ")` and `out.write(NEW_LINE)` in `writeFieldEntry` (line 1936+), `writeMapWithSimpleKeys` (1917), `writeFieldEntryInline`, `writeNestedObject`, `writeObjectFields`, `writeMap`. Use `Edit` with `replace_all` only after confirming `NEW_LINE` is exactly `"\n"`.
- **Plan:** replace `out.write(": ")` with two char writes: `out.write(':'); out.write(' ');`. Replace `out.write(NEW_LINE)` with `out.write('\n')`. Each `String` overload of `StringBuilder.append` triggers `String.getBytes` + `ensureCapacityInternal` (the #1 and #2 leaves overall in JFR — 354 + 276 samples). A single-char append skips the String allocation and the byte-copy entirely.
- **Risk:** verify `NEW_LINE` literal (`grep -n "NEW_LINE\s*=" ToonWriter.java`). If it's anything other than `"\n"`, expand to the matching char sequence. Don't touch any path that emits `\r\n`.

## Candidate 2 — Cache `getTypeNameAlias` on `JsonWriter`

- **Status:** reverted
- **Primary target:** `JsonIo Write Time (cycleSupport=false)` (Full Java Resolution)
- **Secondary watch:** `JsonIo Read Time` (should be neutral)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonWriter.java:1428` (`writeType`) and `:878` (the second `getTypeNameAlias` call site).
  - Reference pattern: `ToonWriter.java:221` declares `typeNameCache`; `ToonWriter.java:544–552` implements the cache.
- **Plan:** add a per-`JsonWriter`-instance `Map<String,String> typeNameAliasCache` (HashMap, not Identity, since the key is a String name not a Class). In `writeType`, look up the cache first; on miss, call `writeOptions.getTypeNameAlias(name)` and `put`. JFR shows 65 samples in `JsonWriter.writeType → getTypeNameAlias → HashMap.getNode` (plus the unknown-type fallthrough into `ClassUtilities.forName` + `AnnotationResolver.getMetadata` is much more expensive on miss).
- **Risk:** none structural — this mirrors ToonWriter exactly. Cache is per-write-call, lives only as long as the `JsonWriter` instance.

## Candidate 3 — Pre-size + thread-local the write StringBuilder

- **Status:** kept (sub-experiment a only — capacity raise from 8192 to 32768; sub-experiment b filed as Candidate 3b below)
- **Primary target:** `Toon Write Time (cycleSupport=false)`
- **Secondary watch:** `JsonIo Write Time (cycleSupport=false)` (should also gain), `Toon Read Time` (no regression)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonIo.java:375` (`toJson`) and `:513` (`toToon`).
- **Plan:** two sub-experiments, **try (a) first**:
  - **(a)** Raise the initial capacity from `DEFAULT_CHAR_BUFFER_SIZE` (8192) to 32768 in both call sites. JFR shows 241 leaf samples in `ensureCapacityInternal` and 90 in `inflate`, virtually all from `StringBuilderWriter.write`. A larger initial buffer skips several doublings on typical payloads.
  - **(b)** If (a) clears the bar, also try a `ThreadLocal<StringBuilder>` pattern (mirror `TL_LINE_BUF` on the read side). After `sb.toString()`, call `sb.setLength(0)` to retain the capacity. Cap retained capacity at 1 MB by checking `sb.capacity()` and re-allocating if exceeded. Test (b) as a *separate* candidate with its own median run — file it as Candidate 3b in this list and commit if it clears the bar **on top of** (a)'s baseline.
- **Risk:** capacity is only initial; `StringBuilder` still grows naturally. Thread-local retention can leak in long-lived threads — the 1 MB cap addresses that. (b) only after (a) is locked in.

## Candidate 3b — Thread-local `StringBuilder` for write paths

- **Status:** reverted
- **Primary target:** `Toon Write Time (cycleSupport=false)` (must clear 0.5% on top of Cand-3a's 32K-pre-size baseline)
- **Secondary watch:** `JsonIo Write Time (cycleSupport=false)` (should also gain), `Toon Read Time` (no regression)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonIo.java:375` (`toJson`) and `:513` (`toToon`).
- **Plan:** introduce a `ThreadLocal<StringBuilder>` shared by `toJson` and `toToon` (mirrors `TL_LINE_BUF` on the read side). On entry, retrieve the TL builder, call `setLength(0)` to retain the previously grown capacity. After `sb.toString()`, before exiting, cap retained capacity at 1 MB by checking `sb.capacity()` — if it exceeds the cap, replace the TL value with a fresh `new StringBuilder(32768)`. This avoids the 32 KB per-call allocation and keeps the buffer warm in JIT-compiled write loops, while bounding memory in long-lived threads.
- **Risk:** TL retention can pin memory in pooled threads — the 1 MB cap addresses that. If 3-run median doesn't clear 0.5% on top of Cand-3a's baseline, revert. Don't ship the TL without the cap.

## Candidate 4 — Grow `STRING_CACHE_MASK` on ToonReader from 2047 to 4095

- **Status:** kept
- **Primary target:** `Toon Read Time` (Maps Only and Full Java)
- **Secondary watch:** `JsonIo Read Time` (no regression — different cache)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:66` (`STRING_CACHE_MASK = 2047`)
  - The TL allocation at line 73 will resize automatically (`STRING_CACHE_MASK + 1` slots).
- **Plan:** change to `4095`. This grows the per-thread `String[]` from 2048 refs to 4096 (≈16 KB extra per thread). JFR shows `cacheSubstringFromBuf` is the #1 cedar leaf at 187 samples; the cache uses a direct-mapped `(first,mid,last,len)` hash. With high-cardinality field-value workloads, eviction collisions force `new String(...)` allocations.
- **Risk:** if the workload's working-set fits in 2K slots already, no gain. **Before committing, add a temporary instrumentation counter** (probe count, hit count) — log them at the end of one benchmark run, then remove. The candidate is only worth keeping if hit rate measurably improves AND the median clears 0.5%.

## Candidate 5 — Skip `cacheSubstringFromBuf` for high-cardinality tabular cells

- **Status:** reverted (deferred — no implementation tried; workload mismatch with JsonPerformanceTest)
- **Primary target:** `Toon Read Time` (Full Java Resolution)
- **Secondary watch:** small-table workloads must not regress
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:925, 936` (`parseRowIntoObject` calls `readScalar(buf,...)`).
  - `:1396` (`readScalar(char[]...)`).
- **Plan:** add a `readScalarUncached(buf, start, end)` variant that goes straight to `new String(buf,start,len)` for the string fallthrough (no cache probe, no cache write). Per-column-index, track a small running hit-count vs probe-count; once probe count exceeds 32 and hit rate is below ~10%, switch that column to `readScalarUncached`. Reset on each new tabular array.
- **Risk:** heuristic complexity. If it doesn't measure positive within 0.5%, revert. Don't add the dynamic switch as a global flag — keep it strictly within the tabular-row path.

## Candidate 6 — Bypass duplicate cache write in `formatDecimalNumber` integer fast path

- **Status:** reverted
- **Primary target:** `Toon Write Time (cycleSupport=false)` (Full Java Resolution — the data set has doubles)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java:889–905` (and the `float` mirror at `:924–953`).
- **Plan:** in the integer-valued double fast path (line 897–905), `toCachedLongString(l)` already short-circuits via the small-long string cache. Drop the `SHARED_DOUBLE_FORMAT_CACHE.size() < ... && putIfAbsent` block in that branch — it adds a CHM size-check and put per integer-valued double for no real cache value (the hot data is already cached upstream). Keep the cache write on the truly-decimal path (line 915–917).
- **Risk:** trivial. Verify there's no test asserting CHM contents.

## Candidate 7 — Cache parsed Long in `parseNumber(char[])` fast path

- **Status:** reverted
- **Primary target:** `Toon Read Time` (Full Java Resolution — int arrays + Long fields)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:1649–1673` (the unsigned-fast-path that returns `Long.valueOf(result)` without writing to the number cache).
- **Plan:** when the fast path succeeds, before returning, also write to the number cache (`numKeys[slot] = key; numVals[slot] = boxed;` — but allocating `key` re-pays the `cacheSubstringFromBuf` cost we were trying to skip). Better: special-case 0–1023 to a static `Long[]` table populated once; for those values, return the table entry without any allocation. This is targeted at the common-zero / small-int patterns in the test data (the JFR shows `parseNumber` 72 leaf samples).
- **Risk:** measure carefully — the original commenter explicitly skipped the cache here because the cache write cost more than the boxing. Only the static `Long[]` variant is viable; if it doesn't clear 0.5%, revert.

## Candidate 8 — Skip `hasComplexKeys` for declared `Map<String,…>` in TOON write

- **Status:** reverted (deferred — no implementation tried; workload mismatch with JsonPerformanceTest)
- **Primary target:** `Toon Write Time (cycleSupport=false)`
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java:1858, 1891` (`hasComplexKeys`), and the `currentMapHasSimpleKeyTypeHint` plumbing around line 213.
- **Plan:** when the enclosing field plan says the declared map key type is `String` (or any concrete `isSimpleKeyType`-passing class), the hint is already set. Extend the hint propagation to maps reached via `writeArrayElements` / `writeCollection` element iteration where the *element* type is a known `Map<String,?>`. JFR shows 50 samples in `ToonWriter.writeMap → HashMap.getNode/Iterator` (the entrySet iteration triggered by `hasComplexKeys`).
- **Risk:** declared-type vs runtime-type divergence; raw `Map` types must keep the scan. Stay inside the existing hint-propagation pattern.

## Candidate 9 — Avoid `entry.getKey().toString()` for Number keys

- **Status:** pending
- **Primary target:** `Toon Write Time (cycleSupport=false)` (Maps Only — has the `counterMap` field)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java:1927`
- **Plan:** replace `key.toString()` with `toCachedLongString(((Number)key).longValue())` when `key instanceof Long || key instanceof Integer || key instanceof Short || key instanceof Byte`. Keeps the `(key == null) ? "null" : ...` guard. Verify `needsQuotingForMapKey` still works on the resulting digit-string (it should).
- **Risk:** trivial; mirrors what `writeFieldEntry` already does for value-side numbers.

## Candidate 10 — Inline `Long.valueOf` boxing avoidance in `Resolver.fastScalarCoercion`

- **Status:** pending
- **Primary target:** `Toon Read Time` and `JsonIo Read Time` (both routes)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/Resolver.java:1789–1832`
- **Plan:** for the `valueClass == Long.class` branch with `TARGET_INT`/`TARGET_SHORT`/`TARGET_BYTE`, the cast-and-rebox path goes through autoboxing (`Integer.valueOf`, etc.), which only caches -128…127. For typical small-int field values, route through the JDK's small-Integer cache explicitly with a guarded `Integer.valueOf((int) v)` (already cached) but also pre-extract the long once outside the switch. Look for any savings from removing the redundant `(Long) value` re-cast.
- **Risk:** very small expected gain (136 leaf samples but most are inside the unboxing). If the median doesn't clear 0.5%, revert. Don't touch behaviour.

## Candidate 11 — Port number-parse fast path to `JsonParser.readNumber`

- **Status:** pending
- **Primary target:** `JsonIo Read Time` (Full Java)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java` — locate `readNumber` (it appears at high JFR sample count: 265 leaf samples).
- **Plan:** mirror whatever specific change locked in for ToonReader (e.g., the 0–1023 `Long[]` table from Candidate 7, if kept).
- **Risk:** only worth running if Candidate 7 was kept.

## Candidate 12 — `writeJsonUtf8String` slice via thread-local char[]

- **Status:** pending
- **Primary target:** `JsonIo Write Time (cycleSupport=false)`
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonWriter.java:3105–3156` (`writeJsonUtf8String`)
  - `json-io/src/main/java/com/cedarsoftware/io/StringBuilderWriter.java:50–53` (the `String, off, len` overload)
- **Plan:** when the underlying writer is `StringBuilderWriter`, the `output.write(s, last, i-last)` slice goes through `sb.append(String, off, off+len)` which is essentially a `String.getBytes`-like copy. Add a thread-local scratch `char[]` and replace the slice with `s.getChars(last, i, scratch, 0); sb.append(scratch, 0, i-last)`. The `sb.append(char[], 0, len)` variant skips the String coder check and goes straight to `putCharsAt`, saving the `String.getBytes` leaf hits inside the write loop.
- **Risk:** allocation on first use of the scratch; manage via TL similar to `TL_LINE_BUF`. Watch for non-StringBuilderWriter sinks (FastWriter / OutputStreamWriter) — keep the original path unless the writer is detected as StringBuilder-backed.
