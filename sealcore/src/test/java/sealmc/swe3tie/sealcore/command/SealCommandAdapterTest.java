package sealmc.swe3tie.sealcore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.EconomyFixture;
import sealmc.swe3tie.sealcore.TestFixtures;
import sealmc.swe3tie.sealcore.config.Messages;
import sealmc.swe3tie.sealcore.module.FakePlugin;
import sealmc.swe3tie.sealcore.module.ModuleContext;
import sealmc.swe3tie.sealcore.module.ModuleRegistry;
import sealmc.swe3tie.sealcore.module.SealCommand;
import sealmc.swe3tie.sealcore.module.TestModule;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

/**
 * A module command must survive its module being switched off: the command still
 * exists in {@code plugin.yml}, so it has to answer rather than vanish or throw.
 */
class SealCommandAdapterTest {

    private static final Logger LOGGER = Logger.getLogger("SealCoreTest");

    private FakePlugin plugin;
    private ModuleRegistry registry;
    private TestModule module;

    @BeforeEach
    void setUp() {
        LOGGER.setLevel(Level.OFF);
        plugin = FakePlugin.create();
        plugin.resources.put("modules/jobs-miner.yml", "schema-version: 1\n");
        registry = new ModuleRegistry(plugin.asPlugin(), plugin.getLogger());
        module = new TestModule("jobs.miner");
        registry.register(module);
    }

    private ModuleContext context() {
        return new ModuleContext(
            new Messages("en.yml", null, shippedLanguage(), null),
            EconomyFixture.service(new ExcellentEconomyAPI.Fake()),
            new TestFixtures.InlineScheduler(),
            null,
            registry,
            () -> null,
            LOGGER);
    }

    private static YamlConfiguration shippedLanguage() {
        InputStream stream = SealCommandAdapterTest.class.getClassLoader().getResourceAsStream("languages/en.yml");
        if (stream == null) {
            throw new AssertionError("languages/en.yml is not on the test classpath");
        }
        try (stream) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private SealCommandAdapter adapter(List<String> ran) {
        return new SealCommandAdapter(
            new SealCommand(module, "jobs", "Jobs", "/jobs",
                context -> new CommandNode("jobs", "", "jobs.miner", false, "", ctx -> ran.add("handled"))),
            context(),
            LOGGER);
    }

    @Test
    void aDisabledModuleAnswersInsteadOfRunning() {
        plugin.writeModuleFile("jobs-miner.yml", "enabled: false\n");
        registry.load();
        var player = TestFixtures.recordingPlayer("Alice", java.util.UUID.randomUUID(), Set.of("jobs.miner"));
        List<String> ran = new ArrayList<>();

        adapter(ran).onCommand(player.asPlayer(), JOBS, "jobs", new String[0]);

        assertEquals(List.of("Module jobs.miner is disabled."), player.plain());
        assertTrue(ran.isEmpty());
    }

    @Test
    void aDisabledModuleOffersNoCompletions() {
        plugin.writeModuleFile("jobs-miner.yml", "enabled: false\n");
        registry.load();
        var player = TestFixtures.recordingPlayer("Alice", java.util.UUID.randomUUID(), Set.of("jobs.miner"));

        var completions = adapter(new ArrayList<>()).onTabComplete(player.asPlayer(), JOBS, "jobs", new String[] {""});

        assertTrue(completions.isEmpty());
    }

    @Test
    void anActiveModuleRunsItsTree() {
        registry.load();
        var player = TestFixtures.recordingPlayer("Alice", java.util.UUID.randomUUID(), Set.of("jobs.miner"));
        List<String> ran = new ArrayList<>();

        adapter(ran).onCommand(player.asPlayer(), JOBS, "jobs", new String[0]);

        assertEquals(List.of("handled"), ran);
    }

    private static final Command JOBS = new Command("jobs") {

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return false;
        }
    };
}
