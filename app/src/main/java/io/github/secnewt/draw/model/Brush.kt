package io.github.secnewt.draw.model

/**
 * Brush widths in paper pixels (the paper is 3000 wide). Like a real pen on real paper,
 * the line looks thicker on screen when you zoom in.
 */
enum class BrushSize(val label: String, val width: Float) {
    Small("Small", 8f),
    Medium("Medium", 16f),
    Large("Large", 32f),
    ExtraLarge("Extra large", 64f),
}

enum class Tool(val label: String) {
    Pen("Pen"),
    Eraser("Eraser"),

    /** Tap inside an outline to fill it with the color. */
    Fill("Fill"),

    /** One finger drags the paper, for when two-finger moves are hard. */
    Move("Move"),
}

/** The tools in the switch under the paper. Move is under More. */
val SwitchTools = listOf(Tool.Pen, Tool.Eraser, Tool.Fill)
