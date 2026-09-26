package sealmc.swe3tie.sealcore.config

import org.bukkit.configuration.file.FileConfiguration
import java.time.Duration

/**
 * Typed view over `config.yml`.
 *
 * Every value has a default, so a partial or missing file still yields a usable
 * configuration. Unknown keys are left alone on save so hand-written additions
 * survive a reload.
 */
data class SealCoreConfig(
    /** Language file name under `languages/`, for example `en.yml`. */
    val lang: String = "en.yml",
    val general: General = General(),
    val economy: Economy = Economy(),
    val storage: Storage = Storage(),
    val gui: Gui = Gui(),
    val hud: Hud = Hud(),
    val debug: Boolean = false,
) {

    data class General(
        /** Interval of the framework tick, in ticks. Drives HUD and GUI refreshes. */
        val refreshIntervalTicks: Long = 20L,
    )

    /**
     * Maps SealCore currency keys onto ExcellentEconomy currency ids, so the
     * rest of the plugin never hardcodes a provider specific id.
     */
    data class Economy(
        val money: String = "money",
        val shards: String = "shards",
        val coins: String = "coins",
        val defaultCurrency: String = "money",
        /** Fail loudly when ExcellentEconomy is missing instead of degrading. */
        val required: Boolean = false,
    ) {
        fun currencyId(key: String): String = when (key.lowercase()) {
            "money" -> money
            "shards" -> shards
            "coins" -> coins
            else -> key
        }
    }

    data class Storage(
        val type: Type = Type.SQLITE,
        val fileName: String = "sealcore.db",
        val host: String = "localhost",
        val port: Int = 3306,
        val database: String = "sealcore",
        val username: String = "root",
        val password: String = "",
        val useSsl: Boolean = false,
        val poolSize: Int = 10,
        val connectionTimeoutMillis: Long = 10_000L,
    ) {
        enum class Type { SQLITE, MYSQL }
    }

    data class Gui(
        val enabled: Boolean = true,
        val rows: Int = 6,
        val animationTicks: Long = 0L,
        /** Click handling runs async; the element action is dispatched back to the player thread. */
        val asyncClickHandling: Boolean = true,
    )

    data class Hud(
        val enabled: Boolean = true,
        val updateIntervalTicks: Long = 20L,
        val sidebar: Sidebar = Sidebar(),
        val bossBar: BossBar = BossBar(),
    ) {
        data class Sidebar(val enabled: Boolean = false, val title: String = "<gradient:#00c6ff:#0072ff><bold>SealCore</bold></gradient>")

        data class BossBar(val enabled: Boolean = false, val title: String = "<white>Welcome back!")
    }

    val refreshInterval: Duration get() = Duration.ofMillis(general.refreshIntervalTicks * 50L)

    companion object {
        fun from(raw: FileConfiguration): SealCoreConfig {
            val generalRaw = raw.getConfigurationSection("general")
            val economyRaw = raw.getConfigurationSection("economy")
            val storageRaw = raw.getConfigurationSection("storage")
            val guiRaw = raw.getConfigurationSection("gui")
            val hudRaw = raw.getConfigurationSection("hud")

            return SealCoreConfig(
                lang = raw.getString("lang", "en.yml")?.trim()?.takeIf { it.isNotEmpty() } ?: "en.yml",
                general = General(
                    refreshIntervalTicks = generalRaw?.getLong("refresh-interval-ticks", 20L) ?: 20L,
                ),
                economy = Economy(
                    money = economyRaw?.getString("currencies.money") ?: "money",
                    shards = economyRaw?.getString("currencies.shards") ?: "shards",
                    coins = economyRaw?.getString("currencies.coins") ?: "coins",
                    defaultCurrency = economyRaw?.getString("default-currency") ?: "money",
                    required = economyRaw?.getBoolean("required", false) ?: false,
                ),
                storage = Storage(
                    type = runCatching {
                        Storage.Type.valueOf((storageRaw?.getString("type") ?: "sqlite").uppercase())
                    }.getOrDefault(Storage.Type.SQLITE),
                    fileName = storageRaw?.getString("file-name") ?: "sealcore.db",
                    host = storageRaw?.getString("host") ?: "localhost",
                    port = storageRaw?.getInt("port", 3306) ?: 3306,
                    database = storageRaw?.getString("database") ?: "sealcore",
                    username = storageRaw?.getString("username") ?: "root",
                    password = storageRaw?.getString("password") ?: "",
                    useSsl = storageRaw?.getBoolean("use-ssl", false) ?: false,
                    poolSize = storageRaw?.getInt("pool-size", 10) ?: 10,
                    connectionTimeoutMillis = storageRaw?.getLong("connection-timeout-ms", 10_000L) ?: 10_000L,
                ),
                gui = Gui(
                    enabled = guiRaw?.getBoolean("enabled", true) ?: true,
                    rows = (guiRaw?.getInt("rows", 6) ?: 6).coerceIn(1, 6),
                    animationTicks = guiRaw?.getLong("animation-ticks", 0L) ?: 0L,
                    asyncClickHandling = guiRaw?.getBoolean("async-click-handling", true) ?: true,
                ),
                hud = Hud(
                    enabled = hudRaw?.getBoolean("enabled", true) ?: true,
                    updateIntervalTicks = hudRaw?.getLong("update-interval-ticks", 20L) ?: 20L,
                    sidebar = Hud.Sidebar(
                        enabled = hudRaw?.getBoolean("sidebar.enabled", false) ?: false,
                        title = hudRaw?.getString("sidebar.title") ?: Hud.Sidebar().title,
                    ),
                    bossBar = Hud.BossBar(
                        enabled = hudRaw?.getBoolean("bossbar.enabled", false) ?: false,
                        title = hudRaw?.getString("bossbar.title") ?: Hud.BossBar().title,
                    ),
                ),
                debug = raw.getBoolean("debug", false),
            )
        }
    }
}
