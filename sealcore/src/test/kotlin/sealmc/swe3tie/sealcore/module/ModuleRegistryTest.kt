package sealmc.swe3tie.sealcore.module

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleRegistryTest {

    private lateinit var plugin: FakePlugin
    private lateinit var registry: ModuleRegistry

    @BeforeTest
    fun setUp() {
        plugin = FakePlugin.create()
        registry = ModuleRegistry(plugin, plugin.logger)
    }

    private fun state(id: String) = requireNotNull(registry.report(id)).state

    @Test
    fun `a module with no file is created from the shipped default`() {
        plugin.resources["modules/jobs-miner.yml"] = "schema-version: 1\npayout: 42.0\n"
        val module = TestModule("jobs.miner")
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.ACTIVE, state("jobs.miner"))
        assertEquals(42.0, module.applied.single().payout)
        assertEquals(listOf("modules/jobs-miner.yml"), plugin.savedResources)
    }

    @Test
    fun `a module with no shipped default still loads from its getter defaults`() {
        val module = TestModule("jobs.miner")
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.ACTIVE, state("jobs.miner"))
        assertEquals(10.0, module.applied.single().payout)
    }

    @Test
    fun `enabled false is honoured before anything is applied`() {
        plugin.writeModuleFile("jobs-miner.yml", "enabled: false\npayout: 3.0\n")
        val module = TestModule("jobs.miner")
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.DISABLED, state("jobs.miner"))
        assertTrue(module.applied.isEmpty())
    }

    @Test
    fun `a bad value fails the module and names the key`() {
        plugin.writeModuleFile("jobs-miner.yml", "payout: -3.0\n")
        val module = TestModule("jobs.miner")
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.FAILED, state("jobs.miner"))
        assertTrue(registry.report("jobs.miner")!!.problems.single().contains("payout"))
        assertTrue(module.applied.isEmpty())
    }

    @Test
    fun `a module that throws while parsing is reported, not propagated`() {
        val module = TestModule("jobs.miner", builder = TestModule.SpecBuilder().apply {
            onParse = { throw IllegalArgumentException("boom") }
        })
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.FAILED, state("jobs.miner"))
        assertTrue(registry.report("jobs.miner")!!.problems.single().contains("IllegalArgumentException"))
    }

    @Test
    fun `cross field rules from validate fail the module too`() {
        val module = TestModule("jobs.miner", builder = TestModule.SpecBuilder().apply {
            onValidate = { spec, _ -> if (spec.payout > 100.0) listOf("payout above the cap") else emptyList() }
        })
        plugin.writeModuleFile("jobs-miner.yml", "payout: 500.0\n")
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.FAILED, state("jobs.miner"))
        assertTrue(module.applied.isEmpty())
    }

    @Test
    fun `a failed reload keeps the last good spec and the others still reload`() {
        val good = TestModule("jobs.miner")
        val other = TestModule("shop")
        plugin.writeModuleFile("jobs-miner.yml", "payout: 5.0\n")
        plugin.writeModuleFile("shop.yml", "payout: 6.0\n")
        registry.register(good)
        registry.register(other)
        registry.load()
        assertEquals(5.0, good.applied.single().payout)

        plugin.writeModuleFile("jobs-miner.yml", "payout: -1.0\n")
        plugin.writeModuleFile("shop.yml", "payout: 9.0\n")
        registry.load()

        assertEquals(ModuleState.FAILED, state("jobs.miner"))
        assertEquals(ModuleState.ACTIVE, state("shop"))
        // The live spec is still the old one, and it is not re-applied: the
        // running module already has exactly that spec live.
        assertEquals(5.0, registry.specOf<TestModule.Spec>("jobs.miner")!!.payout)
        assertEquals(1, good.applied.size)
        assertEquals(9.0, other.applied.last().payout)
    }

    @Test
    fun `a module whose dependency is off is skipped, not failed`() {
        val base = TestModule("economy")
        val dependent = TestModule("auction", dependsOn = setOf("economy"))
        plugin.writeModuleFile("economy.yml", "enabled: false\n")
        registry.register(base)
        registry.register(dependent)

        registry.load()

        assertEquals(ModuleState.DISABLED, state("economy"))
        assertEquals(ModuleState.SKIPPED, state("auction"))
        assertTrue(registry.report("auction")!!.problems.single().contains("economy"))
    }

    @Test
    fun `a module is enabled after its dependency even when registered first`() {
        val enabled = mutableListOf<String>()
        val dependent = TestModule("auction", dependsOn = setOf("economy"), builder = TestModule.SpecBuilder().apply {
            onEnable = { enabled += "auction" }
        })
        val base = TestModule("economy", builder = TestModule.SpecBuilder().apply {
            onEnable = { enabled += "economy" }
        })
        registry.register(dependent)
        registry.register(base)

        registry.load()

        assertEquals(listOf("economy", "auction"), enabled)
    }

    @Test
    fun `an unknown dependency is a hard error`() {
        registry.register(TestModule("auction", dependsOn = setOf("ghost")))
        val error = runCatching { registry.load() }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message!!.contains("ghost"))
    }

    @Test
    fun `a dependency cycle is a hard error`() {
        registry.register(TestModule("a", dependsOn = setOf("b")))
        registry.register(TestModule("b", dependsOn = setOf("a")))
        val error = runCatching { registry.load() }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message!!.contains("cycle"))
    }

    @Test
    fun `a restart only key is reported on reload, not silently applied`() {
        val module = TestModule("jobs.miner", restartKeys = setOf("label"))
        plugin.writeModuleFile("jobs-miner.yml", "label: first\n")
        registry.register(module)
        registry.load()
        assertTrue(registry.report("jobs.miner")!!.restartRequired.isEmpty())

        plugin.writeModuleFile("jobs-miner.yml", "label: second\n")
        registry.load()

        assertEquals(listOf("label"), registry.report("jobs.miner")!!.restartRequired)
        assertEquals("second", module.applied.last().label)
    }

    @Test
    fun `an unchanged restart key is not reported`() {
        val module = TestModule("jobs.miner", restartKeys = setOf("label"))
        plugin.writeModuleFile("jobs-miner.yml", "label: same\n")
        registry.register(module)
        registry.load()
        registry.load()

        assertTrue(registry.report("jobs.miner")!!.restartRequired.isEmpty())
    }

    @Test
    fun `a module behind on schema version is migrated in place`() {
        plugin.writeModuleFile("jobs-miner.yml", "schema-version: 1\npayout: 4.0\n")
        registry.addMigration(
            ModuleMigration("jobs.miner", fromVersion = 1) { section ->
                section.set("payout", 8.0)
            },
        )
        val module = TestModule("jobs.miner", schemaVersion = 2)
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.ACTIVE, state("jobs.miner"))
        assertEquals(2, plugin.readModuleFile("jobs-miner.yml").getInt("schema-version"))
        assertEquals(8.0, module.applied.single().payout)
    }

    @Test
    fun `a file from a newer plugin fails the module instead of guessing`() {
        plugin.writeModuleFile("jobs-miner.yml", "schema-version: 9\n")
        val module = TestModule("jobs.miner", schemaVersion = 1)
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.FAILED, state("jobs.miner"))
        assertTrue(registry.report("jobs.miner")!!.problems.single().contains("schema-version 9"))
        assertTrue(module.applied.isEmpty())
    }

    @Test
    fun `a missing migration fails the module rather than half upgrading it`() {
        plugin.writeModuleFile("jobs-miner.yml", "schema-version: 1\n")
        val module = TestModule("jobs.miner", schemaVersion = 2)
        registry.register(module)

        registry.load()

        assertEquals(ModuleState.FAILED, state("jobs.miner"))
        assertEquals(1, plugin.readModuleFile("jobs-miner.yml").getInt("schema-version"))
    }

    @Test
    fun `disable walks active modules in reverse and forgets their spec`() {
        val order = mutableListOf<String>()
        val dependent = TestModule("auction", dependsOn = setOf("economy"), builder = TestModule.SpecBuilder().apply {
            onDisable = { order += "auction" }
        })
        val base = TestModule("economy", builder = TestModule.SpecBuilder().apply {
            onDisable = { order += "economy" }
        })
        registry.register(dependent)
        registry.register(base)
        registry.load()

        registry.disableAll()

        assertEquals(listOf("auction", "economy"), order)
        assertEquals(ModuleState.DISABLED, state("economy"))
        assertNull(registry.specOf<ModuleSpec>("economy"))
        assertEquals(1, base.disableCount)
    }

    @Test
    fun `ids and counters track what is registered`() {
        registry.register(TestModule("economy"))
        registry.register(TestModule("shop"))
        assertEquals(listOf("economy", "shop"), registry.ids())
        assertTrue(registry.isRegistered("shop"))
        assertEquals(0, registry.activeCount())

        registry.load()
        assertEquals(2, registry.activeCount())
    }

    @Test
    fun `registering the same id twice is refused`() {
        registry.register(TestModule("shop"))
        val error = runCatching { registry.register(TestModule("shop")) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message!!.contains("already registered"))
    }
}

