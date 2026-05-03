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

- **Status:** reverted
- **Primary target:** `Toon Write Time (cycleSupport=false)` (Maps Only — has the `counterMap` field)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonWriter.java:1927`
- **Plan:** replace `key.toString()` with `toCachedLongString(((Number)key).longValue())` when `key instanceof Long || key instanceof Integer || key instanceof Short || key instanceof Byte`. Keeps the `(key == null) ? "null" : ...` guard. Verify `needsQuotingForMapKey` still works on the resulting digit-string (it should).
- **Risk:** trivial; mirrors what `writeFieldEntry` already does for value-side numbers.

## Candidate 10 — Inline `Long.valueOf` boxing avoidance in `Resolver.fastScalarCoercion`

- **Status:** reverted (deferred — plan stale; optimization already applied; remaining changes would be no-ops)
- **Primary target:** `Toon Read Time` and `JsonIo Read Time` (both routes)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/Resolver.java:1789–1832`
- **Plan:** for the `valueClass == Long.class` branch with `TARGET_INT`/`TARGET_SHORT`/`TARGET_BYTE`, the cast-and-rebox path goes through autoboxing (`Integer.valueOf`, etc.), which only caches -128…127. For typical small-int field values, route through the JDK's small-Integer cache explicitly with a guarded `Integer.valueOf((int) v)` (already cached) but also pre-extract the long once outside the switch. Look for any savings from removing the redundant `(Long) value` re-cast.
- **Risk:** very small expected gain (136 leaf samples but most are inside the unboxing). If the median doesn't clear 0.5%, revert. Don't touch behaviour.

## Candidate 11 — Port number-parse fast path to `JsonParser.readNumber`

- **Status:** reverted (deferred — gated on Cand 7 being kept; Cand 7 reverted, so nothing to port)
- **Primary target:** `JsonIo Read Time` (Full Java)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java` — locate `readNumber` (it appears at high JFR sample count: 265 leaf samples).
- **Plan:** mirror whatever specific change locked in for ToonReader (e.g., the 0–1023 `Long[]` table from Candidate 7, if kept).
- **Risk:** only worth running if Candidate 7 was kept.

## Candidate 12 — `writeJsonUtf8String` slice via thread-local char[]

- **Status:** reverted (deferred — speculative gain + TL pattern conflicts with Cand 3b's findings)
- **Primary target:** `JsonIo Write Time (cycleSupport=false)`
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonWriter.java:3105–3156` (`writeJsonUtf8String`)
  - `json-io/src/main/java/com/cedarsoftware/io/StringBuilderWriter.java:50–53` (the `String, off, len` overload)
- **Plan:** when the underlying writer is `StringBuilderWriter`, the `output.write(s, last, i-last)` slice goes through `sb.append(String, off, off+len)` which is essentially a `String.getBytes`-like copy. Add a thread-local scratch `char[]` and replace the slice with `s.getChars(last, i, scratch, 0); sb.append(scratch, 0, i-last)`. The `sb.append(char[], 0, len)` variant skips the String coder check and goes straight to `putCharsAt`, saving the `String.getBytes` leaf hits inside the write loop.
- **Risk:** allocation on first use of the scratch; manage via TL similar to `TL_LINE_BUF`. Watch for non-StringBuilderWriter sinks (FastWriter / OutputStreamWriter) — keep the original path unless the writer is detected as StringBuilder-backed.

---

# Round 2 candidates — derived from 5/3 JFR (post-Cand 4 codebase, commit dc3d92a8)

The 5/3 morning JFR (`~/IdeaSnapshots/JsonPerformanceTest_2026_05_03_084235.jfr`, 9841 ExecutionSample events) shows the round-1 wins held: `String.getBytes` leaf samples dropped 30% (354→247, Cand 1), `ensureCapacityInternal` dropped 16% (276→232, Cand 3a), `cacheSubstringFromBuf` unchanged at 186 (Cand 4 raised hit *rate*, not per-call cost). The new top hotspots are split between JSON read (`JsonParser.readNumber` 246 leaves, `JsonWriter.writeJsonUtf8String` 241 leaves), `Resolver`/`ClassValue` (151+113 leaves combined), and small per-call allocations (`PendingMeta`, `JsonParser.stringCacheArray`). Round-2 candidates target these directly. Cross-referenced against an independent GPT-5.5 review of the same JFR which corroborated 3 of the specific code references.

## Candidate 13 — Port `JsonParser.stringCacheArray` to ThreadLocal (mirror Cand 4)

- **Status:** reverted
- **Primary target:** `JsonIo Read Time` (Maps Only and Full Java)
- **Secondary watch:** `Toon Read Time` (no regression — different cache, but shared TL infrastructure should have no contention)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:93` (`STRING_CACHE_MASK = 2047`)
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:96` (`private final String[] stringCacheArray = new String[STRING_CACHE_MASK + 1]` — instance field, allocated per parse)
  - Reference pattern: `ToonReader.java:72-73` (`TL_STRING_CACHE` ThreadLocal) and Cand 4's mask raise to 4095
