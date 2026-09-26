package sealmc.swe3tie.sealcore.storage

import sealmc.swe3tie.sealcore.storage.SqlDialect

/** The ordered schema history. Append only; never edit a shipped migration. */
object StorageMigrations {

    fun all(dialect: SqlDialect): List<Migration> = listOf(
        Migration(
            version = 1,
            name = "players-and-data",
            statements = listOf(
                """
                CREATE TABLE IF NOT EXISTS sealcore_players (
                    uuid ${dialect.uuidType()} NOT NULL,
                    name ${dialect.textType()} NOT NULL,
                    first_seen ${dialect.timestampType()} NOT NULL,
                    last_seen ${dialect.timestampType()} NOT NULL,
                    PRIMARY KEY (uuid)
                )
                """.trimIndent(),
                """
                CREATE TABLE IF NOT EXISTS sealcore_data (
                    uuid ${dialect.uuidType()} NOT NULL,
                    type ${dialect.textType()} NOT NULL,
                    payload ${dialect.textType()} NOT NULL,
                    updated_at ${dialect.timestampType()} NOT NULL,
                    PRIMARY KEY (uuid, type)
                )
                """.trimIndent(),
            ),
        ),
    )
}
