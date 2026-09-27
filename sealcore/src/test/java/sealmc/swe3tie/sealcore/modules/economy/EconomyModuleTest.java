package sealmc.swe3tie.sealcore.modules.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.EconomyFixture;
import sealmc.swe3tie.sealcore.TestFixtures;
import sealmc.swe3tie.sealcore.TestFixtures.MessageRecorder;
import sealmc.swe3tie.sealcore.command.CommandDispatcher;
import sealmc.swe3tie.sealcore.command.CommandNode;
import sealmc.swe3tie.sealcore.config.Messages;
import sealmc.swe3tie.sealcore.config.SealCoreConfig;
import sealmc.swe3tie.sealcore.economy.EconomyService;
import sealmc.swe3tie.sealcore.module.FakePlugin;
import sealmc.swe3tie.sealcore.module.ModuleContext;
import sealmc.swe3tie.sealcore.module.ModuleRegistry;
import sealmc.swe3tie.sealcore.module.ModuleSection;
import sealmc.swe3tie.sealcore.module.ModuleState;
import sealmc.swe3tie.sealcore.storage.Database;
import sealmc.swe3tie.sealcore.storage.MigrationRunner;
import sealmc.swe3tie.sealcore.storage.PlayerRepository;
import sealmc.swe3tie.sealcore.storage.StorageMigrations;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI.BalanceKey;

/**
 * Drives the economy module through the command dispatcher with the shipped Vietnamese
 * language file, so what is asserted is the exact text a player sees.
 */
class EconomyModuleTest {

    private static final Logger LOGGER = Logger.getLogger("SealCoreTest");
    private static final Set<String> EVERYONE = Set.of("sealcore.economy.balance", "sealcore.economy.pay");

    private final Map<String, Player> online = new LinkedHashMap<>();

    private ModuleRegistry registry;
    private EconomyModule module;
    private ExcellentEconomyAPI.Fake fake;
    private Database database;
    private Path folder;

    @BeforeEach
    void setUp() {
        LOGGER.setLevel(Level.OFF);
        fake = new ExcellentEconomyAPI.Fake();
        boot(resource("modules/economy.yml"));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
        if (folder != null) {
            deleteRecursively(folder);
        }
    }

    // --- harness -------------------------------------------------------------

    /** Loads the module from {@code body} the way an operator's file would be read. */
    private void boot(String body) {
        var plugin = FakePlugin.create();
        plugin.resources.put("modules/economy.yml", body);
        registry = new ModuleRegistry(plugin.asPlugin(), plugin.getLogger());
        module = new EconomyModule();
        registry.register(module);
        registry.load();
    }

