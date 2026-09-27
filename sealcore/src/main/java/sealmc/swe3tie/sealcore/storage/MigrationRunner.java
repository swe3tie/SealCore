package sealmc.swe3tie.sealcore.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Applies pending migrations and records them in {@code sealcore_meta}.
 *
 * <p>Each migration runs inside its own transaction, so a failure leaves the
 * schema at the last good version instead of half applied.
 */
public final class MigrationRunner {

    /** Prefix of the per version rows in {@code sealcore_meta}. */
    private static final String PREFIX = "schema_version:";

    private final Database database;
    private final Logger logger;

    public MigrationRunner(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void apply(List<Migration> migrations) {
        database.withConnection(connection -> {
            createMetaTable(connection);
            Set<Integer> applied = readAppliedVersions(connection);
            List<Migration> ordered = new ArrayList<>(migrations);
            ordered.sort(Comparator.comparingInt(Migration::version));

            Map<Integer, List<Migration>> duplicates = new HashMap<>();
            for (Migration migration : ordered) {
                duplicates.computeIfAbsent(migration.version(), key -> new ArrayList<>()).add(migration);
            }
            for (Map.Entry<Integer, List<Migration>> entry : duplicates.entrySet()) {
                if (entry.getValue().size() > 1) {
                    throw new IllegalArgumentException("Duplicate migration versions: " + entry.getKey());
                }
            }

            int count = 0;
            for (Migration migration : ordered) {
                if (applied.contains(migration.version())) {
                    continue;
                }
                runMigration(connection, migration);
                count++;
            }
            if (count > 0) {
                logger.info("Applied " + count + " migration(s); schema is at version " + lastVersion(ordered) + ".");
            } else {
                logger.info("Storage schema is up to date (version " + lastVersion(ordered) + ").");
            }
            return null;
        });
    }

    public int currentVersion() {
        return database.withConnection(connection -> {
            createMetaTable(connection);
            int highest = 0;
            for (int version : readAppliedVersions(connection)) {
                highest = Math.max(highest, version);
            }
            return highest;
        });
    }

    private void createMetaTable(Connection connection) throws SQLException {
        SqlDialect dialect = database.dialect();
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS sealcore_meta (
                    `key` %s NOT NULL,
                    `value` %s NOT NULL,
                    PRIMARY KEY (`key`)
                )
                """.formatted(dialect.textType(), dialect.textType()));
        }
    }

    private Set<Integer> readAppliedVersions(Connection connection) throws SQLException {
        // `key` is a reserved word in MySQL and a plain column everywhere else,
        // so the quoting is spelled out in both the DDL and the DML.
        Set<Integer> versions = new LinkedHashSet<>();
        try (var statement = connection.createStatement();
             var results = statement.executeQuery(
                 "SELECT `value` FROM sealcore_meta WHERE `key` LIKE '" + PREFIX + "%'")) {
            while (results.next()) {
                try {
                    versions.add(Integer.valueOf(results.getString(1)));
                } catch (NumberFormatException notAVersion) {
                    // A row that is not a version is ignored, not fatal.
                }
            }
        }
        // A build before per version rows existed wrote one row holding the
        // latest version, which stands for everything up to it.
        try (var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT `value` FROM sealcore_meta WHERE `key` = 'schema_version'")) {
            if (results.next()) {
                int latest;
                try {
                    latest = Integer.parseInt(results.getString(1));
                } catch (NumberFormatException notAVersion) {
                    latest = 0;
                }
                for (int version = 1; version <= latest; version++) {
                    versions.add(version);
                }
            }
        }
        return versions;
    }

    private void runMigration(Connection connection, Migration migration) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (var statement = connection.createStatement()) {
                for (String sql : migration.statements()) {
                    statement.execute(sql);
                }
            }
            // One row per version, so a rerun of the whole history skips what is
            // already there, and the legacy row stays for an older build.
            String upsert = database.dialect().upsert(List.of("`key`"), List.of("`value`"));
            for (String key : List.of(PREFIX + migration.version(), "schema_version")) {
                try (var statement = connection.prepareStatement(
                    "INSERT INTO sealcore_meta (`key`, `value`) VALUES (?, ?) " + upsert)) {
                    statement.setString(1, key);
                    statement.setString(2, Integer.toString(migration.version()));
                    statement.executeUpdate();
                }
            }
            connection.commit();
            logger.info("Migration " + migration.version() + " (" + migration.name() + ") applied.");
        } catch (SQLException failure) {
            connection.rollback();
            throw new IllegalStateException(
                "Migration " + migration.version() + " (" + migration.name() + ") failed", failure);
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static int lastVersion(List<Migration> ordered) {
        int highest = 0;
        for (Migration migration : ordered) {
            highest = Math.max(highest, migration.version());
        }
        return highest;
    }
}
