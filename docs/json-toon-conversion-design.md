# JSON ↔ TOON Conversion — Design Note

**Status:** design locked, implementation pending.
**Targets:** 4.104.0 (String API on the existing tree path) → 4.105.0 (streaming engine + `ToonGenerator` / `ToonTokenizer`).
**Spec baseline:** TOON **v3.3** (conformance fixtures pinned at `toon-format/spec@07161ccc`, `2026-05-21`).

This note consolidates the design decisions made across the 4.104.0/4.105.0 planning
discussions: the public API surface, the memory contract (and the spec constraint that
forces it), the two-engine implementation strategy, and the `ToonGenerator` /
`ToonTokenizer` building blocks the streaming forms depend on.

---

## 1. Motivation

json-io already converts JSON↔TOON *through the object tree*: `toToon(toMaps(json))`
and the reverse work today. What's missing is a **first-class, named conversion API**
that (a) reads cleanly for the common small-payload case and (b) can stream large
documents without materializing the whole thing in memory.

Primary use cases:
- **LLM payload conversion** — convert a JSON request/response to TOON (~40–50% fewer
  tokens) before sending onward. Usually small, already a `String` in memory.
- **Database export / proxy streaming** — stream a large result set out as TOON, or
  forward a converted body through a gateway, without buffering the whole document.

---

## 2. Public API surface (12 methods)

```java
// ── String form ───────────────────────────────────────────  ships 4.104.0
String jsonToToon(String json)
String jsonToToon(String json, ReadOptions readOptions, WriteOptions writeOptions)
String toonToJson(String toon)
String toonToJson(String toon, ReadOptions readOptions, WriteOptions writeOptions)

// ── Push stream form (caller owns streams; never closed) ──   ships 4.105.0
void jsonToToon(InputStream in, OutputStream out)
void jsonToToon(InputStream in, OutputStream out, ReadOptions readOptions, WriteOptions writeOptions)
void toonToJson(InputStream in, OutputStream out)
void toonToJson(InputStream in, OutputStream out, ReadOptions readOptions, WriteOptions writeOptions)

// ── Decorator / pull form (composes into pull-based sinks) ─   ships 4.105.0 (see §7, OPEN)
InputStream jsonToToon(InputStream in)
InputStream jsonToToon(InputStream in, ReadOptions readOptions, WriteOptions writeOptions)
InputStream toonToJson(InputStream in)
InputStream toonToJson(InputStream in, ReadOptions readOptions, WriteOptions writeOptions)
```

These are **text/stream transforms** (JSON-text ↔ TOON-text), siblings of `formatJson` —
*not* databind. No fluent builder (the output format is fixed; there is no target-type
choice), and the return is the converted text directly.

### Phasing rationale
4.104.0 ships only the **String** forms, implemented on the existing tree path
(`toToon`/`fromToon`). The signatures are locked now so the 4.105.0 streaming swap is a
transparent internal change — same API, better memory profile. The stream + decorator
forms wait for 4.105.0 because an `InputStream`-shaped signature that secretly buffers
the whole document would over-promise; we ship those only once the engine is genuinely
streaming.

---

## 3. Locked contracts

| # | Contract | Status | Rationale |
|---|----------|--------|-----------|
| 1 | **Stream arg order `(in, out)`** | proposed | Reads left-to-right with the method-name direction; matches `InputStream.transferTo(out)`. Deliberately differs from the existing `toToon(out, source)` *serializer* — different mental model (transform vs. write-to-sink). |
| 2 | **Options order `(ReadOptions, WriteOptions)`** | proposed | Read-then-write, matching `deepCopy(src, ReadOptions, WriteOptions)`. `readOptions` drives the *source* parse, `writeOptions` the *target* emit. |
| 3 | **Caller owns the streams — converter never closes `in` or `out`** | **DECIDED** | Overrides json-io's usual `closeStream` default. Correct contract for a pipeline middle-stage; closing a caller's response/connection stream mid-pipeline is surprising. Caller uses try-with-resources. |
| 4 | **Byte streams are UTF-8** | proposed | Matches `createTokenizer(InputStream)` / `createGenerator(OutputStream)`. |
| 5 | **Only parse/emit-relevant options bite** | proposed | Strictness, TOON delimiter/folding/indent, prettyPrint, json5. Databind options (custom readers, type coercion, `@type` injection) are no-ops — no Java objects are built. |
| 6 | **No JSON5-specific methods** | proposed | JSON5 is a `WriteOptions`/`ReadOptions` flavor: `toonToJson(toon, null, new WriteOptionsBuilder().json5().build())`. |
| 7 | **Conversions preserve references by default** (`cycleSupport(true)` internally on the high-fidelity tier) | **DECIDED** | A faithful conversion must carry the source's `@id`/`@ref` structure, not inline shared nodes or throw on cycles. Caller may override via explicit `WriteOptions`. Applies to the Maps tier; the streaming tier passes references through verbatim regardless. |

