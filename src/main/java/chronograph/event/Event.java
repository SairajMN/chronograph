package chronograph.event;

public record Event(long id, long ts, String type, String src, String dst) {
    public Event {
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("type must not be blank");
        if (src == null || src.isBlank())
            throw new IllegalArgumentException("src must not be blank");
        if (dst == null || dst.isBlank())
            throw new IllegalArgumentException("dst must not be blank");
    }
}