- **Plan:** convert `stringCacheArray` from a per-instance `String[]` to a static `ThreadLocal<String[]>` mirroring `ToonReader.TL_STRING_CACHE`. JsonParser currently allocates a fresh 2048-slot `String[]` (~16 KB) on every `JsonIo.toJava()` / `JsonIo.toMaps()` call — 100k iterations × 16 KB = ~1.6 GB of cache-array allocation thrown away each run. The TL pattern (a) eliminates that allocation entirely and (b) preserves cache hits across parses on the same thread, so after warmup virtually every distinct string is cached. While we're at it, raise the mask to 4095 (4096 slots) on the same logic that won Cand 4: more headroom for high-cardinality string sets in the read working set, costs ~16 KB per thread (one-time).
- **Risk:** ToonReader's TL pattern has been in production since before this loop and is proven safe (Cand 4 confirmed). Re-entrancy: a custom JsonClassReader that recursively calls `JsonIo.toJava` would share the TL cache; in the worst case this adds a few cache misses (the inner parse overwrites slots the outer was using), never causes corruption — the cache is read-on-hit, write-on-miss with consistent same-thread serial access. No structural risk.

## Candidate 13b — Port `JsonParser.stringCacheArray` to ThreadLocal (TL only, MASK stays 2047)

- **Status:** reverted
- **Primary target:** `JsonIo Read Time` (Maps Only and Full Java)
- **Secondary watch:** `Toon Read Time` (no regression)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:93` (`STRING_CACHE_MASK` — leave at 2047)
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:96` (`stringCacheArray` field — convert init to TL)
- **Plan:** isolate the TL-conversion factor from Cand 13's coupled change. Cand 13 simultaneously (a) ported the per-instance cache to a `ThreadLocal<String[]>` AND (b) bumped `STRING_CACHE_MASK` from 2047 to 4095. The combined change regressed `JsonIo Read Time` by 1.4% Full / 1.0% Maps. Cand 13b keeps the array size at 2048 slots (16 KB — fits comfortably in L1 cache on x86) and only converts the initializer to `TL_STRING_CACHE.get()` so the per-thread array is reused across parses. This eliminates the ~16 KB allocation per `JsonIo.toJava()` call without changing memory pressure on L1.
- **Risk:** if the regression was driven by cross-parse cache pollution rather than L1 spill, Cand 13b will also lose. In that case, the per-instance cache is genuinely the better shape for JsonParser's working set and we walk away. If 13b clears the bar, the L1-pressure theory is confirmed and we have an isolated TL win we can keep.

## Candidate 14 — `writeJsonUtf8String` no-escape fast path via `String.indexOf`

