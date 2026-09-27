package sealmc.swe3tie.sealcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import sealmc.swe3tie.sealcore.platform.MinecraftVersion;

class MinecraftVersionTest {

    @Test
    void parsesDottedVersions() {
        var version = MinecraftVersion.parse("1.21.11");
        assertEquals(List.of(1, 21, 11), version.parts());
        assertEquals(1, version.major());
        assertFalse(version.isModern());
    }

    @Test
    void flagsCalendarVersionsAsModern() {
        assertTrue(MinecraftVersion.parse("26.1.2").isModern());
        assertTrue(MinecraftVersion.parse("26.2").isModern());
        assertFalse(MinecraftVersion.parse("1.21.11").isModern());
    }

    @Test
    void ordersAcrossTheVersioningSchemeChange() {
        var ordered = List.of("1.21.11", "26.1.2", "26.2").stream().map(MinecraftVersion::parse).toList();
        assertEquals(
            ordered.stream().map(MinecraftVersion::raw).toList(),
            ordered.stream().sorted().map(MinecraftVersion::raw).toList());
        assertTrue(MinecraftVersion.parse("1.21.11").compareTo(MinecraftVersion.parse("26.1.2")) < 0);
        assertTrue(MinecraftVersion.parse("26.1.2").compareTo(MinecraftVersion.parse("26.2")) < 0);
    }

    @Test
    void treatsMissingPartsAsZeroWhenComparing() {
        // Equality keeps the raw string, so 26.2 and 26.2.0 are different values
        // that happen to order the same.
        assertEquals(0, MinecraftVersion.parse("26.2").compareTo(MinecraftVersion.parse("26.2.0")));
    }

    @Test
    void unknownInputIsNotMarkedKnown() {
        assertFalse(MinecraftVersion.parse(null).isKnown());
        assertFalse(MinecraftVersion.parse("  ").isKnown());
        assertEquals(MinecraftVersion.UNKNOWN, MinecraftVersion.parse(""));
    }

    @Test
    void keepsAPrefixedReleaseCandidateParsable() {
        assertEquals(List.of(1, 21, 11), MinecraftVersion.parse("1.21.11-rc1").parts());
    }
}
