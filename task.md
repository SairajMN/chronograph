# Chronograph — task tracker

## 1. Event log
- [x] sqlite schema: `events(id, ts, type, src, dst)`, created on startup
- [x] `EventStore.append(Event)` — insert, no validation logic yet
- [x] `EventStore.replay()` — PriorityQueue by (ts, id), returns ordered list
- [x] test: append out of order, replay twice, assert identical output
Done: sqlite-jdbc works fine, PriorityQueue ordering is correct.

## 2. Graph model
- [x] `TemporalEdge` record (from, to, validFrom, validTo, eventId)
- [x] `GraphSnapshot` — adjacency map + applyDelta(Event)
- [x] test: replay a 4-node fixture, assert snapshot matches hand-built graph
Done: forward and reverse adjacency maps both maintained; remove closes edges correctly.

## 3. Temporal BFS/DFS + reachability-at-time
- [x] Bfs.reachable(snapshot, source, target) respecting validFrom/validTo
- [x] Dfs for dependency-chain walks
- [x] test: reachable before an edge's validFrom is false, after is true
Done: both BFS and DFS work with temporal edges via edgesFrom filtering.

## 4. Causal chain
- [x] CausalChain.why(snapshot, node) — reverse BFS to root cause
- [ ] wire into `/graph/why` once tested standalone
Done: reverse BFS walks incoming edges; returns root cause and path.

## 5. Snapshot diff
- [x] Diff.between(snapshotA, snapshotB) -> added/removed nodes+edges
- [x] test: known before/after fixture produces expected diff
Done: set comparison of edges valid at each time works.

## 6. Tarjan SCC
- [x] TarjanScc.find(snapshot) -> list of cycles
- [x] test: a 3-node cycle is detected, a DAG returns none
Done: Tarjan's algorithm implemented; correctly detects multi-node cycles.

## 7. Rollback + cache
- [x] operation stack for undo-to-snapshot
- [x] SnapshotCache (LinkedHashMap LRU, fixed capacity)
- [x] test: cache evicts oldest entry past capacity
Done: Rollback replays events from scratch up to a count; LRU works via LinkedHashMap.

## 8. HTTP layer
- [x] Main.java — HttpServer, routes to each endpoint from README's API list
- [x] seed script: db-fail -> auth-fail -> payment-fail -> checkout-fail
- [x] manual check: curl /graph/why?node=checkout&time=T3 returns the README's demo output verbatim
Done: all endpoints verified — snapshot, reachable, why, diff, impact all return correct JSON. Demo curl returns `{"root_cause":"db","path":["db","auth","payment","checkout"]}`.
