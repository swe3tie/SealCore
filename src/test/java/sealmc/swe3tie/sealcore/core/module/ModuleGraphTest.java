package sealmc.swe3tie.sealcore.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleGraphTest {

    @Test
    void independentModulesAreAlphabetical() {
        List<String> order = ModuleGraph.order(Map.of("shop", Set.of(), "auction", Set.of(), "economy", Set.of()));
        assertEquals(List.of("auction", "economy", "shop"), order);
    }

    @Test
    void dependenciesComeFirst() {
        List<String> order = ModuleGraph.order(Map.of(
            "auction", Set.of("economy"),
            "economy", Set.of(),
            "bounty", Set.of("economy", "auction")));
        assertTrue(order.indexOf("economy") < order.indexOf("auction"));
        assertTrue(order.indexOf("auction") < order.indexOf("bounty"));
        assertEquals(3, order.size());
    }

    @Test
    void unknownDependenciesAreIgnored() {
        assertEquals(List.of("shop"), ModuleGraph.order(Map.of("shop", Set.of("missing"))));
    }

    @Test
    void cycleIsDetected() {
        var e = assertThrows(ModuleGraph.CycleException.class,
            () -> ModuleGraph.order(Map.of("a", Set.of("b"), "b", Set.of("c"), "c", Set.of("a"))));
        assertTrue(e.getMessage().contains("a -> b -> c -> a"), e.getMessage());
    }

    @Test
    void selfDependencyIsACycle() {
        assertThrows(ModuleGraph.CycleException.class, () -> ModuleGraph.order(Map.of("a", Set.of("a"))));
    }
}
