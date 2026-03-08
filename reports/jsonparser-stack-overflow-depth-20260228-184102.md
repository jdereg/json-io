# JsonParser implicit stack depth experiment

- Parser path: current implicit recursion-based `JsonParser.readValue()` (heap changes stashed).
- ReadOptions: `maxDepth(1_000_000)` to avoid parser depth guard dominating results.
- JVM: default stack size (`java` default, no `-Xss` override).

## Array nesting
- Max depth parsed successfully: **7260**
- First depth with StackOverflowError: **7261**
- Last OK probe output: `OK depth=7260 elapsedMs=97`

## Object nesting
- Max depth parsed successfully: **4607**
- First depth with StackOverflowError: **4608**
- Last OK probe output: `OK depth=4607 elapsedMs=112`

## Raw result JSON
```json
{
  "array": {
    "kind": "array",
    "max_ok_depth": 7260,
    "min_soe_depth": 7261,
    "last_ok_output": "OK depth=7260 elapsedMs=97"
  },
  "object": {
    "kind": "object",
    "max_ok_depth": 4607,
    "min_soe_depth": 4608,
    "last_ok_output": "OK depth=4607 elapsedMs=112"
  }
}
```
