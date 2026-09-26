package sealmc.swe3tie.sealcore.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Generic per-player JSON document store.
 *
 * Feature modules that do not justify a dedicated table yet store their state
 * here under a [type] key. Everything is a single JSON string so the same row
 * works on SQLite and MySQL without dialect specific columns.
 */
class DataStore(private val database: Database) {

    private val gson = Gson()

    fun put(uuid: UUID, type: String, value: Any) {
        val payload = gson.toJson(value)
        val dialect = database.dialect
        database.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO sealcore_data (uuid, type, payload, updated_at)
                VALUES (?, ?, ?, ?)
                ${dialect.upsert(listOf("uuid", "type"), listOf("payload", "updated_at"))}
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, type)
                statement.setString(3, payload)
                statement.setLong(4, System.currentTimeMillis())
                statement.executeUpdate()
            }
        }
    }

    fun <T> get(uuid: UUID, type: String, target: Class<T>): T? = database.withConnection { connection ->
        connection.prepareStatement("SELECT payload FROM sealcore_data WHERE uuid = ? AND type = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.setString(2, type)
            statement.executeQuery().use { results ->
                if (!results.next()) return@withConnection null
                gson.fromJson(results.getString(1), target)
            }
        }
    }

    fun <T> get(uuid: UUID, type: String, typeToken: TypeToken<T>): T? = database.withConnection { connection ->
        connection.prepareStatement("SELECT payload FROM sealcore_data WHERE uuid = ? AND type = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.setString(2, type)
            statement.executeQuery().use { results ->
                if (!results.next()) return@withConnection null
                gson.fromJson(results.getString(1), typeToken.type)
            }
        }
    }

    fun remove(uuid: UUID, type: String) {
        database.withConnection { connection ->
            connection.prepareStatement("DELETE FROM sealcore_data WHERE uuid = ? AND type = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, type)
                statement.executeUpdate()
            }
        }
    }

    fun types(uuid: UUID): Set<String> = database.withConnection { connection ->
        connection.prepareStatement("SELECT type FROM sealcore_data WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { results ->
                buildSet { while (results.next()) add(results.getString(1)) }
            }
        }
    }
}
