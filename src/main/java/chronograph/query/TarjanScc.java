package chronograph.query;

import chronograph.graph.GraphSnapshot;

import java.util.*;

/**
 * Tarjan's strongly connected components algorithm on a temporal graph
 * snapshot.
 * Detects cycles in the dependency graph.
 */
public class TarjanScc {

    /**
     * Returns all SCCs (each as a list of node names) found in the snapshot at the
     * given time.
     */
    public static List<List<String>> find(GraphSnapshot snap, long time) {
        var nodes = snap.nodes();
        var index = new HashMap<String, Integer>();
        var lowlink = new HashMap<String, Integer>();
        var onStack = new HashMap<String, Boolean>();
        var stack = new ArrayDeque<String>();
        var sccs = new ArrayList<List<String>>();
        var nextIndex = new int[] { 0 };

        for (var node : nodes) {
            if (!index.containsKey(node)) {
                strongconnect(node, snap, time, index, lowlink, onStack, stack, sccs, nextIndex);
            }
        }
        return sccs;
    }

    private static void strongconnect(String v, GraphSnapshot snap, long time,
            Map<String, Integer> index, Map<String, Integer> lowlink,
            Map<String, Boolean> onStack, ArrayDeque<String> stack,
            List<List<String>> sccs, int[] nextIndex) {
        index.put(v, nextIndex[0]);
        lowlink.put(v, nextIndex[0]);
        nextIndex[0]++;
        stack.push(v);
        onStack.put(v, true);

        for (var e : snap.edgesFrom(v, time)) {
            var w = e.to();
            if (!index.containsKey(w)) {
                strongconnect(w, snap, time, index, lowlink, onStack, stack, sccs, nextIndex);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.getOrDefault(w, false)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            var scc = new ArrayList<String>();
            String w;
            do {
                w = stack.pop();
                onStack.put(w, false);
                scc.add(w);
            } while (!w.equals(v));
            sccs.add(scc);
        }
    }
}