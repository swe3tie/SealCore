package sealmc.swe3tie.sealcore.storage;

import java.util.List;

/** One schema change, identified by version. Versions must never be reused. */
public record Migration(int version, String name, List<String> statements) {
}
