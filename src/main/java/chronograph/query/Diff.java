package chronograph.query;

import chronograph.graph.GraphSnapshot;
import chronograph.graph.TemporalEdge;

import java.util.*;
import java.util.stream.Collectors;

/** Computes the diff between two graph snapshots at different times. */
public class Diff {

    public record Result(
            Set<String> nodesAdded,
            Set<String> nodesRemoved,
            Set<TemporalEdge> edgesAdded,
            Set<TemporalEdge> edgesRemoved) {
    }

    public static Result between(GraphSnapshot snap, long t1, long t2) {
        var nodes1 = snap.nodes();
        var edges1 = edgesToSet(snap, t1);
        var nodes2 = snap.nodes();
        var edges2 = edgesToSet(snap, t2);

        var nodesAdded = new HashSet<>(nodes2);
        nodesAdded.removeAll(nodes1);

        var nodesRemoved = new HashSet<>(nodes1);
        nodesRemoved.removeAll(nodes2);

        var edgesAdded = new HashSet<>(edges2);
        edgesAdded.removeAll(edges1);

        var edgesRemoved = new HashSet<>(edges1);
        edgesRemoved.removeAll(edges2);

        return new Result(nodesAdded, nodesRemoved, edgesAdded, edgesRemoved);
    }

    private static Set<TemporalEdge> edgesToSet(GraphSnapshot snap, long time) {
        return snap.allEdges(time).stream().collect(Collectors.toSet());
    }
}