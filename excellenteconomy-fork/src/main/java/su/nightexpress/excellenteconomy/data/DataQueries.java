package su.nightexpress.excellenteconomy.data;

import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.user.CoinsUser;
import su.nightexpress.excellenteconomy.user.UserBalance;
import su.nightexpress.excellenteconomy.user.data.CurrencySettings;
import su.nightexpress.nightcore.db.statement.RowMapper;
import su.nightexpress.nightcore.db.statement.template.InsertStatement;
import su.nightexpress.nightcore.db.statement.template.SelectStatement;
import su.nightexpress.nightcore.db.statement.template.UpdateStatement;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DataQueries {

    private DataQueries() {
    }

    public static final RowMapper<UserBalance> USER_BALANCE = resultSet -> {
        UserBalance balance = new UserBalance();

        DataColumns.currencies().forEach((id, column) -> {
            try {
                balance.add(id, column.readOrThrow(resultSet));
            }
            catch (SQLException exception) {
                exception.printStackTrace();
            }
        });

        return balance;
    };

    public static final RowMapper<CoinsUser> USER_LOADER = resultSet -> {
        try {
            UUID uuid = DataColumns.USER_UUID.readOrThrow(resultSet);
            String name = DataColumns.USER_NAME.readOrThrow(resultSet);
            Map<String, CurrencySettings> settingsMap = DataColumns.USER_SETTINGS.read(resultSet).orElse(
                new HashMap<>());

            long lastSeen = DataColumns.USER_LAST_SEEN.readOrThrow(resultSet);
            boolean hiddenFromTops = DataColumns.USER_HIDE_FROM_TOPS.readOrThrow(resultSet);

            UserBalance balance = Optional.ofNullable(USER_BALANCE.map(resultSet)).orElse(new UserBalance());

            return new CoinsUser(uuid, name, balance, settingsMap, lastSeen, hiddenFromTops);
        }
        catch (SQLException exception) {
            exception.printStackTrace();
            return null;
        }
    };

    @NonNull
    public static SelectStatement<CoinsUser> userSelect() {
        return SelectStatement.builder(DataQueries.USER_LOADER).build();
    }

    @NonNull
    public static InsertStatement<CoinsUser> userInsert() {
        var builder = InsertStatement.<CoinsUser>builder()
            .setUUID(DataColumns.USER_UUID, CoinsUser::getId)
            .setString(DataColumns.USER_NAME, CoinsUser::getName)
            .setString(DataColumns.USER_SETTINGS, user -> DataHandler.GSON.toJson(user.getSettingsMap()))
            .setLong(DataColumns.USER_LAST_SEEN, CoinsUser::getLastSeen)
            .setBoolean(DataColumns.USER_HIDE_FROM_TOPS, CoinsUser::isHiddenFromTops);

        DataColumns.currencies().forEach((id, column) -> {
            builder.setDouble(column, user -> user.getBalance().get(id));
        });

        return builder.build();
    }

    @NonNull
    public static UpdateStatement<CoinsUser> userUpdate() {
        var builder = UpdateStatement.<CoinsUser>builder()
            .setString(DataColumns.USER_NAME, CoinsUser::getName)
            .setString(DataColumns.USER_SETTINGS, user -> DataHandler.GSON.toJson(user.getSettingsMap()))
            .setLong(DataColumns.USER_LAST_SEEN, CoinsUser::getLastSeen)
            .setBoolean(DataColumns.USER_HIDE_FROM_TOPS, CoinsUser::isHiddenFromTops);

        DataColumns.currencies().forEach((id, column) -> {
            builder.setDouble(column, user -> user.getBalance().get(id));
        });

        return builder.build();
    }

    @NonNull
    public static UpdateStatement<CoinsUser> userTinyUpdate() {
        return UpdateStatement.<CoinsUser>builder()
            .setString(DataColumns.USER_NAME, CoinsUser::getName)
            .setLong(DataColumns.USER_LAST_SEEN, CoinsUser::getLastSeen)
            .build();
    }
}
