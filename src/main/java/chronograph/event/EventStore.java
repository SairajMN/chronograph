package chronograph.event;

import java.sql.*;
import java.util.*;

public class EventStore implements AutoCloseable {
    private final Connection conn;
    private long nextId = 0;

    public EventStore(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS events (" +
                    "id INTEGER PRIMARY KEY, " +
                    "ts INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "src TEXT NOT NULL, " +
                    "dst TEXT NOT NULL)");
        }
        // seed nextId from existing rows
        try (var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COALESCE(MAX(id), -1) FROM events");
            if (rs.next())
                nextId = rs.getLong(1) + 1;
        }
    }

    public synchronized Event append(long ts, String type, String src, String dst) throws SQLException {
        var id = nextId++;
        try (var ps = conn.prepareStatement(
                "INSERT INTO events (id, ts, type, src, dst) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setLong(2, ts);
            ps.setString(3, type);
            ps.setString(4, src);
            ps.setString(5, dst);
            ps.executeUpdate();
        }
        return new Event(id, ts, type, src, dst);
    }

    /** Replay all events ordered by (ts, id) using a PriorityQueue. */
    public List<Event> replay() throws SQLException {
        var buffer = new PriorityQueue<Event>(
                Comparator.comparingLong(Event::ts).thenComparingLong(Event::id));
        try (var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT id, ts, type, src, dst FROM events");
            while (rs.next()) {
                buffer.add(new Event(rs.getLong("id"), rs.getLong("ts"),
                        rs.getString("type"), rs.getString("src"), rs.getString("dst")));
            }
        }
        var out = new ArrayList<Event>(buffer.size());
        while (!buffer.isEmpty())
            out.add(buffer.poll());
        return out;
    }

    public int count() throws SQLException {
        try (var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM events");
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }
}