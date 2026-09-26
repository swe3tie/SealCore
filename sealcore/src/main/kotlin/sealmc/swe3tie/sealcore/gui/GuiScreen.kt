package sealmc.swe3tie.sealcore.gui

/**
 * A packet driven menu.
 *
 * Implementations declare their layout, fill [elements] and return content from
 * [render]. The manager takes care of opening, refreshing, navigating and
 * closing, and only the slots whose content actually changed are sent.
 */
abstract class GuiScreen {

    abstract val layout: GuiLayout

    val elements: MutableList<GuiElement> = mutableListOf()

    /** Fills the slot buffer that will be sent to the client. */
    open fun render(context: GuiContext, buffer: MutableMap<Int, GuiItem>) {
        for (element in elements) {
            when (element) {
                is ProgressBar -> element.renderInto(buffer, context)
                is PaginatedList -> element.renderInto(buffer, context)
                else -> element.render(context)?.let { buffer[element.slot] = it }
            }
        }
    }

    open fun onOpen(context: GuiContext) = Unit

    open fun onClose(context: GuiContext) = Unit

    fun elementAt(slot: Int): GuiElement? = elements.firstOrNull { it.slot == slot && it.visible }

    // --- convenience builders -------------------------------------------------

    protected fun button(slot: Int, item: GuiItem, action: (GuiContext) -> Unit = {}): Button =
        Button(slot, item, action).also { elements += it }

    protected fun icon(slot: Int, item: GuiItem): Icon = Icon(slot, item).also { elements += it }

    protected fun label(slot: Int, item: GuiItem): Label = Label(slot, item).also { elements += it }

    protected fun progressBar(
        startSlot: Int,
        length: Int,
        filled: GuiItem,
        empty: GuiItem,
        value: (GuiContext) -> Double,
    ): ProgressBar = ProgressBar(startSlot, length, filled, empty, value).also { elements += it }

    protected fun paginatedList(
        entries: MutableList<GuiItem>,
        topLeft: Int,
        width: Int,
        height: Int,
        onSelect: (GuiContext, Int) -> Unit = { _, _ -> },
    ): PaginatedList = PaginatedList(entries, topLeft, width, height, onSelect).also { elements += it }
}
