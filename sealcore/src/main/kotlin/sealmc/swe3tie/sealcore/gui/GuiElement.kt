package sealmc.swe3tie.sealcore.gui

import org.bukkit.entity.Player

/**
 * One rendered slot.
 *
 * [render] is called on every refresh, so an element can show live data; the
 * [GuiSession] diffs the result against what the client already has and only
 * sends the slots that actually changed.
 */
abstract class GuiElement(
    var slot: Int,
    protected var item: GuiItem,
) {
    var visible: Boolean = true

    open fun update(item: GuiItem): GuiElement {
        this.item = item
        return this
    }

    open fun render(context: GuiContext): GuiItem? = if (visible) item else null

    open fun onClick(context: GuiContext, click: ClickInfo) = Unit
}

/** What the player did with a slot. */
data class ClickInfo(
    val slot: Int,
    val button: Int,
    val mode: ClickMode,
) {
    enum class ClickMode {
        /** Plain left or right click. */
        NORMAL,

        /** Shift click, which moves a stack instead of picking it up. */
        SHIFT,

        /** Number key swap. */
        SWAP,

        /** Middle click / creative clone. */
        MIDDLE,

        /** Double click. */
        DOUBLE,

        /** Any drop key. */
        DROP,

        /** Drag across slots. */
        DRAG,
    }
}

/** A slot that runs [action] when clicked. */
class Button(
    slot: Int,
    item: GuiItem,
    val action: (GuiContext) -> Unit = {},
) : GuiElement(slot, item) {

    override fun onClick(context: GuiContext, click: ClickInfo) {
        action(context)
    }
}

/** A slot that is shown but not interactive. */
open class Icon(
    slot: Int,
    item: GuiItem,
) : GuiElement(slot, item) {

    override fun onClick(context: GuiContext, click: ClickInfo) = Unit
}

/** A text-only slot. */
class Label(
    slot: Int,
    item: GuiItem,
) : Icon(slot, item)

/** A filled progress bar drawn as a row of segments. */
class ProgressBar(
    startSlot: Int,
    val length: Int,
    val filled: GuiItem,
    val empty: GuiItem,
    private val valueProvider: (GuiContext) -> Double = { 0.0 },
) : GuiElement(startSlot, empty) {

    private val segments = (0 until length).map { startSlot + it }.toList()
    var currentFill: Double = 0.0
        private set

    init {
        slot = startSlot
    }

    override fun render(context: GuiContext): GuiItem? = null

    /** Renders into the shared buffer rather than a single slot. */
    fun renderInto(buffer: MutableMap<Int, GuiItem>, context: GuiContext) {
        val clamped = valueProvider(context).coerceIn(0.0, 1.0)
        currentFill = clamped
        val fillCount = Math.round(clamped * length).toInt()
        segments.forEachIndexed { index, segmentSlot ->
            buffer[segmentSlot] = if (index < fillCount) filled else empty
        }
    }

    override fun onClick(context: GuiContext, click: ClickInfo) = Unit
}

/** A player head carrying a skin texture. */
class PlayerHead(
    slot: Int,
    item: GuiItem,
) : Icon(slot, item)

/**
 * A list that spans a rectangle of slots and pages through its entries.
 *
 * Owns its navigation buttons, so a feature module only supplies the entries
 * and the two button slots.
 */
class PaginatedList(
    val entries: MutableList<GuiItem>,
    val regionTopLeft: Int,
    val regionWidth: Int,
    val regionHeight: Int,
    private val onSelect: (GuiContext, Int) -> Unit = { _, _ -> },
) : GuiElement(regionTopLeft, entries.firstOrNull() ?: GuiItem("stone")) {

    var page: Int = 0
        private set

    val perPage: Int get() = regionWidth * regionHeight

    val pageCount: Int get() = if (perPage <= 0) 1 else maxOf(1, (entries.size + perPage - 1) / perPage)

    fun slotOf(index: Int): Int = regionTopLeft + index

    fun itemAt(index: Int): GuiItem? = entries.getOrNull(index)

    fun renderInto(buffer: MutableMap<Int, GuiItem>, context: GuiContext) {
        val start = page * perPage
        for (offset in 0 until perPage) {
            val index = start + offset
            val target = slotOf(offset)
            val entry = entries.getOrNull(index)
            if (entry == null) {
                buffer.remove(target)
            } else {
                buffer[target] = entry
            }
        }
    }

    fun nextPage() {
        if (page < pageCount - 1) page++
    }

    fun previousPage() {
        if (page > 0) page--
    }

    override fun onClick(context: GuiContext, click: ClickInfo) {
        val offset = click.slot - regionTopLeft
        if (offset < 0 || offset >= perPage) return
        onSelect(context, page * perPage + offset)
    }
}
