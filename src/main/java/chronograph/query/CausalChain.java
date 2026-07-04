package chronograph.query;

import chronograph.graph.GraphSnapshot;

import java.util.*;

/**
 * Reverse BFS to find root causes and the causal path.
 * A root cause is a node with no incoming edges at the given time.
 */
public class CausalChain {

    /**
     * Returns the root cause and the full causal path from root to the given node.
     * If multiple root causes exist, returns the first one found.
     */
    public static Result why(GraphSnapshot snap, String node, long time) {
        // Walk backwards to find root cause
        var path = new ArrayList<String>();
        var current = node;
        while (true) {
            path.add(current);
            var incoming = snap.edgesTo(current, time);
            if (incoming.isEmpty())
                break; // root cause
            // Follow the first incoming edge backwards
            current = incoming.get(0).from();
        }
        Collections.reverse(path);
        return new Result(path.get(0), path);
    }

    public record Result(String rootCause, List<String> path) {
    }
}