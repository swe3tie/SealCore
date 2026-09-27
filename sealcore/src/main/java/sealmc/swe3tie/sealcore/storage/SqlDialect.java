package sealmc.swe3tie.sealcore.storage;

import java.util.List;

/** Per-backend SQL differences, kept in one place so repositories stay portable. */
public enum SqlDialect {

    SQLITE("sqlite") {
        @Override
        public String primaryKey() {
            return "INTEGER PRIMARY KEY AUTOINCREMENT";
        }

        @Override
        public String uuidType() {
            return "TEXT";
        }

        @Override
        public String textType() {
            return "TEXT";
        }

        @Override
        public String timestampType() {
            return "INTEGER";
        }

        @Override
        public String createIndex(String name, String table, List<String> columns) {
            return "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + String.join(", ", columns) + ")";
        }

        @Override
        public String upsert(List<String> keyColumns, List<String> valueColumns) {
            return "ON CONFLICT(" + String.join(", ", keyColumns) + ") DO UPDATE SET "
                + String.join(", ", valueColumns.stream().map(column -> column + " = excluded." + column).toList());
        }
    },

    MYSQL("mysql") {
        @Override
        public String primaryKey() {
            return "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        }

        @Override
        public String uuidType() {
            return "VARCHAR(36)";
        }

        @Override
        public String textType() {
            return "TEXT";
        }

        @Override
        public String timestampType() {
            return "BIGINT";
        }

        // MySQL has no IF NOT EXISTS on an index; migrations run once per
        // version, so a second run never reaches this statement.
        @Override
        public String createIndex(String name, String table, List<String> columns) {
            return "CREATE INDEX " + name + " ON " + table + " (" + String.join(", ", columns) + ")";
        }

        @Override
        public String upsert(List<String> keyColumns, List<String> valueColumns) {
            return "ON DUPLICATE KEY UPDATE "
                + String.join(", ", valueColumns.stream().map(column -> column + " = VALUES(" + column + ")").toList());
        }
    };

    private final String id;

    SqlDialect(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public abstract String primaryKey();

    public abstract String uuidType();

    public abstract String textType();

    public abstract String timestampType();

    /** Index DDL, kept per dialect because the IF NOT EXISTS clause differs. */
    public abstract String createIndex(String name, String table, List<String> columns);

    /**
     * Trailing clause that turns {@code INSERT} into an upsert.
     *
     * @param keyColumns the conflict target (the primary key)
     * @param valueColumns the ones refreshed on conflict
     */
    public abstract String upsert(List<String> keyColumns, List<String> valueColumns);

    public static SqlDialect of(String id) {
        for (SqlDialect dialect : values()) {
            if (dialect.id.equalsIgnoreCase(id)) {
                return dialect;
            }
        }
        return SQLITE;
    }
}
