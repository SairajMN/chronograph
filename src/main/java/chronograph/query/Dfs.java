package chronograph.query;

import chronograph.graph.GraphSnapshot;

import java.util.*;

/** Depth-first search on a temporal graph snapshot. */
public class Dfs {

    /** Returns all nodes reachable from source at the given time, in DFS order. */
    public static List<String> traverse(GraphSnapshot snap, String source, long time) {
        var visited = new LinkedHashSet<String>();
        var stack = new ArrayDeque<String>();
        stack.push(source);
        while (!stack.isEmpty()) {
            var node = stack.pop();
            if (visited.add(node)) {
                // push neighbors in reverse order so first neighbor is explored first
                var edges = snap.edgesFrom(node, time);
                for (int i = edges.size() - 1; i >= 0; i--) {
                    stack.push(edges.get(i).to());
                }
            }
        }
        return new ArrayList<>(visited);
    }
}