package sealmc.swe3tie.sealcore.modules.economy;

import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Persistent, serialized per-player category sell progress. */
public final class ProgressStore {

    private final StorageService storage;
    private final ConcurrentMap<UUID, EnumMap<SellCategory, Double>> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Void>> writes = new ConcurrentHashMap<>();

    public ProgressStore(@NotNull StorageService storage) {
        this.storage = storage;
    }

    @NotNull
    public CompletableFuture<Map<SellCategory, Double>> load(@NotNull UUID playerId) {
        EnumMap<SellCategory, Double> existing = this.cache.get(playerId);
        if (existing != null) return CompletableFuture.completedFuture(copy(existing));

        CompletableFuture<Void> previous = this.writes.getOrDefault(playerId, CompletableFuture.completedFuture(null));
        return previous.handle((ignored, error) -> null).thenCompose(ignored -> {
            EnumMap<SellCategory, Double> loaded = this.cache.get(playerId);
            if (loaded != null) return CompletableFuture.completedFuture(copy(loaded));
            return this.storage.query(connection -> {
                EnumMap<SellCategory, Double> values = empty();
                String sql = "SELECT category, paid FROM " + this.storage.table("economy_sell_progress")
                    + " WHERE player_uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerId.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            SellCategory category = SellCategory.fromId(rs.getString("category"));
                            if (category != null) values.put(category, Math.max(0D, rs.getDouble("paid")));
                        }
                    }
                }
                this.cache.putIfAbsent(playerId, values);
                return copy(this.cache.get(playerId));
            });
        });
    }

    @NotNull
    public CompletableFuture<Map<SellCategory, Double>> add(@NotNull UUID playerId, @NotNull SellCategory category,
                                                              double amount) {
        if (!(amount > 0D) || !Double.isFinite(amount)) return this.load(playerId);
        CompletableFuture<Void> previous = this.writes.getOrDefault(playerId, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> next = previous.handle((ignored, error) -> null).thenCompose(ignored ->
            this.load(playerId).thenCompose(values -> {
                double paid = values.getOrDefault(category, 0D) + amount;
                this.cache.compute(playerId, (id, current) -> {
                    EnumMap<SellCategory, Double> updated = current == null ? empty() : current;
                    updated.put(category, paid);
                    return updated;
                });
                return this.storage.execute(connection -> upsert(connection, playerId, category, paid));
            })
        );
        this.writes.put(playerId, next);
        next.whenComplete((ignored, error) -> this.writes.remove(playerId, next));
        return next.thenApply(ignored -> copy(this.cache.get(playerId)));
    }

    public void clear() {
        this.cache.clear();
        this.writes.clear();
    }

    private void upsert(java.sql.Connection connection, UUID playerId, SellCategory category, double paid)
        throws SQLException {
        String table = this.storage.table("economy_sell_progress");
        String sql;
        if (this.storage.dialect() == StorageService.Dialect.MYSQL) {
            sql = "INSERT INTO " + table + " (player_uuid, category, paid) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE paid = VALUES(paid)";
        } else {
            sql = "INSERT INTO " + table + " (player_uuid, category, paid) VALUES (?, ?, ?) "
                + "ON CONFLICT(player_uuid, category) DO UPDATE SET paid = excluded.paid";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, category.id());
            ps.setDouble(3, paid);
            ps.executeUpdate();
        }
    }

    private static EnumMap<SellCategory, Double> empty() {
        EnumMap<SellCategory, Double> map = new EnumMap<>(SellCategory.class);
        for (SellCategory category : SellCategory.values()) map.put(category, 0D);
        return map;
    }

    private static Map<SellCategory, Double> copy(Map<SellCategory, Double> source) {
        return Map.copyOf(source == null ? empty() : source);
    }
}
