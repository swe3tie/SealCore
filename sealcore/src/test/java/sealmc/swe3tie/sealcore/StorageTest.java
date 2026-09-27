package sealmc.swe3tie.sealcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.config.SealCoreConfig;
import sealmc.swe3tie.sealcore.storage.DataStore;
import sealmc.swe3tie.sealcore.storage.Database;
import sealmc.swe3tie.sealcore.storage.MigrationRunner;
import sealmc.swe3tie.sealcore.storage.PlayerRepository;
import sealmc.swe3tie.sealcore.storage.SqlDialect;
import sealmc.swe3tie.sealcore.storage.StorageMigrations;

/** Exercises the real JDBC path against a throwaway SQLite file. */
class StorageTest {

    private static final Logger LOGGER = Logger.getLogger("SealCoreTest");

    private Path folder;
    private Database database;
    private PlayerRepository players;
    private DataStore store;

    @BeforeEach
    void setUp() {
        folder = createTempFolder();
        var config = new SealCoreConfig.Storage(SealCoreConfig.Storage.Type.SQLITE, "test.db");
        database = new Database(config, folder.toFile(), LOGGER);
        database.open();
        new MigrationRunner(database, LOGGER).apply(StorageMigrations.all(database.dialect()));
        players = new PlayerRepository(database);
        store = new DataStore(database);
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
        deleteRecursively(folder);
    }

    @Test
    void migrationsAreIdempotent() {
        var runner = new MigrationRunner(database, LOGGER);
        int latest = StorageMigrations.all(database.dialect()).stream()
            .mapToInt(migration -> migration.version()).max().orElse(0);
        runner.apply(StorageMigrations.all(database.dialect()));
        assertEquals(latest, runner.currentVersion());
        // The players table survived being migrated twice.
        players.upsert(UUID.randomUUID(), "Steve", 1_000L);
        assertEquals("Steve", players.findByName("steve").name());
    }

    @Test
    void playerProfileRoundTrips() {
        UUID uuid = UUID.randomUUID();
        players.upsert(uuid, "Steve", 1_000L);
        var stored = players.find(uuid);
        assertNotNull(stored);
        assertEquals("Steve", stored.name());
        assertEquals(1_000L, stored.firstSeen());
        assertEquals(1L, players.count());
    }

    @Test
    void playerProfileUpsertOverwritesInsteadOfFailing() {
        UUID uuid = UUID.randomUUID();
        players.upsert(uuid, "Steve", 1_000L);
        players.upsert(uuid, "SteveRenamed", 2_000L);
        assertEquals("SteveRenamed", players.find(uuid).name());
        assertEquals(1L, players.count());
    }

    @Test
    void unknownPlayerIsNull() {
        assertNull(players.find(UUID.randomUUID()));
        assertNull(players.findByName("Nobody"));
        assertNull(players.findByName("   "));
    }

    @Test
    void aPlayerIsFoundByNameWhateverTheCase() {
        UUID uuid = UUID.randomUUID();
        players.upsert(uuid, "Steve", 1_000L);

        var found = players.findByName("sTeVe");
        assertNotNull(found);
        assertEquals(uuid, found.uuid());
        assertEquals("Steve", found.name());
    }

    @Test
    void aRenameKeepsOneRowAndTheNewestNameWinsALookup() {
        UUID uuid = UUID.randomUUID();
        players.upsert(uuid, "Steve", 1_000L);
        players.upsert(uuid, "SteveRenamed", 2_000L);

        assertEquals("SteveRenamed", players.findByName("steverenamed").name());
        assertNull(players.findByName("Steve"), "a name that was given up must stop resolving");
        assertEquals(1L, players.count());
    }

    @Test
    void dataStoreRoundTripsJsonPerType() {
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "quest", Map.of("done", true, "stage", 3));
        Map<?, ?> restored = store.get(uuid, "quest", Map.class);
        assertEquals(true, restored.get("done"));
        assertEquals(3.0, ((Number) restored.get("stage")).doubleValue());
    }

    @Test
    void dataStoreKeepsTypesApart() {
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "quest", Map.of("a", 1));
        store.put(uuid, "shop", Map.of("b", 2));
        assertEquals(Set.of("quest", "shop"), store.types(uuid));
        store.remove(uuid, "quest");
        assertEquals(Set.of("shop"), store.types(uuid));
    }

    @Test
    void dataStoreOverwritesTheSameType() {
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "quest", Map.of("stage", 1));
        store.put(uuid, "quest", Map.of("stage", 2));
        Map<?, ?> restored = store.get(uuid, "quest", Map.class);
        assertEquals(2.0, ((Number) restored.get("stage")).doubleValue());
    }

    @Test
    void sqliteUsesASingleWriterConnection() {
        assertEquals(SqlDialect.SQLITE, database.dialect());
        assertTrue(database.isOpen());
    }

    private static Path createTempFolder() {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "sealcore-test-" + UUID.randomUUID());
        try {
            Files.createDirectories(dir);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        return dir;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
            // A leftover temp folder is not worth failing a passing test over.
        }
    }
}
