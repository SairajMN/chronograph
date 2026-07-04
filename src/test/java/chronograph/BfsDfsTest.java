package chronograph;

import chronograph.graph.GraphSnapshot;
import chronograph.query.Bfs;
import chronograph.query.Dfs;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BfsDfsTest {

    // Graph: a->b (ts=10), b->c (ts=20), c->d (ts=30), a->c (ts=15)
    private GraphSnapshot fixture() {
        return new GraphSnapshot()
                .applyAdd(10, 0, "a", "b")
                .applyAdd(15, 1, "a", "c")
                .applyAdd(20, 2, "b", "c")
                .applyAdd(30, 3, "c", "d");
    }

    @Test
    void reachableBeforeEdgeCreationIsFalse() {
        var snap = fixture();
        // At time 5, no edges exist
        assertFalse(Bfs.reachable(snap, "a", "b", 5));
        assertFalse(Bfs.reachable(snap, "a", "d", 5));
    }

    @Test
    void reachableAfterEdgeCreationIsTrue() {
        var snap = fixture();
        assertTrue(Bfs.reachable(snap, "a", "b", 50));
        assertTrue(Bfs.reachable(snap, "a", "d", 50));
        assertTrue(Bfs.reachable(snap, "b", "c", 50));
        assertTrue(Bfs.reachable(snap, "c", "d", 50));
    }

    @Test
    void sameNodeIsAlwaysReachable() {
        var snap = new GraphSnapshot();
        assertTrue(Bfs.reachable(snap, "x", "x", 0));
    }

    @Test
    void unreachableReturnsFalse() {
        var snap = fixture();
        assertFalse(Bfs.reachable(snap, "d", "a", 50));
    }

    @Test
    void reachableNodesReturnsCorrectSet() {
        var snap = fixture();
        var fromA = Bfs.reachableNodes(snap, "a", 50);
        assertEquals(Set.of("a", "b", "c", "d"), fromA);
        var fromB = Bfs.reachableNodes(snap, "b", 50);
        assertEquals(Set.of("b", "c", "d"), fromB);
    }

    @Test
    void dfsTraversalCoversAllReachableNodes() {
        var snap = fixture();
        var order = Dfs.traverse(snap, "a", 50);
        assertEquals(Set.of("a", "b", "c", "d"), new java.util.HashSet<>(order));
        // a should be first
        assertEquals("a", order.get(0));
    }
}