    private static String resource(String name) {
        InputStream stream = EconomyModuleTest.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new AssertionError(name + " is not on the test classpath");
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static Messages messages(String name) {
        return new Messages(
            name,
            "en.yml",
            YamlConfiguration.loadConfiguration(new InputStreamReader(
                new java.io.ByteArrayInputStream(resource("languages/" + name).getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8)),
            YamlConfiguration.loadConfiguration(new InputStreamReader(
                new java.io.ByteArrayInputStream(resource("languages/en.yml").getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8)));
    }

    private ModuleContext context() {
        return context(null, EconomyFixture.service(fake));
    }

    private ModuleContext context(PlayerRepository storage) {
        return context(storage, EconomyFixture.service(fake));
    }

    private ModuleContext context(PlayerRepository storage, EconomyService economy) {
        return new ModuleContext(
            messages("vi.yml"),
            economy,
            new TestFixtures.InlineScheduler(),
            null,
            registry,
            () -> storage,
            LOGGER);
    }

    private TargetResolver resolver(ModuleContext ctx) {
        return new TargetResolver(ctx, online::get, () -> List.copyOf(online.keySet()));
    }

    private CommandNode balanceNode(ModuleContext ctx) {
        return new BalanceCommand(ctx, module, resolver(ctx)).node();
    }

    private CommandNode payNode(ModuleContext ctx) {
        return payNode(ctx, new ConcurrentHashMap<UUID, Long>());
    }

    private CommandNode payNode(ModuleContext ctx, Map<UUID, Long> cooldowns) {
        return new PayCommand(ctx, module, cooldowns, resolver(ctx)).node();
    }

    private static void run(CommandNode node, CommandSender sender, String... args) {
        new CommandDispatcher(node, LOGGER).dispatch(sender, node.name(), Arrays.asList(args));
    }

    private PlayerRepository profiles() {
        try {
            folder = Path.of(System.getProperty("java.io.tmpdir"), "sealcore-economy-" + UUID.randomUUID());
            Files.createDirectories(folder);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        var db = new Database(
            new SealCoreConfig.Storage(SealCoreConfig.Storage.Type.SQLITE, "test.db"),
            folder.toFile(),
            LOGGER);
        db.open();
        database = db;
        new MigrationRunner(db, LOGGER).apply(StorageMigrations.all(db.dialect()));
        return new PlayerRepository(db);
    }

    /** What the service looks like when ExcellentEconomy is not installed. */
    private static EconomyService withoutEconomy() {
        var service = new EconomyService(new SealCoreConfig.Economy(), LOGGER);
        service.install(null);
        return service;
    }

    private static MessageRecorder alice() {
        return alice(EVERYONE);
    }

    private static MessageRecorder alice(Set<String> permissions) {
        return TestFixtures.recordingPlayer("Alice", UUID.randomUUID(), permissions);
    }

    // --- configuration -------------------------------------------------------

    @Test
    void anEmptyFileLoadsTheShippedDefaults() {
        var section = new ModuleSection("economy", "modules/economy.yml", new YamlConfiguration());
        var parsed = module.parse(section);

        assertEquals("money", parsed.currency().id());
        assertTrue(parsed.allowOffline());
        assertEquals("sealcore.economy.balance", parsed.balance().permission());
        assertTrue(parsed.balance().allowOtherPlayers());
        assertEquals("sealcore.economy.pay", parsed.pay().permission());
        assertEquals("sealcore.economy.bypass", parsed.pay().bypassPermission());
        assertEquals(1.0, parsed.pay().minAmount());
        assertEquals(1_000_000_000.0, parsed.pay().maxAmount());
        assertFalse(parsed.pay().allowSelf());
        assertEquals("$", parsed.format().symbol());
        assertEquals(2, parsed.format().decimals());
        assertEquals(List.of("K", "M", "B", "T", "Qa", "Qi"), parsed.format().suffixes());
        assertFalse(section.hasProblems());
    }

    @Test
    void aWrongValueIsReportedWithTheFileAndTheKey() {
        var yaml = new YamlConfiguration();
        yaml.set("format.decimals", "two");
        yaml.set("pay.cooldown-seconds", "often");
        var section = new ModuleSection("economy", "modules/economy.yml", yaml);

        module.parse(section);

        assertEquals(2, section.problems().size());
        assertTrue(section.problems().stream().allMatch(problem -> problem.startsWith("modules/economy.yml :: ")));
        assertTrue(section.problems().stream().anyMatch(problem -> problem.contains("format.decimals")));
        assertTrue(section.problems().stream().anyMatch(problem -> problem.contains("pay.cooldown-seconds")));
    }

    @Test
    void anAmountRangeThatCannotBePaidIsRefused() {
        var yaml = new YamlConfiguration();
        yaml.set("pay.min-amount", 500.0);
        yaml.set("pay.max-amount", 100.0);
        var section = new ModuleSection("economy", "modules/economy.yml", yaml);

        var problems = module.validate(module.parse(section), section);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("pay.min-amount"));
    }

    @Test
    void anEmptySymbolIsRefused() {
        var yaml = new YamlConfiguration();
        yaml.set("format.symbol", "  ");
        var section = new ModuleSection("economy", "modules/economy.yml", yaml);

        assertEquals(1, module.validate(module.parse(section), section).size());
    }

    @Test
    void anEmptySuffixListIsRefused() {
        var yaml = new YamlConfiguration();
        yaml.set("format.suffixes", List.of("K", ""));
        var section = new ModuleSection("economy", "modules/economy.yml", yaml);

        assertEquals(1, module.validate(module.parse(section), section).size());
    }

    @Test
    void theModuleIsActiveAndOffersBothCommands() {
        assertEquals(ModuleState.ACTIVE, registry.report("economy").state());
        assertEquals(
            List.of("balance", "pay"),
            module.commands(context()).stream().map(sealmc.swe3tie.sealcore.module.SealCommand::name).toList());
    }

    @Test
    void aModuleThatIsSwitchedOffIsNotActive() {
        boot("schema-version: 1\nenabled: false\n");

        assertEquals(ModuleState.DISABLED, registry.report("economy").state());
        assertNull(registry.specOf("economy"));
    }

    // --- /balance ------------------------------------------------------------

    @Test
    void balanceWithoutAPlayerShowsTheCallersOwnMoney() {
        var alice = alice();
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 89_890_000.0);

        run(balanceNode(context()), alice.asPlayer());

        assertEquals(List.of("Bạn có $89.89M"), alice.plain());
    }

    @Test
    void balanceWithAPlayerShowsThatPlayersMoney() {
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());
        fake.balances.put(new BalanceKey(bob.asPlayer().getUniqueId(), "money"), 1_500.0);

        run(balanceNode(context()), alice.asPlayer(), "Bob");

        assertEquals(List.of("Bob có $1.50K"), alice.plain());
    }

    @Test
    void balanceReadsAPlayerWhoHasJoinedBeforeEvenOffline() {
        var profiles = profiles();
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        profiles.upsert(bob.asPlayer().getUniqueId(), "Bob", 1_000L);
        fake.balances.put(new BalanceKey(bob.asPlayer().getUniqueId(), "money"), 250.0);

        run(balanceNode(context(profiles)), alice.asPlayer(), "bOb");

        assertEquals(List.of("Bob có $250.00"), alice.plain());
    }

    @Test
    void balanceReportsAPlayerNobodyHasHeardOf() {
        var alice = alice();

        run(balanceNode(context()), alice.asPlayer(), "Nobody");

        assertEquals(List.of("Không tìm thấy người chơi Nobody."), alice.plain());
    }

    @Test
    void balanceRefusesAnotherPlayerWhenTheFileSaysSo() {
        boot("schema-version: 1\nbalance:\n  allow-other-players: false\n");
        var alice = alice();

        run(balanceNode(context()), alice.asPlayer(), "Bob");

        assertTrue(alice.plain().get(0).contains("không có quyền"));
    }

    @Test
    void balanceExplainsAMissingProviderInsteadOfPrintingNothing() {
        var alice = alice();

        run(balanceNode(context(null, withoutEconomy())), alice.asPlayer());

        assertEquals(List.of("Chưa nạp hệ kinh tế, không đọc được số dư."), alice.plain());
    }

    @Test
    void aCurrencyTheProviderDoesNotKnowIsNamedNotReadAsZero() {
        // config.yml maps money onto a currency id ExcellentEconomy never had.
        var service = EconomyFixture.service(fake, new SealCoreConfig.Economy("credits", "shards", "coins", "money", false));
        var alice = alice();

        run(balanceNode(context(null, service)), alice.asPlayer());

        assertEquals(List.of("Không biết loại tiền: money."), alice.plain());
    }

    @Test
    void theConsoleIsToldWhatToType() {
        var console = TestFixtures.recordingSender("Console", EVERYONE);

        run(balanceNode(context()), console.asSender());

        assertEquals(List.of("Cách dùng: /balance [player]"), console.plain());
    }

    // --- /pay ----------------------------------------------------------------

    @Test
    void payTellsBothSidesAndMovesTheMoney() {
        var ctx = context();
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 1_000.0);

        run(payNode(ctx), alice.asPlayer(), "Bob", "250");

        assertEquals(List.of("Bạn đã gửi $250.00 tới Bob"), alice.plain());
        assertEquals(List.of("Đã gửi $250.00 tới Bob"), alice.plainActionbars());
        assertEquals(List.of("Alice đã gửi $250.00 tới bạn"), bob.plain());
        assertEquals(List.of("Aliceđã gửi $250.00"), bob.plainActionbars());
        assertEquals(750.0, fake.balances.get(new BalanceKey(alice.asPlayer().getUniqueId(), "money")));
        assertEquals(250.0, fake.balances.get(new BalanceKey(bob.asPlayer().getUniqueId(), "money")));
    }

