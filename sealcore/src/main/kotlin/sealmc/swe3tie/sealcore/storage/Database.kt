package sealmc.swe3tie.sealcore.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import sealmc.swe3tie.sealcore.config.SealCoreConfig
import java.io.File
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Owns the connection pool and hands out connections.
 *
 * Callers always work through [withConnection]; nothing outside this class
 * touches JDBC directly. The driver classes are declared in `plugin.yml` and
 * downloaded by the server at boot, so they are never shaded into the jar.
 */
class Database(
    private val config: SealCoreConfig.Storage,
    private val dataFolder: File,
    private val logger: Logger,
) : AutoCloseable {

    val dialect: SqlDialect = SqlDialect.of(config.type.name)

    private var dataSource: HikariDataSource? = null

    val isOpen: Boolean get() = dataSource?.isClosed == false

    fun open() {
        if (isOpen) return

        val hikari = HikariConfig()
        hikari.poolName = "SealCore-Pool"
        hikari.connectionTimeout = config.connectionTimeoutMillis
        hikari.isAutoCommit = true

        when (dialect) {
            SqlDialect.SQLITE -> {
                val file = File(dataFolder, config.fileName)
                file.parentFile?.mkdirs()
                hikari.driverClassName = "org.sqlite.JDBC"
                hikari.jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
                // SQLite serialises writers; a larger pool only turns lock
                // contention into SQLITE_BUSY errors.
                hikari.maximumPoolSize = 1
                hikari.connectionInitSql = "PRAGMA journal_mode=WAL"
            }

            SqlDialect.MYSQL -> {
                hikari.driverClassName = "com.mysql.cj.jdbc.Driver"
                hikari.jdbcUrl = buildString {
                    append("jdbc:mysql://").append(config.host).append(':').append(config.port)
                    append('/').append(config.database)
                    append("?useSSL=").append(config.useSsl)
                    append("&serverTimezone=UTC")
                    append("&characterEncoding=utf8")
                    append("&allowPublicKeyRetrieval=true")
                }
                hikari.username = config.username
                hikari.password = config.password
                hikari.maximumPoolSize = config.poolSize
            }
        }

        dataSource = HikariDataSource(hikari)
        logger.info("Storage ready: ${dialect.id} (${if (dialect == SqlDialect.SQLITE) config.fileName else "${config.host}/${config.database}"})")
    }

    fun dataSource(): DataSource =
        dataSource ?: error("Database is not open")

    fun <T> withConnection(block: (Connection) -> T): T = dataSource().connection.use(block)

    override fun close() {
        dataSource?.close()
        dataSource = null
    }
}
