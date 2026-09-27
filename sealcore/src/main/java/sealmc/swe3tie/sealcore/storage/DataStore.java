package sealmc.swe3tie.sealcore.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generic per-player JSON document store.
 *
 * <p>Feature modules that do not justify a dedicated table yet store their
 * state here under a type key. Everything is a single JSON string so the same
 * row works on SQLite and MySQL without dialect specific columns.
 */
public final class DataStore {

    private final Database database;
    private final Gson gson = new Gson();

    public DataStore(Database database) {
        this.database = database;
    }

    public void put(UUID uuid, String type, Object value) {
        String payload = gson.toJson(value);
        String sql = """
            INSERT INTO sealcore_data (uuid, type, payload, updated_at)
            VALUES (?, ?, ?, ?)
            %s
            """.formatted(database.dialect().upsert(List.of("uuid", "type"), List.of("payload", "updated_at")));
        database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type);
                statement.setString(3, payload);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public <T> T get(UUID uuid, String type, Class<T> target) {
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                "SELECT payload FROM sealcore_data WHERE uuid = ? AND type = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type);
                try (var results = statement.executeQuery()) {
                    if (!results.next()) {
                        return null;
                    }
                    return gson.fromJson(results.getString(1), target);
                }
            }
        });
    }

    public <T> T get(UUID uuid, String type, TypeToken<T> typeToken) {
        return database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                "SELECT payload FROM sealcore_data WHERE uuid = ? AND type = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type);
                try (var results = statement.executeQuery()) {
                    if (!results.next()) {
                        return null;
                    }
                    return gson.fromJson(results.getString(1), typeToken.getType());
                }
            }
        });
    }

    public void remove(UUID uuid, String type) {
        database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                "DELETE FROM sealcore_data WHERE uuid = ? AND type = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public Set<String> types(UUID uuid) {
        return database.withConnection(connection -> {
            Set<String> types = new LinkedHashSet<>();
            try (var statement = connection.prepareStatement("SELECT type FROM sealcore_data WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (var results = statement.executeQuery()) {
                    while (results.next()) {
                        types.add(results.getString(1));
                    }
                }
            }
            return types;
        });
    }
}