The options that plug straight in:
- **Read side:** `strictToon`, `toonExpandPaths` (§13.4), `toonIndentSize` (§12) — all with
  `addPermanent*` siblings.
- **Write side:** `toonKeyFolding`, `toonDelimiter`, `indentationSize`.

---

## 4. The memory contract (four quadrants)

| Direction | Constant-memory streaming? | Why |
|---|---|---|
| **Caller-driven TOON write** (DB export via `ToonGenerator`) | ✅ Yes | Caller supplies each array's `N`; objects need no count |
| **TOON → JSON** | ✅ Yes | `[N]` + tabular header known up front; feeds straight into `JsonGenerator` |
| **JSON → JSON** (`formatJson`) | ✅ Yes | already true today |
| **JSON → TOON** | ⚠️ **Hard floor = largest array subtree** | JSON arrays have no count; TOON *requires* one — see §5 |

**Documented memory contract for `jsonToToon(in, out)`:** "memory bounded by the largest
array subtree," **not** "constant memory." For a big flat DB export that is one giant
top-level array, that bound is effectively the whole document — the accepted answer is
**segmented transfer** (caller chunks into multiple known-count arrays), which is exactly
what the caller-driven `ToonGenerator` enables. This is not a json-io limitation; it is
inherent to TOON's length-prefixed grammar (§5) and applies to any conformant encoder.

For the **String** forms the floor is moot — the whole document is already resident, so
per-array buffering costs nothing incremental. The String API is the honest right tool
when the data is already a `String`.

---

## 5. The spec constraint that forces the JSON→TOON floor

Verified against the normative spec at the pinned v3.3 commit, not just json-io behavior.

**`[N]` is mandatory for every non-empty array.** §6 ABNF:
```
bracket-seg   = "[" length [ delimsym ] "]"
length        = "0" / ( %x31-39 *DIGIT )
header        = [ key ] bracket-seg [ fields-seg ] ":"
```
`bracket-seg` (which contains `length`) is required in the array `header`. Inline, list,
and tabular forms all derive from this one header production — there is **no count-free
form for any of them**.

**The only count-free array is empty `[]`** (§9.1: encoders SHOULD emit `key: []`, MAY
emit legacy `key[0]:`). Confirmed against the fixtures: every non-empty header carries a
count (`[2]:`, `[3]:`, `[2]{…}` tabular, `[3|]:` pipe); the only count-free token is `[]`.