- **Status:** pending
- **Primary target:** `JsonIo Write Time (cycleSupport=false)` (Full Java Resolution and Maps Only)
- **Secondary watch:** `JsonIo Write Time (cycleSupport=true)` (should also gain — same path), `Toon Write Time` (no regression — ToonWriter uses its own escape path)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonWriter.java:3105–3156` (`writeJsonUtf8String`)
  - Optional helper: `java-util/src/main/java/com/cedarsoftware/util/StringUtilities.java:1070` (`getChars(String, int)` — TL-backed bulk copy via SIMD intrinsic)
- **Plan:** two-tier optimization. **Tier 1 (no-escape fast path, common case):** before entering the per-character loop, do a single `String.indexOf` (intrinsified) for the lowest escape boundary character — practically `'"'` or `'\\'` — and a quick scan for any `< 0x20` / ` ` / ` `. If none found, write the entire string in one `output.write(s)` call between the surrounding quotes; return. JFR shows 241 leaf samples in `writeJsonUtf8String` and 162+183 = 345 leaves combined in `String.charAt`/`StringLatin1.charAt` — those are this loop. Most JSON field values (UUIDs, ISO dates, plain ASCII names) need no escapes, so the fast path captures the bulk of the cost. **Tier 2 (escape path):** when the pre-scan finds an escape, use `StringUtilities.getChars(s, len)` to bulk-copy the string into a TL char[] via the SIMD-intrinsic `String.getChars` (the helper's documented win is 20-50% on strings of 7+ chars), then walk `buf[i]` with the existing escape detection and emit safe slices via `output.write(buf, off, len)`. The TL handoff is paid once per string instead of N times per character.
- **Risk:** **Re-entrancy on Tier 2.** `StringUtilities.getChars` returns a TL-shared scratch buffer. Within `writeJsonUtf8String` the buf is consumed synchronously by `output.write(buf, off, len)` calls (which copy into the SB before returning). Standard writers (`StringBuilderWriter`, `FastWriter`, `OutputStreamWriter`) don't transitively call `getChars`. **The corner case:** a custom `JsonClassWriter` registered by user code that recursively calls `JsonIo.toJson` from within its `write` callback would re-enter `writeJsonUtf8String` and trample the outer call's buf mid-loop. For the JsonPerformanceTest workload no custom writers are in play; for general use, document the contract at the top of the method and consider an `assert StringUtilities.getCharsToken() == startToken + 1` paranoia check (zero cost in production with `-da`). **Note:** `StringUtilities.getChars` is moving to a restricted access pattern post-loop (qualified-export to `com.cedarsoftware.io` only). The candidate uses today's public form; the API change after the loop won't affect the call site (only the import).

## Candidate 15 — Shrink `JsonParser.readArray` initial ArrayList capacity (64 → adaptive)

- **Status:** pending
- **Primary target:** `JsonIo Read Time` (Maps Only and Full Java)
- **Secondary watch:** none (no read time metric is uniquely sensitive to ArrayList grow patterns)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:489-492` (`readArray` method, line 492: `final List<Object> list = new ArrayList<>(64)`)
- **Plan:** the current `new ArrayList<>(64)` allocates an `Object[64]` up front for every JSON array parsed. TestData arrays are mostly 10–50 elements (`values`=10, `integerList`=100, `largeIntArray`=50, `list`=50, `nestedList`=10, `concreteHashMap` entries=20, etc.) — 64 is over-allocated for many. Test three variants in order: **(a)** `new ArrayList<>()` — defers Object[] alloc to first add, then starts at 10 and doubles (best for arrays ≤10 or ≥80; pays a copy on each grow). **(b)** `new ArrayList<>(8)` — small fixed pre-size, two-three doublings reach 32-64 for typical sizes. **(c)** Keep 64 but only allocate lazily — i.e., don't call the capacity constructor unless the parser already has a hint about size. Start with (a). The benchmark's array-size distribution is well-suited because most arrays are smaller than 64; the doubling cost on grows is amortized over fewer over-allocations. Allocation samples in the JFR (28490 ObjectAllocationSample events, biased toward small array allocs in `JsonParser`) suggest non-trivial pressure here.
- **Risk:** for large JSON arrays (>>64 elements) outside the benchmark, default-then-grow incurs more copy overhead than the static pre-size. TestData has no array > 100 elements; a real-world workload with 1000-element arrays would prefer the larger pre-size. Acceptance is benchmark-bound, but document the shape sensitivity in the keep-path log. If (a) doesn't clear 0.5%, try (b).

## Candidate 16 — Pre-cache scalar target kind on read-side field/element plans

