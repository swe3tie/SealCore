package sealmc.swe3tie.sealcore.storage;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Profile rows for players who have joined while SealCore was running. */
public final class PlayerRepository {

    private static final List<String> COLUMNS = List.of("uuid", "name", "first_seen", "last_seen");

    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID uuid, String name, long now) {
        SqlDialect dialect = database.dialect();
        String sql = """
            INSERT INTO sealcore_players (uuid, name, name_lower, first_seen, last_seen)
            VALUES (?, ?, ?, ?, ?)
            %s
            """.formatted(dialect.upsert(List.of("uuid"), List.of("name", "name_lower", "first_seen", "last_seen")));
        database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setString(3, name.toLowerCase(Locale.ROOT));
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public PlayerRecord find(UUID uuid) {
        String sql = "SELECT " + String.join(", ", COLUMNS) + " FROM sealcore_players WHERE uuid = ?";
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (var results = statement.executeQuery()) {
                    if (!results.next()) {
                        return null;
                    }
                    return read(results);
                }
            }
        });
    }

    /**
     * Finds a player by name, case insensitively.
     *
     * <p>This is what lets a command resolve a name the server has never seen
     * online without asking Mojang: the row only exists because the player
     * joined at some point. A rename moves the row, so the old name stops
     * resolving rather than pointing at whoever took the name next.
     */
    public PlayerRecord findByName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String sql = """
            SELECT %s FROM sealcore_players
            WHERE name_lower = ?
            ORDER BY last_seen DESC
            LIMIT 1
            """.formatted(String.join(", ", COLUMNS));
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, trimmed.toLowerCase(Locale.ROOT));
                try (var results = statement.executeQuery()) {
                    if (!results.next()) {
                        return null;
                    }
                    return read(results);
                }
            }
        });
    }

    public long count() {
        return database.withConnection((Connection connection) -> {
            try (var statement = connection.createStatement();
                 var results = statement.executeQuery("SELECT COUNT(*) FROM sealcore_players")) {
                return results.next() ? results.getLong(1) : 0L;
            }
        });
    }

    private static PlayerRecord read(java.sql.ResultSet results) throws java.sql.SQLException {
        return new PlayerRecord(
            UUID.fromString(results.getString("uuid")),
            results.getString("name"),
            results.getLong("first_seen"),
            results.getLong("last_seen"));
    }
}
