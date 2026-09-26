package sealmc.swe3tie.sealcore.module

import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleSectionTest {

    private enum class Mode { FAST, SAFE }

    private fun mode(section: ModuleSection, key: String = "mode", default: Mode = Mode.FAST): Mode =
        section.enumValue(key, default, Mode.entries.toTypedArray())


    private fun section(yaml: String): ModuleSection {
        val raw = YamlConfiguration()
        raw.loadFromString(yaml)
        return ModuleSection("jobs.miner", "modules/jobs-miner.yml", raw)
    }

    @Test
    fun `a deleted key falls back to the shipped default`() {
        // payout is present, the others were deleted by the operator.
        val section = section("payout: 5.0")
        assertEquals(5.0, section.double("payout", 10.0))
        assertEquals(10.0, section.double("missing", 10.0))
        assertEquals(7L, section.long("cooldown", 7L))
        assertEquals("hi", section.string("label", "hi"))
        assertTrue(section.bool("enabled", true))
        assertEquals(emptyList(), section.stringList("tags"))
        assertFalse(section.hasProblems)
    }

    @Test
    fun `a wrong type is reported with the file and the key`() {
        val section = section("payout: not-a-number")
        assertEquals(10.0, section.double("payout", 10.0))
        assertEquals(1, section.problems().size)
        assertTrue(section.problems().single().startsWith("modules/jobs-miner.yml :: payout"))
    }

    @Test
    fun `an out of range value is reported and falls back`() {
        val section = section("payout: -5.0")
        assertEquals(1.0, section.double("payout", 1.0, min = 0.0))
        assertTrue(section.hasProblems)
    }

    @Test
    fun `numbers in range are accepted at their bounds`() {
        val section = section("lo: 0\nhi: 10")
        assertEquals(0, section.int("lo", 5, min = 0, max = 10))
        assertEquals(10, section.int("hi", 5, min = 0, max = 10))
        assertFalse(section.hasProblems)
    }

    @Test
    fun `enum lookup ignores case`() {
        val section = section("mode: safe")
        assertEquals(Mode.SAFE, mode(section))
        assertFalse(section.hasProblems)
    }

    @Test
    fun `an unknown enum value names the valid options`() {
        val section = section("mode: reckless")
        assertEquals(Mode.FAST, mode(section))
        assertTrue(section.problems().single().contains("SAFE"))
    }

    @Test
    fun `lists and maps are read with their own element checks`() {
        val section = section("tags:\n  - a\n  - b\nprices:\n  coal: 1.5\n  bad: x\n")
        assertEquals(listOf("a", "b"), section.stringList("tags"))
        assertEquals(mapOf("coal" to 1.5), section.doubleMap("prices"))
        assertTrue(section.problems().single().contains("prices.bad"))
    }

    @Test
    fun `a non list where a list belongs is reported`() {
        val section = section("tags: 5")
        assertEquals(emptyList(), section.stringList("tags"))
        assertTrue(section.hasProblems)
    }

    @Test
    fun `every problem is collected, not just the first`() {
        val section = section("payout: x\nlabel: 5\ntags: 7")
        section.double("payout", 1.0)
        section.string("label", "d")
        section.stringList("tags")
        assertEquals(3, section.problems().size)
    }
}
