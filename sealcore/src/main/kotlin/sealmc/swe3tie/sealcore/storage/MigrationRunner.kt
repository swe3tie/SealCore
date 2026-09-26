package sealmc.swe3tie.sealcore.storage

import java.sql.Connection
import java.util.logging.Logger

/** One schema change, identified by [version]. Versions must never be reused. */
data class Migration(
    val version: Int,
    val name: String,
    val statements: List<String>,
)

/**
 * Applies pending migrations and records them in `sealcore_meta`.
 *
 * Each migration runs inside its own transaction, so a failure leaves the
 * schema at the last good version instead of half applied.
 */
class MigrationRunner(
    private val database: Database,
    private val logger: Logger,
) {

    fun apply(migrations: List<Migration>) {
        database.withConnection { connection ->
            createMetaTable(connection)
            val applied = readAppliedVersions(connection)
            val ordered = migrations.sortedBy { it.version }
            val duplicates = ordered.groupBy { it.version }.filterValues { it.size > 1 }
            require(duplicates.isEmpty()) {
                "Duplicate migration versions: ${duplicates.keys.sorted()}"
            }

            var count = 0
            for (migration in ordered) {
                if (migration.version in applied) continue
                runMigration(connection, migration)
                count++
            }
            if (count > 0) {
                logger.info("Applied $count migration(s); schema is at version ${ordered.last().version}.")
            } else {
                logger.info("Storage schema is up to date (version ${applied.maxOrNull() ?: 0}).")
            }
        }
    }

    fun currentVersion(): Int = database.withConnection { connection ->
        createMetaTable(connection)
        readAppliedVersions(connection).maxOrNull() ?: 0
    }

    private fun createMetaTable(connection: Connection) {
        val dialect = database.dialect
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sealcore_meta (
                    key ${dialect.textType()} NOT NULL,
                    value ${dialect.textType()} NOT NULL,
                    PRIMARY KEY (key)
                )
                """.trimIndent(),
            )
        }
    }

    private fun readAppliedVersions(connection: Connection): Set<Int> = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT value FROM sealcore_meta WHERE key = 'schema_version'").use { results ->
            buildSet {
                while (results.next()) {
                    results.getString(1).toIntOrNull()?.let { add(it) }
                }
            }
        }
    }

    private fun runMigration(connection: Connection, migration: Migration) {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                for (sql in migration.statements) {
                    statement.execute(sql)
                }
            }
            connection.prepareStatement("INSERT INTO sealcore_meta (key, value) VALUES (?, ?)").use { statement ->
                statement.setString(1, "schema_version")
                statement.setString(2, migration.version.toString())
                statement.executeUpdate()
            }
            connection.commit()
            logger.info("Migration ${migration.version} (${migration.name}) applied.")
        } catch (error: Exception) {
            connection.rollback()
            throw IllegalStateException("Migration ${migration.version} (${migration.name}) failed", error)
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }
}
