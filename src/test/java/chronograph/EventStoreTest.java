package chronograph;

import chronograph.event.EventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventStoreTest {

    @Test
    void replayOutOfOrderProducesSameOrderTwice(@TempDir Path tmp) throws Exception {
        var dbPath = tmp.resolve("test.db").toString();
        try (var store = new EventStore(dbPath)) {
            store.append(30, "fail", "a", "b");
            store.append(10, "fail", "c", "d");
            store.append(20, "fail", "e", "f");
        }

        try (var store = new EventStore(dbPath)) {
            var first = store.replay();
            var second = store.replay();
            assertEquals(first, second);
            assertEquals(3, first.size());
            // verify order: ts 10, 20, 30
            assertEquals(10, first.get(0).ts());
            assertEquals(20, first.get(1).ts());
            assertEquals(30, first.get(2).ts());
        }
    }
}