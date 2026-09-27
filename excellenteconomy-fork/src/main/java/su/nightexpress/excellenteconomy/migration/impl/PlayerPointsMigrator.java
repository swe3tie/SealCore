package su.nightexpress.excellenteconomy.migration.impl;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.manager.DataManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.hook.HookPlugin;
import su.nightexpress.excellenteconomy.migration.Migrator;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerPointsMigrator extends Migrator {

    public PlayerPointsMigrator(@NotNull EconomyPlugin plugin) {
        super(plugin, HookPlugin.PLAYER_POINTS);
    }

    @Override
    public boolean canMigrate(@NotNull ExcellentCurrency currency) {
        return true;
    }

    @Override
    @NotNull
    public Map<OfflinePlayer, Double> getBalances(@NotNull ExcellentCurrency currency) {
        Map<OfflinePlayer, Double> balances = new HashMap<>();

        PlayerPoints playerPoints = (PlayerPoints) this.getBackend();
        if (playerPoints == null) return balances;

        Map<UUID, Integer> pointsMap = new HashMap<>();

        DataManager dataManager = playerPoints.getManager(DataManager.class);
        try {
            dataManager.getDatabaseConnector().connect(connection -> {
                String query = "SELECT * FROM " + dataManager.getTablePrefix() + "points";
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query);

                while (resultSet.next()) {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    int points = resultSet.getInt("points");

                    pointsMap.put(uuid, points);
                }
                statement.close();
            });
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }

        pointsMap.forEach((id, balance) -> {
            OfflinePlayer offlinePlayer = this.plugin.getServer().getOfflinePlayer(id);
            balances.put(offlinePlayer, (double) balance);
        });

        return balances;
    }
}
