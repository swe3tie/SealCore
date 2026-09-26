package sealmc.swe3tie.sealcore.config

import org.bukkit.plugin.Plugin
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessagesTest {

    private fun plugin(root: Path): Plugin = Proxy.newProxyInstance(
        Plugin::class.java.classLoader,
        arrayOf(Plugin::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getDataFolder" -> root.toFile()
            "getLogger" -> logger
            "toString" -> "FakePlugin"
            "hashCode" -> 1
            "equals" -> false
            else -> null
        }
    } as Plugin

    private val logger: Logger = Logger.getLogger("SealCoreTest").apply { level = Level.OFF }

    private fun write(root: Path, name: String, body: String) {
        val directory = root.resolve("languages")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve(name), body)
    }

    @Test
    fun `language names are normalised to a file name`() {
        assertEquals("en.yml", Messages.normalise(null))
        assertEquals("en.yml", Messages.normalise("  "))
        assertEquals("en.yml", Messages.normalise("en"))
        assertEquals("EN.yml", Messages.normalise("EN.YML"))
        // Case is preserved, because jar resources are matched case sensitively.
        assertEquals("en-US.yml", Messages.normalise("en-US"))
    }

    @Test
    fun `a language typed in the wrong case still resolves`() {
        val root = Files.createTempDirectory("sealcore-lang")
        write(root, "en.yml", "core:\n  hi: 'hello'\n")

        assertEquals("hello", Messages.load(plugin(root), "EN.YML").text("core.hi"))
        assertEquals("hello", Messages.load(plugin(root), "en").text("core.hi"))
    }

    @Test
    fun `a key missing from the language falls back to english`() {
        val root = Files.createTempDirectory("sealcore-lang")
        write(root, "en.yml", "core:\n  hi: 'hello'\n  bye: 'bye'\n")
        write(root, "vi.yml", "core:\n  hi: 'chao'\n")

        val messages = Messages.load(plugin(root), "vi.yml")

        assertEquals("vi.yml", messages.language)
        assertEquals("en.yml", messages.fallbackLanguage)
        assertEquals("chao", messages.text("core.hi"))
        assertEquals("bye", messages.text("core.bye"))
    }

    @Test
    fun `a key missing everywhere renders the key itself`() {
        val root = Files.createTempDirectory("sealcore-lang")
        write(root, "en.yml", "core:\n  hi: 'hello'\n")
        write(root, "vi.yml", "core: {}\n")

        val messages = Messages.load(plugin(root), "vi.yml")

        assertEquals("core.gone", messages.text("core.gone"))
        assertFalse(messages.has("core.gone"))
        assertTrue(messages.has("core.hi"))
    }

    @Test
    fun `english alone needs no fallback`() {
        val root = Files.createTempDirectory("sealcore-lang")
        write(root, "en.yml", "core:\n  hi: 'hello'\n")

        val messages = Messages.load(plugin(root), "en.yml")

        assertEquals("en.yml", messages.language)
        assertNull(messages.fallbackLanguage)
        assertEquals("hello", messages.text("core.hi"))
    }

    @Test
    fun `placeholders are substituted`() {
        val root = Files.createTempDirectory("sealcore-lang")
        write(root, "en.yml", "core:\n  paid: 'got <white><amount></white>'\n")

        val messages = Messages.load(plugin(root), "en.yml")

        assertEquals("got 5", messages.text("core.paid", "amount" to "5"))
    }
}
