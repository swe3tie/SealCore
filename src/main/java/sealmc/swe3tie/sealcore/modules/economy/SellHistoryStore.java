package sealmc.swe3tie.sealcore.modules.economy;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sealmc.swe3tie.sealcore.core.storage.StorageService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Persistent storage for item sell history. */
public final class SellHistoryStore {

    public record RecordItem(
        @NotNull Material material,
        @Nullable String customName,
        @Nullable String potionType,
        @Nullable SellCategory category,
        int amount,
        double totalPrice,
        long soldAt
    ) {
        public RecordItem(@NotNull Material material, @Nullable String customName,
                          @Nullable SellCategory category, int amount, double totalPrice, long soldAt) {
            this(material, customName, null, category, amount, totalPrice, soldAt);
        }
    }

    private final StorageService storage;
    private final ConcurrentMap<UUID, List<SellHistoryEntry>> cache = new ConcurrentHashMap<>();

    public SellHistoryStore(@NotNull StorageService storage) {
        this.storage = storage;
    }

    @NotNull
    public CompletableFuture<Void> record(@NotNull UUID playerId, @NotNull List<RecordItem> items) {
        if (items.isEmpty()) return CompletableFuture.completedFuture(null);

        return this.storage.execute(connection -> {
            String sql = "INSERT INTO " + this.storage.table("economy_sell_history")
                + " (player_uuid, material, custom_name, potion_type, category, amount, total_price, sold_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (RecordItem item : items) {
                    ps.setString(1, playerId.toString());
                    ps.setString(2, item.material().name());
                    ps.setString(3, item.customName());
                    ps.setString(4, item.potionType());
                    ps.setString(5, item.category() != null ? item.category().id() : null);
                    ps.setInt(6, item.amount());
                    ps.setDouble(7, item.totalPrice());
                    ps.setLong(8, item.soldAt());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            this.cache.remove(playerId);
        });
    }

    @NotNull
    public CompletableFuture<List<SellHistoryEntry>> load(@NotNull UUID playerId) {
        List<SellHistoryEntry> cached = this.cache.get(playerId);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return this.storage.query(connection -> {
            String sql = "SELECT id, material, custom_name, potion_type, category, amount, total_price, sold_at FROM "
                + this.storage.table("economy_sell_history")
                + " WHERE player_uuid = ? ORDER BY sold_at DESC, id DESC LIMIT 500";
            List<SellHistoryEntry> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String matName = rs.getString("material");
                        Material material = Material.matchMaterial(matName);
                        if (material == null) material = Material.STONE;
                        String customName = rs.getString("custom_name");
                        String potionType = rs.getString("potion_type");
                        String catName = rs.getString("category");
                        SellCategory category = catName != null ? SellCategory.fromId(catName) : null;
                        int amount = rs.getInt("amount");
                        double totalPrice = rs.getDouble("total_price");
                        long soldAt = rs.getLong("sold_at");

                        list.add(new SellHistoryEntry(id, playerId, material, customName, potionType, category, amount, totalPrice, soldAt));
                    }
                }
            }
            this.cache.put(playerId, list);
            return list;
        });
    }

    public void invalidate(@NotNull UUID playerId) {
        this.cache.remove(playerId);
    }

    public void clear() {
        this.cache.clear();
    }
}
