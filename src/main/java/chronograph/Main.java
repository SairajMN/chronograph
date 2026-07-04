package chronograph;

import chronograph.cache.SnapshotCache;
import chronograph.event.Event;
import chronograph.event.EventStore;
import chronograph.graph.GraphSnapshot;
import chronograph.query.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static EventStore store;
    private static final SnapshotCache cache = new SnapshotCache(128);
    private static final Rollback rollback = new Rollback();

    public static void main(String[] args) throws Exception {
        store = new EventStore("chronograph.db");

        // Seed demo data if empty
        if (store.count() == 0) {
            seedDemoData();
        }

        // Rebuild rollback history from stored events
        for (var e : store.replay()) {
            rollback.record(e);
        }

        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        // Static file handler for frontend
        server.createContext("/", ex -> {
            var path = ex.getRequestURI().getPath();
            if (path.equals("/"))
                path = "/index.html";
            try {
                var file = Path.of(".", path);
                if (!Files.exists(file)) {
                    var bytes = "not found".getBytes(StandardCharsets.UTF_8);
                    cors(ex.getResponseHeaders());
                    ex.sendResponseHeaders(404, bytes.length);
                    ex.getResponseBody().write(bytes);
                    ex.getResponseBody().close();
                    return;
                }
                var mime = path.endsWith(".css") ? "text/css"
                        : path.endsWith(".js") ? "application/javascript"
                                : path.endsWith(".html") ? "text/html" : "application/octet-stream";
                var bytes = Files.readAllBytes(file);
                var h = ex.getResponseHeaders();
                cors(h);
                h.set("Content-Type", mime);
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.getResponseBody().close();
            } catch (IOException e) {
                var bytes = e.getMessage().getBytes(StandardCharsets.UTF_8);
                cors(ex.getResponseHeaders());
                ex.sendResponseHeaders(500, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.getResponseBody().close();
            }
        });
        server.createContext("/events", Main::handlePostEvent);
        server.createContext("/graph/snapshot", Main::handleSnapshot);
        server.createContext("/graph/reachable", Main::handleReachable);
        server.createContext("/graph/why", Main::handleWhy);
        server.createContext("/graph/diff", Main::handleDiff);
        server.createContext("/graph/impact", Main::handleImpact);
        server.setExecutor(null);
        server.start();
        System.out.println("chronograph running on http://localhost:8080");
    }

    // ── Seed ──────────────────────────────────────────────────────────────

    private static void seedDemoData() throws Exception {
        // A rich ecosystem of services with branching, removal, self-loop, and
        // cascading failure
        // Timeline:
        // T10 add db → auth core database drives auth
        // T20 add auth → payment payment depends on auth
        // T30 add auth → checkout checkout also depends on auth
        // T40 add payment → ledger payment writes to ledger
        // T50 add checkout → invoice invoice after checkout
        // T60 add auth → audit audit trail from auth
        // T70 add payment → fraud fraud check before payment (!)
        // T80 remove payment → fraud fraud link dies — payment bypasses
        // T90 add fraud → fraud self-loop cycle for SCC demo
        //
        // At T75: db → auth → payment → ledger
        // → checkout → invoice
        // → audit
        // auth → payment → fraud → fraud (cycle!)
        // At T85: fraud is orphaned, payment → fraud removed
        //
        store.append(10, "add", "db", "auth");
        store.append(20, "add", "auth", "payment");
        store.append(30, "add", "auth", "checkout");
        store.append(40, "add", "payment", "ledger");
        store.append(50, "add", "checkout", "invoice");
        store.append(60, "add", "auth", "audit");
        store.append(70, "add", "payment", "fraud");
        store.append(80, "remove", "payment", "fraud");
        store.append(90, "add", "fraud", "fraud");
        System.out.println("seeded demo data: db → auth → {payment→ledger, checkout→invoice} + audit + fraud cycle");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static GraphSnapshot snapshotAt(long time) throws Exception {
        var cached = cache.get(time);
        if (cached != null)
            return cached;
        var snap = new GraphSnapshot();
        for (var e : store.replay()) {
            if (e.ts() > time)
                break;
            apply(snap, e);
        }
        cache.put(time, snap);
        return snap;
    }

    private static void apply(GraphSnapshot snap, Event e) {
        switch (e.type()) {
            case "add" -> snap.applyAdd(e.ts(), e.id(), e.src(), e.dst());
            case "remove" -> snap.applyRemove(e.ts(), e.id(), e.src(), e.dst());
        }
    }

    private static Map<String, String> parseQuery(String query) {
        var map = new HashMap<String, String>();
        if (query == null || query.isBlank())
            return map;
        for (var pair : query.split("&")) {
            var parts = pair.split("=", 2);
            if (parts.length == 2) {
                map.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void cors(Headers h) {
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        var h = ex.getResponseHeaders();
        cors(h);
        h.set("Content-Type", "application/json");
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private static void handlePostEvent(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        try (var in = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            var body = in.lines().collect(Collectors.joining());
            // Minimal JSON parse: extract type, src, dst, timestamp
            var type = extractString(body, "type");
            var src = extractString(body, "src");
            var dst = extractString(body, "dst");
            var ts = extractLong(body, "timestamp");
            if (type == null || src == null || dst == null) {
                respond(ex, 400, "{\"error\":\"missing required fields: type, src, dst\"}");
                return;
            }
            var event = store.append(ts, type, src, dst);
            rollback.record(event);
            respond(ex, 201, "{\"id\":" + event.id() + ",\"ts\":" + event.ts()
                    + ",\"type\":" + json(event.type())
                    + ",\"src\":" + json(event.src())
                    + ",\"dst\":" + json(event.dst()) + "}");
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":" + json(e.getMessage()) + "}");
        }
    }

    private static void handleSnapshot(HttpExchange ex) throws IOException {
        try {
            var params = parseQuery(ex.getRequestURI().getQuery());
            var time = Long.parseLong(params.getOrDefault("time", "0"));
            var snap = snapshotAt(time);
            var edges = snap.allEdges(time);
            var sb = new StringBuilder("{\"time\":").append(time).append(",\"edges\":[");
            var first = true;
            for (var e : edges) {
                if (!first)
                    sb.append(",");
                sb.append("{\"from\":").append(json(e.from()))
                        .append(",\"to\":").append(json(e.to()))
                        .append(",\"validFrom\":").append(e.validFrom())
                        .append(",\"validTo\":").append(e.validTo()).append("}");
                first = false;
            }
            sb.append("]}");
            respond(ex, 200, sb.toString());
        } catch (Exception e) {
            respond(ex, 400, "{\"error\":" + json(e.getMessage()) + "}");
        }
    }

    private static void handleReachable(HttpExchange ex) throws IOException {
        try {
            var params = parseQuery(ex.getRequestURI().getQuery());
            var source = params.get("source");
            var target = params.get("target");
            var time = Long.parseLong(params.getOrDefault("time", "0"));
            if (source == null || target == null) {
                respond(ex, 400, "{\"error\":\"source and target required\"}");
                return;
            }
            var snap = snapshotAt(time);
            var reachable = Bfs.reachable(snap, source, target, time);
            respond(ex, 200, "{\"source\":" + json(source) + ",\"target\":" + json(target)
                    + ",\"time\":" + time + ",\"reachable\":" + reachable + "}");
        } catch (Exception e) {
            respond(ex, 400, "{\"error\":" + json(e.getMessage()) + "}");
        }
    }

    private static void handleWhy(HttpExchange ex) throws IOException {
        try {
            var params = parseQuery(ex.getRequestURI().getQuery());
            var node = params.get("node");
            var time = Long.parseLong(params.getOrDefault("time", "0"));
            if (node == null) {
                respond(ex, 400, "{\"error\":\"node required\"}");
                return;
            }
            var snap = snapshotAt(time);
            var result = CausalChain.why(snap, node, time);
            var pathJson = result.path().stream().map(Main::json).collect(Collectors.joining(","));
            respond(ex, 200, "{\"root_cause\":" + json(result.rootCause())
                    + ",\"path\":[" + pathJson + "]}");
        } catch (Exception e) {
            respond(ex, 400, "{\"error\":" + json(e.getMessage()) + "}");
        }
    }

    private static void handleDiff(HttpExchange ex) throws IOException {
        try {
            var params = parseQuery(ex.getRequestURI().getQuery());
            var t1 = Long.parseLong(params.get("t1"));
            var t2 = Long.parseLong(params.get("t2"));
            var snap = snapshotAt(Math.max(t1, t2));
            var result = Diff.between(snap, t1, t2);
            var addedEdges = result.edgesAdded().stream()
                    .map(e -> "{\"from\":" + json(e.from()) + ",\"to\":" + json(e.to()) + "}")
                    .collect(Collectors.joining(","));
            var removedEdges = result.edgesRemoved().stream()
                    .map(e -> "{\"from\":" + json(e.from()) + ",\"to\":" + json(e.to()) + "}")
                    .collect(Collectors.joining(","));
            respond(ex, 200, "{\"t1\":" + t1 + ",\"t2\":" + t2
                    + ",\"edges_added\":[" + addedEdges + "]"
                    + ",\"edges_removed\":[" + removedEdges + "]}");
        } catch (Exception e) {
            respond(ex, 400, "{\"error\":" + json(e.getMessage()) + "}");
        }
    }

    private static void handleImpact(HttpExchange ex) throws IOException {
        try {
            var params = parseQuery(ex.getRequestURI().getQuery());
            var node = params.get("node");
            var time = Long.parseLong(params.getOrDefault("time", "0"));
            if (node == null) {
                respond(ex, 400, "{\"error\":\"node required\"}");
                return;
            }
            var snap = snapshotAt(time);
            var impacted = Bfs.reachableNodes(snap, node, time);
            impacted.remove(node); // don't include the node itself
            var nodesJson = impacted.stream().map(Main::json).collect(Collectors.joining(","));
            respond(ex, 200, "{\"node\":" + json(node) + ",\"time\":" + time
                    + ",\"impacted_nodes\":[" + nodesJson + "]}");
        } catch (Exception e) {
            respond(ex, 400, "{\"error\":" + json(e.getMessage()) + "}");
        }
    }

    // ── Minimal JSON parser ───────────────────────────────────────────────

    private static String extractString(String json, String key) {
        var search = "\"" + key + "\":\"";
        var idx = json.indexOf(search);
        if (idx < 0)
            return null;
        idx += search.length();
        var end = json.indexOf("\"", idx);
        return end < 0 ? null : json.substring(idx, end);
    }

    private static long extractLong(String json, String key) {
        var search = "\"" + key + "\":";
        var idx = json.indexOf(search);
        if (idx < 0)
            return 0;
        idx += search.length();
        var end = idx;
        while (end < json.length() && Character.isDigit(json.charAt(end)))
            end++;
        return end > idx ? Long.parseLong(json.substring(idx, end)) : 0;
    }
}