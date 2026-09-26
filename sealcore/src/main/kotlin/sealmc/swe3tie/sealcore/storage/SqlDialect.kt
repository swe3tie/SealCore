package sealmc.swe3tie.sealcore.storage

/** Per-backend SQL differences, kept in one place so repositories stay portable. */
enum class SqlDialect(val id: String) {

    SQLITE("sqlite") {
        override fun primaryKey(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
        override fun uuidType(): String = "TEXT"
        override fun textType(): String = "TEXT"
        override fun timestampType(): String = "INTEGER"
        override fun upsert(keyColumns: List<String>, valueColumns: List<String>): String =
            "ON CONFLICT(${keyColumns.joinToString()}) DO UPDATE SET " +
                valueColumns.joinToString { "$it = excluded.$it" }
    },

    MYSQL("mysql") {
        override fun primaryKey(): String = "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"
        override fun uuidType(): String = "VARCHAR(36)"
        override fun textType(): String = "TEXT"
        override fun timestampType(): String = "BIGINT"
        override fun upsert(keyColumns: List<String>, valueColumns: List<String>): String =
            "ON DUPLICATE KEY UPDATE " + valueColumns.joinToString { "$it = VALUES($it)" }
    };

    abstract fun primaryKey(): String

    abstract fun uuidType(): String

    abstract fun textType(): String

    abstract fun timestampType(): String

    /**
     * Trailing clause that turns `INSERT` into an upsert.
     *
     * [keyColumns] is the conflict target (the primary key), [valueColumns] the
     * ones refreshed on conflict.
     */
    abstract fun upsert(keyColumns: List<String>, valueColumns: List<String>): String

    companion object {
        fun of(id: String): SqlDialect = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SQLITE
    }
}
