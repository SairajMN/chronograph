package chronograph.graph;

public record TemporalEdge(String from, String to, long validFrom, long validTo, long eventId) {
    public boolean validAt(long time) {
        return time >= validFrom && time < validTo;
    }

    /** A "forever" edge: valid from validFrom until Long.MAX_VALUE. */
    public TemporalEdge {
        if (from == null || to == null)
            throw new IllegalArgumentException("from and to must not be null");
    }
}