package sealmc.swe3tie.sealcore.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import sealmc.swe3tie.sealcore.core.config.CoreSettings.StorageSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Shared SQL storage (SQLite or MySQL/MariaDB) for every module.
 * <p>
 * All queries run on a dedicated executor and return {@link CompletableFuture}s. Never block a
 * region/main thread on them; continue on the right scheduler instead. Modules version their
 * tables through {@link #migrate(String, List)}.
 */
public final class StorageService {

    private final Plugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private Dialect dialect = Dialect.SQLITE;
    private String tablePrefix = "sealcore_";

    public StorageService(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(@NotNull StorageSettings settings) throws Exception {
        this.dialect = settings.isMySql() ? Dialect.MYSQL : Dialect.SQLITE;
        this.tablePrefix = settings.tablePrefix();

        HikariConfig config = new HikariConfig();
        config.setPoolName("SealCore-" + this.dialect.name());
        config.setConnectionTimeout(settings.connectionTimeoutMs());
        config.setMaxLifetime(settings.maxLifetimeMs());

        int threads;
        if (this.dialect == Dialect.SQLITE) {
            Path dbFile = this.plugin.getDataFolder().toPath().resolve("data").resolve(settings.file());
            Files.createDirectories(dbFile.getParent());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
            // SQLite allows a single writer; one connection avoids SQLITE_BUSY entirely.
            config.setMaximumPoolSize(1);
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("foreign_keys", "true");
            threads = 1;
        } else {
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                + "?useSSL=" + settings.useSsl() + "&characterEncoding=utf8&useUnicode=true&serverTimezone=UTC");
            config.setUsername(settings.username());
            config.setPassword(settings.password());
            config.setMaximumPoolSize(settings.maximumPoolSize());
            config.setMinimumIdle(Math.min(settings.minimumIdle(), settings.maximumPoolSize()));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            threads = settings.maximumPoolSize();
        }

        this.dataSource = new HikariDataSource(config);
        this.executor = Executors.newFixedThreadPool(threads, namedFactory());

        try (Connection connection = this.dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.table("schema")
                + " (owner VARCHAR(64) NOT NULL PRIMARY KEY, version INT NOT NULL)");
        }
    }

    public void close() {
        if (this.executor != null) {
            this.executor.shutdown();
            try {
                if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    this.plugin.getLogger().warning("Storage tasks did not finish within 10s; forcing shutdown.");
                    this.executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (this.dataSource != null) this.dataSource.close();
    }

    public boolean isRunning() {
        return this.dataSource != null && this.dataSource.isRunning();
    }

    @NotNull
    public Dialect dialect() {
        return this.dialect;
    }

    /** Prefixed table name, e.g. {@code table("homes")} → {@code sealcore_homes}. */
    @NotNull
    public String table(@NotNull String name) {
        return this.tablePrefix + name;
    }

    /** Runs a query on the storage executor. */
    @NotNull
    public <T> CompletableFuture<T> query(@NotNull SqlFunction<Connection, T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = this.dataSource.getConnection()) {
                return work.apply(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, this.executor);
    }

    /** Runs an update on the storage executor. */
    @NotNull
    public CompletableFuture<Void> execute(@NotNull SqlConsumer<Connection> work) {
        return this.query(connection -> {
            work.accept(connection);
            return null;
        });
    }

    /**
     * Applies pending schema migrations for {@code owner} (usually a module id), in a transaction
     * per step. Blocks the calling thread: call from module enable, never from a gameplay handler.
     */
    public void migrate(@NotNull String owner, @NotNull List<Migration> migrations) throws SQLException {
        try (Connection connection = this.dataSource.getConnection()) {
            int current = 0;
            try (PreparedStatement ps = connection.prepareStatement("SELECT version FROM " + this.table("schema") + " WHERE owner = ?")) {
                ps.setString(1, owner);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) current = rs.getInt(1);
                }
            }

            List<Migration> pending = migrations.stream()
                .filter(m -> m.version() > 0)
                .sorted((a, b) -> Integer.compare(a.version(), b.version()))
                .toList();

            for (Migration migration : pending) {
                if (migration.version() <= current) continue;
                boolean auto = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    migration.apply(connection, this);
                    try (PreparedStatement ps = connection.prepareStatement(this.dialect.upsertSchema(this.table("schema")))) {
                        ps.setString(1, owner);
                        ps.setInt(2, migration.version());
                        ps.executeUpdate();
                    }
                    connection.commit();
                    current = migration.version();
                    this.plugin.getLogger().info("Applied " + owner + " schema v" + current);
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(auto);
                }
            }
        }
    }

    private ThreadFactory namedFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "SealCore-Storage-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> this.plugin.getLogger().log(Level.SEVERE, "Storage thread error", e));
            return thread;
        };
    }

    /** One schema step. Versions start at 1 and must be unique per owner. */
    public record Migration(int version, @NotNull SqlStep step) {
        void apply(Connection connection, StorageService storage) throws SQLException {
            this.step.apply(connection, storage);
        }
    }

    @FunctionalInterface
    public interface SqlStep {
        void apply(@NotNull Connection connection, @NotNull StorageService storage) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<I, O> {
        O apply(@NotNull I input) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer<I> {
        void accept(@NotNull I input) throws SQLException;
    }

    public enum Dialect {
        SQLITE {
            @Override
            String upsertSchema(String table) {
                return "INSERT INTO " + table + " (owner, version) VALUES (?, ?) ON CONFLICT(owner) DO UPDATE SET version = excluded.version";
            }
        },
        MYSQL {
            @Override
            String upsertSchema(String table) {
                return "INSERT INTO " + table + " (owner, version) VALUES (?, ?) ON DUPLICATE KEY UPDATE version = VALUES(version)";
            }
        };

        abstract String upsertSchema(String table);
    }
}