- **Status:** pending
- **Primary target:** `JsonIo Read Time` and `Toon Read Time`, **Full Java Resolution** (Maps mode skips the Resolver scalar coercion path)
- **Secondary watch:** `JsonIo Read Time` (Maps), `Toon Read Time` (Maps) — should be unaffected (Maps mode doesn't traverse field plans for primitive coercion)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/Resolver.java:1789-1832` (`fastScalarCoercion`)
  - `json-io/src/main/java/com/cedarsoftware/io/Resolver.java:91-111` (`SCALAR_KINDS` map and `TARGET_*` constants)
  - `json-io/src/main/java/com/cedarsoftware/io/ObjectResolver.java:686` (representative call site: `fastScalarCoercion(element, elementClass, targetElementKind)`)
  - Look for read-side field plan / accessor structure in `com.cedarsoftware.io.reflect.Accessor` for the cache-attachment point.
- **Plan:** JFR shows `ClassValue.get` at 151 leaves and `ClassValueMap.getByClass` at 241 any-frame samples — these are read-side type-kind lookups happening per-field-assignment to determine TARGET_INT/TARGET_DOUBLE/etc. Pre-compute the `targetKind` int once when the field accessor / element-type plan is built, store it as a primitive `int` field on the accessor (or its read-side equivalent), then route all `fastScalarCoercion` call sites through the cached value instead of re-resolving via `ClassValueMap.getByClass(targetClass)` each call. This is mechanical: one-time compute, many-time use. The 241 any-frame samples represent ~2.4% of total CPU time in this subsystem; eliminating most of the per-call cost should clear the 0.5% bar on Full Java reads.
- **Risk:** invasive plumbing through the read-side field-plan / accessor abstractions — needs careful audit of every `fastScalarCoercion` call site to ensure the pre-cached kind is plumbed through rather than re-derived. Higher implementation effort (~30-60 min) than typical loop candidates. If the read-side accessor doesn't have a clean attachment point, this might require introducing one — pause and check with the user before expanding scope.

## Candidate 17 — Eliminate `ToonReader.PendingMeta` per-object allocation

- **Status:** pending
- **Primary target:** `Toon Read Time` (Full Java Resolution — `PendingMeta` is for object metadata, exercised more in Java mode where every parsed object goes through this path)
- **Secondary watch:** `Toon Read Time` (Maps) — should also gain (Maps mode also constructs PendingMeta per object), `JsonIo Read Time` (no regression — different parser)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:322` (`PendingMeta pending = new PendingMeta()` — the hot allocation site)
  - `json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:1263` (second hot allocation site)
  - `json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:481+` (`PendingMeta` class definition)
  - `json-io/src/main/java/com/cedarsoftware/io/ToonReader.java:380, 432, 506-509` (call sites that take `PendingMeta` as a parameter)
- **Plan:** replace the `PendingMeta` heap object with primitive sentinel fields directly on the `ToonReader` instance (e.g., `private long pendingId = -1; private String pendingType; private long pendingRef = -1;`), plus a `clearPendingMeta()` helper called at object-parse boundaries. Update `readAndDispatchField` / `dispatchField` / `applyPendingMetadata` signatures to drop the `PendingMeta pending` parameter and read instance state instead. With 100k iterations × ~30 objects per parse (TestData has ~30 nested objects/maps) = ~3M PendingMeta allocations per benchmark. Eliminating those reduces GC pressure and removes the indirection through a holder object. Verified at hot path: GPT-5.5's independent review surfaced the same allocation source.
- **Risk:** state leakage between objects if `clearPendingMeta()` is missed at any boundary — would manifest as cross-object metadata bleeding. Audit every `applyPendingMetadata` call site to confirm the clear happens. For safety, the clear can be inlined at the start of `readAndDispatchField` so it's automatic.

## Candidate 18 — `JsonParser.readNumber` hotspot investigation (discovery candidate)

- **Status:** reverted (deferred — investigation completed pre-loop; no actionable optimization within the 0.5% bar)
- **Investigation finding (5/3 9:46 JFR call-tree analysis):** of 237 leaf samples in `JsonParser.readNumber`, the bulk are inside the method body's integer fast-path (lines 667–690) doing the actual digit-parse work. The fast path is structurally identical to `ToonReader.parseNumber`'s (tight char-range checks, direct `n = n * 10 + (d - '0')` accumulation, no per-digit boxing). Children: 29 leaves in `readFloatingPoint` (delegated for floats), 29 in `skipWhitespaceRead` (preceding whitespace), only 3 in `Long.valueOf` (confirming Cand 7's lesson — JIT intrinsifies small-Long boxing). No surprise hotspot underneath. The 2.5% leaf time represents necessary work, not bypassable overhead.
- **The deeper opportunity** (out of scope for this loop): eliminate `Number` autoboxing across the `JsonParser → JsonObject → Resolver` pipeline by returning primitive `long` from `readNumber` and threading the primitive through the value path. Multi-file refactor with broad blast radius (touches `JsonObject`, the parser/resolver contract, every call site of `readValue`). File as a separate architectural initiative if pursued, not as a loop candidate.
- **Primary target:** `JsonIo Read Time` (Maps Only and Full Java) — investigation result determines exact target
- **Secondary watch:** `Toon Read Time` (no regression — `ToonReader.parseNumber` is the analogous TOON path, separate)
- **Files:**
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:663` (`private Number readNumber(int c)`)
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:721` (`readNumberContinuation`)
  - `json-io/src/main/java/com/cedarsoftware/io/JsonParser.java:772-776` (`readNumberGeneral` overloads — fallback to BigDecimal/BigInteger)
- **Plan:** **discovery-style** candidate — the investigation is itself the first iteration's deliverable. JFR shows 246 leaf samples in `JsonParser.readNumber` (#2 leaf overall, ~2.5% of total CPU). Need to identify which of the four internal paths is hot: (a) the unsigned-integer digit-parse fast path, (b) the cache probe, (c) the `Long.valueOf` / `Number` boxing, (d) the BigInteger/BigDecimal fallback in `readNumberGeneral`. Use a fresh JFR's call-tree view (or `jfr print --events jdk.ExecutionSample --stack-depth 5`) to identify the dominant child. Once the root cause is known, propose a specific optimization in a follow-up candidate (Cand 18b) with file:line refs and a defined target. **Cand 7's lesson is binding here** — manual extension of JDK's intrinsified small-int caches loses to the JIT; if the boxing path turns out to be hot, the fix is *not* another `Long[]` table.
- **Risk:** investigation candidates can't be measured against the 0.5% bar on iteration 1 — there's nothing to compare. The first iteration's "kept" outcome is "produced a concrete follow-up candidate plan with file:line refs." If iteration 1 yields no actionable follow-up, mark this `reverted` with the analysis preserved. The follow-up Cand 18b (if filed) goes through the normal 0.5%-bar process.
