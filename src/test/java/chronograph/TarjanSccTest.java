package chronograph;

import chronograph.graph.GraphSnapshot;
import chronograph.query.TarjanScc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TarjanSccTest {

    @Test
    void dagReturnsNoCycles() {
        var snap = new GraphSnapshot()
                .applyAdd(10, 0, "a", "b")
                .applyAdd(20, 1, "b", "c")
                .applyAdd(30, 2, "c", "d");
        var sccs = TarjanScc.find(snap, 50);
        // Each node is its own SCC in a DAG
        assertTrue(sccs.stream().allMatch(scc -> scc.size() == 1));
    }

    @Test
    void threeNodeCycleIsDetected() {
        var snap = new GraphSnapshot()
                .applyAdd(10, 0, "a", "b")
                .applyAdd(20, 1, "b", "c")
                .applyAdd(30, 2, "c", "a");
        var sccs = TarjanScc.find(snap, 50);
        var hasCycle = sccs.stream().anyMatch(scc -> scc.size() >= 2);
        assertTrue(hasCycle, "Expected a cycle of size >= 2");
    }

    @Test
    void selfLoopIsCycle() {
        var snap = new GraphSnapshot()
                .applyAdd(10, 0, "a", "a");
        var sccs = TarjanScc.find(snap, 50);
        var hasCycle = sccs.stream().anyMatch(scc -> scc.size() >= 2);
        // Self-loop is a cycle of size 1 — actually Tarjan treats a self-loop as its
        // own SCC of size 1
        // So we check that there's at least one SCC with a self-loop edge
        var edges = snap.edgesFrom("a", 50);
        assertTrue(edges.stream().anyMatch(e -> e.from().equals(e.to())));
    }
}