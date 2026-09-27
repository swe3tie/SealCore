package sealmc.swe3tie.sealcore.storage;

import java.util.List;

/** The ordered schema history. Append only; never edit a shipped migration. */
public final class StorageMigrations {

    private StorageMigrations() {
    }

    public static List<Migration> all(SqlDialect dialect) {
        return List.of(
            new Migration(1, "players-and-data", List.of("""
                CREATE TABLE IF NOT EXISTS sealcore_players (
                    uuid %s NOT NULL,
                    name %s NOT NULL,
                    first_seen %s NOT NULL,
                    last_seen %s NOT NULL,
                    PRIMARY KEY (uuid)
                )
                """.formatted(dialect.uuidType(), dialect.textType(), dialect.timestampType(), dialect.timestampType()),
                """
                CREATE TABLE IF NOT EXISTS sealcore_data (
                    uuid %s NOT NULL,
                    type %s NOT NULL,
                    payload %s NOT NULL,
                    updated_at %s NOT NULL,
                    PRIMARY KEY (uuid, type)
                )
                """.formatted(dialect.uuidType(), dialect.textType(), dialect.textType(), dialect.timestampType()))),
            new Migration(2, "player-name-index", List.of(
                // Names are looked up case insensitively, and MySQL's default
                // collation cannot be relied on across hosts, so the folded name
                // is stored next to the name itself.
                "ALTER TABLE sealcore_players ADD COLUMN name_lower " + dialect.textType() + " NOT NULL DEFAULT ''",
                "UPDATE sealcore_players SET name_lower = LOWER(name)",
                dialect.createIndex("idx_sealcore_players_name_lower", "sealcore_players", List.of("name_lower")))));
    }
}
