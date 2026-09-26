package sealmc.swe3tie.sealcore

import sealmc.swe3tie.sealcore.platform.MinecraftVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinecraftVersionTest {

    @Test
    fun `parses dotted versions`() {
        val version = MinecraftVersion.parse("1.21.11")
        assertEquals(listOf(1, 21, 11), version.parts)
        assertEquals(1, version.major)
        assertFalse(version.isModern)
    }

    @Test
    fun `flags calendar versions as modern`() {
        assertTrue(MinecraftVersion.parse("26.1.2").isModern)
        assertTrue(MinecraftVersion.parse("26.2").isModern)
        assertFalse(MinecraftVersion.parse("1.21.11").isModern)
    }

    @Test
    fun `orders across the versioning scheme change`() {
        val ordered = listOf("1.21.11", "26.1.2", "26.2").map { MinecraftVersion.parse(it) }
        assertEquals(ordered, ordered.sorted())
        assertTrue(MinecraftVersion.parse("1.21.11") < MinecraftVersion.parse("26.1.2"))
        assertTrue(MinecraftVersion.parse("26.1.2") < MinecraftVersion.parse("26.2"))
    }

    @Test
    fun `treats missing parts as zero when comparing`() {
        // Equality keeps the raw string, so 26.2 and 26.2.0 are different values
        // that happen to order the same.
        assertEquals(0, MinecraftVersion.parse("26.2").compareTo(MinecraftVersion.parse("26.2.0")))
    }

    @Test
    fun `unknown input is not marked known`() {
        assertFalse(MinecraftVersion.parse(null).isKnown)
        assertFalse(MinecraftVersion.parse("  ").isKnown)
        assertEquals(MinecraftVersion.UNKNOWN, MinecraftVersion.parse(""))
    }

    @Test
    fun `keeps a prefixed release candidate parsable`() {
        val version = MinecraftVersion.parse("1.21.11-rc1")
        assertEquals(listOf(1, 21, 11), version.parts)
    }
}
