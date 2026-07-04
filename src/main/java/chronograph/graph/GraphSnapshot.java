package chronograph.graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A point-in-time view of the graph: adjacency map of node -> outgoing edges.
 * Built by replaying events through applyDelta.
 */
public class GraphSnapshot {
    private final Map<String, List<TemporalEdge>> adj = new HashMap<>();
    private final Map<String, List<TemporalEdge>> inAdj = new HashMap<>();

    /**
     * Apply a single "add" event: creates a new edge valid from this event's ts
     * until Long.MAX_VALUE.
     */
    public GraphSnapshot applyAdd(long ts, long eventId, String src, String dst) {
        var e = new TemporalEdge(src, dst, ts, Long.MAX_VALUE, eventId);
        adj.computeIfAbsent(src, k -> new ArrayList<>()).add(e);
        inAdj.computeIfAbsent(dst, k -> new ArrayList<>()).add(e);
        return this;
    }

    /**
     * Apply a single "remove" event: sets validTo on matching edge(s) from src to
     * dst.
     */
    public GraphSnapshot applyRemove(long ts, long eventId, String src, String dst) {
        var outEdges = adj.get(src);
        if (outEdges != null) {
            for (int i = 0; i < outEdges.size(); i++) {
                var e = outEdges.get(i);
                if (e.to().equals(dst) && e.validTo() == Long.MAX_VALUE) {
                    var closed = new TemporalEdge(e.from(), e.to(), e.validFrom(), ts, e.eventId());
                    outEdges.set(i, closed);
                }
            }
        }
        var inEdges = inAdj.get(dst);
        if (inEdges != null) {
            for (int i = 0; i < inEdges.size(); i++) {
                var e = inEdges.get(i);
                if (e.from().equals(src) && e.validTo() == Long.MAX_VALUE) {
                    var closed = new TemporalEdge(e.from(), e.to(), e.validFrom(), ts, e.eventId());
                    inEdges.set(i, closed);
                }
            }
        }
        return this;
    }

    /** Edges valid at the given time, for forward traversal. */
    public List<TemporalEdge> edgesFrom(String node, long time) {
        var list = adj.get(node);
        if (list == null)
            return List.of();
        return list.stream().filter(e -> e.validAt(time)).collect(Collectors.toList());
    }

    /** Edges valid at the given time, for reverse traversal. */
    public List<TemporalEdge> edgesTo(String node, long time) {
        var list = inAdj.get(node);
        if (list == null)
            return List.of();
        return list.stream().filter(e -> e.validAt(time)).collect(Collectors.toList());
    }

    /** All nodes that have at least one valid edge at the given time. */
    public Set<String> nodes() {
        var all = new HashSet<>(adj.keySet());
        all.addAll(inAdj.keySet());
        return all;
    }

    /** All edges valid at the given time. */
    public List<TemporalEdge> allEdges(long time) {
        return adj.values().stream()
                .flatMap(Collection::stream)
                .filter(e -> e.validAt(time))
                .collect(Collectors.toList());
    }
}