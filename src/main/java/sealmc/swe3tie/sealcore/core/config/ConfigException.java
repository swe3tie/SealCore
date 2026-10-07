package sealmc.swe3tie.sealcore.core.config;

/** Thrown when a configuration or language file cannot be read, parsed or written. */
public final class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
