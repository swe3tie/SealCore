package sealmc.swe3tie.sealcore.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A unit of JDBC work that may fail.
 *
 * <p>{@link java.util.function.Function} cannot declare a checked exception, and
 * every statement in this package can fail, so the signature carries
 * {@link SQLException} and {@link Database#withConnection} turns it into the
 * unchecked failure the rest of the plugin expects.
 */
@FunctionalInterface
public interface SqlWork<T> {

    T apply(Connection connection) throws SQLException;
}