**Strict mode validates declared-count == actual** (§13.2: "the number of decoded values
MUST equal N"; §14.1 enumerates the inline/list/tabular mismatch errors). Upstream's own
error vector: `"input": "tags[3]: a,b"`, `shouldError: true`.

**Consequence:** a forward-only `JsonTokenizer → ToonGenerator` pipe cannot emit `[N]`
until it has seen `END_ARRAY`, so it must buffer the array to count it. No degraded
count-free streaming form exists to fall back to. Hence the §4 floor.

---

## 6. Implementation strategy — tier by String-vs-stream, not by direction

The deciding factor is `$id`/`$ref` (and `@type`, `@keys`/`@items`) handling. A pure
tokenizer→generator splice treats reference metadata as **opaque field tokens** — it
neither resolves nor re-derives them. The **Maps layer** resolves on read (`MapResolver`
pairs `@id`/`@ref` into a real object graph — its Javadoc: "each `@ref` will be a pointer
to the appropriate Map") and re-derives on write (`ToonWriter`/`JsonWriter`
`traceReferences` re-emits references canonically for the target format). So whenever
references matter, the Maps path is *correct* and the splice is merely *verbatim*.

This splits the implementation into two **fidelity tiers**:

|                | **String / in-memory** (high fidelity) | **Streaming** (low memory) |
|----------------|----------------------------------------|----------------------------|
| **JSON → TOON** | `toToon(toMaps(json))` | buffer-per-array → `ToonWriter`→`ToonGenerator`; **no global ref re-derivation** |
| **TOON → JSON** | `toJson(fromToonToMaps(toon))` | `ToonTokenizer`→`JsonGenerator` splice; **verbatim refs** |

### String / in-memory tier → Maps round-trip
For the String forms, route **both directions through the Maps APIs**
(`toMaps` / `fromToonToMaps` → `toToon` / `toJson`). This is strictly better than a token
splice here:
1. **Reference correctness** — `@id`/`@ref` resolution + canonical re-derivation, free.
2. **Everything else free too** — meta-key prefix normalization (`@`↔`$`), tabular
   detection, key folding, `@keys`/`@items`, map rehashing — all already in
   `MapResolver` + the writers.
3. **Near-zero new code** — `toToon(toMaps(json).asClass(Object.class))` and the mirror.
   Ships 4.104.0 immediately.
4. **Memory is irrelevant** — String input is already fully resident.

### Streaming tier → cursor (cannot use Maps)
`toMaps(in)` / `fromToonToMaps(in)` materialize the **whole** graph (reference
re-derivation is inherently a whole-graph op), so the streaming forms cannot use them
without abandoning their purpose. Streaming is therefore a **lower-fidelity tier**:
references pass through verbatim (or cyclic graphs are rejected), acceptable because the
streaming use cases (DB export, large flat data, LLM payloads) are reference-light.
Per-direction the streaming engine is still asymmetric: TOON→JSON is a clean
`ToonTokenizer`→`JsonGenerator` splice; JSON→TOON must buffer per array and route through
`ToonWriter` (a raw `ToonGenerator` does not auto-tabularize — that needs the materialized
array). **Do NOT** implement JSON→TOON streaming as a naive `JsonTokenizer →
ToonGenerator` splice (it would emit list form, worse than `toToon`, or duplicate
`ToonWriter`'s tabular detection).

### Fidelity caveat (inherent, not a flaw)
String and stream forms can **diverge on reference-bearing documents**: the String form
re-derives *canonical* references via the resolver; the stream form preserves the
*source's* reference metadata verbatim. For reference-free documents — the overwhelming
majority, and **all** streaming use cases — they are byte-identical. This bends the
"single source of truth" ideal, but it is an inherent consequence of streaming (whole-graph
resolution needs the whole graph), not a fixable design flaw. Document it explicitly.

---

## 7. `ToonGenerator` — caller-driven streaming write (the easy, high-value piece)

The array-count "challenge" largely evaporates for a generator because the generator is
**caller-driven**: the caller supplies `N`. The database programmer always knows it (or
one `SELECT COUNT(*)` away), and **objects carry no count** — only arrays do.

```java
try (ToonGenerator g = JsonIo.createToonGenerator(out)) {
    g.writeStartArray(rowCount);          // emits "[rowCount]:" — caller declares N
    while (rs.next()) {
        g.writeStartObject();             // objects need NO count
        g.writeStringField("name", rs.getString(1));
        g.writeNumberField("age",  rs.getInt(2));
        g.writeEndObject();
    }
    g.writeEndArray();                    // generator validates it emitted exactly N
}
```

Design points:
- `writeStartArray(int size)` is **Jackson-precedented** — Jackson added the size-hinted
  overload precisely for length-prefixed formats (Smile/CBOR/Avro). TOON belongs in that
  family.
- **Trust-but-verify:** accept the declared `N`, throw on `writeEndArray` if the actual
  element count differs (strict-parity with the reader's §13.2/§14.1 validation).
- **Tabular** needs columns up front too — a TOON-specific extension,
  e.g. `writeStartTabularArray(int rows, String[] columns)`, then stream value-tuples.
  Default to list form when not opted in.
- Enables genuinely **unlimited constant-memory streaming** for the DB-export case — the
  highest-value streaming story in this whole feature.

---

## 8. `ToonTokenizer` — extraction from `ToonReader` (the hard piece)

Mirror of `JsonParser → CharStreamTokenizer` (done 4.103.0 Phase A), but **~1.5–2× the
effort** because TOON has two structural properties JSON lacks. Difficulty is
**concentrated**, not diffuse.

**Low-risk lift-and-shift (~40–50% of `ToonReader`'s 2731 lines):** scalar/string/number
lexing (`readScalar`, `parseQuotedString`, `parseNumber`, `decodeUnicodeEscape`,
`hasForbiddenLeadingZero`, `isValidNumberToken`), the string/number caches, the physical-
line cursor (`readLineRaw`/`hasLine`/`peek*`/`consumeLine`/`findColon*`), and array-header
parsing (`parseCombinedArrayHeader`/`parseColumnHeaders` → `ArrayHeader{count, delimiter,
columnHeaders}` as token metadata).

**Hard part #1 — indentation → structural tokens.** Today the START/END boundaries are
*implicit* in `readObject`'s `baseIndent` recursion (`indent < baseIndent` *is* the
END_OBJECT signal). The tokenizer must make them *explicit* — emit synthetic
`START_OBJECT`/`END_OBJECT`/`START_ARRAY`/`END_ARRAY` from indent transitions
(YAML INDENT/DEDENT style). New code; owns the line-granularity lookahead that
`hasAdditionalListElements`/`hasAdditionalTabularRows` do today.

**Hard part #2 — tabular row → object-token synthesis.** No JSON analogue. `[3]{name,age}:`
declares field names *once*; rows are bare positional tuples. To emit a uniform stream
(so TOON→JSON "just works" feeding a `JsonGenerator`), the tokenizer must remember the
column headers and synthesize per row: `START_OBJECT`, `(FIELD_NAME=headers[i],
VALUE=cell[i])` ×N, `END_OBJECT`. That is what `parseRowIntoObject`/`appendColumn` do
today inline against a `JsonObject`; it becomes token emission and the bespoke builders
get deleted.

**Stays in the tree-builder (mirrors JSON, clean):** `@id`/`@ref`/`@type`/`@items`/`@keys`
semantics, path expansion (§13.4), key folding. `ToonReader.applyPendingMetadata` /
`resolveBufferedType` / `validateBufferedIdValue` are already near-clones of
`JsonParser.applyPendingMetadata` / `loadType` / `validateAndExtractIdValue`.

**Three design forks:**
1. **Tabular expansion lives in the tokenizer** (emits per-row object tokens), not the
   tree-builder — required for the TOON→JSON streaming story.
2. **Count/columns/delimiter as token metadata** — `START_ARRAY` needs
   `getCurrentArrayLength()` / columns / delimiter getters. TOON-specific extension beyond
   the Jackson cursor surface, analogous to `CharStreamTokenizer`'s package-private
   `hasNonWhitespaceContent()`.
3. **Strict count validation moves into the tokenizer** — it knows `N` and emits
   `END_ARRAY`, so it's the natural place to throw on mismatch.

**The prize:** if `ToonTokenizer` emits the same `JsonToken` stream shape as
`CharStreamTokenizer`, the **tree-builder can be unified** — one builder consuming either
cursor. The two readers already converged independently on the same
peek-through/`PendingMeta`/`@`-vocabulary design, so this *deletes duplication* rather than
adding abstraction. Mirror image of "one `JsonWriter` core, two generators" — cleaner,
since the writer side never unified.

**Risk register:** line-granularity lookahead, blank-line tolerance, empty `[]`/`[0]:`,
bare-hyphen empty objects, inline objects (`readInlineObject`), inline arrays, delimiter
detection — each must become a deterministic token-emission rule.

---

## 9. Build order (dependency-driven)

1. **`ToonGenerator`** (4.105.0) — easy, caller-driven, no indentation/tabular look-ahead
   headaches. Unlocks the DB-export streaming. Independent; build first.
2. **`ToonTokenizer`** (4.105.0) — the §8 extraction. Unlocks TOON→JSON streaming *and*
   the unified tree-builder.
3. **Stream + decorator conversion forms** (4.105.0) — thin wrappers over the §6 engines,
   once 1 + 2 land.
4. **String conversion forms** (4.104.0) — Maps round-trip (`toToon(toMaps(...))` and the
   mirror), the high-fidelity tier per §6. These **stay** on the Maps path permanently;
   they are *not* later rewritten onto the streaming cursor (the two tiers are allowed to
   differ on reference-bearing documents — §6 fidelity caveat).

`ToonWriter` dogfooding `ToonGenerator` (mirror of `JsonWriter`→`JsonGenerator`) is a
prerequisite for the JSON→TOON engine in §6; fold it into the `ToonGenerator` work.

---

## 10. Open decisions

- **Decorator form (`InputStream jsonToToon(InputStream)`)** — *lean keep*. Narrow but
  real: pull-based sinks (Java 11 `HttpClient.BodyPublishers.ofInputStream`, Spring
  `WebClient`, reactive stacks) want a `Supplier<InputStream>`; without the decorator the
  caller bridges push→pull with a `PipedInputStream` + worker thread. Relevant to the
  gateway/proxy topology. Hardest to implement (lazy pull-driven `InputStream` carrying
  the per-array buffering), but ships in 4.105.0 when the engine exists anyway.
- **`createToonGenerator(...)` / `createToonTokenizer(...)` factory signatures** — mirror
  the existing `createGenerator` / `createTokenizer` (Writer/OutputStream and
  String/InputStream + options overloads). Exact surface is a separate design pass when §7/§8 start.
- ~~Default `cycleSupport` for the conversion APIs~~ — **DECIDED (see §3 contract #7):
  preserve references by default** (`cycleSupport(true)` internally on the Maps tier). A
  faithful conversion carries the source's reference structure rather than inlining shared
  nodes or throwing on cycles; caller may override via explicit `WriteOptions`. The
  streaming tier passes references through verbatim regardless.
- **Whether to keep the String forms on the Maps round-trip permanently** (recommended —
  see §6) or ever move them onto the streaming cursor. Recommendation: keep String on Maps;
  the cursor is only for the genuinely-streaming entry points. The String and stream tiers
  are allowed to differ on reference-bearing documents (§6 fidelity caveat).

---

## 10a. Discovered during the 4.104.0 spike (2026-05)

Building the String conversion on the Maps round-trip surfaced that re-serializing a
*resolved Maps graph* (whose containers are json-io's internal `JsonObject` /
`JsonObjectMap` / `JsonObjectArray`) is the wrong mechanism for type-faithful output.
Findings, in order of severity:

1. **`@type` leak (FIXED, latent bug).** Both `JsonWriter` and `ToonWriter` emitted the
   internal carrier class (`com.cedarsoftware.io.JsonObject` etc.) as `@type` for an untyped
   JsonObject under `showTypeInfoMinimal`/`MinimalPlus`; the `JsonObjectMap` form then crashed
   on read-back. Fixed in `JsonWriter.getTypeNameForOutput` (return null) and
   `ToonWriter.shouldWriteTypeMetadata` (skip JsonObject-family classes). No regressions.
2. **`ToonWriter` carrier gap (DEFERRED to this conversion work).** `ToonWriter` has no
   handler for a `JsonObjectArray`/`JsonObjectMap` *carrier* (data in `@items`/`@keys`, not
   map entries), so re-serializing such a graph to TOON silently drops the contents (a
   resolved `TreeSet` writes empty). `JsonWriter` has `writeJsonObjectArray`/
   `writeJsonObjectMap`; ToonWriter does not. The §6 verbatim-transcode approach sidesteps
   this by never re-serializing carriers.
3. **Type-inference modes can't suppress container noise on a typeless graph.** `MINIMAL`/
   `MINIMAL_PLUS` decide `@type` by comparing runtime type to the *declared field type*; a
   Maps graph has none, so they emit container types (`ArrayList`, etc.) as noise. This is
   why the Maps round-trip cannot produce clean *and* type-faithful output — confirming the
   §6 decision to do type-faithful conversion via verbatim transcode (4.105.0), not Maps.

**Status:** #35 (the String `jsonToToon`/`toonToJson` impl) is parked in a git stash
(`WIP #35 ...`) pending the 4.105.0 tokenizer/generator. Revisit it as a verbatim transcode.

---

## 11. References

- Spec (pinned): `toon-format/spec@07161ccc` (v3.3) — array grammar §6, arrays §9, strict
  validation §13.2/§14.1, key folding/path expansion §13.4.
- Vendored fixtures + manifest: `json-io/src/test/resources/toon-spec-fixtures/`.
- Related design docs: `.claude/perf-loop/4.103.0-jsontokenizer-design.md` (the JSON-side
  extraction this mirrors), `.claude/perf-loop/4.103.0-eager-construction-design.md`.
- Key existing code: `ToonReader.java` (§8 source), `ToonWriter.java` (tabular detection,
  §6), `CharStreamTokenizer.java` / `JsonTokenizer.java` (cursor template),
  `CharStreamGenerator.java` / `JsonGenerator.java` (generator template).
