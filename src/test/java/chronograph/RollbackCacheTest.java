package chronograph;

import chronograph.cache.SnapshotCache;
import chronograph.event.Event;
import chronograph.graph.GraphSnapshot;
import chronograph.query.Rollback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RollbackCacheTest {

    @Test
    void cacheEvictsOldestEntryPastCapacity() {
        var cache = new SnapshotCache(3);
        var snap0 = new GraphSnapshot();
        var snap1 = new GraphSnapshot();
        var snap2 = new GraphSnapshot();
        var snap3 = new GraphSnapshot();
        cache.put(0, snap0);
        cache.put(1, snap1);
        cache.put(2, snap2);
        assertEquals(3, cache.size());
        cache.put(3, snap3); // should evict entry at 0
        assertEquals(3, cache.size());
        assertNull(cache.get(0));
        assertNotNull(cache.get(1));
        assertNotNull(cache.get(2));
        assertNotNull(cache.get(3));
    }

    @Test
    void rollbackUndoRestoresPreviousState() {
        var rb = new Rollback();
        rb.record(new Event(0, 10, "add", "a", "b"));
        rb.record(new Event(1, 20, "add", "b", "c"));

        var afterTwo = rb.rebuildTo(2);
        assertTrue(afterTwo.edgesFrom("a", 30).size() == 1);
        assertTrue(afterTwo.edgesFrom("b", 30).size() == 1);

        var afterUndo = rb.undo(1);
        assertTrue(afterUndo.edgesFrom("a", 30).size() == 1);
        assertTrue(afterUndo.edgesFrom("b", 30).isEmpty()); // b->c was undone
    }
}