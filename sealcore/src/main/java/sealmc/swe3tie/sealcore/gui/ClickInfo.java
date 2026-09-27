package sealmc.swe3tie.sealcore.gui;

/** What the player did with a slot. */
public record ClickInfo(int slot, int button, ClickMode mode) {

    public enum ClickMode {
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
        DRAG
    }
}
