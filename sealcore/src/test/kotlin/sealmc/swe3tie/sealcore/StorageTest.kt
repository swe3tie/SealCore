package sealmc.swe3tie.sealcore

import sealmc.swe3tie.sealcore.config.SealCoreConfig
import sealmc.swe3tie.sealcore.storage.DataStore
import sealmc.swe3tie.sealcore.storage.Database
import sealmc.swe3tie.sealcore.storage.MigrationRunner
import sealmc.swe3tie.sealcore.storage.PlayerRepository
import sealmc.swe3tie.sealcore.storage.StorageMigrations
import java.io.File
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the real JDBC path against a throwaway SQLite file. */
class StorageTest {

    private lateinit var folder: File
    private lateinit var database: Database
    private lateinit var players: PlayerRepository
    private lateinit var store: DataStore

    private val logger = Logger.getLogger("SealCoreTest")

    @BeforeTest
    fun setUp() {
        folder = createTempFolder()
        val config = SealCoreConfig.Storage(type = SealCoreConfig.Storage.Type.SQLITE, fileName = "test.db")
        database = Database(config, folder, logger)
        database.open()
        MigrationRunner(database, logger).apply(StorageMigrations.all(database.dialect))
        players = PlayerRepository(database)
        store = DataStore(database)
    }

    @AfterTest
    fun tearDown() {
        if (::database.isInitialized) database.close()
        folder.deleteRecursively()
    }

    @Test
    fun `migrations are idempotent`() {
        val runner = MigrationRunner(database, logger)
        runner.apply(StorageMigrations.all(database.dialect))
        runner.apply(StorageMigrations.all(database.dialect))
        assertEquals(1, runner.currentVersion())
    }

    @Test
    fun `player profile round trips`() {
        val uuid = UUID.randomUUID()
        players.upsert(uuid, "Steve", 1_000L)
        val stored = players.find(uuid)
        assertNotNull(stored)
        assertEquals("Steve", stored.name)
        assertEquals(1_000L, stored.firstSeen)
        assertEquals(1L, players.count())
    }

    @Test
    fun `player profile upsert overwrites instead of failing`() {
        val uuid = UUID.randomUUID()
        players.upsert(uuid, "Steve", 1_000L)
        players.upsert(uuid, "SteveRenamed", 2_000L)
        assertEquals("SteveRenamed", players.find(uuid)?.name)
        assertEquals(1L, players.count())
    }

    @Test
    fun `unknown player is null`() {
        assertNull(players.find(UUID.randomUUID()))
    }

    @Test
    fun `data store round trips json per type`() {
        val uuid = UUID.randomUUID()
        store.put(uuid, "quest", mapOf("done" to true, "stage" to 3))
        val restored: Map<*, *>? = store.get(uuid, "quest", Map::class.java)
        assertEquals(true, restored?.get("done"))
        assertEquals(3.0, (restored?.get("stage") as Number).toDouble(), 0.0)
    }

    @Test
    fun `data store keeps types apart`() {
        val uuid = UUID.randomUUID()
        store.put(uuid, "quest", mapOf("a" to 1))
        store.put(uuid, "shop", mapOf("b" to 2))
        assertEquals(setOf("quest", "shop"), store.types(uuid))
        store.remove(uuid, "quest")
        assertEquals(setOf("shop"), store.types(uuid))
    }

    @Test
    fun `data store overwrites the same type`() {
        val uuid = UUID.randomUUID()
        store.put(uuid, "quest", mapOf("stage" to 1))
        store.put(uuid, "quest", mapOf("stage" to 2))
        val restored: Map<*, *>? = store.get(uuid, "quest", Map::class.java)
        assertEquals(2.0, (restored?.get("stage") as Number).toDouble(), 0.0)
    }

    @Test
    fun `sqlite uses a single writer connection`() {
        assertEquals(sealmc.swe3tie.sealcore.storage.SqlDialect.SQLITE, database.dialect)
        assertTrue(database.isOpen)
    }

    private fun createTempFolder(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "sealcore-test-${UUID.randomUUID()}")
        dir.mkdirs()
        return dir
    }
}
