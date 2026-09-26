package sealmc.swe3tie.sealcore.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.time.Duration

/**
 * MiniMessage helpers. Adventure is provided by the server, so nothing here is
 * shaded into the plugin jar.
 */
object Text {

    val mini: MiniMessage = MiniMessage.miniMessage()

    private val PLAIN = PlainTextComponentSerializer.plainText()

    fun parse(input: String): Component = mini.deserialize(input)

    fun parse(input: String, resolver: TagResolver): Component = mini.deserialize(input, resolver)

    /** Renders [template] with MiniMessage after substituting [pairs]. */
    fun render(template: String, vararg pairs: Pair<String, String>): Component {
        if (pairs.isEmpty()) return parse(template)
        val builder = TagResolver.builder()
        for ((key, value) in pairs) {
            builder.resolver(Placeholder.unparsed(key, value))
        }
        return parse(template, builder.build())
    }

    fun empty(): Component = Component.empty()

    /** Formats [Duration] as `1d 2h 3m`, dropping leading zero units. */
    fun duration(duration: Duration): String {
        val totalSeconds = duration.seconds
        if (totalSeconds <= 0) return "0m"
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return buildList {
            if (days > 0) add("${days}d")
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (seconds > 0 && days == 0L && hours == 0L) add("${seconds}s")
        }.joinToString(" ")
    }

    /**
     * Strips every MiniMessage tag and colour, for logging and console output.
     *
     * This is a plain text render, not another MiniMessage render: serialising
     * back to MiniMessage would leave the markup in the log, and console
     * commands have no renderer to interpret it.
     */
    fun plain(input: String): String = plain(parse(input))

    fun plain(component: Component): String = PLAIN.serialize(component)
}
