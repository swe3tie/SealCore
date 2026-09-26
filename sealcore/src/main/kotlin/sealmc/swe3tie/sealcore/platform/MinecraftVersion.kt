package sealmc.swe3tie.sealcore.platform

import org.bukkit.Bukkit

/**
 * A Minecraft version such as `1.21.11`, `26.1.2` or `26.2`.
 *
 * Version parts are compared numerically and element-wise, which keeps the
 * ordering correct across the 1.21 -> 26 scheme change: `1.21.11` sorts before
 * `26.1.2` because `1 < 26`, and `26.1.2` sorts before `26.2` because `1 < 2`.
 */
data class MinecraftVersion(
    val raw: String,
    val parts: List<Int>,
) : Comparable<MinecraftVersion> {

    val major: Int get() = parts.firstOrNull() ?: 0

    /** True for the calendar-versioned releases (26.x and newer). */
    val isModern: Boolean get() = major >= 26

    val isKnown: Boolean get() = parts.isNotEmpty()

    override fun compareTo(other: MinecraftVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val result = (parts.getOrElse(index) { 0 }).compareTo(other.parts.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    override fun toString(): String = raw

    companion object {
        val UNKNOWN = MinecraftVersion("unknown", emptyList())

        val FIRST_SUPPORTED = MinecraftVersion("1.21.11", listOf(1, 21, 11))

        fun parse(raw: String?): MinecraftVersion {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return UNKNOWN
            val parts = value.split('.', '-').mapNotNull { segment ->
                segment.takeWhile(Char::isDigit).toIntOrNull()
            }
            return if (parts.isEmpty()) MinecraftVersion(value, emptyList()) else MinecraftVersion(value, parts)
        }

        fun detect(): MinecraftVersion = parse(runCatching { Bukkit.getMinecraftVersion() }.getOrNull())
    }
}
