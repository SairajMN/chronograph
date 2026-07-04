package chronograph.query;

import chronograph.graph.GraphSnapshot;

import java.util.*;

/** Breadth-first search on a temporal graph snapshot. */
public class Bfs {

    /** Returns true if target is reachable from source at the given time. */
    public static boolean reachable(GraphSnapshot snap, String source, String target, long time) {
        if (source.equals(target))
            return true;
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        visited.add(source);
        queue.add(source);
        while (!queue.isEmpty()) {
            var node = queue.poll();
            for (var e : snap.edgesFrom(node, time)) {
                if (e.to().equals(target))
                    return true;
                if (visited.add(e.to()))
                    queue.add(e.to());
            }
        }
        return false;
    }

    /** Returns all nodes reachable from source at the given time. */
    public static Set<String> reachableNodes(GraphSnapshot snap, String source, long time) {
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        visited.add(source);
        queue.add(source);
        while (!queue.isEmpty()) {
            var node = queue.poll();
            for (var e : snap.edgesFrom(node, time)) {
                if (visited.add(e.to()))
                    queue.add(e.to());
            }
        }
        return visited;
    }
}