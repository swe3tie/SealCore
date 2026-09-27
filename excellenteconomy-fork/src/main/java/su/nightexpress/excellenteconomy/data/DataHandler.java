package su.nightexpress.excellenteconomy.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.user.CoinsUser;
import su.nightexpress.excellenteconomy.user.data.CurrencySettings;
import su.nightexpress.excellenteconomy.user.data.CurrencySettingsSerializer;
import su.nightexpress.nightcore.db.AbstractDatabaseManager;
import su.nightexpress.nightcore.db.column.Column;
import su.nightexpress.nightcore.db.statement.condition.Operator;
import su.nightexpress.nightcore.db.statement.condition.Wheres;
import su.nightexpress.nightcore.db.statement.template.InsertStatement;
import su.nightexpress.nightcore.db.statement.template.SelectStatement;
import su.nightexpress.nightcore.db.statement.template.UpdateStatement;
import su.nightexpress.nightcore.db.table.Table;
import su.nightexpress.nightcore.user.data.UserDataSchema;
import su.nightexpress.nightcore.util.TimeUtil;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class DataHandler extends AbstractDatabaseManager<EconomyPlugin> implements UserDataSchema<CoinsUser> {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .registerTypeAdapter(CurrencySettings.class, new CurrencySettingsSerializer())
        .create();

    private final Table usersTable;

    private boolean synchronizationActive; // A little helper to pause synchronization during operations disable

    public DataHandler(@NonNull EconomyPlugin plugin) {
        super(plugin);
        this.setSynchronizationActive(true);

        this.usersTable = Table.builder(this.getTablePrefix() + "_users")
            .withColumn(DataColumns.ID)
            .withColumn(DataColumns.USER_UUID)
            .withColumn(DataColumns.USER_NAME)
            .withColumn(DataColumns.USER_LAST_SEEN)
            .withColumn(DataColumns.USER_SETTINGS)
            .withColumn(DataColumns.USER_HIDE_FROM_TOPS)
            .build();
    }

    @Override
    protected void onInitialize() {
        this.createTable(this.usersTable);

        this.dropColumn(this.usersTable, "dateCreated");
        this.dropColumn(this.usersTable, "last_online");
    }

    @Override
    protected void onClose() {
        DataColumns.clearCache();
    }

    @Override
    public void onPurge() {
        int period = this.config.getPurgePeriod();
        long deadline = TimeUtil.toEpochMillis(TimeUtil.getCurrentDateTime().minusDays(period));

        this.delete(this.usersTable, Wheres.where(DataColumns.USER_LAST_SEEN, Operator.SMALLER, o -> deadline));
    }

    @Override
    public void onSynchronize() {
        // Do not synchronize data if operations are disabled to prevent data loss/clash.
        if (!this.synchronizationActive) return;

        this.synchronizer.syncAll();
    }

    public void onCurrencyRegister(@NonNull ExcellentCurrency currency) {
        this.addCurrencyColumn(currency);
    }

    public void onCurrencyUnload(@NonNull ExcellentCurrency currency) {
        DataColumns.uncacheCurrency(currency);
    }

    public void setSynchronizationActive(boolean synchronizationActive) {
        this.synchronizationActive = synchronizationActive;
    }

    public void addCurrencyColumn(@NonNull ExcellentCurrency currency) {
        this.addColumn(this.usersTable, DataColumns.forCurrency(currency));
    }

    @Override
    @NonNull
    public Table getUsersTable() {
        return this.usersTable;
    }

    @Override
    @NonNull
    public Column<UUID> getUserIdColumn() {
        return DataColumns.USER_UUID;
    }

    @Override
    @NonNull
    public Column<String> getUserNameColumn() {
        return DataColumns.USER_NAME;
    }

    @Override
    @NonNull
    public SelectStatement<CoinsUser> getUserSelectStatement() {
        return DataQueries.userSelect();
    }

    @Override
    @NonNull
    public InsertStatement<CoinsUser> getUserInsertStatement() {
        return DataQueries.userInsert();
    }

    @Override
    @NonNull
    public UpdateStatement<CoinsUser> getUserUpdateStatement() {
        return DataQueries.userUpdate();
    }

    @Override
    @NonNull
    public UpdateStatement<CoinsUser> getUserTinyUpdateStatement() {
        return DataQueries.userTinyUpdate();
    }

    public void resetBalances(@NonNull ExcellentCurrency currency) {
        this.resetBalances(Set.of(currency));
    }

    public void resetBalances(@NonNull Collection<ExcellentCurrency> currencies) {
        UpdateStatement.Builder<Object> builder = UpdateStatement.builder();

        for (ExcellentCurrency currency : currencies) {
            builder.setDouble(DataColumns.forCurrency(currency), o -> currency.getStartValue());
        }

        this.update(this.usersTable, builder.build(), currencies);
    }
}
