package chronograph.cache;

import chronograph.graph.GraphSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded LRU cache for reconstructed graph snapshots.
 * Uses LinkedHashMap with removeEldestEntry — no third-party lib needed.
 */
public class SnapshotCache {
    private final LinkedHashMap<Long, GraphSnapshot> cache;

    public SnapshotCache(int capacity) {
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, GraphSnapshot> eldest) {
                return size() > capacity;
            }
        };
    }

    public GraphSnapshot get(long time) {
        return cache.get(time);
    }

    public void put(long time, GraphSnapshot snapshot) {
        cache.put(time, snapshot);
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }
}