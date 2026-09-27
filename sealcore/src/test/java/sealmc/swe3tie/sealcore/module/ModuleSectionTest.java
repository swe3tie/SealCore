package sealmc.swe3tie.sealcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ModuleSectionTest {

    private enum Mode {
        FAST,
        SAFE
    }

    private static Mode mode(ModuleSection section) {
        return section.enumValue("mode", Mode.FAST, Mode.values());
    }

    private static ModuleSection section(String yaml) {
        var raw = new YamlConfiguration();
        try {
            raw.loadFromString(yaml);
        } catch (InvalidConfigurationException malformed) {
            throw new AssertionError(malformed);
        }
        return new ModuleSection("jobs.miner", "modules/jobs-miner.yml", raw);
    }

    @Test
    void aDeletedKeyFallsBackToTheShippedDefault() {
        // payout is present, the others were deleted by the operator.
        var section = section("payout: 5.0");
        assertEquals(5.0, section.decimal("payout", 10.0));
        assertEquals(10.0, section.decimal("missing", 10.0));
        assertEquals(7L, section.number("cooldown", 7L));
        assertEquals("hi", section.string("label", "hi"));
        assertTrue(section.bool("enabled", true));
        assertEquals(List.of(), section.stringList("tags"));
        assertFalse(section.hasProblems());
    }

    @Test
    void aWrongTypeIsReportedWithTheFileAndTheKey() {
        var section = section("payout: not-a-number");
        assertEquals(10.0, section.decimal("payout", 10.0));
        assertEquals(1, section.problems().size());
        assertTrue(section.problems().get(0).startsWith("modules/jobs-miner.yml :: payout"));
    }

    @Test
    void anOutOfRangeValueIsReportedAndFallsBack() {
        var section = section("payout: -5.0");
        assertEquals(1.0, section.decimal("payout", 1.0, 0.0, Double.MAX_VALUE));
        assertTrue(section.hasProblems());
    }

    @Test
    void numbersInRangeAreAcceptedAtTheirBounds() {
        var section = section("lo: 0\nhi: 10");
        assertEquals(0, section.integer("lo", 5, 0, 10));
        assertEquals(10, section.integer("hi", 5, 0, 10));
        assertFalse(section.hasProblems());
    }

    @Test
    void enumLookupIgnoresCase() {
        var section = section("mode: safe");
        assertEquals(Mode.SAFE, mode(section));
        assertFalse(section.hasProblems());
    }

    @Test
    void anUnknownEnumValueNamesTheValidOptions() {
        var section = section("mode: reckless");
        assertEquals(Mode.FAST, mode(section));
        assertTrue(section.problems().get(0).contains("SAFE"));
    }

    @Test
    void listsAndMapsAreReadWithTheirOwnElementChecks() {
        var section = section("tags:\n  - a\n  - b\nprices:\n  coal: 1.5\n  bad: x\n");
        assertEquals(List.of("a", "b"), section.stringList("tags"));
        assertEquals(Map.of("coal", 1.5), section.doubleMap("prices", 0.0, Double.MAX_VALUE));
        assertTrue(section.problems().get(0).contains("prices.bad"));
    }

    @Test
    void aNonListWhereAListBelongsIsReported() {
        var section = section("tags: 5");
        assertEquals(List.of(), section.stringList("tags"));
        assertTrue(section.hasProblems());
    }

    @Test
    void everyProblemIsCollectedNotJustTheFirst() {
        var section = section("payout: x\nlabel: 5\ntags: 7");
        section.decimal("payout", 1.0);
        section.string("label", "d");
        section.stringList("tags");
        assertEquals(3, section.problems().size());
    }
}
