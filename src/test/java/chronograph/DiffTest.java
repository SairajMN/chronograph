package chronograph;

import chronograph.graph.GraphSnapshot;
import chronograph.query.Diff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiffTest {

    @Test
    void knownBeforeAfterFixtureProducesExpectedDiff() {
        // At t=10: a->b
        var snap = new GraphSnapshot().applyAdd(10, 0, "a", "b");
        // At t=20: add a->c, also a->b is still valid
        snap.applyAdd(20, 1, "a", "c");

        var result = Diff.between(snap, 15, 25);
        // At t=15: just a->b
        // At t=25: a->b and a->c
        assertEquals(1, result.edgesAdded().size());
        assertTrue(result.edgesAdded().stream().anyMatch(e -> e.to().equals("c")));
        assertTrue(result.edgesRemoved().isEmpty());
    }

    @Test
    void removeEdgeShowsInRemoved() {
        var snap = new GraphSnapshot()
                .applyAdd(10, 0, "a", "b")
                .applyRemove(50, 1, "a", "b");
        var result = Diff.between(snap, 30, 70);
        assertEquals(1, result.edgesRemoved().size());
        assertTrue(result.edgesRemoved().stream().anyMatch(e -> e.to().equals("b")));
        assertTrue(result.edgesAdded().isEmpty());
    }
}