class ModuleDependencyReloadTest {

    @Test
    fun `a dependency that fails to reload keeps its dependents on old settings`() {
        val plugin = FakePlugin.create()
        val registry = ModuleRegistry(plugin, plugin.logger)
        val base = TestModule("economy")
        val dependent = TestModule("auction", dependsOn = setOf("economy"))
        plugin.writeModuleFile("economy.yml", "payout: 1.0\n")
        plugin.writeModuleFile("auction.yml", "payout: 2.0\n")
        registry.register(base)
        registry.register(dependent)
        registry.load()
        assertEquals(ModuleState.ACTIVE, state(registry, "economy"))

        plugin.writeModuleFile("economy.yml", "payout: -1.0\n")
        plugin.writeModuleFile("auction.yml", "payout: 3.0\n")
        registry.load()

        assertEquals(ModuleState.FAILED, state(registry, "economy"))
        assertEquals(ModuleState.SKIPPED, state(registry, "auction"))
        val reason = requireNotNull(registry.report("auction")).problems.single()
        assertTrue(reason.contains("could not be reloaded"), reason)
        // Still live on what it had, not torn down.
        assertEquals(2.0, registry.specOf<TestModule.Spec>("auction")!!.payout)
    }

    @Test
    fun `a dependency that was never on reports as simply inactive`() {
        val plugin = FakePlugin.create()
        val registry = ModuleRegistry(plugin, plugin.logger)
        plugin.writeModuleFile("economy.yml", "enabled: false\n")
        registry.register(TestModule("economy"))
        registry.register(TestModule("auction", dependsOn = setOf("economy")))
        registry.load()

        val reason = requireNotNull(registry.report("auction")).problems.single()
        assertTrue(reason.contains("is not active"), reason)
    }

    private fun state(registry: ModuleRegistry, id: String) =
        requireNotNull(registry.report(id)).state
}
