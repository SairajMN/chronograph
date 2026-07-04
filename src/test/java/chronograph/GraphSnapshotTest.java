package chronograph;

import chronograph.graph.GraphSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphSnapshotTest {

    @Test
    void replay4NodeFixtureProducesExpectedSnapshot() {
        // Build a graph: a->b (ts=10), b->c (ts=20), c->d (ts=30)
        var snap = new GraphSnapshot()
                .applyAdd(10, 0, "a", "b")
                .applyAdd(20, 1, "b", "c")
                .applyAdd(30, 2, "c", "d");

        // Check forward edges
        assertEquals(1, snap.edgesFrom("a", 50).size());
        assertEquals("b", snap.edgesFrom("a", 50).get(0).to());
        assertEquals(1, snap.edgesFrom("b", 50).size());
        assertEquals("c", snap.edgesFrom("b", 50).get(0).to());
        assertEquals(1, snap.edgesFrom("c", 50).size());
        assertEquals("d", snap.edgesFrom("c", 50).get(0).to());
        assertTrue(snap.edgesFrom("d", 50).isEmpty());

        // Check reverse edges
        assertEquals(1, snap.edgesTo("d", 50).size());
        assertEquals("c", snap.edgesTo("d", 50).get(0).from());
    }

    @Test
    void edgeIsValidFromItsCreationTime() {
        var snap = new GraphSnapshot().applyAdd(100, 0, "a", "b");
        assertFalse(snap.edgesFrom("a", 100).isEmpty());
        assertTrue(snap.edgesFrom("a", 99).isEmpty());
    }

    @Test
    void removeClosesEdge() {
        var snap = new GraphSnapshot()
                .applyAdd(10, 0, "a", "b")
                .applyRemove(50, 1, "a", "b");
        assertTrue(snap.edgesFrom("a", 30).size() == 1);
        assertTrue(snap.edgesFrom("a", 60).isEmpty());
    }
}