package sealmc.swe3tie.sealcore.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.util.logging.Logger;
import javax.sql.DataSource;
import sealmc.swe3tie.sealcore.config.SealCoreConfig;

/**
 * Owns the connection pool and hands out connections.
 *
 * <p>Callers always work through {@link #withConnection}; nothing outside this
 * class touches JDBC directly. The driver classes are declared in
 * {@code plugin.yml} and downloaded by the server at boot, so they are never
 * shaded into the jar.
 */
public final class Database implements AutoCloseable {

    private final SealCoreConfig.Storage config;
    private final File dataFolder;
    private final Logger logger;
    private final SqlDialect dialect;

    private HikariDataSource dataSource;

    public Database(SealCoreConfig.Storage config, File dataFolder, Logger logger) {
        this.config = config;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.dialect = SqlDialect.of(config.type().name());
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public boolean isOpen() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void open() {
        if (isOpen()) {
            return;
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("SealCore-Pool");
        hikari.setConnectionTimeout(config.connectionTimeoutMillis());
        hikari.setAutoCommit(true);

        if (dialect == SqlDialect.SQLITE) {
            File file = new File(dataFolder, config.fileName());
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite serialises writers; a larger pool only turns lock contention
            // into SQLITE_BUSY errors.
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA journal_mode=WAL");
        } else {
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + config.host() + ':' + config.port() + '/' + config.database()
                + "?useSSL=" + config.useSsl()
                + "&serverTimezone=UTC"
                + "&characterEncoding=utf8"
                + "&allowPublicKeyRetrieval=true");
            hikari.setUsername(config.username());
            hikari.setPassword(config.password());
            hikari.setMaximumPoolSize(config.poolSize());
        }

        dataSource = new HikariDataSource(hikari);
        String where = dialect == SqlDialect.SQLITE ? config.fileName() : config.host() + '/' + config.database();
        logger.info("Storage ready: " + dialect.id() + " (" + where + ")");
    }

    public DataSource dataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("Database is not open");
        }
        return dataSource;
    }

    /** Runs {@code block} with a pooled connection and closes it afterwards. */
    public <T> T withConnection(SqlWork<T> block) {
        try (Connection connection = dataSource().getConnection()) {
            return block.apply(connection);
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Storage call failed", failure);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