    @Test
    void payReadsAShorthandAmount() {
        var ctx = context();
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 10_000.0);

        run(payNode(ctx), alice.asPlayer(), "Bob", "2.5k");

        assertEquals(List.of("Bạn đã gửi $2.50K tới Bob"), alice.plain());
        assertEquals(7_500.0, fake.balances.get(new BalanceKey(alice.asPlayer().getUniqueId(), "money")));
    }

    @Test
    void payReachesAPlayerWhoHasJoinedBeforeEvenOffline() {
        var profiles = profiles();
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        profiles.upsert(bob.asPlayer().getUniqueId(), "Bob", 1_000L);
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 500.0);

        run(payNode(context(profiles)), alice.asPlayer(), "Bob", "100");

        assertEquals(List.of("Bạn đã gửi $100.00 tới Bob"), alice.plain());
        assertTrue(bob.messages.isEmpty(), "an offline receiver cannot be told anything");
        assertEquals(100.0, fake.balances.get(new BalanceKey(bob.asPlayer().getUniqueId(), "money")));
    }

    @Test
    void payRefusesToPayYourself() {
        var alice = alice();

        run(payNode(context()), alice.asPlayer(), "alice", "10");

        assertEquals(List.of("Bạn không thể gửi tiền cho chính mình."), alice.plain());
    }

    @Test
    void payReportsABalanceThatIsTooSmall() {
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 10.0);

        run(payNode(context()), alice.asPlayer(), "Bob", "100");

        assertEquals(List.of("Bạn cần $100.00 nhưng chỉ có $10.00."), alice.plain());
    }

    @Test
    void payReportsAnAmountOutsideTheConfiguredRange() {
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());

        run(payNode(context()), alice.asPlayer(), "Bob", "0.5");

        assertEquals(List.of("Số tiền phải từ $1.00 đến $1.00B."), alice.plain());
    }

    @Test
    void payReportsAnAmountThatIsNotANumber() {
        var alice = alice();

        run(payNode(context()), alice.asPlayer(), "Bob", "abc");

        assertEquals(List.of("Số tiền không hợp lệ."), alice.plain());
    }

    @Test
    void payReportsAReceiverNobodyHasHeardOf() {
        var alice = alice();

        run(payNode(context()), alice.asPlayer(), "Nobody", "100");

        assertEquals(List.of("Không tìm thấy người chơi Nobody."), alice.plain());
    }

    @Test
    void payExplainsAMissingProviderInsteadOfFailing() {
        var alice = alice();

        run(payNode(context(null, withoutEconomy())), alice.asPlayer(), "Bob", "10");

        assertEquals(List.of("Chưa nạp hệ kinh tế, không đọc được số dư."), alice.plain());
    }

    @Test
    void theActionbarCanBeSwitchedOff() {
        boot("schema-version: 1\npay:\n  actionbar: false\n");
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 100.0);

        run(payNode(context()), alice.asPlayer(), "Bob", "10");

        assertTrue(alice.actionbars.isEmpty());
        assertTrue(bob.actionbars.isEmpty());
        assertEquals(List.of("Bạn đã gửi $10.00 tới Bob"), alice.plain());
    }

    @Test
    void aSecondPayInsideTheCooldownIsTurnedAway() {
        boot("schema-version: 1\npay:\n  cooldown-seconds: 3.0\n");
        var ctx = context();
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 100.0);
        var node = payNode(ctx);

        run(node, alice.asPlayer(), "Bob", "10");
        run(node, alice.asPlayer(), "Bob", "10");

        assertEquals("Bạn đã gửi $10.00 tới Bob", alice.plain().get(0));
        assertEquals("Chờ 3 giây rồi hãy gửi tiếp.", alice.plain().get(alice.plain().size() - 1));
        assertEquals(90.0, fake.balances.get(new BalanceKey(alice.asPlayer().getUniqueId(), "money")));
    }

    @Test
    void theBypassPermissionIgnoresTheAmountRange() {
        var permissions = new java.util.LinkedHashSet<>(EVERYONE);
        permissions.add("sealcore.economy.bypass");
        var alice = alice(permissions);
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());
        fake.balances.put(new BalanceKey(alice.asPlayer().getUniqueId(), "money"), 5.0);

        // 0.5 is below the minimum, which only the bypass may send.
        run(payNode(context()), alice.asPlayer(), "Bob", "0.5");

        assertEquals(List.of("Bạn đã gửi $0.50 tới Bob"), alice.plain());
    }

    @Test
    void aSenderWithoutThePermissionNeverReachesTheHandler() {
        var alice = TestFixtures.recordingPlayer("Alice");

        run(payNode(context()), alice.asPlayer(), "Bob", "10");

        assertEquals(List.of("You do not have permission."), alice.plain());
    }

    @Test
    void theAmountRangeFollowsTheConfiguredFormatter() {
        boot("""
            schema-version: 1
            pay:
              min-amount: 1000.0
              max-amount: 1000000.0
            format:
              symbol: 'đ'
            """);
        var alice = alice();
        var bob = TestFixtures.recordingPlayer("Bob");
        online.put("Bob", bob.asPlayer());

        run(payNode(context()), alice.asPlayer(), "Bob", "0.5");

        assertEquals(List.of("Số tiền phải từ đ1.00K đến đ1.00M."), alice.plain());
    }

    private static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
            // A leftover temp folder is not worth failing a passing test over.
        }
    }
}
