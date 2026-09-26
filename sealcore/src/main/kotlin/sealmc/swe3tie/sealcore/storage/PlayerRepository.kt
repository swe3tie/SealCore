package sealmc.swe3tie.sealcore.storage

import java.sql.Connection
import java.util.UUID

data class PlayerRecord(
    val uuid: UUID,
    val name: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

/** Profile rows for players who have joined while SealCore was running. */
class PlayerRepository(private val database: Database) {

    fun upsert(uuid: UUID, name: String, now: Long) {
        val dialect = database.dialect
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO sealcore_players (uuid, name, first_seen, last_seen)
                VALUES (?, ?, ?, ?)
                ${dialect.upsert(listOf("uuid"), listOf("name", "first_seen", "last_seen"))}
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, name)
                statement.setLong(3, now)
                statement.setLong(4, now)
                statement.executeUpdate()
            }
        }
    }

    fun find(uuid: UUID): PlayerRecord? = database.withConnection { connection ->
        connection.prepareStatement("SELECT uuid, name, first_seen, last_seen FROM sealcore_players WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { results ->
                if (!results.next()) return@withConnection null
                PlayerRecord(
                    uuid = UUID.fromString(results.getString("uuid")),
                    name = results.getString("name"),
                    firstSeen = results.getLong("first_seen"),
                    lastSeen = results.getLong("last_seen"),
                )
            }
        }
    }

    fun count(): Long = database.withConnection { connection: Connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM sealcore_players").use { results ->
                if (results.next()) results.getLong(1) else 0L
            }
        }
    }
}
