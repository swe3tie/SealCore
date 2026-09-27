package sealmc.swe3tie.sealcore.storage;

import java.util.UUID;

/** A stored player profile. */
public record PlayerRecord(UUID uuid, String name, long firstSeen, long lastSeen) {
}
