package chronograph;

import chronograph.graph.GraphSnapshot;
import chronograph.query.CausalChain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CausalChainTest {

    @Test
    void demoScenarioDbFailToCheckoutFail() {
        // db-fail (T0=10) -> auth-fail (T1=20) -> payment-fail (T2=30) -> checkout-fail
        // (T3=40)
        var snap = new GraphSnapshot()
                .applyAdd(10, 0, "db", "auth")
                .applyAdd(20, 1, "auth", "payment")
                .applyAdd(30, 2, "payment", "checkout");

        var result = CausalChain.why(snap, "checkout", 50);
        assertEquals("db", result.rootCause());
        assertEquals("[db, auth, payment, checkout]", result.path().toString());
    }

    @Test
    void isolatedNodeHasSelfAsRootCause() {
        var snap = new GraphSnapshot().applyAdd(10, 0, "a", "b");
        var result = CausalChain.why(snap, "a", 50);
        assertEquals("a", result.rootCause());
        assertEquals(1, result.path().size());
    }
}