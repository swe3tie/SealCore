package sealmc.swe3tie.sealcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleDependencyReloadTest {

    @Test
    void aDependencyThatFailsToReloadKeepsItsDependentsOnOldSettings() {
        var plugin = FakePlugin.create();
        var registry = new ModuleRegistry(plugin.asPlugin(), plugin.getLogger());
        var base = new TestModule("economy");
        var dependent = new TestModule("auction", Set.of("economy"), null);
        plugin.writeModuleFile("economy.yml", "payout: 1.0\n");
        plugin.writeModuleFile("auction.yml", "payout: 2.0\n");
        registry.register(base);
        registry.register(dependent);
        registry.load();
        assertEquals(ModuleState.ACTIVE, state(registry, "economy"));

        plugin.writeModuleFile("economy.yml", "payout: -1.0\n");
        plugin.writeModuleFile("auction.yml", "payout: 3.0\n");
        registry.load();

        assertEquals(ModuleState.FAILED, state(registry, "economy"));
        assertEquals(ModuleState.SKIPPED, state(registry, "auction"));
        String reason = registry.report("auction").problems().get(0);
        assertTrue(reason.contains("could not be reloaded"), reason);
        // Still live on what it had, not torn down.
        assertEquals(2.0, registry.<TestModule.Spec>specOf("auction").payout());
    }

    @Test
    void aDependencyThatWasNeverOnReportsAsSimplyInactive() {
        var plugin = FakePlugin.create();
        var registry = new ModuleRegistry(plugin.asPlugin(), plugin.getLogger());
        plugin.writeModuleFile("economy.yml", "enabled: false\n");
        registry.register(new TestModule("economy"));
        registry.register(new TestModule("auction", Set.of("economy"), null));
        registry.load();

        String reason = registry.report("auction").problems().get(0);
        assertTrue(reason.contains("is not active"), reason);
    }

    private static ModuleState state(ModuleRegistry registry, String id) {
        return registry.report(id).state();
    }
}
