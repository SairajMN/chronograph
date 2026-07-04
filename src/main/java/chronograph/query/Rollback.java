package chronograph.query;

import chronograph.event.Event;
import chronograph.graph.GraphSnapshot;

import java.util.*;

/**
 * Rollback support via an operation stack.
 * Tracks applied events and can rebuild a snapshot up to a given event count.
 */
public class Rollback {
    private final List<Event> applied = new ArrayList<>();

    public void record(Event e) {
        applied.add(e);
    }

    /** Rebuild snapshot by replaying the first N events. */
    public GraphSnapshot rebuildTo(int eventCount) {
        var snap = new GraphSnapshot();
        for (int i = 0; i < Math.min(eventCount, applied.size()); i++) {
            var e = applied.get(i);
            applyToSnapshot(snap, e);
        }
        return snap;
    }

    /**
     * Rebuild snapshot by replaying events up to (but not including) the given
     * index.
     */
    public GraphSnapshot rebuildBefore(int eventIndex) {
        return rebuildTo(eventIndex);
    }

    /** Undo the last N events: rebuild snapshot from scratch up to (size - N). */
    public GraphSnapshot undo(int n) {
        if (n > applied.size())
            n = applied.size();
        return rebuildTo(applied.size() - n);
    }

    public int size() {
        return applied.size();
    }

    public List<Event> history() {
        return Collections.unmodifiableList(applied);
    }

    private void applyToSnapshot(GraphSnapshot snap, Event e) {
        switch (e.type()) {
            case "add" -> snap.applyAdd(e.ts(), e.id(), e.src(), e.dst());
            case "remove" -> snap.applyRemove(e.ts(), e.id(), e.src(), e.dst());
            default -> {
                /* unknown event type, skip */ }
        }
    }
}