package sealmc.swe3tie.sealcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModuleRegistryTest {

    private FakePlugin plugin;
    private ModuleRegistry registry;

    @BeforeEach
    void setUp() {
        plugin = FakePlugin.create();
        registry = new ModuleRegistry(plugin.asPlugin(), plugin.getLogger());
    }

    private ModuleState state(String id) {
        return registry.report(id).state();
    }

    @Test
    void aModuleWithNoFileIsCreatedFromTheShippedDefault() {
        plugin.resources.put("modules/jobs-miner.yml", "schema-version: 1\npayout: 42.0\n");
        var module = new TestModule("jobs.miner");
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.ACTIVE, state("jobs.miner"));
        assertEquals(42.0, module.applied.get(0).payout());
        assertEquals(List.of("modules/jobs-miner.yml"), plugin.savedResources);
    }

    @Test
    void aModuleWithNoShippedDefaultStillLoadsFromItsGetterDefaults() {
        var module = new TestModule("jobs.miner");
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.ACTIVE, state("jobs.miner"));
        assertEquals(10.0, module.applied.get(0).payout());
    }

    @Test
    void enabledFalseIsHonouredBeforeAnythingIsApplied() {
        plugin.writeModuleFile("jobs-miner.yml", "enabled: false\npayout: 3.0\n");
        var module = new TestModule("jobs.miner");
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.DISABLED, state("jobs.miner"));
        assertTrue(module.applied.isEmpty());
    }

    @Test
    void aBadValueFailsTheModuleAndNamesTheKey() {
        plugin.writeModuleFile("jobs-miner.yml", "payout: -3.0\n");
        var module = new TestModule("jobs.miner");
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.FAILED, state("jobs.miner"));
        assertTrue(registry.report("jobs.miner").problems().get(0).contains("payout"));
        assertTrue(module.applied.isEmpty());
    }

    @Test
    void aModuleThatThrowsWhileParsingIsReportedNotPropagated() {
        var module = new TestModule("jobs.miner", new TestModule.SpecBuilder()
            .onParse(section -> {
                throw new IllegalArgumentException("boom");
            }));
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.FAILED, state("jobs.miner"));
        assertTrue(registry.report("jobs.miner").problems().get(0).contains("IllegalArgumentException"));
    }

    @Test
    void crossFieldRulesFromValidateFailTheModuleToo() {
        var module = new TestModule("jobs.miner", new TestModule.SpecBuilder()
            .onValidate((spec, section) -> spec.payout() > 100.0 ? List.of("payout above the cap") : List.of()));
        plugin.writeModuleFile("jobs-miner.yml", "payout: 500.0\n");
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.FAILED, state("jobs.miner"));
        assertTrue(module.applied.isEmpty());
    }

    @Test
    void aFailedReloadKeepsTheLastGoodSpecAndTheOthersStillReload() {
        var good = new TestModule("jobs.miner");
        var other = new TestModule("shop");
        plugin.writeModuleFile("jobs-miner.yml", "payout: 5.0\n");
        plugin.writeModuleFile("shop.yml", "payout: 6.0\n");
        registry.register(good);
        registry.register(other);
        registry.load();
        assertEquals(5.0, good.applied.get(0).payout());

        plugin.writeModuleFile("jobs-miner.yml", "payout: -1.0\n");
        plugin.writeModuleFile("shop.yml", "payout: 9.0\n");
        registry.load();

        assertEquals(ModuleState.FAILED, state("jobs.miner"));
        assertEquals(ModuleState.ACTIVE, state("shop"));
        // The live spec is still the old one, and it is not re-applied: the
        // running module already has exactly that spec live.
        assertEquals(5.0, registry.<TestModule.Spec>specOf("jobs.miner").payout());
        assertEquals(1, good.applied.size());
        assertEquals(9.0, other.applied.get(other.applied.size() - 1).payout());
    }

    @Test
    void aModuleWhoseDependencyIsOffIsSkippedNotFailed() {
        var base = new TestModule("economy");
        var dependent = new TestModule("auction", Set.of("economy"), null);
        plugin.writeModuleFile("economy.yml", "enabled: false\n");
        registry.register(base);
        registry.register(dependent);

        registry.load();

        assertEquals(ModuleState.DISABLED, state("economy"));
        assertEquals(ModuleState.SKIPPED, state("auction"));
        assertTrue(registry.report("auction").problems().get(0).contains("economy"));
    }

    @Test
    void aModuleIsEnabledAfterItsDependencyEvenWhenRegisteredFirst() {
        List<String> enabled = new ArrayList<>();
        var dependent = new TestModule("auction", Set.of("economy"), new TestModule.SpecBuilder()
            .onEnable(spec -> enabled.add("auction")));
        var base = new TestModule("economy", new TestModule.SpecBuilder()
            .onEnable(spec -> enabled.add("economy")));
        registry.register(dependent);
        registry.register(base);

        registry.load();

        assertEquals(List.of("economy", "auction"), enabled);
    }

    @Test
    void anUnknownDependencyIsAHardError() {
        registry.register(new TestModule("auction", Set.of("ghost"), null));

        var error = assertThrows(IllegalStateException.class, () -> registry.load());
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("ghost"));
    }

    @Test
    void aDependencyCycleIsAHardError() {
        registry.register(new TestModule("a", Set.of("b"), null));
        registry.register(new TestModule("b", Set.of("a"), null));

        var error = assertThrows(IllegalStateException.class, () -> registry.load());
        assertTrue(error.getMessage().contains("cycle"));
    }

    @Test
    void aRestartOnlyKeyIsReportedOnReloadNotSilentlyApplied() {
        var module = new TestModule("jobs.miner", Set.<String>of(), Set.of("label"), new TestModule.SpecBuilder());
        plugin.writeModuleFile("jobs-miner.yml", "label: first\n");
        registry.register(module);
        registry.load();
        assertTrue(registry.report("jobs.miner").restartRequired().isEmpty());

        plugin.writeModuleFile("jobs-miner.yml", "label: second\n");
        registry.load();

        assertEquals(List.of("label"), registry.report("jobs.miner").restartRequired());
        assertEquals("second", module.applied.get(module.applied.size() - 1).label());
    }

    @Test
    void anUnchangedRestartKeyIsNotReported() {
        var module = new TestModule("jobs.miner", Set.<String>of(), Set.of("label"), new TestModule.SpecBuilder());
        plugin.writeModuleFile("jobs-miner.yml", "label: same\n");
        registry.register(module);
        registry.load();
        registry.load();

        assertTrue(registry.report("jobs.miner").restartRequired().isEmpty());
    }

    @Test
    void aModuleBehindOnSchemaVersionIsMigratedInPlace() {
        plugin.writeModuleFile("jobs-miner.yml", "schema-version: 1\npayout: 4.0\n");
        registry.addMigration(new ModuleMigration("jobs.miner", 1, section -> section.set("payout", 8.0)));
        var module = new TestModule("jobs.miner", 2, Set.of(), Set.of(), new TestModule.SpecBuilder());
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.ACTIVE, state("jobs.miner"));
        assertEquals(2, plugin.readModuleFile("jobs-miner.yml").getInt("schema-version"));
        assertEquals(8.0, module.applied.get(0).payout());
    }

    @Test
    void aFileFromANewerPluginFailsTheModuleInsteadOfGuessing() {
        plugin.writeModuleFile("jobs-miner.yml", "schema-version: 9\n");
        var module = new TestModule("jobs.miner", 1, Set.of(), Set.of(), new TestModule.SpecBuilder());
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.FAILED, state("jobs.miner"));
        assertTrue(registry.report("jobs.miner").problems().get(0).contains("schema-version 9"));
        assertTrue(module.applied.isEmpty());
    }

    @Test
    void aMissingMigrationFailsTheModuleRatherThanHalfUpgradingIt() {
        plugin.writeModuleFile("jobs-miner.yml", "schema-version: 1\n");
        var module = new TestModule("jobs.miner", 2, Set.of(), Set.of(), new TestModule.SpecBuilder());
        registry.register(module);

        registry.load();

        assertEquals(ModuleState.FAILED, state("jobs.miner"));
        assertEquals(1, plugin.readModuleFile("jobs-miner.yml").getInt("schema-version"));
    }

    @Test
    void disableWalksActiveModulesInReverseAndForgetsTheirSpec() {
        List<String> order = new ArrayList<>();
        var dependent = new TestModule("auction", Set.of("economy"), new TestModule.SpecBuilder()
            .onDisable(() -> order.add("auction")));
        var base = new TestModule("economy", new TestModule.SpecBuilder()
            .onDisable(() -> order.add("economy")));
        registry.register(dependent);
        registry.register(base);
        registry.load();

        registry.disableAll();

        assertEquals(List.of("auction", "economy"), order);
        assertEquals(ModuleState.DISABLED, state("economy"));
        assertNull(registry.<TestModule.Spec>specOf("economy"));
        assertEquals(1, base.disableCount());
    }

    @Test
    void idsAndCountersTrackWhatIsRegistered() {
        registry.register(new TestModule("economy"));
        registry.register(new TestModule("shop"));
        assertEquals(List.of("economy", "shop"), registry.ids());
        assertTrue(registry.isRegistered("shop"));
        assertEquals(0, registry.activeCount());

        registry.load();
        assertEquals(2, registry.activeCount());
    }

    @Test
    void registeringTheSameIdTwiceIsRefused() {
        registry.register(new TestModule("shop"));

        var error = assertThrows(IllegalArgumentException.class, () -> registry.register(new TestModule("shop")));
        assertFalse(error.getMessage() == null);
        assertTrue(error.getMessage().contains("already registered"));
    }
}
